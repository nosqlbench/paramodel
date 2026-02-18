package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

///
/// Default {@link ExecutionGraph} implementation built from compiled step data.
///
/// Provides DAG analysis including topological sort, parallel waves, critical path,
/// acyclicity validation, and basic graph statistics. Scheduling and subgraph
/// extraction methods throw {@link UnsupportedOperationException} as they require
/// runtime capabilities.
///
public class DefaultExecutionGraph implements ExecutionGraph {

    private final List<AtomicStep> steps;
    private final Map<String, AtomicStep> stepIndex;
    private final List<Edge> edges;

    public DefaultExecutionGraph(List<AtomicStep> steps) {
        this.steps = List.copyOf(steps);
        this.stepIndex = new LinkedHashMap<>();
        for (AtomicStep step : steps) {
            stepIndex.put(step.id(), step);
        }

        // Build edges from declared dependencies
        List<Edge> edgeList = new ArrayList<>();
        for (AtomicStep target : steps) {
            for (String depId : target.dependencies()) {
                AtomicStep source = stepIndex.get(depId);
                if (source != null) {
                    Duration weight = source.estimatedDuration().orElse(Duration.ZERO);
                    edgeList.add(new Edge(source, target, weight));
                }
            }
        }
        this.edges = List.copyOf(edgeList);
    }

    @Override
    public List<AtomicStep> steps() {
        return steps;
    }

    @Override
    public List<Edge> edges() {
        return edges;
    }

    @Override
    public Optional<AtomicStep> findStep(String stepId) {
        return Optional.ofNullable(stepIndex.get(stepId));
    }

