# Compiler Package — Requirements, Flows, Logic, and Composition Rules

This document extracts the normative rules, data flows, algorithms, and
composition semantics implemented by the `io.nosqlbench.paramodel.engine.compiler`
package.  It is derived entirely from the current source code and the
paramodel API contracts it honours.

---

## 1. Pipeline Overview

Compilation transforms a `TestPlan` into an `ExecutionPlan` through eight
sequential stages.  Each stage reads from and writes to a shared
`CompilationContext`.  If any stage adds an error, the pipeline aborts
immediately.

```
TestPlan
  │
  ├─→ 1. Validation        ── verify plan correctness
  ├─→ 2. Normalization      ── canonicalize, derive scopes
  ├─→ 3. TrialEnumeration   ── expand parameter space into trials
  ├─→ 4. Instantiation      ── map elements to instance scopes
  ├─→ 5. StepGeneration     ── produce atomic steps and barriers
  ├─→ 6. DependencyAnalysis ── build execution graph, detect cycles
  ├─→ 7. Optimization       ── prune/merge redundant steps
  └─→ 8. CodeGeneration     ── assemble ExecutionPlan
```

### Pipeline Invariants

- Stages execute in the order above; no stage may run out of order.
- A stage MUST NOT read context state that a prior stage has not yet produced.
- A stage MUST report errors via the context, not throw exceptions.
- Stages SHOULD be idempotent.
- The pipeline halts on the first error; warnings do not halt.

---

## 2. Stage 1 — Validation

**Input**: `TestPlan` (from context)
**Output**: errors/warnings on context, validation metrics

### Definition-Level Validation

When operating on a `TestPlanDefinition` (standalone mode), the following
checks are applied in order:

1. **Basic structure**
   - Name must be non-null and non-blank (`MISSING_NAME`).
   - At least one element must be present (`NO_ELEMENTS`).

2. **Element validation**
   - Each element must have a non-blank id (`MISSING_ELEMENT_ID`).
   - Element ids must be unique within the plan (`DUPLICATE_ELEMENT_ID`).
   - Element fields are checked against the registered `ElementTypeDescriptor`:
     - Required fields must be present (`REQUIRED_FIELD`).
     - Forbidden fields must be absent (`FORBIDDEN_FIELD`).
     - Advisory fields produce warnings (`FIELD_ADVISORY`).

3. **Dependency validation**
   - No element may depend on itself (`SELF_DEPENDENCY`).
   - All dependency targets must reference existing element ids (`UNKNOWN_DEPENDENCY`).

4. **Cycle detection**
   - DFS traversal detects cycles in the dependency graph (`DEPENDENCY_CYCLE`).

5. **Scope validation**
   - An element may not depend on another element with a shorter-lived scope.
   - Specifically: ordinal comparison `upstream.scope < downstream.scope` is
     an error (`SCOPE_VIOLATION`).  PER_RUN > PER_GROUP > PER_TRIAL in
     lifetime.  A PER_RUN element cannot depend on a PER_TRIAL element.

6. **Axis validation**
   - Each axis must reference an existing element (`UNKNOWN_AXIS_ELEMENT`).
   - PER_RUN-scoped elements cannot have varied parameters (`AXIS_LOCALITY`).
   - Axes must have values or a min/max range (`EMPTY_AXIS_VALUES`).
   - CONCURRENT mode is only valid on the innermost axis for a given element
     (`CONCURRENT_NOT_INNERMOST`).

7. **Settings validation**
   - If `onFailure` is "retry", `retryCount` must be ≥ 1 (`INVALID_RETRY_COUNT`).

8. **Parameter completeness**
   - Every axis must specify a `parameter` name (`MISSING_AXIS_PARAMETER`).
   - Every binding must specify `parameter`, `element`, and `value`
     (`MISSING_BINDING_FIELD`).  The binding's element must exist.

