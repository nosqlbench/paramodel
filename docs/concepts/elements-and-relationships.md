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
elements interact, which in turn determines concurrency rules and instance
counts.

`Element` lives in `io.nosqlbench.paramodel.elements`.
`RelationshipType` is an enum in the same package.

## Element

### What an Element Represents

An element models any deployable resource needed by trials. The Paramodel
API treats the kind of resource (service, environment, cache, tool, etc.)
as an opaque label supplied via `labels()` (typically the `"type"` key).

Typical examples:
- A database instance (PostgreSQL, Cassandra)
- A Docker container or Kubernetes pod
- A profiler or monitoring agent
- A shared read-only dataset

### Maturity Levels

1.  **Model**: The `Element` interface. Defines what *can* be configured
    (parameters, domains, constraints).
2.  **Instance**: Produced during compilation. Has bound parameter values.
3.  **Materialized**: Produced at runtime. A live resource handle (JDBC URL, PID).

### Interface Overview

| Method               | Returns                       | Purpose                                       |
|----------------------|-------------------------------|-----------------------------------------------|
| `name()`             | `String`                      | Unique identifier within the study            |
| `labels()`           | `Map<String, String>`         | Immutable structural metadata (includes `"name"`, `"type"`) |
| `traits()`           | `Map<String, String>`         | Type-relational metadata (adopter extension point) |
| `tags()`             | `Map<String, String>`         | User-mutable categorization (adopter extension point) |
| `parameters()`       | `List<Parameter<?>>`          | Input dimensions of this element              |
| `resultParameters()` | `List<Parameter<?>>`          | Output dimensions published after deployment   |
| `dependencies()`     | `List<Dependency>`            | Directed dependency edges (target + type)     |
| `healthCheck()`      | `Optional<HealthCheckSpec>`   | Readiness verification strategy               |
| `maxConcurrency()`   | `OptionalInt`                 | Parallel deployment limit (empty = unlimited) |
| `shutdownSemantics()`| `SERVICE` or `COMMAND`        | Explicit stop vs. natural completion          |

## RelationshipType

The `RelationshipType` is a property of the **Dependency Edge**. It describes
how a dependent element relates to its upstream dependency.

### SHARED (Default)
The dependency may be used concurrently by multiple dependents.
- **Cardinality**: Single shared instance.
- **Best For**: Thread-safe services, read-only datasets.

### EXCLUSIVE
Dependents of the target must be serialized.
- **Concurrency**: Compiler inserts barriers to prevent overlap.
- **Cardinality**: Single instance, shared serially.
- **Best For**: Single GPUs, exclusive file locks.

### DEDICATED
The target gets a dedicated instance for each dependent.
- **Isolation**: No sharing between dependents.
- **Cardinality**: High (1 instance per dependent).
- **Best For**: Total test isolation, per-tenant resources.

### LINEAR
Both elements are trial elements in the same scope and must occur in order.
- **Serialization**: Strict serialization is required.
- **Data Flow**: Data flow may be implied between elements in the same trial scope.
- **Cardinality**: Shared instance within the scope, ordered actions.
- **Best For**: Multi-step trial logic, dependent setup/action sequences.

### LIFELINE
The target's lifecycle subsumes the dependent's.
- **Lifecycle**: Dependent is automatically torn down when the target is.
- **Best For**: Containers running on a compute node.

## Cardinality and Scoping

An element's **Cardinality** (how many instances are created) is derived from:
1.  **Axis Targeting**: If an axis targets an element's parameter, its
    configuration varies, forcing a finer scope.
2.  **Taint Propagation**: If A depends on B, and B is tainted (varied),
    then A is also tainted and may require redeployment.

| Group Level | Cardinality | Persistence |
|-------------|-------------|-------------|
| 0 | 1 | Persists for the whole study (no bound axes). |
| 1..N | Unique Fingerprints | Persists while bound axis values are constant. |
| Deepest | Number of Trials | Fresh instance every trial. |

For a deep dive into optimizing these numbers, see [Cardinality and Costs](cardinality-and-costs.md).

## Further Reading

- [Test Plans and Axes](test-plans-and-axes.md) — declaration within a plan
- [Execution Plans](execution-plans.md) — how relationships become barriers
- [Manage Cardinality](../howto/manage-cardinality.md) — recipes for optimization
