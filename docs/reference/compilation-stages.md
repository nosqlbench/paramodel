<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->

# Compilation Stages

Reference for the 8-stage compilation pipeline that transforms a mutable
`TestPlan` into an immutable `ExecutionPlan`. The pipeline is implemented by
`DefaultCompiler` in the `paramodel-engine` module and is defined by the
`CompilationStage` contract in `io.nosqlbench.paramodel.compilation`.

For the contract interfaces themselves, see [Contract Types](contract-types.md).
For term definitions, see [Glossary](glossary.md).

---

## Pipeline Overview

```
TestPlan (mutable, declarative)
  |
  v
[1] Validation
  |
  v
[2] Normalization
  |
  v
[3] Trial Enumeration
  |
  v
[4] Instantiation
  |
  v
[5] Step Generation
  |
  v
[6] Dependency Analysis
  |
  v
[7] Optimization
  |
  v
[8] Code Generation
  |
  v
ExecutionPlan (immutable, operational)
```

Each stage reads from and writes to a shared `CompilationContext`. If any stage
produces an error, the pipeline aborts and returns a failure result. Warnings are
accumulated and returned alongside a successful result.

---

## Stage 1: Validation

**Class:** `ValidationStage`
**Input:** `TestPlan`
**Output:** Validated `TestPlan` (unchanged) or compilation errors

Verifies that the `TestPlan` is structurally and semantically correct before any
transformation occurs. This is the earliest opportunity to reject invalid input.

### Checks Performed

| Category | Check | Error on Failure |
|----------|-------|-----------------|
| Structural | At least one axis defined | `"TestPlan must have at least one axis"` |
| Structural | All axis names unique | `"Duplicate axis name: <name>"` |
| Structural | All element names unique | `"Duplicate element name: <name>"` |
| Structural | All axes have non-empty value lists | `"Axis '<name>' has no values"` |
| Semantic | All relationships reference existing elements | `"Relationship references unknown element: <name>"` |
| Semantic | Relationship graph is acyclic | `"Relationship graph contains cycle"` |
| Semantic | No contradictory relationships | `"Conflicting relationships between <a> and <b>"` |
| Feasibility | Trial space size within configured limit | `"Trial space (<n>) exceeds limit (<max>)"` |
| Policy | Retry counts are positive | `"Retry count must be positive"` |
| Policy | Timeouts are positive durations | `"Timeout must be positive"` |

### Context Writes

- Validation status (pass/fail)
- List of errors and warnings
- Timing metric: `validation_duration`

---

## Stage 2: Normalization

**Class:** `NormalizationStage`
**Input:** Validated `TestPlan`
**Output:** Normalized `TestPlan`

Canonicalizes the `TestPlan` representation so that downstream stages can make
consistent assumptions.

### Transformations

| Transformation | Description |
|---------------|-------------|
| Axis ordering standardization | Ensures axes are in a canonical order (user-specified or alphabetical) |
| Constraint normalization | Flattens nested AND/OR trees, removes tautologies |
| Relationship symmetry | Ensures `(A, B)` and `(B, A)` resolve to the same relationship type |
| Policy defaults | Fills in default values for unspecified policies |
| Element resolution | Resolves element references and validates configurations |

### Context Writes

- `normalizedPlan`: the canonicalized `TestPlan`
- Timing metric: `normalization_duration`

---

## Stage 3: Trial Enumeration

**Class:** `TrialEnumerationStage`
**Input:** Normalized `TestPlan`
**Output:** Ordered list of `Trial` instances

Expands the parameter space by computing the Cartesian product of all axes and
then applying the configured trial ordering strategy.

### Process

1. **Cartesian product generation** -- Produce all combinations of axis values.
   For axes with cardinalities `c1, c2, ..., cn`, this generates `c1 * c2 * ... * cn` trials.
   Each trial is assigned a unique ID and wrapped in a `DefaultTrial` with its assignments.

2. **Trial ordering** -- Apply the `TrialOrdering` strategy specified in the plan's
   policies.

3. **Trial ID assignment** -- Assign unique identifiers to each trial (UUID-based).

### Context Writes

- `trials`: ordered `List<Trial>`
- Metrics: `trials_enumerated`
- Timing metric: `enumeration_duration`

---

## Stage 4: Instantiation

**Class:** `InstantiationStage`
**Input:** Trial list, elements, and axes from the normalized plan
**Output:** Planned element instances bound to trials

Maps axis assignments to specific element parameters and determines the lifecycle
scope for every element in the study.

### Parameter Binding Rules

The compiler binds axis values to element parameters using prioritized matching:
1. **Identity**: Match by object instance.
2. **Qualified**: Match by `element.parameter` name.
3. **Simple**: Match by `parameter` name.

### Element Instance Scoping

| Condition | Determined Scope | Result |
|-----------|------------------|--------|
| No parameters vary across axes | Group level 0 | 1 instance shared by all trials |
| Parameters vary across axes | Group level > 0 | N instances (one per unique config fingerprint) |

### Context Writes

- `elementInstances`: mapping of element IDs to instance scopes and bound trials
- Metrics: `instances_created`
- Timing metric: `instantiation_duration`

