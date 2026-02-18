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

# Simplica

Simplica is a contract-first study execution system built on the Paramodel
framework. If Paramodel provides the algebraic "atoms" -- parameters,
constraints, domains, values -- then Simplica assembles those atoms into
complete "molecules": execution plans with scheduling, barriers, resource
lifecycle management, and full provenance.

This document explains what Simplica is, why it exists as a layer above
raw parameter modeling, and what it adds.

## Relationship to Paramodel

```
+---------------------------------------------------------------+
| Simplica Layer                                                |
|   TestPlans, ExecutionPlans, Scheduling, Resource             |
|   Orchestration, Provenance, Observability                    |
+---------------------------------------------------------------+
        |  uses
        v
+---------------------------------------------------------------+
| Paramodel Layer                                               |
|   Parameter<T>, Constraint<T>, Domain<T>, Value<T>            |
|   Sequence, Trial, TrialResult, TrialStatus                   |
+---------------------------------------------------------------+
```

Paramodel is a library of composable types. You can use `Parameter<T>`,
`Constraint<T>`, and `Domain<T>` (from
`io.nosqlbench.paramodel.parameters`) without ever creating a `TestPlan`.
You can build `Sequence` and `Trial` objects (from
`io.nosqlbench.paramodel.sequence`) for simple parameter sweeps without
involving elements or barriers.

Simplica is what you reach for when a study has real-world resources:
databases that need to be provisioned, caches that need to be shared or
isolated, services that need health checks, and trials that need to be
scheduled with awareness of element relationships. Simplica takes the
algebraic building blocks and adds the operational machinery to run a study
end to end.

## What Simplica Adds

### 1. Rigorous Test Plans

A `TestPlan` (in `io.nosqlbench.paramodel.plan`) is a declarative study
specification. It combines:

- **Axes** -- parameter dimensions to explore, each backed by a
  `Domain<T>`. Axes are ordered; the ordering affects trial generation
  strategy.
- **Elements** -- resources required for execution (databases, caches,
  services, infrastructure). Each `Element` (in
  `io.nosqlbench.paramodel.elements`) has a name, type, configuration,
  health check specification, and lifecycle hooks.
