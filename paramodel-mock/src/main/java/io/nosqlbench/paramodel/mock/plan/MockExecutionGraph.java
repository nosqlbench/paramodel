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
    public List<AtomicStep> steps() {
        return new ArrayList<>(steps.values());
    }

    @Override
    public List<Edge> edges() {
        List<Edge> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            String fromId = entry.getKey();
            for (String toId : entry.getValue()) {
                result.add(new StubEdge(fromId, toId));
            }
        }
        return result;
    }

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
    public List<AtomicStep> criticalPath() {
        return new ArrayList<>(steps.values());
    }

    @Override
    public java.time.Duration criticalPathDuration() {
        return java.time.Duration.ZERO;
    }

    @Override
    public java.time.Duration totalDuration() {
        return java.time.Duration.ZERO;
    }

    @Override
    public List<AtomicStep> topologicalSort() {
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

    @Override
    public Map<Integer, List<AtomicStep>> parallelWaves() {
        return Map.of(0, new ArrayList<>(steps.values()));
    }

    @Override
    public int maximumParallelism() {
        return steps.size();
    }

    @Override
    public double averageParallelism() {
        return steps.isEmpty() ? 0.0 : 1.0;
    }

    @Override
    public boolean isAcyclic() {
        return true;
    }

    @Override
    public GraphStatistics statistics() {
        return new StubGraphStatistics();
    }

    private static class StubEdge implements Edge {
        private final String fromId;
        private final String toId;

        public StubEdge(String fromId, String toId) {
            this.fromId = fromId;
            this.toId = toId;
        }

        @Override
        public String from() {
            return fromId;
        }

        @Override
        public String to() {
            return toId;
        }

        @Override
        public EdgeType type() {
            return EdgeType.SEQUENTIAL;
        }

        @Override
        public Optional<String> label() {
            return Optional.empty();
        }
    }

    private static class StubGraphStatistics implements GraphStatistics {
        @Override
        public int totalSteps() {
            return 0;
        }

        @Override
        public int totalEdges() {
            return 0;
        }

        @Override
        public java.time.Duration criticalPathDuration() {
            return java.time.Duration.ZERO;
        }

        @Override
        public int maximumParallelism() {
            return 1;
        }

        @Override
        public double averageParallelism() {
            return 1.0;
        }

        @Override
        public Map<String, Object> additionalMetrics() {
            return Map.of();
        }
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
