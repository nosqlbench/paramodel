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

# Cardinality and Operational Costs

In Paramodel, **Cardinality** refers to the number of instances of an **Element**
that are created during a study. Managing cardinality is the primary lever for
controlling the **operational cost** (resource usage) and **timing** (wall-clock
duration) of your test plans.

## How Cardinality is Determined

The number of element instances is not set manually. Instead, it is **derived**
by the compiler based on three factors:

1.  **Axis Targeting**: Does an axis vary a parameter belonging to this element?
2.  **Instancing Scope**: Is the element run-scoped, group-scoped, or trial-scoped?
3.  **Relationship Type**: How does the element relate to its dependencies?

### 1. Axis Targeting and "Taint"

If an axis targets a parameter of Element A, then Element A's configuration
varies across the trial space. This "taints" the element, forcing it into a
finer instancing group level (group level > 0).

**Taint Propagation**: If Element B depends on Element A, and A is tainted,
then B is also tainted. This ensures that downstream elements are redeployed
when their upstream dependencies change.

### 2. Group Levels

| Group Level       | Cardinality Formula | Best For |
|-------------------|---------------------|----------|
| **0**             | Exactly 1           | Static infrastructure (e.g., a shared DB) |
| **1..N**          | Unique Fingerprints | Resources that persist while bound axis values are constant |
| **Deepest**       | Number of Trials    | Total isolation (e.g., a fresh container per trial) |

### 3. Relationship Types

The `RelationshipType` on a dependency edge affects how instances are shared:

- **SHARED**: Multiple dependents use the same instance. (Lower Cardinality)
- **DEDICATED**: Every dependent gets its own private instance. (Higher Cardinality)
- **LINEAR**: Both elements are trial elements in the same scope. (Shared instance within scope, ordered actions)
- **EXCLUSIVE**: 1 instance, but serial access via barriers. (Affects Timing, not Cardinality)

## Operational Costs

Operational costs are driven by the total number of **Lifecycle Transitions**
(Deploy/Teardown steps) in the execution plan.

### Factors increasing cost:
- **High Cardinality**: Leaf-level (deepest group) scope on a heavy resource (like a database).
- **Frequent Redeployment**: Intermediate group level with axes that change frequently.
- **Dedicated Instances**: Using `DEDICATED` relationships for many dependents.

### Strategies for cost reduction:
- **Axis Reordering**: Place axes that trigger redeployment (e.g., "db_version")
  as **Major Axes** (outer loops) to minimize churn.
- **Scope Hints**: Use the `instancing_hint` tag (`per_run`) to override
  automatic derivation if a resource is safely reusable.
- **Shared Relationships**: Default to `SHARED` unless isolation is strictly required.

## Timing and the Execution Graph

The wall-clock time of a study is determined by the **Critical Path** in the
`ExecutionGraph`.

### Parallelism vs. Serialization
- **SHARED** relationships allow concurrent trial execution, shortening the
  critical path.
- **EXCLUSIVE** relationships insert **Barriers**, forcing trials to run
  sequentially for that resource, lengthening the wall-clock time.

### Throughput vs. Latency
- High cardinality often increases **Cumulative Latency** (more time spent
  deploying) but may improve **Throughput** if resources are isolated and
  don't contend.
- Low cardinality minimizes deployment overhead but may create **Resource
  Contention** or serialization bottlenecks.

## Cost Estimation from Telemetry

Paramodel's `CostEstimator` uses historical logs to predict future costs:
1. **Transition Cost**: Average time to DEPLOY/TEARDOWN an element.
2. **Execution Cost**: Average time to EXECUTE a trial.
3. **Resource Footprint**: Peak CPU/Memory usage per instance.

The compiler multiplies these telemetry-derived averages by the **Derived
Cardinality** to provide an estimated duration and cost before you commit to
the run.

## Summary

Manage your test plan's efficiency by:
1. **Monitoring the graph**: Use `executionPlan.executionGraph().statistics()`
   to see node counts and parallelism.
2. **Prioritizing major axes**: Reduce redeployment churn by sorting axes.
3. **Choosing group levels wisely**: Prefer `SHARED` relationships and group level 0 for expensive
   resources.
