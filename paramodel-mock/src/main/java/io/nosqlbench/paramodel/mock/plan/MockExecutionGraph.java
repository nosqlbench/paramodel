package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.*;

import java.util.*;

/**
 * Simple execution graph implementation using adjacency lists.
 */
public class MockExecutionGraph implements ExecutionGraph {
    private final Map<String, AtomicStep> steps;
    private final Map<String, Set<String>> dependencies;
    private final List<Barrier> barriers;

    public MockExecutionGraph() {
        this(Map.of(), Map.of(), List.of());
    }

    public MockExecutionGraph(Map<String, AtomicStep> steps,
                             Map<String, Set<String>> dependencies,
                             List<Barrier> barriers) {
        this.steps = new HashMap<>(steps);
        this.dependencies = new HashMap<>(dependencies);
        this.barriers = new ArrayList<>(barriers);
    }

    @Override
    public Set<AtomicStep> nodes() {
        return new HashSet<>(steps.values());
    }

    @Override
    public Set<AtomicStep> dependencies(AtomicStep step) {
        Set<String> depIds = dependencies.getOrDefault(step.id(), Set.of());
        Set<AtomicStep> result = new HashSet<>();
        for (String id : depIds) {
            AtomicStep dep = steps.get(id);
            if (dep != null) {
                result.add(dep);
            }
        }
        return result;
    }

    @Override
    public List<Barrier> barriers() {
        return Collections.unmodifiableList(barriers);
    }

    @Override
    public List<AtomicStep> topologicalOrder() {
        // Simple topological sort
        List<AtomicStep> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (AtomicStep step : steps.values()) {
            if (!visited.contains(step.id())) {
                topologicalSortUtil(step.id(), visited, result);
            }
        }

        return result;
    }

    private void topologicalSortUtil(String stepId, Set<String> visited, List<AtomicStep> result) {
        visited.add(stepId);

        Set<String> deps = dependencies.getOrDefault(stepId, Set.of());
        for (String depId : deps) {
            if (!visited.contains(depId)) {
                topologicalSortUtil(depId, visited, result);
            }
        }

        AtomicStep step = steps.get(stepId);
        if (step != null) {
            result.add(step);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, AtomicStep> steps = new HashMap<>();
        private final Map<String, Set<String>> dependencies = new HashMap<>();
        private final List<Barrier> barriers = new ArrayList<>();

        public Builder addStep(AtomicStep step) {
            steps.put(step.id(), step);
            return this;
        }

        public Builder addDependency(String stepId, String dependsOnId) {
            dependencies.computeIfAbsent(stepId, k -> new HashSet<>()).add(dependsOnId);
            return this;
        }

        public Builder addBarrier(Barrier barrier) {
            barriers.add(barrier);
            return this;
        }

        public MockExecutionGraph build() {
            return new MockExecutionGraph(steps, dependencies, barriers);
        }
    }
}
