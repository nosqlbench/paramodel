package examples;

import io.nosqlbench.paramodel.engine.compiler.*;
import io.nosqlbench.paramodel.engine.execution.*;
import io.nosqlbench.paramodel.mock.core.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.mock.sequence.*;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.sequence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Example demonstrating concurrent execution with the engine.
 *
 * Shows:
 * - Executor configuration
 * - Resource management
 * - Parallel trial execution
 * - Result collection
 */
public class ExecutionExample {

    public static void main(String[] args) {
        System.out.println("=== Execution Example ===\n");

        // 1. Create and compile test plan
        TestPlan testPlan = createTestPlan();
        Compiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();
        ExecutionPlan executionPlan = compiler.compile(testPlan);

        System.out.println("1. ExecutionPlan ready:");
        System.out.println("   Estimated trials: " + executionPlan.estimatedTrialCount());
        System.out.println();

        // 2. Configure executor
        int maxConcurrency = Runtime.getRuntime().availableProcessors();
        Executor executor = DefaultExecutor.builder()
            .maxConcurrency(maxConcurrency)
            .build();

        System.out.println("2. Executor configured:");
        System.out.println("   Max concurrency: " + maxConcurrency);
        System.out.println();

        // 3. Execute trials
        System.out.println("3. Executing trials...");
        Instant startTime = Instant.now();

        List<Trial.TrialResult> results = executor.execute(executionPlan, trial -> {
            return executeTrial(trial);
        });

        Duration duration = Duration.between(startTime, Instant.now());

        System.out.println("   ✓ Execution complete!");
        System.out.println();

        // 4. Analyze results
        System.out.println("4. Results:");
        System.out.println("   Total trials: " + results.size());
        System.out.println("   Duration: " + duration.toMillis() + "ms");
        System.out.println("   Throughput: " + (results.size() * 1000.0 / duration.toMillis()) + " trials/sec");
        System.out.println();

        long successful = results.stream()
            .filter(r -> r.status() == Trial.TrialResult.Status.SUCCESS)
            .count();
        long failed = results.stream()
            .filter(r -> r.status() == Trial.TrialResult.Status.FAILED)
            .count();

        System.out.println("   Success: " + successful);
        System.out.println("   Failed: " + failed);
        System.out.println("   Success rate: " + (100.0 * successful / results.size()) + "%");
        System.out.println();

        // 5. Show sample results
        System.out.println("5. Sample results:");
        results.stream()
            .limit(3)
            .forEach(result -> {
                System.out.println("   Trial " + result.trialId() + ":");
                System.out.println("     Status: " + result.status());
                System.out.println("     Duration: " + result.executionTime().toMillis() + "ms");
                System.out.println("     Observations: " + result.observations());
            });

        // 6. Cleanup
        executor.shutdown();
        System.out.println("\n6. Executor shut down");
    }

    private static TestPlan createTestPlan() {
        MockDomain<String> opDomain = MockDomain.of("read", "write");
        MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4);

        MockParameter<String> operation = MockParameter.of("operation", opDomain);
        MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);

        return MockTestPlan.builder()
            .parameter(operation)
            .parameter(threads)
            .axis(MockAxis.of("ops", MockElement.exhaustive("operation")))
            .axis(MockAxis.of("conc", MockElement.exhaustive("threads")))
            .build();
    }

    private static Trial.TrialResult executeTrial(Trial trial) {
        Instant start = Instant.now();

        // Simulate work
        try {
            Thread.sleep(10 + new Random().nextInt(40));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return MockTrialResult.failed(trial.id(), e);
        }

        Instant end = Instant.now();

        // Collect observations
        Map<String, Object> observations = new HashMap<>();
        observations.put("latency_ms", Duration.between(start, end).toMillis());
        observations.put("throughput", 1000.0 + new Random().nextDouble() * 100);
        observations.put("cpu_usage", new Random().nextDouble());

        return MockTrialResult.builder(trial.id())
            .startTime(start)
            .endTime(end)
            .status(Trial.TrialResult.Status.SUCCESS)
            .observation("latency_ms", observations.get("latency_ms"))
            .observation("throughput", observations.get("throughput"))
            .observation("cpu_usage", observations.get("cpu_usage"))
            .build();
    }
}