- **Relationships** -- how elements relate to their dependencies, expressed
  as `RelationshipType` values on each dependency edge: `SHARED` (concurrent
    access, the default), `EXCLUSIVE` (serialized access), `DEDICATED`
    (dedicated instance per dependent), `LINEAR` (strict ordering within trial),
    or `LIFELINE` (target's teardown subsumes dependent).
   Element instance lifecycle is derived by the
  compilation pipeline from parameter-axis overlap, not from relationship
  type.
- **Policies** -- `ExecutionPolicies` (in
  `io.nosqlbench.paramodel.plan.policies`) governing retry strategies,
  timeouts, error handling, and intervention behavior.

The test plan is mutable during authoring. Axes can be reordered, elements
added, relationships changed. But this flexibility is bounded: the plan
must pass validation (structural, semantic, policy) before it can proceed.

### 2. Immutable Execution Plans

When a `TestPlan` is committed (via `testPlan.commit()`), the 8-stage
compilation pipeline in `DefaultCompiler` (from
`io.nosqlbench.paramodel.engine.compiler`) transforms it into an immutable
`ExecutionPlan`. The execution plan contains:

- **AtomicSteps** -- `DEPLOY_ELEMENT`, `TRIAL_STEP`, `AWAIT_ELEMENT`,
  `TEARDOWN_ELEMENT`, `BARRIER`, and `CHECKPOINT` steps, each independently
  executable and recoverable.
- **ExecutionGraph** -- a directed acyclic graph capturing dependencies and
  parallelism opportunities.
- **Barriers** -- synchronization points that enforce relationship
  constraints (e.g., `EXCLUSIVE` dependency edges serialize access to
  shared elements).
- **TrialOrdering** -- the strategy used to sequence trials (`SEQUENTIAL`,
  `SHUFFLED`, `EDGE_FIRST`, `DEPENDENCY_OPTIMIZED`, `COST_OPTIMIZED`).

Once compiled, the execution plan is frozen. No aspect can be changed. Any
refinement requires creating a new `TestPlan` and compiling a new
`ExecutionPlan`. This immutability is not a limitation; it is the
foundation of reproducibility and provenance. See
[Immutability and Reproducibility](immutability-and-reproducibility.md) for
the full reasoning.

### 3. Smart Scheduling

The `DefaultScheduler` (from `io.nosqlbench.paramodel.engine.execution`)
uses element relationships to make concurrency decisions:

- **EXCLUSIVE** dependency edges produce barriers that serialize trial
  access. If a database cannot handle concurrent connections, the scheduler
  ensures only one trial uses it at a time.
- **SHARED** dependency edges allow concurrent access. The scheduler can run
  multiple trials simultaneously against a shared cache or connection pool.
- **DEDICATED** edges provision a dedicated instance per dependent element.
- **LIFELINE** edges couple the dependent's lifecycle to the target's --
  tearing down the target implicitly destroys the dependent.

**The Trial Element**:
The duration and outcome of a trial are bounded by the lifecycle of exactly
one designated **Trial Element** (the leaf element in the trial scope).
The compiler emits either a `TrialStep` or an `AwaitElement` step to
represent this operative action.
Element instance lifecycle (when elements are redeployed vs. persisted) is
determined by the fingerprint-based group mechanism in the compilation
pipeline, based on parameter-axis overlap. The scheduler manages instance
lifecycle -- deploying before use and tearing down at group boundaries
when configuration changes.

Grouping, barrier coalescing, and critical-path prioritization are handled
during compilation so the scheduler operates on a pre-optimized graph.

### 4. Operational Durability

Simplica is designed for studies that take hours or days to run:

- **Idempotent re-runs.** If an execution is interrupted (crash, timeout,
  resource failure), it can be resumed from the last checkpoint. Completed
  steps are not re-executed.
- **Resumable execution.** The `ExecutionPlan.resumeFrom(Checkpoint)`
  method creates a continuation plan that picks up where the previous run
  left off.
- **Partial result retention.** Results collected before a failure are
  preserved. A study that completes 80% of its trials before crashing
  retains the 80% of results, and the remaining 20% can be completed in a
  subsequent run.

### 5. Complete Provenance

Every execution plan carries:

- **Cryptographic fingerprint.** A hash of the plan's configuration,
  enabling change detection and result linkage.
- **TestPlan fingerprint.** The execution plan records the fingerprint of
  the source `TestPlan`, creating a provenance chain from results back to
  the original specification.
- **Structured metadata.** `TestPlanMetadata` and `ExecutionPlanMetadata`
  capture creation time, author, description, tags, and version.

Results reference the exact plan fingerprint that produced them. If someone
asks "what configuration produced this result?", the answer is a lookup,
not a guess.

### 6. Real-Time Observability

The `ExecutionPlan.ExecutionObserver` interface provides callbacks for:

- Step started / completed / failed events
- Barrier reached events
- Checkpoint creation events

Implementations can use these callbacks for progress tracking, dependency
visualization, logging, and alerting. The observer interface is
non-invasive: it receives events but cannot modify the execution.

## Typical Workflow

A Simplica study follows this lifecycle:

```
Author TestPlan
    |
    v
Validate (structural, semantic, policy checks)
    |
    v
Commit (testPlan.commit() -> ExecutionPlan)
    |
    v
Execute (Executor runs the compiled plan)
    |
    +--> Observe / Intervene (pause, resume, monitor)
    |
    v
Complete / Resume (collect results, or resume from checkpoint)
```

The key transition point is `commit()`. Before commitment, the study is a
draft -- mutable and under active refinement. After commitment, it is a
contract -- immutable and deterministically executable.

## User Intervention

Users can interact with a running study without modifying the committed
plan:

- **Pause** -- temporarily suspend execution. No new steps are started;
  in-progress steps complete normally.
- **Resume** -- continue execution from the current state.
- **Stop** -- gracefully terminate. Element teardown runs; partial results
  are checkpointed.

These operations affect *run control* only, not *plan semantics*. The
`ExecutionPlan` remains unchanged throughout. This distinction is
important: operational decisions (when to pause) are separated from
scientific decisions (what to test).

## Platform Agnosticism

Simplica defines contract types and behaviors, not specific infrastructure.
The `Element` interface does not know whether the database it represents is
PostgreSQL on a local machine or a managed cloud service. The `Executor`
interface does not know whether it runs on a single JVM or across a
cluster.

Platform-specific implementations provide these details. Simplica provides
the contracts that those implementations must satisfy, validated by the
TCK.

## Key Concepts

### Axes and Trial Space

An `Axis<T>` (in `io.nosqlbench.paramodel.plan`) represents one dimension
of the parameter space. The trial space is the Cartesian product of all
axes:

```
Trial space size = axis_1.cardinality x axis_2.cardinality x ... x axis_n.cardinality
```

Axis ordering matters: the first axis is the "major" axis in edge-first
and grouped ordering strategies.

### Elements and Lifecycles

An `Element` is a resource with a lifecycle: deploy, health check, use,
teardown. Elements are classified by their `RelationshipType` with other
elements, which determines how many instances are created and whether
concurrent access is allowed.

### Relationship Types

| Type | Instance Sharing | Concurrency | Barrier Needed |
|------|------------------|-------------|----------------|
| `SHARED`    | Shared    | Yes | No  |
| `EXCLUSIVE` | Shared    | No  | Yes |
| `DEDICATED` | Dedicated | N/A | No  |
| `LINEAR`    | Shared    | No* | No**|
| `LIFELINE`  | Shared    | Yes | No  |

These five types cover the fundamental patterns of resource sharing in
distributed systems. Relationship types are a directional property of
each dependency edge (declared by the dependent element). Element instance
lifecycle (redeployment vs. persistence) is determined by the
fingerprint-based group mechanism, not by relationship type.

\* `LINEAR` enforces strict ordering between trial elements in the same scope.
\** Coordination is handled via step dependencies within the trial, not global barriers.

### Barriers

Barriers are synchronization points inserted by the compiler based on
dependency edge relationships. They ensure that `EXCLUSIVE` constraints
are enforced, that elements are fully deployed before trials begin, and
that teardown does not start until all dependent trials complete.

### Runs and Partial Runs

A "run" is a single execution of an `ExecutionPlan`. Runs can be complete
(all trials finished) or partial (interrupted and checkpointed). Partial
runs can be resumed, producing a continuation run. The union of a partial
run and its continuation produces the same results as an uninterrupted run.

## Further Reading

- [Architecture](architecture.md) -- the module structure that enables
  Simplica's layered design
- [Immutability and Reproducibility](immutability-and-reproducibility.md)
  -- the immutability model that underpins execution plan commitment
- [Parameters and Domains](../concepts/parameters-and-domains.md) -- the
  algebraic primitives that Simplica builds on
- [Constraints and Validation](../concepts/constraints-and-validation.md)
  -- the constraint algebra used in plan validation
