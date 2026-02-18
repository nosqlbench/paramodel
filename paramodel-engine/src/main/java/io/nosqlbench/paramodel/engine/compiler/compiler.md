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
   - EXCLUSIVE dependency edges with > 10 downstream invocations trigger a
     high-cost warning (`EXCLUSIVE_HIGH_COST`).

10. **Node sufficiency warnings**
    - CONCURRENT axes require infrastructure capacity.
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
scopes on `DefaultElement` instances

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

For each `DefaultElement` without an explicit scope:

**Pass 1 — Direct assignment:**

1. Inference from axis targeting:
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
using a two-tier strategy:

**Tier 1 — Axis targeting**: If any axis's `targetElement()` matches
this element's name, the element varies.

**Tier 2 — Parameter matching**: If any axis matches an element parameter
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

### Design Rules (in order of precedence)

The step generation algorithm is governed by three design rules that
determine which elements are trial elements and when each element deploys
relative to the trial notification scope:

1. **Trial element identity**: Trial elements are the innermost leaf
   nodes in the dependency graph, even when the innermost layer is also
   the outermost layer (i.e. all elements are run-scoped).  Leaf-node
   detection always applies — binding depth does not disqualify an
   element from being a trial element.
2. **Notification scope containment**: The full lifecycle of every trial
   element — deploy, execute, completion — must fall within the
   NotifyTrialStart / NotifyTrialEnd bracket.
3. **Non-trial elements as notification receivers**: Non-trial elements
   deploy *before* NotifyTrialStart so they are running and able to
   observe trial lifecycle events.

Rule 1 determines *which* elements are trial elements.  Rules 2 and 3
together determine *when* each element deploys relative to the
notification scope.  When rules conflict (e.g. a run-scoped element
that is also a trial element), the higher-precedence rule wins: the
element deploys within the notification scope (rule 2) rather than
alongside non-trial elements (rule 3).

### Element Lifecycle

All elements — regardless of binding depth — are processed through
a single fingerprint-based mechanism.  Run-scoped (depth-0) elements
naturally deploy on trial 0 and persist (constant fingerprint), while
bound elements redeploy when their fingerprint changes.

| Binding Depth | Fingerprint Behaviour | Deploy Timing |
|---------------|----------------------|---------------|
| 0 (run-scoped)| Constant (`"static:<name>"`) | Trial 0 only |
| >0 (bound)    | Changes when axis values change | At group boundaries |

### Barrier Types

Two barrier types are used for lifecycle coordination:

| Barrier Type         | Emitted when                                    | Purpose                                        |
|----------------------|-------------------------------------------------|------------------------------------------------|
| `ELEMENT_READY`      | After deploy of element with health check       | Downstream steps await element readiness       |
| `TRIAL_BATCH`        | At each trial boundary (when PER_GROUP elements exist) | Marks trial completion for group-boundary sync |

> **Note:** `ELEMENT_SCOPE_END` barriers are no longer emitted. Teardown
> steps depend directly on execution steps, reducing plan complexity.

### Unified Per-Trial Algorithm

All elements — regardless of binding depth — are processed through a
single fingerprint-based mechanism.  There is no separate "Phase 1" for
run-scoped elements; they are handled by the same per-trial loop and
naturally deploy on trial 0 (their fingerprint is constant).

For each trial, in sequence:

**1. Fingerprint Check**

For each element sorted by ascending binding depth (stable preserves
topo order within each depth level):

1. Compute the element's **configuration fingerprint** for this trial.
2. If the fingerprint differs from the previous trial (or is absent):
   a. Mark for teardown (if previously deployed).
   b. Mark for deploy.
   c. Update stored fingerprint.

Run-scoped elements (depth 0) have fingerprint `"static:<name>"` which
never changes, so they deploy on trial 0 and persist for the entire run.

**2. Teardown at Group Boundaries**

For elements marked for teardown, in REVERSE dependency order:

- Each teardown depends on the last execution step and on teardowns of
  elements that depend on it (reverse dependency ordering allows
  independent elements to tear down concurrently).
- COMMAND trial elements and lifeline-subsumed elements skip teardown.

**3. Deploy Non-Trial Elements**

Non-trial elements from the deploy list are deployed in ascending depth
order (Design Rule 3 — they are notification receivers and must be
running before NotifyTrialStart).

**4. NotifyTrialStart**

Opens the trial notification scope.  Depends on all deployed elements'
last steps.  Non-trial elements are already deployed and will receive
this notification.

**5. Deploy Trial Elements**

Trial elements from the deploy list are deployed after NotifyTrialStart,
with NotifyTrialStart as an extra dependency (Design Rule 2 — their
full lifecycle must fall within the notification scope).  This includes
both bound and run-scoped trial elements.

**6. Operative Steps**

For each trial element in topological order, emit `TrialStep` (SERVICE)
or `AwaitElement` (COMMAND).  Dependencies include NotifyTrialStart and
any intra-trial element dependencies.

**7. NotifyTrialEnd**

Closes the notification scope.  Depends on ALL operative steps of this
trial.

**8. Predictive Eager Teardown**

Elements whose fingerprint will change for the next trial are torn down
eagerly in reverse dependency order to free resources sooner.  The
stored fingerprint is cleared so step 1 of the next trial deploys fresh.

#### Final Teardown

After all trials complete, in reverse dependency order:

1. Elements not already eagerly torn down receive a final teardown step.
2. COMMAND trial elements and lifeline-subsumed elements are skipped.
3. Teardowns depend on teardowns of elements that depend on them
   (concurrent where independent).
4. `TeardownElement` steps carry `collectArtifacts=true`.

### Step Dependency Rules

| Step type              | Depends on                                                |
|------------------------|-----------------------------------------------------------|
| Deploy (non-trial)     | Dependency deploys + own prior teardown + max_concurrency window |
| Deploy (trial)         | Same as non-trial + NotifyTrialStart                      |
| NotifyTrialStart       | All deployed elements' last steps (minimal deps by default)|
| TrialStep / AwaitElement| NotifyTrialStart + intra-trial element dependencies       |
| NotifyTrialEnd         | All operative steps of this trial                         |
| Teardown (predictive)  | Operative step or NotifyTrialEnd + reverse dep teardowns  |
| Teardown (group boundary)| Last execution step + reverse dep teardowns              |
| Teardown (final)       | All trial-end steps + reverse dep teardowns               |
| `ELEMENT_READY` barrier| The deploy step it follows                                |

### Max Concurrency Enforcement
...
### Rule 9: Execution Step Dependencies Are Comprehensive

