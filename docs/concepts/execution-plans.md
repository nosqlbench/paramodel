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
| 2     | Normalization              | Canonicalize representation and derive instancing scopes     |
| 3     | Trial space enumeration    | Cartesian product of axes, ordering applied                  |
| 4     | Instantiation              | Determine element instance counts and binding hierarchy      |
| 5     | Step generation            | Create `AtomicStep` records for lifecycle and trial boundaries|
| 6     | Dependency analysis        | Build execution graph, detect cycles, analyze critical path  |
| 7     | Optimization               | Apply the chosen `OptimizationStrategy` (prune, reorder)     |
| 8     | Finalization               | Produce the immutable `ExecutionPlan` with metadata          |

## AtomicStep

An `AtomicStep` is the indivisible unit of work in an execution plan.
It is a sealed interface with several record types that support element
lifecycles and trial bracketing:

| Subtype             | Purpose                                          | Key Fields                             |
|---------------------|--------------------------------------------------|----------------------------------------|
| `DeployElement`     | Provision and start an element instance           | `elementId`, `configuration`, `tags`   |
| `NotifyTrialStart`  | Notify elements that a trial is starting          | `trialId`, `elementNames`              |
| `TrialStep`         | The operative action of the designated **Trial Element** | `trialId`, `elementBindings`           |
| `AwaitElement`      | Await completion of a `COMMAND` trial element     | `trialId`, `elementId`                 |
| `NotifyTrialEnd`    | Notify elements that a trial has ended           | `trialId`, `elementNames`, `reason`    |
| `TeardownElement`   | Shut down and clean up an element instance        | `elementId`, `collectArtifacts`        |
| `BarrierSync`       | Synchronize concurrent steps at dependency boundaries | `barrierId`, `dependencies`            |
| `CheckpointState`   | Persist execution state for recovery              | `checkpointId`                         |

## The Trial Elements

Trial elements are the **innermost leaf nodes** in the dependency graph —
the elements that actually perform the trial workload.  This
identification applies regardless of binding depth: even when all
elements are run-scoped (depth 0), the leaf nodes are still the trial
elements.

Trial element identity is determined by a scope-aware, override-respecting
algorithm:

1. Explicit overrides from `Element.trialElement()` take priority.
2. When trial-scoped (bound) elements exist, leaf nodes among them are
   auto-detected.
3. When all elements are run-scoped, the classic leaf-node heuristic
   applies across all elements.

### Notification Scope

The full lifecycle of every trial element — **deploy, execute,
completion** — falls within the `NotifyTrialStart` / `NotifyTrialEnd`
bracket.  Non-trial elements deploy *before* `NotifyTrialStart` so they
are running and able to observe trial lifecycle events as notification
receivers.

```
(non-trial deploys) → NotifyTrialStart → (trial deploys) → (operative steps) → NotifyTrialEnd
```

### Ordering and Linearization

The compiler determines the relative order of these elements' actions
using topological sorting of their dependencies. A new dependency type,
**LINEAR**, can be used to explicitly force a specific sequence between
trial elements within the same trial scope. Elements must occur in order,
as strict serialization is required and further, data flow may be
implied between elements in the same trial scope (parameter group).

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

## ExecutionGraph

The `ExecutionGraph` is a directed acyclic graph (DAG) representing the
dependency structure of the plan. It enables analysis of critical paths and
parallelism opportunities.

### Graph Properties

- **Nodes**: `AtomicStep` instances.
- **Edges**: Dependency relationships (A → B means B depends on A).
- **Acyclic**: Guaranteed by construction; no circular dependencies.

### Key Capabilities

- **Critical Path**: The longest dependency chain determining minimum execution time.
- **Topological Sort**: A valid sequential ordering of all steps.
- **Parallel Waves**: Groups of steps that can run concurrently because they have no mutual dependencies.
- **Resource-Constrained Scheduling**: Computing a schedule that respects CPU and memory limits.

## Barrier

A **Barrier** is a synchronization primitive that coordinates concurrent
execution. It ensures that downstream steps wait until all prerequisite
conditions are met.

### Barrier Types

| Type                  | Purpose                                          |
|-----------------------|--------------------------------------------------|
| `ELEMENT_READY`       | Signals that an element instance is fully deployed. |
| `ELEMENT_SCOPE_END`   | Signals that all trials using an instance are done. |
| `TRIAL_BATCH`         | Groups trials into batches for checkpointing.     |
| `CHECKPOINT_BOUNDARY` | Forces synchronization before state persistence.  |
| `CUSTOM`              | User-defined synchronization point.              |

### States and Transitions

```
PENDING (waiting) → SATISFIED (released)
                  → FAILED (dependency failed)
                  → TIMEOUT (limit exceeded)
```

Barriers support timeout policies (`FAIL_FAST`, `SKIP_DEPENDENT`, `RETRY`) to
handle stuck or slow dependencies.

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
| `performanceMetrics()`   | `PerformanceMetrics`          | Parallelism, speedup, graph complexity       |

## Further Reading

- [Test Plans and Axes](test-plans-and-axes.md) -- the source specification
- [Elements and Relationships](elements-and-relationships.md) -- resource orchestration
- [../explanation/immutability-and-reproducibility.md](../explanation/immutability-and-reproducibility.md) -- why immutability matters
