package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.plan.AtomicStep;

import java.util.List;
import java.util.Map;

///
/// Stage 6: Dependency Analysis
///
/// Builds the execution dependency graph from compiled steps and barriers:
///
/// 1. Reads steps and barriers from context
/// 2. Constructs a {@link DefaultExecutionGraph}
/// 3. Validates the graph is acyclic (Kahn's algorithm)
/// 4. Computes parallel waves for concurrency analysis
/// 5. Stores the graph in context as {@code "executionGraph"}
///
public class DependencyAnalysisStage implements CompilationStage {
    public DependencyAnalysisStage() {}

    @Override
    public String name() {
        return "DependencyAnalysis";
    }

    @Override
    public void execute(CompilationContext context) {
        List<AtomicStep> steps = context.steps().orElse(List.of());
        if (steps.isEmpty()) {
            context.recordMetric("dependencies_analyzed", 0);
            return;
        }

        DefaultExecutionGraph graph = new DefaultExecutionGraph(steps);

        // Validate acyclicity
        if (!graph.isAcyclic()) {
            context.addError(
                Compiler.ErrorSeverity.ERROR,
                "Execution graph contains cycles",
                "DependencyAnalysis",
                "Check element dependencies for circular references"
            );
            return;
        }

        // Compute parallel waves for metrics
        Map<Integer, List<AtomicStep>> waves = graph.parallelWaves();
        int maxParallelism = graph.maximumParallelism();

        context.put("executionGraph", graph);
        context.recordMetric("dependencies_analyzed", graph.edges().size());
        context.recordMetric("parallel_waves", waves.size());
        context.recordMetric("max_parallelism", maxParallelism);
    }
}
