package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.plan.ElementInstanceGraph.InstanceNode;
import io.nosqlbench.paramodel.plan.ElementInstanceGraph.InstanceEdge;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Simple execution plan implementation.
 */
public class MockExecutionPlan implements ExecutionPlan {
    private final String id;
    private final String testPlanFingerprint;
    private final List<AtomicStep> steps;
    private final ExecutionGraph graph;
    private final ExecutionPlanMetadata metadata;

    public MockExecutionPlan(String id, String testPlanFingerprint) {
        this(id, testPlanFingerprint, List.of(), new MockExecutionGraph(), MockExecutionPlanMetadata.empty());
    }

    public MockExecutionPlan(String id, String testPlanFingerprint, List<AtomicStep> steps,
                           ExecutionGraph graph, ExecutionPlanMetadata metadata) {
        this.id = Objects.requireNonNull(id);
        this.testPlanFingerprint = Objects.requireNonNull(testPlanFingerprint);
        this.steps = new ArrayList<>(steps);
        this.graph = Objects.requireNonNull(graph);
        this.metadata = Objects.requireNonNull(metadata);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String testPlanFingerprint() {
        return testPlanFingerprint;
    }

    @Override
    public List<AtomicStep> steps() {
        return Collections.unmodifiableList(steps);
    }

    @Override
    public List<Barrier> barriers() {
        return List.of();
    }

    @Override
    public ExecutionGraph executionGraph() {
        return graph;
    }

    @Override
    public List<String> trialElements() {
        return List.of();
    }

    @Override
    public TrialOrdering trialOrdering() {
        return TrialOrdering.SEQUENTIAL;
    }

    @Override
    public Optional<Duration> estimatedDuration() {
        return Optional.empty();
    }

    @Override
    public int estimatedMaxParallelism() {
        return 1;
    }

    @Override
    public ResourceRequirements resourceRequirements() {
        return new ResourceRequirements(1.0, 512, 1, 0.1, Map.of());
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
        return new StubExecutionResults();
    }

    @Override
    public ExecutionResults execute(ExecutionObserver observer) throws ExecutionException {
        return new StubExecutionResults();
    }

    @Override
    public ExecutionResults executeWithCheckpoints(Duration checkpointInterval) throws ExecutionException {
        return new StubExecutionResults();
    }

    @Override
    public ExecutionPlan resumeFrom(Checkpoint checkpoint) {
        return this;
    }

    @Override
    public ExecutionPlan withMaxConcurrency(int maxConcurrency) {
        return this;
    }

    @Override
    public ElementInstanceGraph elementInstanceGraph() {
        return new ElementInstanceGraph() {
            @Override public Set<InstanceNode> nodes() { return Set.of(); }
            @Override public List<InstanceEdge> edges() { return List.of(); }
            @Override public List<InstanceEdge> edgesFrom(String elementId) { return List.of(); }
            @Override public List<InstanceEdge> edgesTo(String elementId) { return List.of(); }
            @Override public List<String> topologicalOrder() { return List.of(); }
            @Override public int instanceCount(String elementId) { return 0; }
            @Override public int totalInstances() { return 0; }
        };
    }

    @Override
    public ExecutionPlanMetadata metadata() {
        return metadata;
    }

    private static class StubExecutionResults implements ExecutionResults {
        @Override
        public String executionPlanId() {
            return "stub-execution";
        }

        @Override
        public Duration totalDuration() {
            return Duration.ZERO;
        }

        @Override
        public List<TrialResult> trialResults() {
            return List.of();
        }

        @Override
        public Map<String, Object> aggregateMetrics() {
            return Map.of();
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Optional<Throwable> error() {
            return Optional.empty();
        }
    }

    public static Builder builder(String id, String testPlanFingerprint) {
        return new Builder(id, testPlanFingerprint);
    }

    public static class Builder {
        private final String id;
        private final String testPlanFingerprint;
        private final List<AtomicStep> steps = new ArrayList<>();
        private ExecutionGraph graph = new MockExecutionGraph();
        private ExecutionPlanMetadata metadata = MockExecutionPlanMetadata.empty();

        public Builder(String id, String testPlanFingerprint) {
            this.id = id;
            this.testPlanFingerprint = testPlanFingerprint;
        }

        public Builder step(AtomicStep step) {
            this.steps.add(step);
            return this;
        }

        public Builder graph(ExecutionGraph graph) {
            this.graph = graph;
            return this;
        }

        public Builder metadata(ExecutionPlanMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public MockExecutionPlan build() {
            return new MockExecutionPlan(id, testPlanFingerprint, steps, graph, metadata);
        }
    }
}
