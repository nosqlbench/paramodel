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

# How to Manage Element Cardinality

Effective study execution requires balancing the need for isolation against
the operational costs of deployment. This guide provides recipes for
controlling how many element instances are created and how to optimize
execution timing.

> **Background**: See [Cardinality and Operational Costs](../concepts/cardinality-and-costs.md)
> for the underlying theory.

---

## Control Cardinality via Major Axes

The order of axes in a `TestPlan` determines the nesting of trial loops.
Placing an axis that triggers redeployment as a **Major Axis** (early in
the list) minimizes the number of times an element is torn down and redeployed.

```java
// OPTIMIZED: db_version is major. 
// DB deploys once per version.
TestPlan plan = TestPlan.builder()
    .axis(MockAxis.of("db_version", "15", "16")) // Major
    .axis(MockAxis.of("query_type", "read", "write", "scan"))
    .build();

// SUB-OPTIMAL: query_type is major.
// DB may redeploy for every query_type change if not run-scoped.
```

---

## Use Scope Hints to Force Persistence

If the compiler infers a deep group level but you know the resource is
safely reusable, use an `instancing_hint` to force it to group level 0.

```java
Element db = DefaultElement.builder("postgres")
    .tag("instancing_hint", "global") // Force to group level 0: exactly 1 instance
    .build();
```

Common hint values:
- `per_run`: Deploy once for the entire study.
- `per_trial`: Deploy a fresh instance for every trial.

---

## Share Expensive Dependencies

Default to `RelationshipType.SHARED` for dependencies unless you specifically
require serial access or total isolation.

```java
Element disk = /* expensive storage */;
Element db = DefaultElement.builder("db")
    .dependency(disk, RelationshipType.SHARED) // Share the disk
    .build();
```

---

## Minimize Serialization Bottlenecks

`RelationshipType.EXCLUSIVE` forces trials to wait for each other. If your
wall-clock time is too high, check if you can replace `EXCLUSIVE` with
`DEDICATED` (if the resource is cheap) or `SHARED` (if the resource is
thread-safe).

```java
// Slow: 1 DB, trials run one by one.
app.dependency(db, RelationshipType.EXCLUSIVE);

// Fast (if resources allow): N DBs, trials run in parallel.
app.dependency(db, RelationshipType.DEDICATED);
```

---

## Inspect Predicted Costs

Before committing a large study, use the `ExecutionGraph` statistics to view
the derived cardinality and parallelism.

```java
ExecutionPlan plan = testPlan.commit();
var stats = plan.executionGraph().statistics();

System.out.println("Lifecycle steps: " + stats.nodeCount());
System.out.println("Max parallelism: " + stats.maximumParallelism());
System.out.println("Estimated duration: " + plan.estimatedDuration());
```

---

## Next Steps

- [Cardinality and Operational Costs](../concepts/cardinality-and-costs.md) — theory
- [Build a Test Plan](./build-test-plan.md) — comprehensive plan construction
- [Execution Plans](../concepts/execution-plans.md) — graph and barrier concepts
