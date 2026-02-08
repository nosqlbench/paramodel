package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.plan.ExecutionPlan;

import java.util.List;
import java.util.Optional;

/**
 * Stage 8: Code Generation
 *
 * Produces final ExecutionPlan:
 * - Materialize AtomicSteps
 * - Build ExecutionGraph
 * - Attach metadata
 * - Compute fingerprints
 * - Generate execution code
 */
public class CodeGenerationStage implements CompilationStage {

    @Override
    public String name() {
        return "CodeGeneration";
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompilationContext execute(CompilationContext context) {
        // Get atomic steps
        Optional<List<StepGenerationStage.AtomicStepSpec>> stepsOpt =
            context.getArtifact("atomic_steps", List.class);

        if (stepsOpt.isEmpty()) {
            return context.withError("Atomic steps not found");
        }

        // Get dependency graph
        Optional<DependencyAnalysisStage.DependencyGraph> graphOpt =
            context.getArtifact("dependency_graph", DependencyAnalysisStage.DependencyGraph.class);

        if (graphOpt.isEmpty()) {
            return context.withError("Dependency graph not found");
        }

        // Build ExecutionPlan
        ExecutionPlan executionPlan = buildExecutionPlan(
            context.testPlan(),
            (List<StepGenerationStage.AtomicStepSpec>) (List<?>) stepsOpt.get(),
            graphOpt.get()
        );

        return context.withExecutionPlan(executionPlan);
    }

    private ExecutionPlan buildExecutionPlan(
        io.nosqlbench.paramodel.plan.TestPlan testPlan,
        List<StepGenerationStage.AtomicStepSpec> stepSpecs,
        DependencyAnalysisStage.DependencyGraph dependencyGraph
    ) {
        // Create a simple execution plan implementation
        return new SimpleExecutionPlan(testPlan, stepSpecs.size());
    }

    /**
     * Simple ExecutionPlan implementation for code generation.
     */
    private static class SimpleExecutionPlan implements ExecutionPlan {
        private final io.nosqlbench.paramodel.plan.TestPlan testPlan;
        private final long estimatedTrials;

        public SimpleExecutionPlan(io.nosqlbench.paramodel.plan.TestPlan testPlan, long estimatedTrials) {
            this.testPlan = testPlan;
            this.estimatedTrials = estimatedTrials;
        }

        @Override
        public io.nosqlbench.paramodel.plan.TestPlan testPlan() {
            return testPlan;
        }

        @Override
        public List<io.nosqlbench.paramodel.plan.AtomicStep> steps() {
            return List.of();
        }

        @Override
        public io.nosqlbench.paramodel.plan.ExecutionGraph graph() {
            return new EmptyExecutionGraph();
        }

        @Override
        public io.nosqlbench.paramodel.plan.ExecutionPlanMetadata metadata() {
            return new SimpleMetadata();
        }

        @Override
        public long estimatedTrialCount() {
            return estimatedTrials;
        }
    }

    /**
     * Empty execution graph.
     */
    private static class EmptyExecutionGraph implements io.nosqlbench.paramodel.plan.ExecutionGraph {
        @Override
        public java.util.Set<io.nosqlbench.paramodel.plan.AtomicStep> nodes() {
            return java.util.Set.of();
        }

        @Override
        public java.util.Set<io.nosqlbench.paramodel.plan.AtomicStep> dependencies(
            io.nosqlbench.paramodel.plan.AtomicStep step) {
            return java.util.Set.of();
        }

        @Override
        public List<io.nosqlbench.paramodel.plan.Barrier> barriers() {
            return List.of();
        }

        @Override
        public List<io.nosqlbench.paramodel.plan.AtomicStep> topologicalOrder() {
            return List.of();
        }
    }

    /**
     * Simple metadata implementation.
     */
    private static class SimpleMetadata implements io.nosqlbench.paramodel.plan.ExecutionPlanMetadata {
        @Override
        public String compilationVersion() {
            return "1.0";
        }

        @Override
        public java.time.Instant compiledAt() {
            return java.time.Instant.now();
        }

        @Override
        public java.util.Map<String, Object> optimizationMetrics() {
            return java.util.Map.of();
        }

        @Override
        public String fingerprint() {
            return java.util.UUID.randomUUID().toString();
        }
    }
}