---

## Stage 5: Step Generation

**Class:** `StepGenerationStage`
**Input:** Instantiated trials and element instance plan
**Output:** List of `AtomicStep` instances and `Barrier` synchronization points

Creates the concrete atomic steps that constitute the execution plan. Each trial
and each element lifecycle transition becomes one or more steps.

### Design Rules (in order of precedence)

1. **Trial element identity**: Trial elements are the innermost leaf nodes in
   the dependency graph, even when the innermost layer is also the outermost
   layer.  Binding depth does not disqualify an element from being a trial
   element.
2. **Notification scope containment**: The full lifecycle of every trial element
   — deploy, execute, completion — must fall within the NotifyTrialStart /
   NotifyTrialEnd bracket.
3. **Non-trial elements as notification receivers**: Non-trial elements deploy
   *before* NotifyTrialStart so they are running and able to observe trial
   lifecycle events.

### Unified Per-Trial Algorithm

All elements are processed through a single fingerprint-based mechanism per
trial.  Run-scoped (depth-0) elements naturally deploy on trial 0 and persist
(constant fingerprint), while bound elements redeploy when their fingerprint
changes.  For each trial: fingerprint check → teardown at boundaries → deploy
non-trial elements → NotifyTrialStart → deploy trial elements → operative
steps → NotifyTrialEnd → predictive eager teardown.

### Step Types Generated

| Step Type | When Generated |
|-----------|---------------|
| `DeployElement` | Non-trial elements before NotifyTrialStart; trial elements after it |
| `NotifyTrialStart`| After non-trial deploys, before trial element deploys |
| `TrialStep` | For `SERVICE` trial elements (the operative action) |
| `AwaitElement` | For `COMMAND` trial elements (natural completion) |
| `NotifyTrialEnd` | After all operative steps of the trial complete |
| `TeardownElement` | At group boundaries (predictive) and after all trials (final) |
| `BarrierSync` | After deploys when elements have health checks |

**Linearization Rule**:
When trial elements declare a `LINEAR` relationship, the stage enforces
strict sequential ordering of their operative steps (`TrialStep` or
`AwaitElement`) within the trial boundary, supporting implied data flow.

### Barrier Insertion Rules

| Barrier Type | Trigger |
|-------------|---------|
| `ELEMENT_READY` | After each `DeployElement` step for elements with health checks |

### Context Writes

- `steps`: `List<AtomicStep>`
- `barriers`: `List<Barrier>`
- Metrics: `steps_generated`, `barriers_generated`
- Timing metric: `step_generation_duration`

---

## Stage 6: Dependency Analysis

**Class:** `DependencyAnalysisStage`
**Input:** Step list and barriers
**Output:** `ExecutionGraph` (DAG)

Builds the execution dependency graph from steps and barriers. Verifies the graph
is acyclic and computes structural properties.

### Process

1. **Node creation** -- Each `AtomicStep` becomes a node in the DAG.

2. **Edge creation** -- Dependencies declared by each step (via `dependencies()`)
   become directed edges. Barrier dependencies create fan-in edges; barrier
   dependents create fan-out edges.

3. **Cycle detection** -- Topological sort using Kahn's algorithm. If the sorted
   result is smaller than the node count, a cycle exists and compilation fails
   with `"Dependency graph contains cycle"`.

4. **Critical path computation** -- Longest path through the DAG using
   estimated step durations. Identifies the minimum execution time with
   unlimited parallelism.

5. **Parallelism analysis** -- Groups steps into parallel waves. Wave 0 contains
   steps with no dependencies; wave N contains steps whose dependencies are all
   in waves less than N.

### Context Writes

- `executionGraph`: the computed `ExecutionGraph`
- Metrics: `node_count`, `edge_count`, `max_depth`, `max_parallelism`, `critical_path_duration`
- Timing metric: `dependency_analysis_duration`

---

## Stage 7: Optimization

**Class:** `OptimizationStage`
**Input:** `ExecutionGraph`
**Output:** Optimized `ExecutionGraph`

Applies optimization passes based on the `OptimizationStrategy` specified in the
`TestPlan`. Skipped entirely if strategy is `NONE`.

### Optimization Passes

| Pass | Strategy Level | Description |
|------|---------------|-------------|
| Barrier coalescing | `BASIC`+ | Merges adjacent barriers with overlapping dependencies into single synchronization points |
| Redundancy elimination | `BASIC`+ | Removes duplicate deploy/teardown steps for the same element |
| Step fusion | `PRUNE_REDUNDANT`+ | Combines sequential steps that can be executed atomically (e.g., deploy + health check) |
| Resource packing | `PRUNE_REDUNDANT`+ | Reorders independent steps to improve resource utilization |
| Critical path prioritization | `AGGRESSIVE` | Reorders steps to minimize critical path duration |
| Trial pruning | `AGGRESSIVE` | Removes provably equivalent trials that would produce identical results |

Each pass implements the `OptimizationPass` interface from
`io.nosqlbench.paramodel.compilation` and can inspect the `CompilationContext` to
decide whether to apply.

### Strategy Levels

