package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.*;

import java.util.*;

/**
 * Simple execution plan implementation.
 */
public class MockExecutionPlan implements ExecutionPlan {
    private final TestPlan testPlan;
    private final List<AtomicStep> steps;
    private final ExecutionGraph graph;
    private final ExecutionPlanMetadata metadata;

    public MockExecutionPlan(TestPlan testPlan) {
        this(testPlan, List.of(), new MockExecutionGraph(), MockExecutionPlanMetadata.empty());
    }

    public MockExecutionPlan(TestPlan testPlan, List<AtomicStep> steps,
                           ExecutionGraph graph, ExecutionPlanMetadata metadata) {
        this.testPlan = Objects.requireNonNull(testPlan);
        this.steps = new ArrayList<>(steps);
        this.graph = Objects.requireNonNull(graph);
        this.metadata = Objects.requireNonNull(metadata);
    }

    @Override
    public TestPlan testPlan() {
        return testPlan;
    }

    @Override
    public List<AtomicStep> steps() {
        return Collections.unmodifiableList(steps);
    }

    @Override
    public ExecutionGraph graph() {
        return graph;
    }

    @Override
    public ExecutionPlanMetadata metadata() {
        return metadata;
    }

    @Override
    public long estimatedTrialCount() {
        return steps.size();
    }

    public static Builder builder(TestPlan testPlan) {
        return new Builder(testPlan);
    }

    public static class Builder {
        private final TestPlan testPlan;
        private final List<AtomicStep> steps = new ArrayList<>();
        private ExecutionGraph graph = new MockExecutionGraph();
        private ExecutionPlanMetadata metadata = MockExecutionPlanMetadata.empty();

        public Builder(TestPlan testPlan) {
            this.testPlan = testPlan;
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
            return new MockExecutionPlan(testPlan, steps, graph, metadata);
        }
    }
}
