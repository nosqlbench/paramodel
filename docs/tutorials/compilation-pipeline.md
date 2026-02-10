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

# The Compilation Pipeline

In [Your First Test Plan](first-test-plan.md) you called `plan.commit()` to
turn a `TestPlan` into an `ExecutionPlan` in a single step. Under the hood
that call runs an 8-stage compilation pipeline. In this tutorial you will use
the `DefaultCompiler` from `paramodel-engine` to run those stages explicitly,
inspect the intermediate results, and understand what each stage does.

**Prerequisites:** complete [Getting Started](getting-started.md) and
[Your First Test Plan](first-test-plan.md).

## Goal

Build a three-parameter test plan, compile it through the standard 8-stage
pipeline, and inspect the resulting `ExecutionPlan` -- its steps, execution
graph, metadata, and optimization metrics.

## Step 1: Create a TestPlan

We will model a database benchmark with three parameters:

| Parameter | Domain |
|-----------|--------|
| `database` | `cassandra`, `mongodb`, `postgres` |
| `queryType` | `point`, `range`, `aggregate` |
| `batchSize` | `1`, `10`, `100`, `1000` |

```java
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;

// Define domains
MockDomain<String> dbDomain = MockDomain.of("cassandra", "mongodb", "postgres");
MockDomain<String> queryDomain = MockDomain.of("point", "range", "aggregate");
MockDomain<Integer> batchDomain = MockDomain.of(1, 10, 100, 1000);

// Create parameters
MockParameter<String> database = MockParameter.of("database", dbDomain);
MockParameter<String> queryType = MockParameter.of("queryType", queryDomain);
MockParameter<Integer> batchSize = MockParameter.of("batchSize", batchDomain);

// Build the test plan
TestPlan testPlan = MockTestPlan.builder()
    .parameter(database)
    .parameter(queryType)
    .parameter(batchSize)
    .axis(MockAxis.of("databases",
        MockElement.exhaustive("database")))
    .axis(MockAxis.of("queries",
        MockElement.exhaustive("queryType")))
    .axis(MockAxis.of("batching",
        MockElement.boundary("batchSize")))
    .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
    .build();
```

Note two things that differ from the earlier tutorial:

- The `batchSize` axis uses `MockElement.boundary("batchSize")` instead of
  `MockElement.exhaustive(...)`. Boundary traversal only tests the extreme
  values (smallest and largest) of the domain, which reduces the trial count.
- We set `OptimizationStrategy.PRUNE_REDUNDANT` to tell the compiler to
  eliminate redundant steps during optimization.

## Step 2: Build the Compiler

Instead of calling `testPlan.commit()` directly, we create a `DefaultCompiler`
with the standard pipeline. This gives us access to compilation metadata,
timing, and error reporting.

```java
import io.nosqlbench.paramodel.engine.compiler.*;
import io.nosqlbench.paramodel.compilation.*;

Compiler compiler = DefaultCompiler.builder()
    .standardPipeline()
    .build();
```

`standardPipeline()` registers all eight stages in order. You could also add
stages individually with `.stage(new ValidationStage())` and so on, which is
useful when you want to swap in custom stages.

## Step 3: Compile

```java
Compiler.CompilationResult result = compiler.compile(testPlan);
```

The `compile()` method returns a `CompilationResult` rather than throwing on
failure. This lets you handle errors gracefully.

```java
if (result.isSuccess()) {
    ExecutionPlan executionPlan = result.executionPlan().orElseThrow();
    System.out.println("Compilation succeeded.");
    System.out.println("  Duration: " + result.compilationDuration().toMillis() + "ms");
} else {
    System.out.println("Compilation failed:");
    for (Compiler.CompilationError error : result.errors()) {
        System.out.println("  [" + error.severity() + "] " + error.message());
        error.suggestion().ifPresent(s ->
            System.out.println("    Suggestion: " + s));
    }
}
```

## The 8 Stages Explained

When `compile()` runs, it processes the `TestPlan` through the following
stages, in order. If any stage adds an error to the compilation context, the
pipeline aborts and returns the error immediately.

### Stage 1: Validation

Verifies that the `TestPlan` is structurally and semantically correct.

- All axes have non-empty value lists.
- All element and axis names are unique.
- Relationship graphs are acyclic.
- Policies (retry counts, timeouts) are sensible.

If validation fails, compilation stops here. You can also run validation
independently with `compiler.validate(testPlan)`.

### Stage 2: Normalization

Canonicalizes the plan so that downstream stages see a consistent
representation.

- Applies default policies where none were specified.
- Resolves element references.
- Normalizes relationship symmetry.

### Stage 3: Trial Enumeration

Expands the parameter space into individual trials by computing the Cartesian
product of all axes. Constraint filters (if any) eliminate invalid
combinations.

For our example: 3 databases x 3 query types x boundary(4 batch sizes) = 3 x
3 x 2 = **18 trials** (boundary traversal picks the two extreme values from
the batch-size domain).

### Stage 4: Instantiation

Creates concrete parameter values from domains and computes element instance
scopes (shared, instanced-per-trial, etc.) based on relationships.

### Stage 5: Step Generation

Converts trials into `AtomicStep` objects -- the fundamental unit of work in an
`ExecutionPlan`. Step types include:

- `DEPLOY_ELEMENT` -- provision a resource
- `EXECUTE_TRIAL` -- run a single trial with its parameter bindings
- `TEARDOWN_ELEMENT` -- clean up a resource
- `BARRIER` -- synchronization point
- `CHECKPOINT` -- persist execution state for recovery

### Stage 6: Dependency Analysis

