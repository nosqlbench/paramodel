package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

///
/// Structural {@link ExecutionPlan} implementation assembled during code generation.
///
/// This is a data-holder execution plan. The runtime methods ({@code execute},
/// {@code resumeFrom}, {@code executeWithCheckpoints}) throw
/// {@link UnsupportedOperationException} — they must be replaced by a runtime
/// execution engine implementation.
///
public class DefaultExecutionPlan implements ExecutionPlan {

    private final String id;
    private final String testPlanFingerprint;
    private final List<AtomicStep> steps;
    private final List<Barrier> barriers;
    private final ExecutionGraph executionGraph;
    private final TrialOrdering trialOrdering;

    public DefaultExecutionPlan(
        String id,
        String testPlanFingerprint,
        List<AtomicStep> steps,
        List<Barrier> barriers,
        ExecutionGraph executionGraph,
        TrialOrdering trialOrdering
    ) {
        this.id = Objects.requireNonNull(id);
        this.testPlanFingerprint = Objects.requireNonNull(testPlanFingerprint);
        this.steps = List.copyOf(steps);
        this.barriers = List.copyOf(barriers);
        this.executionGraph = Objects.requireNonNull(executionGraph);
        this.trialOrdering = trialOrdering != null ? trialOrdering : TrialOrdering.SEQUENTIAL;
    }

    @Override public String id() { return id; }
    @Override public String testPlanFingerprint() { return testPlanFingerprint; }
    @Override public List<AtomicStep> steps() { return steps; }
    @Override public List<Barrier> barriers() { return barriers; }
    @Override public ExecutionGraph executionGraph() { return executionGraph; }
    @Override public TrialOrdering trialOrdering() { return trialOrdering; }

    @Override
    public Optional<Duration> estimatedDuration() {
        Duration critical = executionGraph.criticalPathDuration();
        return critical.isZero() && !steps.isEmpty() ? Optional.empty() : Optional.of(critical);
    }

    @Override
    public int estimatedMaxParallelism() {
        return executionGraph.maximumParallelism();
    }

    @Override
    public ResourceRequirements resourceRequirements() {
        double peakCpu = steps.stream()
            .mapToDouble(s -> s.resourceRequirements().cpu())
            .max().orElse(0);
        long peakMem = steps.stream()
            .mapToLong(s -> s.resourceRequirements().memoryMb())
            .max().orElse(0);
        return new ResourceRequirements(peakCpu, peakMem, 0, 0, Map.of());
    }

    @Override
    public Optional<CheckpointStrategy> checkpointStrategy() {
        return Optional.empty();
    }

    @Override
    public Optional<Checkpoint> latestCheckpoint() {
        return Optional.empty();
    }

    @Override
    public List<Checkpoint> checkpoints() {
        return List.of();
    }

    @Override
    public ExecutionResults execute() throws ExecutionException {
        throw new UnsupportedOperationException(
            "DefaultExecutionPlan is a structural placeholder; use a runtime executor");
    }

    @Override
    public ExecutionResults execute(ExecutionObserver observer) throws ExecutionException {
        throw new UnsupportedOperationException(
            "DefaultExecutionPlan is a structural placeholder; use a runtime executor");
    }

    @Override
    public ExecutionResults executeWithCheckpoints(Duration checkpointInterval) throws ExecutionException {
        throw new UnsupportedOperationException(
            "DefaultExecutionPlan is a structural placeholder; use a runtime executor");
    }

    @Override
    public ExecutionPlan resumeFrom(Checkpoint checkpoint) {
        throw new UnsupportedOperationException(
            "DefaultExecutionPlan is a structural placeholder; use a runtime executor");
    }

    @Override
    public ExecutionPlan withMaxConcurrency(int maxConcurrency) {
        if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
        // Return self — structural plan does not enforce concurrency limits
        return this;
    }

    @Override
    public ExecutionPlanMetadata metadata() {
        return new DefaultExecutionPlanMetadata(this);
    }

    private static class DefaultExecutionPlanMetadata implements ExecutionPlanMetadata {
        private final DefaultExecutionPlan plan;
        private final Instant compiledAt = Instant.now();

        DefaultExecutionPlanMetadata(DefaultExecutionPlan plan) {
            this.plan = plan;
        }

        @Override public String id() { return plan.id; }
        @Override public String testPlanFingerprint() { return plan.testPlanFingerprint; }
        @Override public Instant compiledAt() { return compiledAt; }
        @Override public Duration compilationDuration() { return Duration.ZERO; }
        @Override public String compilerVersion() { return "0.1.0"; }
        @Override public OptimizationLevel optimizationLevel() { return OptimizationLevel.STANDARD; }

        @Override public int trialCount() {
            return (int) plan.steps.stream()
                .filter(s -> s instanceof AtomicStep.ExecuteTrial)
                .count();
        }

        @Override public int stepCount() { return plan.steps.size(); }
        @Override public int barrierCount() { return plan.barriers.size(); }
        @Override public int elementInstanceCount() { return 0; }

        @Override public ResourceProfile resourceProfile() {
            return new ResourceProfile(0, 0, 0, 0, 0, 0);
        }

        @Override public Duration estimatedDuration() {
            return plan.executionGraph.criticalPathDuration();
        }

        @Override public Optional<Double> estimatedCost() { return Optional.empty(); }

        @Override public PerformanceMetrics performanceMetrics() {
            ExecutionGraph.GraphStatistics stats = plan.executionGraph.statistics();
            double speedup = stats.totalDuration().isZero() ? 1.0
                : (double) stats.totalDuration().toMillis() / Math.max(1, stats.criticalPathDuration().toMillis());
            return new PerformanceMetrics(
                stats.maximumParallelism(),
                stats.averageParallelism(),
                stats.criticalPathDuration(),
                stats.totalDuration(),
                speedup,
                stats.maximumParallelism() == 0 ? 0 : stats.averageParallelism() / stats.maximumParallelism(),
                new GraphComplexity(stats.nodeCount(), stats.edgeCount(), stats.averageDegree(), stats.maxDepth(), stats.maxDepth())
            );
        }

        @Override public Optional<OptimizationReport> optimizationReport() { return Optional.empty(); }
        @Override public List<ExecutionRecord> executionHistory() { return List.of(); }
        @Override public Optional<ExecutionRecord> latestExecution() { return Optional.empty(); }
        @Override public int executionCount() { return 0; }
        @Override public int successfulExecutionCount() { return 0; }
        @Override public Map<String, Object> customMetadata() { return Map.of(); }
    }
}