9. **Cost warnings**
   - Trial count > 100 triggers a warning (`LARGE_TRIAL_COUNT`).
   - MUTUALLY_EXCLUSIVE edges with > 10 downstream invocations trigger a
     high-cost warning (`MUTUALLY_EXCLUSIVE_HIGH_COST`).

10. **INSTANCED_PER edge warnings**
    - INSTANCED_PER without a CONCURRENT axis on the downstream element
      triggers a warning (`INSTANCED_PER_WITHOUT_CONCURRENT`).

11. **Node sufficiency warnings**
    - INSTANCED_PER + CONCURRENT axes require infrastructure capacity.
      A warning is issued if no infrastructure-providing element is defined,
      or if capacity should be verified (`INSUFFICIENT_NODES`).

### Draft-Mode Validation

`validateDraft()` downgrades the following errors to informational notes
for incremental plan building:

- `NO_ELEMENTS`
- `UNKNOWN_DEPENDENCY`
- `UNKNOWN_AXIS_ELEMENT`

### Post-Composition Validation

`validateComposed()` runs after trial generation and checks:

- **Unstable reuse** (`UNSTABLE_REUSE`):  When a SHARED dependency edge
  exists but the upstream element's bindings change between adjacent
  trials, the SHARED optimisation is silently overridden.  A warning is
  emitted for the first such pair.

### Pipeline-Mode Validation (CompilationStage.execute)

When running as stage 1 of the compiler pipeline, validation:

1. Calls `testPlan.validate()` and maps failures to errors.
2. Warns if no axes or no elements are present.
3. Records `axes_count`, `elements_count`, `trial_space_size` metrics.

---

## 3. Stage 2 — Normalization

**Input**: `TestPlan` (from context)
**Output**: `SamplingConfig` in context as `"samplingConfig"`, derived
instancing scopes on `DefaultElement` instances

### Sampling Configuration Extraction

Reads axis-level tags and assembles a `SamplingConfig`:

| Tag                | Purpose                                         |
|--------------------|-------------------------------------------------|
| `repetitions`      | Number of times to repeat each combination      |
| `nesting`          | Controls which axis forms the outermost loop    |
| `sampling_type`    | `grid`, `linspace`, or `random`                 |
| `sampling_count`   | Number of samples for linspace/random           |
| `sampling_seed`    | Seed for random sampling                        |

If a `SamplingConfig` already exists in context (set by adopter code or
tests), it is not overwritten.

### Scope Derivation

The scope hierarchy encodes element lifetime:

```
PER_TRIAL (ordinal 0)  — shortest lifetime, fresh per trial
PER_GROUP (ordinal 1)  — persists for contiguous group with constant config
PER_RUN   (ordinal 2)  — longest lifetime, outermost group = entire run
```

PER_RUN is a logical placeholder for the outermost group — the element has
no varying axis, so its "group" spans the entire run.

For each `DefaultElement` without an explicit instancing scope:

**Pass 1 — Direct assignment:**

1. Read the `instancing_hint` tag on the element.
   - `"per_run"` → `PER_RUN`
   - `"per_trial"` → `PER_TRIAL`
   - `"per_group"` → `PER_GROUP`
   - absent → infer from axis targeting

2. Inference (when no hint is present):
   - If any axis declares `targetElement()` matching this element's name
     → `PER_GROUP` (persists for a contiguous group of trials with constant
     config, redeployed at group boundaries when the configuration
     fingerprint changes)
   - Otherwise → `PER_RUN` (outermost group — entire run)

PER_TRIAL is **only** assigned when explicitly declared (via hint or
builder).  It is never inferred from axis targeting.

**Pass 2 — Taint propagation (fixed-point):**

Repeat until no changes occur:

- If any dependency has a shorter-lived scope (lower ordinal) than the
  current element, promote the element to match:
  - PER_RUN element depending on PER_GROUP → promote to PER_GROUP
  - PER_RUN element depending on PER_TRIAL → promote to PER_TRIAL
  - PER_GROUP element depending on PER_TRIAL → promote to PER_TRIAL

