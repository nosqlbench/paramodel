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

# Running Trials

In the previous tutorials you built a `TestPlan` and compiled it into an
`ExecutionPlan`. In this tutorial you will take that plan and actually run it
-- executing trials concurrently, collecting results, and analysing the
outcome.

**Prerequisites:** complete [Getting Started](getting-started.md),
[Your First Test Plan](first-test-plan.md), and
[The Compilation Pipeline](compilation-pipeline.md).

## Goal

Execute an `ExecutionPlan` using the `DefaultExecutor` from
`paramodel-engine`, then inspect individual trial results including status,
timing, and observations.

## Step 1: Create and Compile a Test Plan

This step is a brief recap. We define a small plan with two parameters so that
execution finishes quickly.

```java
import io.nosqlbench.paramodel.engine.compiler.*;
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;

// Define parameters
MockDomain<String> opDomain = MockDomain.of("read", "write");
MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4);

MockParameter<String> operation = MockParameter.of("operation", opDomain);
MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);

// Build plan
TestPlan testPlan = MockTestPlan.builder()
    .parameter(operation)
    .parameter(threads)
    .axis(MockAxis.of("ops", MockElement.exhaustive("operation")))
    .axis(MockAxis.of("conc", MockElement.exhaustive("threads")))
    .build();

// Compile
Compiler compiler = DefaultCompiler.builder()
    .standardPipeline()
    .build();

ExecutionPlan executionPlan = compiler.compile(testPlan)
    .executionPlan()
    .orElseThrow();

System.out.println("ExecutionPlan ready. Estimated trials: " +
    executionPlan.estimatedTrialCount());
```

This creates 2 operations x 3 thread counts = **6 trials**.

## Step 2: Configure the Executor

The `DefaultExecutor` manages resource provisioning, concurrency, and result
collection. You configure it through an `ExecutorConfig`, or let it use
defaults.

```java
import io.nosqlbench.paramodel.engine.execution.*;
import io.nosqlbench.paramodel.execution.Executor;

DefaultExecutor executor = DefaultExecutor.builder()
    .build();
```

By default the executor uses a configuration that is appropriate for local
development. For production use you would supply an explicit config:

```java
DefaultExecutor executor = DefaultExecutor.builder()
    .config(ExecutorConfig.builder()
        .maxConcurrentTrials(Runtime.getRuntime().availableProcessors())
        .build())
    .build();
```

The `maxConcurrentTrials` setting caps how many trials run in parallel. A good
starting point is the number of available CPU cores.

## Step 3: Execute the Plan

Call `execute()` to run the plan synchronously. This blocks until every trial
has completed (or failed).

```java
Executor.ExecutionResult result = executor.execute(executionPlan);
```

Internally the executor follows a four-phase lifecycle:

1. **Initializing** -- validates the plan and prepares runtime state.
2. **Deploying** -- provisions any elements (databases, services, etc.)
   declared in the plan.
3. **Executing** -- runs trials concurrently up to the concurrency limit,
   respecting dependency ordering from the execution graph.
4. **Tearing down** -- releases resources and collects final artifacts.

When `execute()` returns, all four phases have completed.

## Step 4: Analyze the Results

The `ExecutionResult` provides aggregate counts and per-trial detail.

```java
import java.time.Duration;

System.out.println("Execution complete.");
System.out.println("  Total trials:   " + result.totalTrialCount());
System.out.println("  Successful:     " + result.successfulTrialCount());
System.out.println("  Failed:         " + result.failedTrialCount());
System.out.println("  Duration:       " + result.duration().toMillis() + "ms");
System.out.println("  Success:        " + result.isSuccess());

// Compute throughput
if (result.duration().toMillis() > 0) {
    double trialsPerSec =
        result.totalTrialCount() * 1000.0 / result.duration().toMillis();
    System.out.printf("  Throughput:     %.1f trials/sec%n", trialsPerSec);
}
```

## Step 5: Inspect Individual Trial Results

Each trial produces a `TrialResult` that captures everything about its
execution.

```java
import io.nosqlbench.paramodel.sequence.TrialResult;

for (TrialResult trialResult : result.trialResults()) {
    System.out.println("Trial: " + trialResult.trial().id());
    System.out.println("  Status:       " + trialResult.status());
    System.out.println("  Attempt:      " + trialResult.attemptNumber());

    // Timing
    TrialResult.ExecutionTiming timing = trialResult.timing();
    Duration elapsed = Duration.between(
        timing.startedAt(), timing.completedAt());
    System.out.println("  Started at:   " + timing.startedAt());
    System.out.println("  Completed at: " + timing.completedAt());
    System.out.println("  Elapsed:      " + elapsed.toMillis() + "ms");

    // Metrics (observations recorded during execution)
    trialResult.metrics().forEach((key, value) ->
        System.out.println("  " + key + ": " + value));

    // Error info (only present for failed trials)
    trialResult.error().ifPresent(err ->
        System.out.println("  Error: [" + err.type() + "] " + err.message()));

    System.out.println();
}
```

Key fields on `TrialResult`:

