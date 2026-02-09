package io.nosqlbench.paramodel.mock.execution;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

///
/// Simple executor implementation for testing.
///
/// Provides synchronous stub execution, in-memory checkpoints,
/// and default configuration.
///
/// @see Executor
/// @since 0.1.0
///
public class MockExecutor implements Executor {
    private final ExecutorConfig config;
    private final List<Checkpoint> savedCheckpoints = new ArrayList<>();

    ///
    /// Creates a mock executor with default configuration.
    ///
    public MockExecutor() {
        this.config = new MockExecutorConfig();
    }

    @Override
    public ExecutionResult execute(ExecutionPlan plan) throws ExecutionFailedException {
        Instant start = Instant.now();
        return new MockExecutionResult(
            UUID.randomUUID().toString(),
            plan,
            new MockExecutionStatus(ExecutionPhase.COMPLETED, 0, 0, 0, 0, 100.0,
                Optional.empty(), Map.of()),
            start,
            Instant.now(),
            Duration.between(start, Instant.now()),
            List.of(),
            0, 0, 0,
            true,
            Optional.empty(),
            new MockExecutionMetrics(),
            0.0,
            Map.of()
        );
    }

    @Override
    public ExecutionHandle executeAsync(ExecutionPlan plan) {
        try {
            ExecutionResult result = execute(plan);
            return new MockExecutionHandle(result);
        } catch (ExecutionFailedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ExecutionResult resume(ExecutionPlan plan, Checkpoint checkpoint)
            throws ExecutionFailedException {
        return execute(plan);
    }

    @Override
    public Optional<Checkpoint> latestCheckpoint(ExecutionPlan plan) {
        if (savedCheckpoints.isEmpty()) return Optional.empty();
        return Optional.of(savedCheckpoints.get(savedCheckpoints.size() - 1));
    }

    @Override
    public List<Checkpoint> checkpoints(ExecutionPlan plan) {
        return Collections.unmodifiableList(savedCheckpoints);
    }

    @Override
    public ExecutorConfig config() {
        return config;
    }

    private record MockExecutionResult(
        String executionId,
        ExecutionPlan plan,
        ExecutionStatus finalStatus,
        Instant startedAt,
        Instant completedAt,
        Duration duration,
        List<TrialResult> trialResults,
        int totalTrialCount,
        int successfulTrialCount,
        int failedTrialCount,
        boolean isSuccess,
        Optional<Throwable> error,
        ExecutionMetrics metrics,
        double actualCost,
        Map<String, Object> metadata
    ) implements ExecutionResult {}

    private record MockExecutionStatus(
        ExecutionPhase phase,
        int completedTrials,
        int totalTrials,
        int completedSteps,
        int totalSteps,
        double progressPercentage,
        Optional<Duration> estimatedTimeRemaining,
        Map<String, Object> currentMetrics
    ) implements ExecutionStatus {}

    private static class MockExecutionMetrics implements ExecutionMetrics {
        @Override public double peakCpuUsage() { return 0.0; }
        @Override public double averageCpuUsage() { return 0.0; }
        @Override public double peakMemoryUsageGb() { return 0.0; }
        @Override public double averageMemoryUsageGb() { return 0.0; }
        @Override public long totalNetworkBytesTransferred() { return 0L; }
        @Override public long totalStorageBytesWritten() { return 0L; }
        @Override public Map<String, Double> customMetrics() { return Map.of(); }
    }

    private static class MockExecutionHandle implements ExecutionHandle {
        private final ExecutionResult result;
        private final CompletableFuture<ExecutionResult> future;

        MockExecutionHandle(ExecutionResult result) {
            this.result = result;
            this.future = CompletableFuture.completedFuture(result);
        }

        @Override public String executionId() { return result.executionId(); }
        @Override public ExecutionStatus status() { return result.finalStatus(); }
        @Override public CompletableFuture<ExecutionResult> future() { return future; }
        @Override public void pause() {}
        @Override public void resume() {}
        @Override public void cancel() {}
        @Override public boolean isPaused() { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public ExecutionResult await() { return result; }
        @Override public ExecutionResult await(Duration timeout) { return result; }
        @Override public void onProgress(ProgressListener listener) {}
        @Override public void onTrialComplete(TrialCompleteListener listener) {}
        @Override public void onStepComplete(StepCompleteListener listener) {}
    }

    private static class MockExecutorConfig implements ExecutorConfig {
        @Override public int maxConcurrentTrials() { return 10; }
        @Override public double maxCpu() { return 16.0; }
        @Override public double maxMemoryGb() { return 64.0; }
        @Override public double maxStorageGb() { return 200.0; }
        @Override public Optional<Duration> checkpointInterval() { return Optional.of(Duration.ofMinutes(10)); }
        @Override public boolean checkpointOnBarriers() { return true; }
        @Override public Map<String, Object> customConfig() { return Map.of(); }
    }
}