This ensures scope is monotonically non-decreasing along the dependency
chain.

---

## 4. Stage 3 — Trial Enumeration

**Input**: `TestPlan` axes, optional `SamplingConfig` from context
**Output**: `List<Trial>` on context

### Pre-Generated Trials

If the plan already implements `Sequence` and is non-empty, those trials
are used directly (no enumeration).

### Degenerate Case

If there are no axes, a single trial with no varying parameters is
produced (the degenerate Cartesian product).

### Axis Partitioning

Axes are partitioned by `targetElement()`:

- **Element-targeted axes**: grouped by element id.
- **Global axes**: no `targetElement()`.

### Global Expansion (No Element Targeting)

1. Sort axes by nesting order (from `SamplingConfig` or natural index).
2. Apply the axis's sampling strategy (grid, random, linspace) to get
   effective values.
3. Compute Cartesian product via `CartesianExpander.expandAxis()`.
4. Apply repetitions via `CartesianExpander.applyRepetitions()`.
5. Wrap raw values in `DefaultValue` and `DefaultTrial`.

### Element-Aware Expansion

1. For each element with axes:
   a. Get base configuration from `element.configuration()`.
   b. Sort element axes by nesting order.
   c. Expand axes into binding combinations.
   d. Merge: base config values serve as defaults; axis values override.

2. Expand global axes independently.

3. **Cross-element Cartesian composition**: the final trial set is the
   Cartesian product across all element binding sets and global
   combinations.

4. Assignment keys are qualified as `"elementId.parameterName"`.

5. Apply repetitions.

### Trial Identity

Each trial receives a unique `CompactId` and `TrialMetadata` recording
its sequence index, generation method, and group.

---

## 5. Stage 4 — Instantiation

**Input**: `List<Trial>`, `TestPlan` elements and axes
**Output**: `ElementInstance` entries in context

### Algorithm

Elements are topologically sorted (dependencies first).

For each element, determine whether its configuration varies across trials
using a three-tier strategy:

**Tier 1 — Explicit scope**: If `instancingScope()` is present, the
element varies iff scope ≠ `PER_RUN`.

**Tier 2 — Axis targeting**: If any axis's `targetElement()` matches
this element's name, the element varies.

**Tier 3 — Parameter matching**: If any axis matches an element parameter
(by underlying parameter equality, qualified name, or simple name), the
element varies.

**Instance creation:**

- **Varies** (per-trial):  One `ElementInstance` per trial.
  Dependencies are resolved per-trial from the context instance registry.

- **Does not vary** (global):  One `ElementInstance` covering all trials.
  Dependencies are resolved using a representative trial (the first).

Instances record: `instanceId`, `element`, `trials`, `scopeDescription`,
and `dependsOn` (set of upstream instance ids).

---

## 6. Stage 5 — Step Generation

**Input**: `List<Trial>`, `List<ElementInstance>`, `TestPlan` elements
**Output**: `List<AtomicStep>` and `List<Barrier>` on context

This is the core composition stage.  It converts the abstract plan into
a concrete, ordered step sequence.

### Element Classification

After topological sorting, non-global elements are classified:

| Category         | Condition                                        | Behaviour                         |
|------------------|--------------------------------------------------|-----------------------------------|
| **PER_RUN**      | Instance scope description is `"global"`         | Single deploy at start, single teardown at end |
| **PER_TRIAL**    | Explicit `PER_TRIAL` scope (`isScopeExplicit()`) | Fresh instance per trial, eager teardown after each trial |
| **PER_GROUP**    | PER_GROUP scope (inferred from axis targeting)   | Persists for contiguous group of trials with constant config; redeployed at group boundaries |

### Barrier Types

Three barrier types are used for lifecycle coordination:

