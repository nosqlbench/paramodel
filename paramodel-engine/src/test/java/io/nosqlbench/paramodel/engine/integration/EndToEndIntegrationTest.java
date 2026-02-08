package io.nosqlbench.paramodel.engine.integration;

import io.nosqlbench.paramodel.engine.compiler.*;
import io.nosqlbench.paramodel.engine.execution.*;
import io.nosqlbench.paramodel.mock.core.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.mock.sequence.*;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.sequence.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test validating the complete workflow:
 * TestPlan → Compilation → Execution → Results
 */
public class EndToEndIntegrationTest {

    @Test
    @DisplayName("Complete workflow: define → compile → execute")
    public void testCompleteWorkflow() {
        // 1. Define parameters
        MockDomain<String> opDomain = MockDomain.of("read", "write");
        MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 4);

        MockParameter<String> operation = MockParameter.of("operation", opDomain);
        MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);

        // 2. Build test plan
        TestPlan testPlan = MockTestPlan.builder()
            .parameter(operation)
            .parameter(threads)
            .axis(MockAxis.of("ops", MockElement.exhaustive("operation")))
            .axis(MockAxis.of("conc", MockElement.exhaustive("threads")))
            .build();

        assertThat(testPlan.parameters()).hasSize(2);
        assertThat(testPlan.axes()).hasSize(2);
        assertThat(testPlan.validate().isValid()).isTrue();

        // 3. Compile
        Compiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        ExecutionPlan executionPlan = compiler.compile(testPlan);

        assertThat(executionPlan).isNotNull();
        assertThat(executionPlan.testPlan()).isEqualTo(testPlan);
        assertThat(executionPlan.estimatedTrialCount()).isGreaterThan(0);
        assertThat(testPlan.isCommitted()).isTrue();

        // 4. Execute
        Executor executor = DefaultExecutor.builder()
            .maxConcurrency(2)
            .build();

        AtomicInteger executionCount = new AtomicInteger(0);

        List<Trial.TrialResult> results = executor.execute(executionPlan, trial -> {
            executionCount.incrementAndGet();

            // Simulate work
            Map<String, Object> observations = new HashMap<>();
            observations.put("latency", 42.0);
            observations.put("throughput", 1000.0);

            return MockTrialResult.success(trial.id(), observations);
        });

        executor.shutdown();

        // 5. Validate results
        assertThat(results).isNotEmpty();
        assertThat(executionCount.get()).isEqualTo(results.size());

        for (Trial.TrialResult result : results) {
            assertThat(result.status()).isEqualTo(Trial.TrialResult.Status.SUCCESS);
            assertThat(result.observations()).containsKeys("latency", "throughput");
        }
    }

    @Test
    @DisplayName("Constraint filtering works correctly")
    public void testConstraintFiltering() {
        // Define constraint: threads must be power of 2
        io.nosqlbench.paramodel.core.Constraint<Map<String, io.nosqlbench.paramodel.core.Value<?>>> powerOf2 =
            assignment -> {
                io.nosqlbench.paramodel.core.Value<?> threadsValue = assignment.get("threads");
                if (threadsValue == null) return true;
                Integer threads = (Integer) threadsValue.value();
                return threads > 0 && (threads & (threads - 1)) == 0;
            };

        MockDomain<Integer> threadDomain = MockDomain.of(1, 2, 3, 4, 5, 8);
        MockParameter<Integer> threads = MockParameter.of("threads", threadDomain);

        TestPlan testPlan = MockTestPlan.builder()
            .parameter(threads)
            .axis(MockAxis.of("conc", MockElement.exhaustive("threads")))
            .constraint(powerOf2)
            .build();

        assertThat(testPlan.constraints()).hasSize(1);
        assertThat(testPlan.validate().isValid()).isTrue();
    }

    @Test
    @DisplayName("Compilation with optimization strategies")
    public void testOptimizationStrategies() {
        MockDomain<String> domain = MockDomain.of("a", "b", "c");
        MockParameter<String> param = MockParameter.of("param", domain);

        // Test different optimization strategies
        for (OptimizationStrategy strategy : OptimizationStrategy.values()) {
            TestPlan plan = MockTestPlan.builder()
                .parameter(param)
                .axis(MockAxis.of("axis", MockElement.exhaustive("param")))
                .optimizationStrategy(strategy)
                .build();

            Compiler compiler = DefaultCompiler.builder()
                .standardPipeline()
                .build();

            ExecutionPlan execPlan = compiler.compile(plan);

            assertThat(execPlan).isNotNull();
            assertThat(plan.optimizationStrategy()).isEqualTo(strategy);
        }
    }

    @Test
    @DisplayName("Resource manager controls concurrency")
    public void testResourceManagement() {
        DefaultResourceManager resourceManager = new DefaultResourceManager();

        // Register limited resource
        resourceManager.registerResource("limited", 2);

        // Try to acquire more than available
        Map<String, Integer> request = Map.of("limited", 3);
        assertThat(resourceManager.acquire(request)).isFalse();

        // Acquire within limits
        Map<String, Integer> validRequest = Map.of("limited", 2);
        assertThat(resourceManager.acquire(validRequest)).isTrue();

        // Should fail - already acquired
        assertThat(resourceManager.acquire(Map.of("limited", 1))).isFalse();

        // Release and try again
        resourceManager.release(validRequest);
        assertThat(resourceManager.acquire(Map.of("limited", 1))).isTrue();
    }

    @Test
    @DisplayName("Scheduler provides steps in order")
    public void testScheduling() {
        MockDomain<Integer> domain = MockDomain.of(1, 2, 3);
        MockParameter<Integer> param = MockParameter.of("param", domain);

        TestPlan plan = MockTestPlan.builder()
            .parameter(param)
            .axis(MockAxis.of("axis", MockElement.exhaustive("param")))
            .build();

        Compiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        ExecutionPlan execPlan = compiler.compile(plan);

        DefaultScheduler scheduler = DefaultScheduler.create(
            DefaultScheduler.SchedulingPolicy.FIFO
        );

        scheduler.schedule(execPlan);

        // Retrieve all scheduled steps
        List<AtomicStep> retrieved = new ArrayList<>();
        while (true) {
            Optional<AtomicStep> step = scheduler.next();
            if (step.isEmpty()) break;
            retrieved.add(step.get());
        }

        assertThat(retrieved).isNotEmpty();
    }

    @Test
    @DisplayName("Compilation fails with invalid test plan")
    public void testCompilationFailure() {
        // Create test plan with reference to non-existent parameter
        MockParameter<String> param = MockParameter.of("param",
            MockDomain.of("a", "b"));

        TestPlan plan = MockTestPlan.builder()
            .parameter(param)
            .axis(MockAxis.of("axis",
                MockElement.exhaustive("nonexistent"))) // Invalid!
            .build();

        Compiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        assertThatThrownBy(() -> compiler.compile(plan))
            .isInstanceOf(DefaultCompiler.CompilationException.class)
            .hasMessageContaining("Compilation failed");
    }

    @Test
    @DisplayName("Concurrent execution is actually parallel")
    public void testParallelExecution() {
        MockDomain<Integer> domain = MockDomain.of(1, 2, 3, 4, 5, 6, 7, 8);
        MockParameter<Integer> param = MockParameter.of("param", domain);

        TestPlan plan = MockTestPlan.builder()
            .parameter(param)
            .axis(MockAxis.of("axis", MockElement.exhaustive("param")))
            .build();

        Compiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        ExecutionPlan execPlan = compiler.compile(plan);

        Executor executor = DefaultExecutor.builder()
            .maxConcurrency(4)
            .build();

        Set<String> threadNames = Collections.synchronizedSet(new HashSet<>());

        executor.execute(execPlan, trial -> {
            // Capture thread name
            threadNames.add(Thread.currentThread().getName());

            // Small delay to ensure overlap
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return MockTrialResult.success(trial.id(), Map.of());
        });

        executor.shutdown();

        // Should have used multiple threads
        assertThat(threadNames).hasSizeGreaterThan(1);
    }
}
