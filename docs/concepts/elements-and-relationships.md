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
| `dependencies()`     | `List<Dependency>`            | Directed dependency edges (target + relationship type) |
| `healthCheck()`      | `Optional<HealthCheckSpec>`   | Readiness verification strategy               |
| `shutdownSemantics()`| `ShutdownSemantics`           | SERVICE or COMMAND                             |

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

Elements form a directed acyclic graph through `dependencies()`. Each
dependency is represented as an `Element.Dependency` record carrying both
the target element and a `RelationshipType` that describes how the
dependent element relates to its dependency:

```java
record Dependency(Element target, RelationshipType type) {}
```

If element A depends on element B:

- B must reach READY before A begins STARTING.
- A must reach STOPPED before B begins STOPPING.

The compiler uses the dependency graph to generate `DeployElement` and
`TeardownElement` atomic steps in the correct order.

## RelationshipType

`RelationshipType` is an enum with four values. It is a **directional
property of each dependency edge** -- element A declares how it relates to
its dependency B. A's view of B can differ from C's view of B.

### SHARED (default)

The dependency may be shared concurrently by multiple dependents. All
elements that depend on it use the same running instance at the same time.

```
Element A: ----[depends on B (shared)]----
Element C:     ----[depends on B (shared)]----
               ^ concurrent access to same B instance
```

Use when the resource is thread-safe or read-only -- for example, a
connection pool, a read-only cache, or a shared reference dataset.

### EXCLUSIVE

During the dependent's lifetime, no other dependent of the target can be
active. Dependents are serialized through barriers.

```
Element A: ----[depends on B (exclusive)]----
Element C:                                     ----[depends on B (exclusive)]----
                                ^ barrier prevents overlap
```

Use when the resource does not support concurrent access -- for example, a
single GPU, an exclusive file lock, or a non-thread-safe singleton service.

### DEDICATED

The target gets its own instance for this dependent. The instance is never
shared with other elements.

Use when the dependent requires complete isolation -- for example,
per-tenant database instances or isolated test environments.

### LIFELINE

The target's lifecycle subsumes the dependent's lifecycle. When the target
tears down, the dependent is automatically torn down. No explicit teardown
step is emitted for the dependent.

Use when the dependent is inherently tied to the target's lifetime -- for
example, Docker containers running on a compute node. Tearing down the
node implicitly destroys all containers.

### Summary table

| Relationship | Concurrent Access | Instance Sharing | Barriers Required | Best For                    |
|-------------|-------------------|------------------|-------------------|-----------------------------|
| SHARED      | Yes               | Shared           | No                | Read-heavy / thread-safe    |
| EXCLUSIVE   | No                | Shared           | Yes               | Safety-critical resources   |
| DEDICATED   | N/A               | Dedicated        | No                | Per-tenant isolation        |
| LIFELINE    | Yes               | Shared           | No                | Container-on-node lifecycle |

Semantic methods on the enum:

- `requiresSerializationBarrier()` -- true for EXCLUSIVE.
- `requiresDedicatedInstance()` -- true for DEDICATED.
- `impliesLifecycleCoupling()` -- true for LIFELINE.

### Instance Lifecycle

Element instance lifecycle (when an element is redeployed vs. persisted) is
determined by the fingerprint-based group mechanism in the compilation
pipeline, not by relationship type. If an element's parameters vary across
trials (because an axis targets them), the element is redeployed at group
boundaries when the configuration fingerprint changes. If no axis targets the
element's parameters, it deploys once and persists for the entire run.

## How Relationships Affect Planning

When a `TestPlan` is committed, the compiler inspects every dependency edge
and its connection to defined axes to determine:

1. **Dynamic Scoping** -- whether an element configuration varies.
2. **Group boundaries** -- which trials can share an element instance.
3. **Concurrency** -- which trials can execute in parallel.
4. **Barrier placement** -- where synchronization points are needed.
5. **Instance lifecycle** -- how many deploy/teardown steps to generate.

### Fingerprint-Based Lifecycle

The compiler automatically determines element lifecycle based on
parameter-axis overlap:

*   **Run-scoped**: If an element's parameters are all constant across the
    entire trial space (no axes bind to them), the element is instantiated
    once and shared by all trials.
*   **Group-scoped**: If one or more of an element's parameters are bound to
    an axis with multiple values, the element persists for contiguous trial
    blocks where its configuration fingerprint is constant. It is redeployed
    at group boundaries when the fingerprint changes.

For `EXCLUSIVE` dependency edges the compiler serializes trials through
`BarrierSync` steps. For `SHARED` edges no barriers are inserted but the
element's lifecycle spans all dependent trials. For `LIFELINE` edges the
dependent's teardown is omitted -- the target's teardown implicitly
destroys the dependent.

## Example: Elements and Relationships in a TestPlan

```java
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.plan.TestPlan;

// Elements declare their dependencies and relationship types
// database has no dependencies
Element database  = /* ... */;  // "postgres"

// cache has no dependencies
Element cache     = /* ... */;  // "redis"

// appServer depends on database (exclusive) and cache (shared)
Element appServer = /* element builder */
    .dependency(database, RelationshipType.EXCLUSIVE)
    .dependency(cache)  // defaults to SHARED
    .build();

TestPlan plan = TestPlan.builder()
    .name("performance-study")
    .withAxis(/* ... */)
    .withElement(database)
    .withElement(cache)
    .withElement(appServer)
    .build();
```

In the compiled execution plan:

- The database is deployed once and protected by barriers so trials access
  it one at a time (EXCLUSIVE dependency from appServer).
- The cache is deployed once and shared concurrently by all trials
  (SHARED dependency from appServer).
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
