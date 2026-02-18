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

# How to Build a Test Plan

This guide provides self-contained recipes for assembling parameters into
axes, adding elements and relationships, setting ordering and optimization
strategies, validating, and committing a test plan to an execution plan.

> **Background**: See [Test Plans and Axes](../concepts/test-plans-and-axes.md)
> for the conceptual model. For a full walkthrough, see
> [First Test Plan](../tutorials/first-test-plan.md).

---

## Create a basic test plan

Use `MockTestPlan.builder()` to start building. Give it a name, add at
least one axis, and call `.build()`.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;

MockTestPlan plan = MockTestPlan.builder()
    .name("cache-study")
    .axis(MockAxis.of("cache_size", 128, 256, 512))
    .axis(MockAxis.of("concurrency", 10, 50, 100))
    .build();

assert plan.name().equals("cache-study");
assert plan.axes().size() == 2;
```

---

## Add axes from explicit values

An `Axis<T>` is a named list of discrete values to explore. Use
`MockAxis.of()` to create axes from varargs or a `List<T>`.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;

// From varargs
MockAxis<String> modelAxis = MockAxis.of("model", "gpt-4", "claude-3", "gemini-pro");

// From a List
MockAxis<Double> tempAxis = MockAxis.of("temperature",
    java.util.List.of(0.0, 0.25, 0.5, 0.75, 1.0));

// From the builder for more control
MockAxis<Integer> batchAxis = MockAxis.<Integer>builder("batch_size")
    .values(16, 32, 64, 128, 256)
    .description("Training batch size controlling memory usage")
    .build();

assert modelAxis.values().size() == 3;
assert tempAxis.values().size() == 5;
assert batchAxis.description().isPresent();
```

---

## Understand axis boundary values

Axes expose boundary values (first and last elements in the ordered list).
These are used by the edge-first trial ordering strategy.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;

MockAxis<Integer> axis = MockAxis.of("param", 10, 20, 30, 40, 50);

java.util.List<Integer> boundaries = axis.boundaryValues();
// boundaries = [10, 50] -- first and last

assert boundaries.contains(10);
assert boundaries.contains(50);
assert boundaries.size() == 2;
```

---

## Add elements to a test plan

Elements represent resources that participate in study execution (databases,
caches, services, etc.). Use `MockElement` to create them.

```java
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;

// Simple element with no parameters
var database = MockElement.of("postgres");

// Typed element
var cache = MockElement.ofType("redis-cache", "cache");

// Element with parameters
var appServer = MockElement.builder("api-server")
    .type("service")
    .parameter(
        io.nosqlbench.paramodel.parameters.types.IntegerParameter
            .range("port", 8080, 8090))
    .build();

MockTestPlan plan = MockTestPlan.builder()
    .name("integration-study")
    .axis(MockAxis.of("threads", 1, 2, 4, 8))
    .element(database)
    .element(cache)
    .element(appServer)
    .build();

assert plan.elements().size() == 3;
```

---

## Define relationships between elements

Relationship types are a **directional property of each dependency edge**.
When element A depends on element B, A declares how it relates to B via
the `RelationshipType` on the `Element.Dependency` record.

| Type | Concurrency | Instance Sharing | Best For |
|---|---|---|---|
| `SHARED` (default) | Concurrent | Shared | Read-heavy / thread-safe |
| `EXCLUSIVE` | Serialized | Shared | Safety-critical resources |
| `DEDICATED` | N/A | Dedicated per dependent | Per-tenant isolation |
| `LINEAR` | Serial | Shared within trial scope | Strict ordering and data flow in trial |
| `LIFELINE` | Concurrent | Shared | Container-on-node lifecycle |

Element lifecycle (when instances are redeployed vs. persisted) is determined
by the fingerprint-based group mechanism in the compilation pipeline, not by
relationship type. If an element's parameters change between trials, it is
redeployed automatically; if not, it persists.

Relationships are declared on the element's dependency edges via the builder.

```java
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.mock.plan.MockElement;

var database = MockElement.of("postgres");

// Exclusive access: only one trial uses the database at a time
var appServer = MockElement.builder("api-server")
    .dependency(database, RelationshipType.EXCLUSIVE)
    .build();

// Shared (default): all trials share the same cache instance
var cache = MockElement.of("redis-cache");
var worker = MockElement.builder("worker")
    .dependency(cache)  // defaults to SHARED
    .build();
```

---

## Set element dependencies

Elements form a directed acyclic graph (DAG). If element A depends on
element B, then B starts first and stops last.

```java
import io.nosqlbench.paramodel.mock.plan.MockElement;

var storage = MockElement.of("storage-volume");

var database = MockElement.builder("postgres")
    .dependency(storage)
    .build();

var appServer = MockElement.builder("api-server")
    .dependency(database)
    .build();

// Start order: storage -> database -> appServer
// Stop order:  appServer -> database -> storage
assert database.dependencies().size() == 1;
assert appServer.dependencies().get(0).target().name().equals("postgres");
```

---

## Set the optimization strategy

`OptimizationStrategy` controls how aggressively the compiler optimizes the
execution plan. Set it on the builder.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.plan.OptimizationStrategy;

MockTestPlan plan = MockTestPlan.builder()
    .name("optimized-study")
    .axis(MockAxis.of("cache", 128, 256, 512))
    .axis(MockAxis.of("threads", 1, 4, 16))
    .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
    .build();

assert plan.optimizationStrategy() == OptimizationStrategy.PRUNE_REDUNDANT;
```

Available strategies:

| Strategy | Compilation speed | Runtime efficiency | Use case |
|---|---|---|---|
| `NONE` | Fastest | Baseline | Debugging |
| `BASIC` | Fast | Modest gains | Development |
| `PRUNE_REDUNDANT` | Moderate | Good | Production |
| `AGGRESSIVE` | Slowest | Best | Long-running studies |

## Set the binding policy

`BindingPolicy` determines how the binder behaves when required values
are missing or when multiple inputs conflict.

```java
import io.nosqlbench.paramodel.parameters.BindingPolicy;

MockTestPlan plan = MockTestPlan.builder()
    .name("strict-study")
    .axis(MockAxis.of("threads", 1, 4, 16))
    .bindingPolicy(BindingPolicy.STRICT)
    .build();
```

Common policies:

- `STRICT`: Fail if any required parameter lacks a value.
- `DEFAULT_IF_MISSING`: Use the parameter's default value if available.
- `IGNORE_UNKNOWN`: Silently skip inputs that don't match any parameter.

## Bind axes to element parameters

Axes are automatically bound to element parameters during compilation. You
can control this binding through naming conventions:

1.  **Simple matching**: Name the axis exactly like the parameter (e.g.,
    `"threads"`). This binds the axis to that parameter for all elements.
2.  **Qualified matching**: Name the axis `elementName.parameterName` (e.g.,
    `"postgres.port"`) to bind it to a specific parameter of a specific
    element.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;

var db = MockElement.builder("db")
    .parameter(IntegerParameter.range("port", 5432, 5435))
    .build();

MockTestPlan plan = MockTestPlan.builder()
    .name("binding-example")
    // Binds to db.port specifically
    .axis(MockAxis.of("db.port", 5432, 5433))
    .element(db)
    .build();
```

---

## Calculate trial space size

The trial space size is the Cartesian product of all axis cardinalities.
Use `trialSpaceSize()` on the built plan.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;

MockTestPlan plan = MockTestPlan.builder()
    .name("size-example")
    .axis(MockAxis.of("model", "gpt-4", "claude-3"))           // 2 values
    .axis(MockAxis.of("temperature", 0.0, 0.5, 1.0))           // 3 values
    .axis(MockAxis.of("max_tokens", 100, 500, 1000, 2000))     // 4 values
    .build();

// Total trials = 2 * 3 * 4 = 24
assert plan.trialSpaceSize() == 24;
```

---

## Validate a test plan

Call `plan.validate()` before committing to check for structural problems.
The result is a `ValidationResult`.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.ValidationResult;

MockTestPlan plan = MockTestPlan.builder()
    .name("validated-study")
    .axis(MockAxis.of("threads", 1, 2, 4))
    .build();

ValidationResult result = plan.validate();
assert result.isPassed();
```

---

## Commit a test plan to an execution plan

Committing transforms the mutable `TestPlan` into an immutable
`ExecutionPlan`. After commit, the plan is locked and cannot be modified.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.plan.ExecutionPlan;

MockTestPlan plan = MockTestPlan.builder()
    .name("committed-study")
    .axis(MockAxis.of("threads", 1, 2, 4, 8))
    .build();

assert !plan.isCommitted();

ExecutionPlan execPlan = plan.commit();

assert plan.isCommitted();
assert execPlan.id() != null;
assert execPlan.testPlanFingerprint() != null;
```

A committed plan cannot be committed again:

```java
// This will throw IllegalStateException:
// plan.commit();
```

---

## Reorder axes

Use `reorderAxes()` to change which axis is "major" (outermost loop). This
affects trial ordering -- particularly edge-first strategies.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.plan.TestPlan;

MockTestPlan plan = MockTestPlan.builder()
    .name("reorderable-study")
    .axis(MockAxis.of("model", "gpt-4", "claude-3"))
    .axis(MockAxis.of("temperature", 0.0, 0.5, 1.0))
    .build();

// Original order: model (major), temperature (minor)
assert plan.axes().get(0).name().equals("model");

// Reorder: temperature (major), model (minor)
TestPlan reordered = plan.reorderAxes(
    java.util.List.of("temperature", "model")
);

assert reordered.axes().get(0).name().equals("temperature");
assert reordered.axes().get(1).name().equals("model");
```

Note: `reorderAxes()` returns a new `TestPlan` -- the original is unchanged.
Reordering is not allowed after commit.

---

## Inspect an execution plan

After committing, inspect the `ExecutionPlan` for steps, barriers, ordering,
and resource requirements.

```java
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.plan.ExecutionPlan;

MockTestPlan plan = MockTestPlan.builder()
    .name("inspect-study")
    .axis(MockAxis.of("cache_size", 128, 256, 512))
    .axis(MockAxis.of("concurrency", 10, 50, 100))
    .build();

ExecutionPlan execPlan = plan.commit();

// Inspect the execution plan
System.out.printf("ID: %s%n", execPlan.id());
System.out.printf("Steps: %d%n", execPlan.steps().size());
System.out.printf("Barriers: %d%n", execPlan.barriers().size());
System.out.printf("Trial ordering: %s%n", execPlan.trialOrdering().description());
System.out.printf("Max parallelism: %d%n", execPlan.estimatedMaxParallelism());
```

---

## Next steps

- [How to Define Parameters](./define-parameters.md) -- creating the
  parameters that feed into axes
- [How to Compose Constraints](./compose-constraints.md) -- adding
  validation rules
- [How to Implement a Contract](./implement-a-contract.md) -- creating your
  own Paramodel implementation
- [Test Plans and Axes](../concepts/test-plans-and-axes.md) -- the
  conceptual background
