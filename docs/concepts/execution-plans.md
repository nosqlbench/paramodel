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

# Execution Plans

An **ExecutionPlan** is the compiled, immutable artefact derived from a
validated `TestPlan` through the `commit()` operation. Where a `TestPlan`
says *"what to test"*, an `ExecutionPlan` says *"how to execute it"* -- a
complete, deterministic specification of atomic steps, dependency ordering,
resource allocation, and synchronization barriers.

The types described here live in `io.nosqlbench.paramodel.plan`.

## From TestPlan to ExecutionPlan

```
TestPlan (mutable, declarative)
          |
          |  commit()
          v
ExecutionPlan (immutable, imperative)
```

The commitment step validates the test plan, enumerates the trial space,
resolves element dependencies, inserts barriers, generates atomic steps,
and returns an immutable execution plan. The `TestPlan` is locked after
commitment; any further modification requires creating a new plan and
committing again.

### The compilation pipeline

Compilation proceeds through eight conceptual stages:

| Stage | Name                       | What Happens                                                 |
|-------|----------------------------|--------------------------------------------------------------|
| 1     | Validation                 | Structural and semantic checks on the test plan              |
| 2     | Trial space enumeration    | Cartesian product of axes, ordering applied                  |
| 3     | Element instantiation      | Determine how many instances of each element are needed      |
| 4     | Dependency analysis        | Build element dependency graph, topological sort             |
| 5     | Step generation            | Create `AtomicStep` records for deploy, execute, teardown    |
| 6     | Barrier placement          | Insert synchronization barriers per relationship semantics   |
| 7     | Optimization               | Apply the chosen `OptimizationStrategy` (prune, reorder)     |
| 8     | Code generation / finalize | Produce the immutable `ExecutionPlan` with metadata          |

Concrete implementations of these stages live in
`io.nosqlbench.paramodel.engine.compiler` (e.g., `ValidationStage`,
`TrialEnumerationStage`, `DependencyAnalysisStage`, `StepGenerationStage`,
`OptimizationStage`, `CodeGenerationStage`).

## Immutability -- The Ship of Theseus Principle

Once compiled, an `ExecutionPlan` cannot be modified. This is by design:

- Reproducibility requires that the same plan always defines the same steps.
- Provenance links (fingerprints) are meaningful only if the plan is stable.
- Concurrent executors can safely share the plan without locks.

Any refinement -- adding an axis, changing a policy, adjusting concurrency
-- creates a **new** plan through a new `TestPlan.commit()` cycle.

The one exception is `ExecutionPlan.withMaxConcurrency(int)`, which returns
a *new* `ExecutionPlan` instance with adjusted parallelism. It does not
mutate the original.

## ExecutionPlan Interface

Key methods:

| Method                      | Returns                       | Purpose                                           |
|-----------------------------|-------------------------------|---------------------------------------------------|
| `id()`                      | `String`                      | Unique plan identifier                            |
| `testPlanFingerprint()`     | `String`                      | SHA-256 of the source test plan                   |
| `steps()`                   | `List<AtomicStep>`            | All steps in topological order                    |
| `barriers()`                | `List<Barrier>`               | All synchronization barriers                      |
| `executionGraph()`          | `ExecutionGraph`              | DAG of steps and dependencies                     |
| `trialOrdering()`           | `TrialOrdering`               | The ordering strategy used                        |
| `estimatedDuration()`       | `Optional<Duration>`          | Wall-clock estimate (accounts for parallelism)    |
| `estimatedMaxParallelism()` | `int`                         | Peak concurrent trial count                       |
| `resourceRequirements()`    | `ResourceRequirements`        | Aggregate CPU, memory, storage, network           |
| `metadata()`                | `ExecutionPlanMetadata`       | Compilation version, timestamp, fingerprint, etc. |

## AtomicStep

An `AtomicStep` is the indivisible unit of work in an execution plan.
It is a sealed interface with five subtypes:

| Subtype             | Purpose                                          | Key Fields                             |
|---------------------|--------------------------------------------------|----------------------------------------|
| `DeployElement`     | Provision and start an element instance           | `elementId`, `configuration`, `healthChecks` |
| `ExecuteTrial`      | Run one trial with bound element instances        | `trialId`, `elementBindings`           |
| `TeardownElement`   | Shut down and clean up an element instance        | `elementId`, `collectArtifacts`        |
| `BarrierSync`       | Wait for all listed dependencies to complete      | `barrierId`, `dependencies`            |
| `CheckpointState`   | Persist execution state for recovery              | `checkpointId`                         |