Builds the `ExecutionGraph` -- a directed acyclic graph (DAG) that captures
dependencies between steps. This graph determines:

- Which steps can execute in parallel.
- The critical path (longest chain of dependent steps).
- Required barrier placement.

### Stage 7: Optimization

Applies transformation passes controlled by the `OptimizationStrategy`:

- **Barrier coalescing** -- merge adjacent barriers into one synchronization
  point.
- **Step fusion** -- combine deploy + health-check into a single step.
- **Redundancy elimination** -- remove duplicate deployment steps.
- **Critical-path prioritization** -- schedule the longest chain first.

With `PRUNE_REDUNDANT`, the optimizer focuses on eliminating duplicates and
unnecessary barriers.

### Stage 8: Code Generation

Materializes the final `ExecutionPlan` from the optimized graph. Computes
metadata (fingerprint, compilation version, timestamps) and assigns the
immutable plan its unique ID.

## Step 4: Inspect the ExecutionPlan

After a successful compilation, the `ExecutionPlan` exposes everything you need
to understand what will happen at execution time.

```java
ExecutionPlan executionPlan = result.executionPlan().orElseThrow();

// High-level metrics
System.out.println("Plan ID:          " + executionPlan.id());
System.out.println("Steps:            " + executionPlan.steps().size());
System.out.println("Graph nodes:      " + executionPlan.executionGraph().nodes().size());
System.out.println("Max parallelism:  " + executionPlan.estimatedMaxParallelism());

// Metadata
ExecutionPlanMetadata meta = executionPlan.metadata();
System.out.println("Compiled at:      " + meta.compiledAt());
System.out.println("Compiler version: " + meta.compilationVersion());
System.out.println("Fingerprint:      " + meta.fingerprint());

// Compilation statistics
Compiler.CompilationStatistics stats = result.statistics();
System.out.println("Trials generated:       " + stats.trialsGenerated());
System.out.println("Steps generated:        " + stats.stepsGenerated());
System.out.println("Barriers generated:     " + stats.barriersGenerated());
System.out.println("Optimizations applied:  " + stats.optimizationsApplied());
```

## Step 5: Review the Optimization Strategy

You can confirm which strategy was applied and view its effect:

```java
System.out.println("Optimization strategy: " + testPlan.optimizationStrategy());
System.out.println("Optimization metrics:  " + executionPlan.metadata().optimizationMetrics());
```

Different strategies trade off compilation time against execution efficiency:

| Strategy | Effect |
|----------|--------|
| `NONE` | No optimization; compile as fast as possible |
| `PRUNE_REDUNDANT` | Remove duplicate and unnecessary steps |
| `FULL` | Apply all available optimization passes |

## Complete Program

```java
import io.nosqlbench.paramodel.compilation.*;
import io.nosqlbench.paramodel.engine.compiler.*;
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;

public class CompilationTutorial {
    public static void main(String[] args) {
        // 1. Create test plan
        MockDomain<String> dbDomain = MockDomain.of("cassandra", "mongodb", "postgres");
        MockDomain<String> queryDomain = MockDomain.of("point", "range", "aggregate");
        MockDomain<Integer> batchDomain = MockDomain.of(1, 10, 100, 1000);

        MockParameter<String> database = MockParameter.of("database", dbDomain);
        MockParameter<String> queryType = MockParameter.of("queryType", queryDomain);
        MockParameter<Integer> batchSize = MockParameter.of("batchSize", batchDomain);

        TestPlan testPlan = MockTestPlan.builder()
            .parameter(database)
            .parameter(queryType)
            .parameter(batchSize)
            .axis(MockAxis.of("databases",
                MockElement.exhaustive("database")))
            .axis(MockAxis.of("queries",
                MockElement.exhaustive("queryType")))
            .axis(MockAxis.of("batching",
                MockElement.boundary("batchSize")))
            .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
            .build();

        // 2. Build compiler
        Compiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        // 3. Compile
        Compiler.CompilationResult result = compiler.compile(testPlan);

        if (result.isSuccess()) {
            ExecutionPlan executionPlan = result.executionPlan().orElseThrow();

            System.out.println("Compilation succeeded in " +
                result.compilationDuration().toMillis() + "ms");
            System.out.println("Plan ID:     " + executionPlan.id());
            System.out.println("Steps:       " + executionPlan.steps().size());
            System.out.println("Graph nodes: " +
                executionPlan.executionGraph().nodes().size());
        } else {
            System.err.println("Compilation failed:");
            result.errors().forEach(err ->
                System.err.println("  " + err.message()));
        }
    }
}
```

## What You Learned

- The **DefaultCompiler** lets you run the compilation pipeline explicitly
  rather than relying on `TestPlan.commit()`.
- The pipeline has **8 sequential stages**: Validation, Normalization, Trial
  Enumeration, Instantiation, Step Generation, Dependency Analysis,
  Optimization, and Code Generation.
- Each stage reads from and writes to a shared `CompilationContext`; errors
  at any stage abort the pipeline.
- The resulting `ExecutionPlan` exposes steps, an execution graph, metadata,
  and optimization metrics.
- `OptimizationStrategy` controls how aggressively the compiler prunes and
  reorganizes the plan.

## Next Steps

- **[Running Trials](running-trials.md)** -- take the compiled `ExecutionPlan`
  and execute it concurrently.
- **[Execution Plans](../concepts/execution-plans.md)** -- learn about steps,
  barriers, graphs, and checkpointing in depth.
- **[Compilation Stages Reference](../reference/compilation-stages.md)** --
  full reference for every stage, its inputs, outputs, and configuration
  knobs.