A `TrialStep` (or `AwaitElement`) depends on:
- NotifyTrialStart (or all deployed elements' last steps if no trial elements).
- Intra-trial element dependencies (topological ordering of trial elements).

### Rule 10: Final Teardown Waits For All Trials and Dependent Teardowns

Final teardown steps depend on the latest step from each trial
(NotifyTrialEnd or last predictive teardown) and on teardowns of
elements that depend on them (reverse dependency ordering).

### Rule 10a: Lifeline Dependencies Skip Teardown

When ALL of an element's dependencies are marked as lifeline, the
element's teardown step is omitted in Phase 3 final teardown and in
Phase 2 PER_GROUP group-boundary teardown. The upstream element's
teardown implicitly destroys the downstream. This only applies to
final and group-boundary teardowns — PER_TRIAL eager teardowns in
Phase 2 still occur because the upstream remains alive between trials
and the downstream instance needs recycling.

If an element has a mix of lifeline and non-lifeline dependencies,
it still receives its own explicit teardown (a validation warning is
emitted in this case).

In hyperplane, Docker containers (SERVICE and COMMAND elements) have an
automatic lifeline dependency on their compute node. Tearing down the
node implicitly destroys all containers running on it, so no explicit
container teardown steps are emitted during final cleanup. Lifecycle
status for all lifeline-dependent elements is updated transactionally
with the upstream element across any number of lifeline levels.

### Rule 11: Group-Boundary Teardowns Depend on Execution Steps

A PER_GROUP group-boundary teardown depends directly on the last
execution step (`lastSequentialExecId`), preventing teardown from
racing with an in-progress trial. Teardowns in the same group boundary
are LIFO-chained to prevent concurrent teardown of dependent elements.

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

### Rule 21: Trial Element Identity (Design Rule 1)

Trial elements are the **innermost leaf nodes** in the dependency graph,
even when the innermost layer is also the outermost layer (i.e. all
elements are run-scoped).  Binding depth does not disqualify an element
from being a trial element — leaf-node detection always applies.

Identification uses a scope-aware, override-respecting algorithm:

1. Collect explicit overrides from `Element.trialElement()`.
2. When trial-scoped (non-run-scoped) elements exist, find leaf nodes
   among them (no trial-scoped dependents).
3. When NO trial-scoped elements exist, fall back to the classic
   leaf-node heuristic across all elements.
4. Merge: explicit true + auto-detected leaves (minus explicit false).

The compiler emits a `TrialStep` (or `AwaitElement`) for each trial element
in topological order. The **relative order** of these steps within a trial
is determined by:
1. Standard topological sort of dependencies.
2. **LINEAR** dependency type: Explicitly forces a serial ordering between
   trial elements in the same scope. Elements must occur in order, as
   strict serialization is required and further, data flow may be implied
   between elements in the same trial scope (parameter group).

### Rule 22: Lifeline Clusters
...
### Rule 23: Trial Lifecycle Notifications (Design Rules 2 & 3)

Two notification steps bracket each trial's execution sequence.  Their
placement enforces the design rules:

**NotifyTrialStart** — emitted AFTER non-trial element deploys and
BEFORE trial element deploys.  Non-trial elements are the notification
receivers (Design Rule 3) and must be running to observe the trial
lifecycle.  Trial elements deploy after this notification so their
full lifecycle falls within the notification scope (Design Rule 2).

**NotifyTrialEnd** — emitted after ALL operative steps of all trial
elements complete.  All elements receive `onTrialEnding`.

**Placement in the step DAG:**

```
(non-trial deploys) → NotifyTrialStart → (trial deploys) → (trial element steps 1..N) → NotifyTrialEnd → (teardowns)
```

### Rule 24: Minimal Execution Dependencies

By default, `NotifyTrialStart` steps depend only on **leaf deploy
steps** among the non-trial elements — elements whose steps are not
transitively reachable through the deploy dependency chain.  This
produces cleaner graphs with fewer edges.

For example, given `node → db → app` where `app` is the trial element,
`NotifyTrialStart` depends only on `deploy_db` (the non-trial leaf),
since `deploy_db` already transitively depends on `deploy_node`.
`app` deploys after `NotifyTrialStart` within the notification scope.

The `explicitTransitiveDeps` compiler option (custom option key:
`explicitTransitiveDeps`, value: `true`) restores the previous behavior
of including every upstream deploy step as a direct dependency of the
execution step. This can be useful for debugging or visualization when
explicit edges are desired.

### Rule 25: Shutdown Semantics (COMMAND vs SERVICE)

Elements declare shutdown semantics via `Element.shutdownSemantics()`:

- **SERVICE** (default): Long-running process requiring explicit teardown.
  The planner emits `TrialStep` + `TeardownElement` for the trial element.

- **COMMAND**: Self-terminating process that runs to completion. The planner
  emits `AwaitElement` instead of `TrialStep`, and skips the trial
  element's teardown step (the element exits on its own).

Only the trial element's semantics affect step generation. Non-trial elements
always use the standard deploy/teardown lifecycle regardless of their
declared shutdown semantics.
