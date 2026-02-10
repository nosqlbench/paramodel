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

# Elements and Relationships

An **Element** is any resource that must be provisioned, started, monitored,
and stopped during study execution. A **RelationshipType** defines how two
elements interact, which in turn determines whether trials using those
elements can run concurrently or must be serialized.

`Element` lives in `io.nosqlbench.paramodel.elements`.
`RelationshipType` is an enum in the same package.

## Element

### What an Element represents

An element models any deployable resource needed by trials. The Paramodel
API deliberately does **not** prescribe a fixed type taxonomy; the concrete
kind of resource (service, environment, cache, dataset, profiler, etc.) is
expressed through the element's `tags()` map and is defined by the adopting
system.

Typical examples:

- A database instance (PostgreSQL, Cassandra)
- A Docker container or Kubernetes pod
- An ML model endpoint
- A profiler or monitoring agent
- A shared read-only dataset

### Element maturity levels

An element progresses through three levels during a study:

| Level              | State                     | Description                                  |
|--------------------|---------------------------|----------------------------------------------|
| **Model**          | `Element` interface       | Declares parameter space (what *can* be configured) |
| **Instance**       | Bound parameter values    | Specific configuration chosen (e.g. port=5432)       |
| **Materialized**   | Live resource handle      | Real-world artefact (JDBC URL, PID, endpoint)        |

The `Element` interface represents the **model** level. Instances and
materialized elements are produced during compilation and execution,
respectively.

### Interface overview

| Method               | Returns                       | Purpose                                       |
|----------------------|-------------------------------|-----------------------------------------------|
| `name()`             | `String`                      | Unique identifier within the study            |
| `tags()`             | `Map<String, String>`         | Classification metadata (includes `"name"`)   |
| `parameters()`       | `List<Parameter<?>>`          | Configurable dimensions of this element       |
| `dependencies()`     | `List<Element>`               | Other elements this one depends on (DAG)      |
| `healthCheck()`      | `Optional<HealthCheckSpec>`   | Readiness verification strategy               |
| `instancingScope()`  | `Optional<InstancingScope>`   | PER_TRIAL, PER_GROUP, or PER_RUN              |

### Element lifecycle

```
NOT_STARTED --> STARTING --> READY --> RUNNING --> STOPPING --> STOPPED --> TEARDOWN --> TERMINATED
```

- **STARTING**: provisioning and configuring the resource.
- **READY**: health check has passed; element is available.
- **RUNNING**: trials are actively using this element.
- **STOPPING**: graceful shutdown initiated.
- **TEARDOWN**: final cleanup (remove temp files, collect artefacts).

### Dependencies

Elements form a directed acyclic graph through `dependencies()`. If element
A depends on element B:

- B must reach READY before A begins STARTING.
- A must reach STOPPED before B begins STOPPING.

The compiler uses the dependency graph to generate `DeployElement` and
`TeardownElement` atomic steps in the correct order.

## RelationshipType

`RelationshipType` is an enum with three values. It answers the question:
*when two elements are used by different trials, how do those usages
interact?*

### MUTUALLY_EXCLUSIVE

The element cannot be used concurrently. Trials that depend on it are
serialized through barriers.

```
Trial 1: ----[Element]----
Trial 2:                    ----[Element]----
                ^ barrier prevents overlap
```

Use when the resource does not support concurrent access -- for example, a
single GPU, an exclusive file lock, or a non-thread-safe singleton service.

The compiler inserts barriers so that at most one trial accesses the
element at any time.

### SHARED

The element may be shared concurrently. All trials that depend on it use
the same running instance at the same time.

```
Trial 1: ----[Element (shared)]----
Trial 2:     ----[Element (shared)]----
             ^ concurrent access to same instance
```

Use when the resource is thread-safe or read-only -- for example, a
connection pool, a read-only cache, or a shared reference dataset.

No barriers are needed. The element has a single instance whose lifecycle
spans all dependent trials.

### INSTANCED_PER

A fresh instance is created per defined scope. Each trial (or group, or
run) gets its own isolated copy of the element.