Every step carries:

| Field                   | Type                        | Purpose                            |
|-------------------------|-----------------------------|------------------------------------|
| `id()`                  | `String`                    | Unique within the plan             |
| `type()`                | `StepType` enum             | Discriminator for the five kinds   |
| `description()`         | `String`                    | Human-readable label               |
| `dependencies()`        | `List<String>`              | IDs of prerequisite steps          |
| `estimatedDuration()`   | `Optional<Duration>`        | Time estimate for scheduling       |
| `resourceRequirements()`| `ResourceRequirements`      | CPU, memory, storage, network      |
| `retryPolicy()`         | `Optional<RetryPolicy>`     | Retry count and backoff strategy   |
| `execute(ExecutionContext)` | `StepResult`            | Perform the step (impl-specific)   |

Steps are designed to be **idempotent** where possible, supporting safe
retry after transient failures.

## ExecutionGraph

The `ExecutionGraph` is a directed acyclic graph (DAG) where nodes are
`AtomicStep` instances and edges represent dependency (happens-before)
relationships.

Key capabilities:

| Method                       | Returns                          | Purpose                                     |
|------------------------------|----------------------------------|---------------------------------------------|
| `criticalPath()`             | `List<AtomicStep>`               | Longest dependency chain                    |
| `criticalPathDuration()`     | `Duration`                       | Minimum time with unlimited parallelism     |
| `topologicalSort()`          | `List<AtomicStep>`               | A valid execution ordering                  |
| `parallelWaves()`            | `Map<Integer, List<AtomicStep>>` | Steps grouped by wave (no mutual deps)      |
| `maximumParallelism()`       | `int`                            | Largest wave size                           |
| `averageParallelism()`       | `double`                         | total duration / critical path duration     |
| `canExecuteConcurrently(a,b)`| `boolean`                        | True if neither step depends on the other   |
| `subgraphForElement(id)`     | `ExecutionGraph`                 | Extract lifecycle subgraph for one element  |

The graph is guaranteed acyclic (by construction). All algorithms run in
O(V + E) time where V = nodes and E = edges.

## ExecutionPlanMetadata

`ExecutionPlanMetadata` captures observability data produced during
compilation:

| Field                    | Type                          | Purpose                                      |
|--------------------------|-------------------------------|----------------------------------------------|
| `compiledAt()`           | `Instant`                     | When compilation occurred                    |
| `compilationDuration()`  | `Duration`                    | How long compilation took                    |
| `compilerVersion()`      | `String`                      | Version of the compiler used                 |
| `optimizationLevel()`    | `OptimizationLevel` enum      | NONE, BASIC, STANDARD, or AGGRESSIVE         |
| `trialCount()`           | `int`                         | Number of trials in the plan                 |
| `stepCount()`            | `int`                         | Total atomic steps                           |
| `barrierCount()`         | `int`                         | Synchronization barriers                     |
| `performanceMetrics()`   | `PerformanceMetrics`          | Parallelism, speedup, graph complexity       |
| `testPlanFingerprint()`  | `String`                      | Links back to the source plan                |

The metadata also tracks execution history through `executionHistory()`,
recording the outcome, duration, and cost of every run.

## Checkpoint and Recovery

Execution plans support checkpointing. Periodic `CheckpointState` steps
persist the set of completed and pending step IDs, element instance states,
and partial results. If execution is interrupted, `resumeFrom(Checkpoint)`
produces a new `ExecutionPlan` that skips already-completed steps and
continues from where the previous run left off.

## Further Reading

- [Test Plans and Axes](test-plans-and-axes.md) -- the source specification
  that execution plans are compiled from
- [Elements and Relationships](elements-and-relationships.md) -- the
  resources whose lifecycles execution plans orchestrate
- [../reference/compilation-stages.md](../reference/compilation-stages.md)
  -- detailed reference for each compilation stage
- [../tutorials/compilation-pipeline.md](../tutorials/compilation-pipeline.md)
  -- walkthrough of the compilation process
- [../explanation/immutability-and-reproducibility.md](../explanation/immutability-and-reproducibility.md)
  -- why immutability matters