| Strategy | Passes Applied | Compilation Impact |
|----------|---------------|-------------------|
| `NONE` | None | Fastest compilation |
| `BASIC` | Barrier coalescing, redundancy elimination | Fast |
| `PRUNE_REDUNDANT` | All basic + step fusion, resource packing | Moderate |
| `AGGRESSIVE` | All passes | Slowest compilation, best runtime |

### Context Writes

- `optimizedGraph`: the optimized `ExecutionGraph`
- Optimization report: list of passes attempted, applied, and savings
- Metrics: `optimizations_attempted`, `optimizations_applied`, `steps_before`, `steps_after`, `barriers_before`, `barriers_after`
- Timing metric: `optimization_duration`

---

## Stage 8: Code Generation

**Class:** `CodeGenerationStage`
**Input:** Optimized `ExecutionGraph` and all accumulated metadata
**Output:** Immutable `ExecutionPlan`

Materializes the final `ExecutionPlan` by assembling the optimized graph with
metadata, fingerprints, and version information.

### Process

1. **Finalize execution graph** -- Lock the graph as immutable.

2. **Compute metadata** -- Assemble `ExecutionPlanMetadata`:
   - `compilationVersion`: compiler version string
   - `compiledAt`: timestamp of compilation
   - `fingerprint`: SHA-256 hash of the execution plan content
   - `optimizationMetrics`: summary of optimizations applied

3. **Generate fingerprint** -- Cryptographic hash of the plan's structural
   content (steps, barriers, ordering) ensuring that equivalent plans produce
   equivalent fingerprints.

4. **Link to TestPlan** -- Record the source `TestPlan` fingerprint for
   provenance tracing.

5. **Create ExecutionPlan** -- Construct the immutable `ExecutionPlan` instance
   with all computed data.

### Context Writes

- `executionPlan`: the final `ExecutionPlan`
- Timing metric: `code_generation_duration`

---

## Using the Pipeline

### Standard Pipeline

```java
Compiler compiler = DefaultCompiler.builder()
    .standardPipeline()
    .build();

CompilationResult result = compiler.compile(testPlan);

if (result.isSuccess()) {
    ExecutionPlan plan = result.executionPlan().orElseThrow();
} else {
    result.errors().forEach(e -> System.err.println(e.message()));
}
```

### Validation Only

```java
Compiler compiler = Compiler.create();
Compiler.ValidationResult validation = compiler.validate(testPlan);

if (validation.hasErrors()) {
    validation.errors().forEach(e ->
        System.err.printf("[%s] %s%n", e.severity(), e.message()));
}
```

### Incremental Recompilation

```java
CompilationResult initial = compiler.compile(originalPlan);
ExecutionPlan plan1 = initial.executionPlan().orElseThrow();

// Modify plan (e.g., add axis value)
TestPlan modified = ...;

CompilationResult incremental = compiler.compileIncremental(modified, plan1);
```

### Inspecting Compilation Statistics

```java
CompilationResult result = compiler.compile(testPlan);
Compiler.CompilationStatistics stats = result.statistics();

System.out.printf("Trials generated: %d%n", stats.trialsGenerated());
System.out.printf("Steps generated: %d%n", stats.stepsGenerated());
System.out.printf("Barriers generated: %d%n", stats.barriersGenerated());
System.out.printf("Optimizations applied: %d%n", stats.optimizationsApplied());
System.out.printf("Validation time: %s%n", stats.validationTime());
System.out.printf("Enumeration time: %s%n", stats.enumerationTime());
System.out.printf("Optimization time: %s%n", stats.optimizationTime());
System.out.printf("Code gen time: %s%n", stats.codeGenTime());
```

---

## Stage Dependencies

```
ValidationStage
    depends on: (none)
    produces: validation status

NormalizationStage
    depends on: ValidationStage
    produces: normalizedPlan

TrialEnumerationStage
    depends on: NormalizationStage
    produces: trials

InstantiationStage
    depends on: TrialEnumerationStage
    produces: instantiatedTrials, elementInstances

StepGenerationStage
    depends on: InstantiationStage
    produces: steps, barriers

DependencyAnalysisStage
    depends on: StepGenerationStage
    produces: executionGraph

OptimizationStage
    depends on: DependencyAnalysisStage
    produces: optimizedGraph

CodeGenerationStage
    depends on: OptimizationStage
    produces: ExecutionPlan
```

---

## Error Handling

Each stage reports errors through the `CompilationContext`:

- **ERROR** -- Aborts the pipeline immediately. No subsequent stages execute.
- **WARNING** -- Logged and accumulated. Pipeline continues.
- **INFO** -- Informational messages recorded for diagnostics.

Stages MUST check `context.hasErrors()` before executing and abort early if
previous stages failed. Stages MUST NOT throw exceptions; all errors are reported
via the context.

---

## See Also

- [Contract Types](contract-types.md) -- `Compiler`, `CompilationStage`, `CompilationContext`, `OptimizationPass`
- [API Packages](api-packages.md) -- `io.nosqlbench.paramodel.compilation` package
- [Glossary](glossary.md) -- Execution Plan, Test Plan, Barrier, Atomic Step