    @Override
    public Set<AtomicStep> dependencies(AtomicStep step) {
        return step.dependencies().stream()
            .map(stepIndex::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<AtomicStep> transitiveDependencies(AtomicStep step) {
        Set<AtomicStep> result = new HashSet<>();
        Deque<AtomicStep> stack = new ArrayDeque<>(dependencies(step));
        while (!stack.isEmpty()) {
            AtomicStep dep = stack.pop();
            if (result.add(dep)) {
                stack.addAll(dependencies(dep));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<AtomicStep> dependents(AtomicStep step) {
        String id = step.id();
        return steps.stream()
            .filter(s -> s.dependencies().contains(id))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<AtomicStep> transitiveDependents(AtomicStep step) {
        Set<AtomicStep> result = new HashSet<>();
        Deque<AtomicStep> stack = new ArrayDeque<>(dependents(step));
        while (!stack.isEmpty()) {
            AtomicStep dep = stack.pop();
            if (result.add(dep)) {
                stack.addAll(dependents(dep));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public List<AtomicStep> criticalPath() {
        if (steps.isEmpty()) return List.of();

        // Longest-path on DAG using topological order
        List<AtomicStep> topoOrder = topologicalSort();
        Map<String, Long> distance = new HashMap<>();
        Map<String, String> predecessor = new HashMap<>();

        for (AtomicStep step : topoOrder) {
            distance.put(step.id(), 0L);
        }

        for (AtomicStep step : topoOrder) {
            long currentDist = distance.get(step.id());
            long stepDuration = step.estimatedDuration().orElse(Duration.ZERO).toMillis();
            long newDist = currentDist + stepDuration;

            for (AtomicStep dependent : dependents(step)) {
                if (newDist > distance.getOrDefault(dependent.id(), 0L)) {
                    distance.put(dependent.id(), newDist);
                    predecessor.put(dependent.id(), step.id());
                }
            }
        }

        // Find the step with the longest distance
        String endId = distance.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        if (endId == null) return List.of();

        // Trace back
        List<AtomicStep> path = new ArrayList<>();
        String current = endId;
        while (current != null) {
            path.add(stepIndex.get(current));
            current = predecessor.get(current);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    @Override
    public Duration criticalPathDuration() {
        return criticalPath().stream()
            .map(s -> s.estimatedDuration().orElse(Duration.ZERO))
            .reduce(Duration.ZERO, Duration::plus);
    }

    @Override
    public Duration totalDuration() {
        return steps.stream()
            .map(s -> s.estimatedDuration().orElse(Duration.ZERO))
            .reduce(Duration.ZERO, Duration::plus);
    }

    @Override
    public List<AtomicStep> topologicalSort() {
        Map<String, Integer> inDegree = new HashMap<>();
        for (AtomicStep step : steps) {
            inDegree.put(step.id(), 0);
        }
        for (Edge edge : edges) {
            inDegree.merge(edge.target().id(), 1, Integer::sum);
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<AtomicStep> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            AtomicStep step = stepIndex.get(id);
            if (step != null) result.add(step);

            for (AtomicStep dependent : dependents(step)) {
                int newDeg = inDegree.get(dependent.id()) - 1;
                inDegree.put(dependent.id(), newDeg);
                if (newDeg == 0) queue.add(dependent.id());
            }
        }
        return List.copyOf(result);
    }

    @Override
    public Map<Integer, List<AtomicStep>> parallelWaves() {
        if (steps.isEmpty()) return Map.of();

        Map<String, Integer> waveNumber = new HashMap<>();
        List<AtomicStep> topoOrder = topologicalSort();

        for (AtomicStep step : topoOrder) {
            int maxDepWave = -1;
            for (String depId : step.dependencies()) {
                Integer depWave = waveNumber.get(depId);
                if (depWave != null && depWave > maxDepWave) {
                    maxDepWave = depWave;
                }
            }
            waveNumber.put(step.id(), maxDepWave + 1);
        }

        Map<Integer, List<AtomicStep>> waves = new TreeMap<>();
        for (AtomicStep step : steps) {
            int wave = waveNumber.getOrDefault(step.id(), 0);
            waves.computeIfAbsent(wave, k -> new ArrayList<>()).add(step);
        }
        return Collections.unmodifiableMap(waves);
    }

    @Override
    public int maximumParallelism() {
        return parallelWaves().values().stream()
            .mapToInt(List::size)
            .max()
            .orElse(0);
    }

    /// Returns the average parallelism across execution.
    ///
    /// Calculated as `totalDuration / criticalPathDuration`. When the critical
    /// path duration is zero (e.g. all steps have zero duration), the method
    /// falls back to a wave-based calculation: `steps.size() / waveCount`.
    /// This correctly returns 1.0 for a fully sequential chain rather than
    /// incorrectly implying all steps run in parallel.
    @Override
    public double averageParallelism() {
        Duration total = totalDuration();
        Duration critical = criticalPathDuration();
        if (critical.isZero()) {
            if (steps.isEmpty()) return 0.0;
            int waveCount = parallelWaves().size();
            return waveCount == 0 ? 1.0 : (double) steps.size() / waveCount;
        }
        return (double) total.toMillis() / critical.toMillis();
    }

    @Override
    public boolean canExecuteConcurrently(AtomicStep step1, AtomicStep step2) {
        Set<AtomicStep> deps1 = transitiveDependencies(step1);
        Set<AtomicStep> dependents1 = transitiveDependents(step1);
        return !deps1.contains(step2) && !dependents1.contains(step2);
    }

    @Override
    public Schedule computeSchedule(ResourceLimits limits) {
        throw new UnsupportedOperationException(
            "Resource-constrained scheduling requires a runtime implementation");
    }

    @Override
    public ExecutionGraph subgraph(Set<String> stepIds) {
        List<AtomicStep> subset = steps.stream()
            .filter(s -> stepIds.contains(s.id()))
            .toList();
        return new DefaultExecutionGraph(subset);
    }

    @Override
    public ExecutionGraph subgraphForElement(String elementId) {
        Set<String> ids = steps.stream()
            .filter(s -> {
                if (s instanceof AtomicStep.DeployElement d) return d.elementId().equals(elementId);
                if (s instanceof AtomicStep.TeardownElement t) return t.elementId().equals(elementId);
                if (s instanceof AtomicStep.TrialStep e) return e.elementBindings().containsKey(elementId);
                return false;
            })
            .map(AtomicStep::id)
            .collect(Collectors.toSet());
        return subgraph(ids);
    }

    @Override
    public ExecutionGraph subgraphForTrials(List<String> trialIds) {
        Set<String> trialSet = new HashSet<>(trialIds);
        Set<String> ids = steps.stream()
            .filter(s -> {
                if (s instanceof AtomicStep.TrialStep e) return trialSet.contains(e.trialId());
                return false;
            })
            .map(AtomicStep::id)
            .collect(Collectors.toSet());

        // Include dependencies transitively
        Set<String> allIds = new HashSet<>(ids);
        for (String id : ids) {
            AtomicStep step = stepIndex.get(id);
            if (step != null) {
                transitiveDependencies(step).forEach(d -> allIds.add(d.id()));
            }
        }
        return subgraph(allIds);
    }

    @Override
    public boolean isAcyclic() {
        return topologicalSort().size() == steps.size();
    }

    @Override
    public GraphStatistics statistics() {
        Map<Integer, List<AtomicStep>> waves = parallelWaves();
        int maxFanOut = steps.stream()
            .mapToInt(s -> dependents(s).size())
            .max().orElse(0);
        int maxFanIn = steps.stream()
            .mapToInt(s -> s.dependencies().size())
            .max().orElse(0);
        int maxDepth = waves.isEmpty() ? 0 : Collections.max(waves.keySet()) + 1;
        double avgDegree = steps.isEmpty() ? 0.0 : (double) edges.size() * 2 / steps.size();

        return new GraphStatistics(
            steps.size(),
            edges.size(),
            maxDepth,
            maxFanOut,
            maxFanIn,
            avgDegree,
            criticalPathDuration(),
            totalDuration(),
            maximumParallelism(),
            averageParallelism()
        );
    }
}