| Field | Description |
|-------|-------------|
| `trial()` | The `Trial` object with its parameter assignments |
| `status()` | `COMPLETED`, `FAILED`, or `SKIPPED` |
| `metrics()` | `Map<String, Object>` of observations recorded during execution |
| `timing()` | Start and completion timestamps |
| `attemptNumber()` | Which attempt this was (1 for first try, >1 after retries) |
| `error()` | Error details if the trial failed |
| `provenance()` | Configuration fingerprint and environment metadata |

## Step 6: Asynchronous Execution (Optional)

For long-running plans you may prefer non-blocking execution with progress
tracking.

```java
Executor.ExecutionHandle handle = executor.executeAsync(executionPlan);

// Register a progress listener
handle.onProgress(event ->
    System.out.printf("[%s] %s: %d/%d trials (%.0f%%)%n",
        event.timestamp(),
        event.phase(),
        event.completedTrials(),
        event.totalTrials(),
        event.progressPercentage()));

// Register a per-trial listener
handle.onTrialComplete(trialResult ->
    System.out.println("  Completed trial: " + trialResult.trial().id()));

// Wait for completion
Executor.ExecutionResult asyncResult = handle.await();
System.out.println("Async execution finished: " + asyncResult.isSuccess());
```

The `ExecutionHandle` also supports `pause()`, `resume()`, and `cancel()` for
interactive control.

## The Execution Model

The following diagram summarizes the flow from plan to results:

```
ExecutionPlan
     |
     v
+-----------+    +-----------+    +------------+    +------------------+
| Scheduling| -> | Resource  | -> | Parallel   | -> | Result           |
|           |    | Allocation|    | Execution  |    | Collection       |
+-----------+    +-----------+    +------------+    +------------------+
     |                |                |                     |
  Determine       Provision        Run trials          Aggregate
  step order      elements         concurrently        per-trial
  from DAG        (deploy)         up to limit         results
```

- **Scheduling** reads the execution graph and determines which steps can run
  next.
- **Resource Allocation** provisions elements declared in the plan (databases,
  caches, services).
- **Parallel Execution** runs trials concurrently, bounded by the configured
  maximum.
- **Result Collection** gathers each trial's status, timing, metrics, and
  artifacts into the final `ExecutionResult`.

## Complete Program

```java
import io.nosqlbench.paramodel.compilation.*;
import io.nosqlbench.paramodel.engine.compiler.*;
import io.nosqlbench.paramodel.engine.execution.*;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.mock.parameters.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;

public class RunningTrialsTutorial {
    public static void main(String[] args) throws Exception {
        // 1. Create and compile
        MockDomain<String> opDomain = MockDomain.of("read", "write");
        MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4);

        MockParameter<String> operation = MockParameter.of("operation", opDomain);
        MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);

        TestPlan testPlan = MockTestPlan.builder()
            .parameter(operation)
            .parameter(threads)
            .axis(MockAxis.of("ops", MockElement.exhaustive("operation")))
            .axis(MockAxis.of("conc", MockElement.exhaustive("threads")))
            .build();

        Compiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        ExecutionPlan executionPlan = compiler.compile(testPlan)
            .executionPlan()
            .orElseThrow();

        // 2. Execute
        DefaultExecutor executor = DefaultExecutor.builder().build();
        Executor.ExecutionResult result = executor.execute(executionPlan);

        // 3. Analyze
        System.out.println("Total trials: " + result.totalTrialCount());
        System.out.println("Successful:   " + result.successfulTrialCount());
        System.out.println("Failed:       " + result.failedTrialCount());
        System.out.println("Duration:     " + result.duration().toMillis() + "ms");

        // 4. Inspect per-trial results
        for (TrialResult tr : result.trialResults()) {
            Duration elapsed = Duration.between(
                tr.timing().startedAt(), tr.timing().completedAt());
            System.out.printf("  Trial %s: %s (%dms)%n",
                tr.trial().id(), tr.status(), elapsed.toMillis());
        }

        // 5. Resource usage
        Executor.ExecutionMetrics metrics = result.metrics();
        System.out.printf("Peak CPU:    %.1f cores%n", metrics.peakCpuUsage());
        System.out.printf("Peak memory: %.1f GB%n", metrics.peakMemoryUsageGb());
    }
}
```

## What You Learned

- The **DefaultExecutor** manages the full lifecycle of executing an
  `ExecutionPlan`: initialization, deployment, trial execution, and teardown.
- `execute()` blocks until completion; `executeAsync()` returns an
  `ExecutionHandle` for non-blocking monitoring and control.
- The `ExecutionResult` provides aggregate counts (total, successful, failed)
  plus per-trial `TrialResult` objects.
- Each `TrialResult` captures status, timing, metrics, error information, and
  provenance.
- The execution model follows a pipeline: scheduling from the DAG, resource
  allocation, parallel trial execution, and result collection.

## Next Steps

- **[Execution Plans](../concepts/execution-plans.md)** -- learn about steps,
  barriers, the execution graph, and checkpointing in detail.
- **[API Packages Reference](../reference/api-packages.md)** -- complete
  reference for every public interface and class.
- Explore the `examples/ExecutionExample.java` source in the project root for
  a more complete program that includes simulated work, random latencies, and
  observation recording.
