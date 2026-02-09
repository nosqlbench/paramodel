package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.*;

import java.time.Duration;
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
            String targetId = entry.getKey();
            AtomicStep target = steps.get(targetId);
            if (target == null) continue;
            for (String sourceId : entry.getValue()) {
                AtomicStep source = steps.get(sourceId);
                if (source != null) {
                    result.add(new Edge(source, target, Duration.ZERO));
                }
            }
        }
        return result;
    }

    @Override
    public Optional<AtomicStep> findStep(String stepId) {
        return Optional.ofNullable(steps.get(stepId));
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
    public Set<AtomicStep> transitiveDependencies(AtomicStep step) {
        Set<AtomicStep> result = new HashSet<>();
        Set<String> visited = new HashSet<>();
        collectTransitiveDeps(step.id(), visited, result);
        return result;
    }

    private void collectTransitiveDeps(String stepId, Set<String> visited, Set<AtomicStep> result) {
        Set<String> depIds = dependencies.getOrDefault(stepId, Set.of());
        for (String depId : depIds) {
            if (visited.add(depId)) {
                AtomicStep dep = steps.get(depId);
                if (dep != null) {
                    result.add(dep);
                    collectTransitiveDeps(depId, visited, result);
                }
            }
        }
    }

    @Override
    public Set<AtomicStep> dependents(AtomicStep step) {
        Set<AtomicStep> result = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            if (entry.getValue().contains(step.id())) {
                AtomicStep dependent = steps.get(entry.getKey());
                if (dependent != null) {
                    result.add(dependent);
                }
            }
        }
        return result;
    }

    @Override
    public Set<AtomicStep> transitiveDependents(AtomicStep step) {
        Set<AtomicStep> result = new HashSet<>();
        Set<String> visited = new HashSet<>();
        collectTransitiveDependents(step.id(), visited, result);
        return result;
    }

    private void collectTransitiveDependents(String stepId, Set<String> visited, Set<AtomicStep> result) {
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            if (entry.getValue().contains(stepId) && visited.add(entry.getKey())) {
                AtomicStep dependent = steps.get(entry.getKey());
                if (dependent != null) {
                    result.add(dependent);
                    collectTransitiveDependents(entry.getKey(), visited, result);
                }
            }
        }
    }

    @Override
    public List<AtomicStep> criticalPath() {
        return new ArrayList<>(steps.values());
    }

    @Override
    public Duration criticalPathDuration() {
        return Duration.ZERO;
    }

    @Override
    public Duration totalDuration() {
        return Duration.ZERO;
    }

    @Override
    public List<AtomicStep> topologicalSort() {
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
    public boolean canExecuteConcurrently(AtomicStep step1, AtomicStep step2) {
        return !transitiveDependencies(step1).contains(step2)
            && !transitiveDependencies(step2).contains(step1);
    }

    @Override
    public Schedule computeSchedule(ResourceLimits limits) {
        throw new UnsupportedOperationException("MockExecutionGraph does not support schedule computation");
    }

    @Override
    public ExecutionGraph subgraph(Set<String> stepIds) {
        Map<String, AtomicStep> subSteps = new HashMap<>();
        Map<String, Set<String>> subDeps = new HashMap<>();
        for (String id : stepIds) {
            AtomicStep step = steps.get(id);
            if (step != null) {
                subSteps.put(id, step);
                Set<String> deps = dependencies.getOrDefault(id, Set.of());
                Set<String> filteredDeps = new HashSet<>(deps);
                filteredDeps.retainAll(stepIds);
                if (!filteredDeps.isEmpty()) {
                    subDeps.put(id, filteredDeps);
                }
            }
        }
        return new MockExecutionGraph(subSteps, subDeps, List.of());
    }

    @Override
    public ExecutionGraph subgraphForElement(String elementId) {
        return new MockExecutionGraph();
    }

    @Override
    public ExecutionGraph subgraphForTrials(List<String> trialIds) {
        return new MockExecutionGraph();
    }

    @Override
    public boolean isAcyclic() {
        return true;
    }

    @Override
    public GraphStatistics statistics() {
        int nodeCount = steps.size();
        int edgeCount = dependencies.values().stream().mapToInt(Set::size).sum();
        return new GraphStatistics(
            nodeCount,
            edgeCount,
            0,
            0,
            0,
            nodeCount > 0 ? (double) edgeCount / nodeCount : 0.0,
            Duration.ZERO,
            Duration.ZERO,
            maximumParallelism(),
            averageParallelism()
        );
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
        public Builder() {}

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
