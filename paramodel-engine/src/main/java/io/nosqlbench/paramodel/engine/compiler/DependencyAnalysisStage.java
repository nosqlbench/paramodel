package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;

import java.util.*;

/**
 * Stage 6: Dependency Analysis
 *
 * Computes execution graph dependencies:
 * - Analyze data dependencies between steps
 * - Detect resource conflicts
 * - Build directed acyclic graph (DAG)
 * - Insert barriers for synchronization
 * - Validate no cycles exist
 */
public class DependencyAnalysisStage implements CompilationStage {

    @Override
    public String name() {
        return "DependencyAnalysis";
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompilationContext execute(CompilationContext context) {
        // Get atomic steps from previous stage
        Optional<List<StepGenerationStage.AtomicStepSpec>> stepsOpt =
            context.getArtifact("atomic_steps", List.class);

        if (stepsOpt.isEmpty()) {
            return context.withError("Atomic steps not found");
        }

        List<StepGenerationStage.AtomicStepSpec> steps =
            (List<StepGenerationStage.AtomicStepSpec>) (List<?>) stepsOpt.get();

        // Build dependency graph
        DependencyGraph graph = buildDependencyGraph(steps);

        // Validate no cycles
        if (hasCycle(graph)) {
            return context.withError("Dependency graph contains cycles");
        }

        return context.withArtifact("dependency_graph", graph);
    }

    private DependencyGraph buildDependencyGraph(List<StepGenerationStage.AtomicStepSpec> steps) {
        Map<String, Set<String>> dependencies = new HashMap<>();

        // For now, no dependencies - all steps independent
        for (StepGenerationStage.AtomicStepSpec step : steps) {
            dependencies.put(step.id(), new HashSet<>());
        }

        return new DependencyGraph(dependencies);
    }

    private boolean hasCycle(DependencyGraph graph) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : graph.dependencies().keySet()) {
            if (hasCycleUtil(node, graph, visited, recursionStack)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCycleUtil(String node, DependencyGraph graph,
                                 Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }

        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        recursionStack.add(node);

        Set<String> neighbors = graph.dependencies().get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (hasCycleUtil(neighbor, graph, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(node);
        return false;
    }

    /**
     * Dependency graph representation.
     */
    public static class DependencyGraph {
        private final Map<String, Set<String>> dependencies;

        public DependencyGraph(Map<String, Set<String>> dependencies) {
            this.dependencies = new HashMap<>();
            dependencies.forEach((k, v) -> this.dependencies.put(k, new HashSet<>(v)));
        }

        public Map<String, Set<String>> dependencies() {
            return dependencies;
        }
    }
}