```
Trial 1: ----[Element instance 1]----
Trial 2:     ----[Element instance 2]----
             ^ independent, isolated instances
```

Use when trials need isolated state to avoid cross-contamination -- for
example, per-trial temporary databases, per-trial Docker containers, or
trial-specific scratch storage.

The `instancingScope()` on the element determines how coarse the isolation
is:

| Scope        | Meaning                                |
|--------------|----------------------------------------|
| `PER_TRIAL`  | One instance per individual trial      |
| `PER_GROUP`  | One instance per logical trial group   |
| `PER_RUN`    | One instance per study run             |

### Summary table

| Relationship       | Concurrent Access | Instance Count | Barriers Required | Best For              |
|--------------------|-------------------|----------------|-------------------|-----------------------|
| MUTUALLY_EXCLUSIVE | No                | 1              | Yes               | Safety-critical resources |
| SHARED             | Yes               | 1              | No                | Read-heavy / thread-safe  |
| INSTANCED_PER      | Yes               | N              | No                | Isolation-required        |

Convenience methods on the enum:

- `allowsConcurrency()` -- true for SHARED and INSTANCED_PER.
- `requiresSingleInstance()` -- true for SHARED and MUTUALLY_EXCLUSIVE.
- `requiresBarriers()` -- true only for MUTUALLY_EXCLUSIVE.

## How Relationships Affect Planning

When a `TestPlan` is committed, the compiler inspects every element
relationship and its connection to defined axes to determine:

1. **Dynamic Scoping** -- whether an element configuration varies.
2. **Group boundaries** -- which trials can share an element instance.
3. **Concurrency** -- which trials can execute in parallel.
4. **Barrier placement** -- where synchronization points are needed.
5. **Instance lifecycle** -- how many deploy/teardown steps to generate.

### Dynamic Scoping and Axis Binding

The compiler automatically determines the optimal **Instancing Scope** based
on axis usage:

*   **GLOBAL**: If an element's parameters are all constant across the
    entire trial space (no axes bind to them), the element is instantiated
    once and shared by all trials.
*   **PER_TRIAL**: If one or more of an element's parameters are bound to
    an axis with multiple values, the element's configuration varies per
    trial. The compiler plans separate instances for each trial to ensure
    isolation and correct configuration.

For `MUTUALLY_EXCLUSIVE` relationships the compiler serializes trials
through `BarrierSync` steps. For `SHARED` relationships no barriers are
inserted but the element's lifecycle spans all dependent trials. For
`INSTANCED_PER` relationships the compiler generates separate
`DeployElement` and `TeardownElement` steps for each scope instance.

## Example: Elements and Relationships in a TestPlan

```java
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.plan.TestPlan;

// Assume elements are provided by the adopting system
Element database  = /* ... */;  // "postgres"
Element cache     = /* ... */;  // "redis"
Element appServer = /* ... */;  // "app-server", depends on database and cache

// TODO: This might be out of order if we presume that elements must be added to a plan in order to make the
// axes which depend on them available in the builder or planning state.
TestPlan plan = TestPlan.builder()
    .name("performance-study")
    .withAxis(/* ... */)
    .withElement(database)
    .withElement(cache)
    .withElement(appServer)
    // Only one trial may use the database at a time
    .relationship(database, appServer, RelationshipType.MUTUALLY_EXCLUSIVE)
    // All trials share the same cache instance
    .relationship(cache, appServer, RelationshipType.SHARED)
    .build();
```

In the compiled execution plan:

- The database is deployed once and protected by barriers so trials access
  it one at a time.
- The cache is deployed once and shared concurrently by all trials.
- The app server lifecycle is tied to the trials it serves.

## Further Reading

- [Test Plans and Axes](test-plans-and-axes.md) -- how elements are
  declared within a test plan
- [Execution Plans](execution-plans.md) -- how the compiler turns
  relationships into barriers and step ordering
- [../reference/contract-types.md](../reference/contract-types.md) --
  formal interface contracts for Element and RelationshipType
- [../howto/build-test-plan.md](../howto/build-test-plan.md) -- recipe
  for assembling elements into a plan