| Barrier Type         | Emitted when                                    | Purpose                                        |
|----------------------|-------------------------------------------------|------------------------------------------------|
| `ELEMENT_READY`      | After deploy of element with health check       | Downstream steps await element readiness       |
| `ELEMENT_SCOPE_END`  | At group boundaries and before final teardown   | All trials using this instance have completed  |
| `TRIAL_BATCH`        | At each trial boundary (when PER_GROUP elements exist) | Marks trial completion for group-boundary sync |

### Three-Phase Algorithm

#### Phase 1 — PER_RUN Deployment (Outermost Group)

For each element in topological order whose scope is global:

1. Compute dependencies from `lastStepForElement` map.
2. Build configuration from first trial.
3. Emit `DeployElement` step with `scope=PER_RUN, phase=setup`.
4. If the element has a health check, emit an `ELEMENT_READY` barrier
   after the deploy.  Downstream steps depend on the barrier step instead
   of the deploy step, so they wait for the element to report readiness
   via `OperationalStateObservable`.
5. Update `lastStepForElement`.

#### Phase 2 — Per-Trial Steps

For each trial, in sequence:

**2a. PER_GROUP Group-Boundary Detection**

For each PER_GROUP element in topological order:

1. Compute the element's **configuration fingerprint** for this trial.
2. If the fingerprint differs from the previous trial (or is absent):
   a. **Teardown** the previous instance (if any):
      - In REVERSE topological (LIFO) order.
      - Emit `ELEMENT_SCOPE_END` barrier before each teardown (signals
        that all trials using this element instance have completed).
        The barrier depends on `lastSequentialExecId`.
      - Each teardown depends on the `ELEMENT_SCOPE_END` barrier step
        (not the exec step directly), preventing race with in-progress
        trials.
      - Metadata: `reason=group_boundary, trial_index=N`.
   b. **Deploy** the new instance:
      - In FORWARD topological order.
      - Dependencies: dependency deploys + own last step (ensuring
        teardown completes before redeploy).
      - Enforces `max_concurrency` sliding window if set.
      - If the element has a health check, emit an `ELEMENT_READY`
        barrier after the deploy.
      - Metadata: `scope=PER_GROUP, trial_index=N`.

**2b. PER_TRIAL Deployment**

For each PER_TRIAL element in topological order:

1. Compute dependencies from merged step map (PER_RUN + PER_GROUP + this
   trial's PER_TRIAL deploys).
2. Enforce `max_concurrency` via sliding window tracking teardown step ids.
3. Emit `DeployElement` step with `scope=PER_TRIAL`.

**2c. Execute Trial**

1. Build `elementBindings` map (element name → instance id) for all
   elements.
2. Dependencies: all PER_RUN and PER_GROUP element last steps, plus this
   trial's PER_TRIAL deploy steps.
3. Emit `ExecuteTrial` step.

**2d. Eager PER_TRIAL Teardown**

For each PER_TRIAL element in REVERSE topological (LIFO) order:

1. Emit `TeardownElement` step depending on the trial's `ExecuteTrial`.
2. Track teardown in concurrency window for future deploy enforcement.

**2e. Trial Boundary Barrier**

If PER_GROUP elements exist (trials are not fully independent):

1. Emit `BarrierSync` step depending on the trial's execution step.
2. Emit a `DefaultBarrier` with `type=TRIAL_BATCH`.

When all non-global elements are PER_TRIAL, no barrier is emitted (each
trial is self-contained).

#### Phase 3 — Final Teardown

In reverse topological order, for elements that are NOT PER_TRIAL
(PER_TRIAL elements were already torn down eagerly in Phase 2):

1. Dependencies: the element's own last step PLUS all trial execution
   steps (PER_RUN and PER_GROUP elements may be used by parallel trials).
2. Emit `ELEMENT_SCOPE_END` barrier before each final teardown, depending
   on the above dependencies.
3. Emit `TeardownElement` depending on the barrier step, with
   `phase=cleanup` and `collectArtifacts=true`.

### Configuration Fingerprint

The fingerprint uniquely identifies an element's configuration for a
given trial.  It incorporates dependency fingerprints so that when a
dependency's configuration changes at a group boundary, the dependent
element is also redeployed.

1. Collect sorted parameter fingerprints from `trial.assignment()` for
   each of the element's formal parameters.
2. Collect sorted parameter fingerprints for axes targeting this element
   via `element.name() + "." + axis.name()` qualified keys.
3. Recursively compute dependency fingerprints for each direct dependency
   and include them with the `"__dep:"` prefix.  The dependency graph is
   acyclic (validated upstream), so recursion terminates.
4. Concatenate with `"|"` separator.
5. If no varying parameters or dependencies exist:
   - PER_TRIAL scope: `"per_trial:" + elementName + ":" + trialId`
     (forces unique fingerprint per trial).
   - Otherwise: `"static:" + elementName` (fingerprint never changes).

### Step Dependency Rules

| Step type              | Depends on                                                |
|------------------------|-----------------------------------------------------------|
| Deploy (PER_RUN)       | Deploy steps of dependency elements                       |
| Deploy (PER_GROUP)     | Dependency deploys + own prior teardown + max_concurrency window |
| Deploy (PER_TRIAL)     | Merged map (PER_RUN + PER_GROUP + this trial's deploys) + max_concurrency window |
| ExecuteTrial           | All PER_RUN/PER_GROUP last steps + this trial's PER_TRIAL deploys |
| Teardown (eager)       | This trial's ExecuteTrial                                 |
| Teardown (group boundary)| `ELEMENT_SCOPE_END` barrier step (which depends on exec step) |
| Teardown (final)       | `ELEMENT_SCOPE_END` barrier step (which depends on own last step + ALL exec steps) |
| `ELEMENT_READY` barrier| The deploy step it follows                                |
| `ELEMENT_SCOPE_END` barrier| Previous trial's exec step (group boundary) or all exec steps (final) |

### Max Concurrency Enforcement

When an element specifies `max_concurrency`:

1. A `Deque<String>` (sliding window) tracks the oldest active step ids.
2. When the window is full (size ≥ max_concurrency), the oldest step is
   removed from the deque and added as a dependency of the new deploy.
3. For PER_TRIAL elements, the window tracks teardown step ids (so a new
   deploy waits for the oldest instance to finish tearing down).

### Topological Sort

Uses Kahn's algorithm:
1. Build in-degree map and adjacency list from `element.dependencies()`.
2. Seed queue with zero-in-degree elements.
3. Dequeue, add to result, decrement dependents.

---

## 7. Stage 6 — Dependency Analysis

**Input**: `List<AtomicStep>` from context
**Output**: `DefaultExecutionGraph` stored as `"executionGraph"` in context

1. Constructs a `DefaultExecutionGraph` from the step list.
2. Validates acyclicity via Kahn's algorithm; errors if cycles exist.
3. Computes parallel waves (steps groupable by execution level).
4. Records `dependencies_analyzed`, `parallel_waves`, `max_parallelism` metrics.

---

## 8. Stage 7 — Optimization

**Input**: `List<AtomicStep>` from context, `OptimizationStrategy` from plan
**Output**: modified step list on context

### Strategy Selection

```
OptimizationStrategy.NONE           → skip all passes
OptimizationStrategy.BASIC          → PruneRedundant only
OptimizationStrategy.PRUNE_REDUNDANT→ PruneRedundant only
OptimizationStrategy.AGGRESSIVE     → all passes
```

### PruneRedundantPass

Removes unnecessary teardown/deploy pairs for PER_GROUP elements whose
config is unchanged between adjacent trials:

1. Collect all `DeployElement` steps with `scope=PER_GROUP`, grouped by
   element id, maintaining trial order.
2. For each element with ≥ 2 deploys:
   - Compare adjacent deploy configurations.
   - If configurations are equal:
     a. Mark the later deploy as redundant.
     b. Find the `TeardownElement` for this element with
        `reason=group_boundary` and matching `trial_index`.
     c. Mark that teardown as redundant.
3. Remove all redundant steps.
4. Record `steps_pruned` metric.

### MergeEquivalentPass

Placeholder — currently a no-op for future step merging.

---

## 9. Stage 8 — Code Generation

**Input**: steps, barriers, execution graph from context
**Output**: `DefaultExecutionPlan` stored as `"executionPlan"` in context

1. Read steps, barriers, execution graph (falls back to building graph
   if not already present).
2. Assemble `DefaultExecutionPlan` with:
   - Unique plan id (CompactId)
   - Fingerprint: `"compiled:" + planName + ":" + stepCount`
   - Trial ordering: `SEQUENTIAL`
3. Store in context and record `code_generated` metric.

---

## 10. Compilation Context Data Flow

| Context key          | Set by stage       | Read by stage(s)                |
|----------------------|--------------------|---------------------------------|
| `testPlan()`         | (input)            | All stages                      |
| `options()`          | (input)            | Optimization, Validation        |
| `"normalized_plan"`  | Normalization      | —                               |
| `"samplingConfig"`   | Normalization      | TrialEnumeration                |
| `trials()`           | TrialEnumeration   | Instantiation, StepGeneration   |
| `elementInstances()` | Instantiation      | StepGeneration                  |
| `steps()`            | StepGeneration     | DependencyAnalysis, Optimization, CodeGeneration |
| `barriers()`         | StepGeneration     | CodeGeneration                  |
| `"executionGraph"`   | DependencyAnalysis | CodeGeneration                  |
| `"executionPlan"`    | CodeGeneration     | DefaultCompiler (output)        |

---

## 11. Export Resolution

`ExportResolver` validates runtime token references in element
configurations:

### `${element.export}` Tokens

Pattern: `${elementId.exportName}`

- The referenced element must exist in the plan.
- The referenced element must declare the named export in `exports()`.
- Validation is at composition time; actual value substitution happens at
  deploy time when upstream elements are running.

### `${output_of:element}` Tokens

Pattern: `${output_of:elementId}`

- The referenced element must exist.
- The referenced element must be of COMMAND type (only commands produce
  output).
- The referenced element must be upstream in the dependency graph
  (transitive closure).

---

## 12. Composition Rules Summary

### Rule 1: Scope Determines Lifecycle

| Scope     | Deploy              | Teardown              | Instance count    |
|-----------|---------------------|-----------------------|-------------------|
| PER_RUN   | Once at start       | Once at end           | 1                 |
| PER_GROUP | At group start      | At group boundary     | 1 at a time       |
| PER_TRIAL | Per trial           | Eagerly per trial     | 1 per trial       |

PER_RUN is the degenerate outermost group — the element has no varying
axis, so its "group" spans the entire run.  PER_GROUP is the default for
axis-targeted elements.  PER_TRIAL is only assigned by explicit
declaration, never inferred.

### Rule 2: Scope Must Be Monotonically Non-Decreasing Along Dependencies

A longer-lived element cannot depend on a shorter-lived one.
`PER_RUN` ≥ `PER_GROUP` ≥ `PER_TRIAL` in lifetime (ordinal comparison).

### Rule 3: Teardown Order Is Reverse Topological (LIFO)

If A depends on B, then A is torn down before B.  Within a trial,
PER_TRIAL teardowns execute in reverse topological order.  PER_GROUP
group-boundary teardowns also follow LIFO order relative to the
dependency graph.

### Rule 4: Deploy Order Is Forward Topological

If A depends on B, then B is deployed before A.

### Rule 5: Fingerprint-Based Group-Boundary Detection

PER_GROUP elements are only torn down and redeployed when the
configuration fingerprint changes between adjacent trials.  When the
fingerprint is unchanged, the element continues running across the trial
boundary.  The fingerprint incorporates dependency fingerprints, so a
dependent element is also redeployed when its dependency's configuration
changes.

### Rule 6: PER_TRIAL Elements Are Eagerly Cleaned Up

PER_TRIAL elements are torn down immediately after their trial's
execution step completes.  They do not persist into subsequent trials.

### Rule 7: Trial Boundary Barriers Are Conditional

TRIAL_BATCH barriers at trial boundaries are only emitted when PER_GROUP
elements exist.  When all non-global elements are PER_TRIAL, trials are
fully independent and no inter-trial synchronisation is needed.

### Rule 8: Max Concurrency Is Enforced Via Sliding Window

When `max_concurrency` is set on an element, a deque-based sliding
window ensures that no more than N instances are active simultaneously.
New deploys wait for the oldest tracked step to complete.

### Rule 9: Execution Step Dependencies Are Comprehensive

An `ExecuteTrial` step depends on:
- All PER_RUN element last steps (deploy or ELEMENT_READY barrier).
- All PER_GROUP element last steps for the current trial.
- All PER_TRIAL element deploy steps for the current trial.

### Rule 10: Final Teardown Waits For All Trials

PER_RUN and PER_GROUP elements' final teardowns are preceded by an
`ELEMENT_SCOPE_END` barrier that depends on ALL trial execution steps,
ensuring no in-flight trial is using the element when teardown begins.

### Rule 11: Group-Boundary Teardowns Are Barrier-Sequenced

A PER_GROUP group-boundary teardown depends on an `ELEMENT_SCOPE_END`
barrier step (which in turn depends on the previous trial's execution
step), preventing teardown from racing with an in-progress trial.

### Rule 12: Scope Derivation Propagates Through Dependencies

If an element has no explicit scope but depends on a PER_GROUP element,
it is promoted to PER_GROUP via taint propagation (fixed-point
iteration using ordinal comparison).

### Rule 13: Axis Targeting Implies PER_GROUP

If any axis declares `targetElement()` matching an element, that element
is inferred as PER_GROUP (not PER_TRIAL).  PER_TRIAL is only assigned
when explicitly declared via hint or builder.

### Rule 14: Explicit PER_TRIAL vs Inferred PER_GROUP

Only elements with **explicitly declared** PER_TRIAL scope
(`isScopeExplicit() == true`) receive fresh independent instances per
trial.  Elements inferred from axis targeting receive PER_GROUP scope
and use fingerprint-based group lifecycle.

### Rule 15: Optimization Respects Step Metadata

The PruneRedundant optimisation pass uses step metadata (`scope`,
`reason`, `trial_index`) to identify redundant pairs.  It only targets
`PER_GROUP`-scoped deploys and `group_boundary` teardowns.

### Rule 16: The Dependency Graph Must Be Acyclic

Both the element dependency graph (validated in Stage 1) and the step
dependency graph (validated in Stage 6) must be acyclic.  Cycle detection
uses DFS (Stage 1) and Kahn's algorithm (Stage 6).

### Rule 17: Cross-Element Trials Are Cartesian Products

When axes target different elements, the final trial set is the Cartesian
product of each element's independent axis expansion.  Assignment keys
are qualified as `elementId.parameterName`.

### Rule 18: Element Configuration Serves As Default

An element's `configuration()` map provides base values.  When an axis
targets the element and varies a parameter, the axis value overrides the
configuration value for that trial.

### Rule 19: Health Check Timing Is Host-System Owned

Element health checks carry only timing parameters (timeout, maxRetries,
retryInterval).  The health check mechanism (HTTP, TCP, etc.) is a
host-system concern.  The paramodel compiler uses health check presence
to emit `ELEMENT_READY` barriers after deploys.

### Rule 20: ELEMENT_READY Barriers Gate Downstream Steps

When an element has a health check, an `ELEMENT_READY` barrier is
emitted after its deploy step.  The barrier is satisfied at runtime when
the element reports readiness via `OperationalStateObservable`.
Downstream steps depend on the barrier step, not the deploy step
directly.
