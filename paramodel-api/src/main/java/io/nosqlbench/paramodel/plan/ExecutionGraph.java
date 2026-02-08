package io.nosqlbench.paramodel.plan;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

///
/// # ExecutionGraph
///
/// Directed acyclic graph (DAG) representing dependencies and parallelism opportunities
/// within an {@link ExecutionPlan}. The execution graph enables analysis of critical
/// paths, maximum parallelism, resource scheduling, and execution optimization.
///
/// ## Graph Structure
///
/// The execution graph is a DAG where:
/// - Nodes represent {@link AtomicStep} instances
/// - Edges represent dependency relationships (happens-before)
/// - Edge weights represent estimated step durations
///
/// ```
/// Graph Representation:
///
///   Node: AtomicStep
///   Edge: Dependency (A → B means "B depends on A")
///
/// Example Graph:
///
///   START
///     │
///     └─→ deploy_db [45s]
///           │
///           ├─→ deploy_cache_1 [30s] ──┐
///           │                           │
///           └─→ deploy_cache_2 [30s] ──┤
///                                       │
///                                       ├─→ barrier_caches_ready [0s]
///                                       │         │
///                                       │         ├─→ execute_t1 [120s] ──┐
///                                       │         ├─→ execute_t2 [120s] ──┤
///                                       │         └─→ execute_t3 [120s] ──┤
///                                       │                                  │
///                                       └──────────────────────────────────┴─→ barrier_trials_done [0s]
///                                                                                   │
///                                                                                   └─→ teardown_all [20s]
///                                                                                         │
///                                                                                         └─→ END
///
/// Graph Properties:
///   - Acyclic: No circular dependencies (guaranteed by construction)
///   - Connected: All nodes reachable from START
///   - Weighted: Edges weighted by step duration estimates
/// ```
///
/// ## Critical Path Analysis
///
/// The critical path is the longest dependency chain determining minimum execution time:
///
/// ```
/// Critical Path Calculation:
///
/// Given graph:
///   Path 1: START → A[10s] → B[20s] → END           = 30s
///   Path 2: START → C[15s] → D[10s] → E[5s] → END  = 30s
///   Path 3: START → F[5s] → END                     = 5s
///
/// Critical path: Path 1 or Path 2 (both 30s)
/// Minimum execution time: 30s (even with infinite parallelism)
///
/// Speedup Analysis:
///   Sequential time: 10 + 20 + 15 + 10 + 5 + 5 = 65s
///   Parallel time (critical path): 30s
///   Speedup: 65/30 = 2.17x
///   Parallelism efficiency: (65-30)/65 = 54% time saved
///
/// Critical Path Algorithm:
///   1. Topologically sort nodes
///   2. For each node in order:
///      earliest_start[node] = max(earliest_finish[dep] for dep in dependencies)
///      earliest_finish[node] = earliest_start[node] + duration[node]
///   3. Critical path = nodes with zero slack
///      slack[node] = latest_start[node] - earliest_start[node]
/// ```
///
/// ## Parallelism Analysis
///
/// The graph reveals opportunities for concurrent execution:
///
/// ```
/// Parallelism Metrics:
///
/// Maximum Parallelism:
///   Max number of steps executable simultaneously
///
/// Example:
///           A
///          ╱│╲
///         B C D    ← 3 steps can run in parallel
///          ╲│╱
///           E
///
/// Average Parallelism:
///   Mean number of steps running over execution time
///
/// Parallel Waves:
///   Groups of steps with no mutual dependencies
///
///   Wave 0: [START]
///   Wave 1: [deploy_db]
///   Wave 2: [deploy_cache_1, deploy_cache_2]         ← 2 parallel
///   Wave 3: [barrier_caches_ready]
///   Wave 4: [execute_t1, execute_t2, execute_t3]     ← 3 parallel
///   Wave 5: [barrier_trials_done]
///   Wave 6: [teardown_all]
///   Wave 7: [END]
///
/// Parallelism Profile:
///   Wave │ Parallelism │ Duration │ Cumulative
///   ─────┼─────────────┼──────────┼───────────
///     1  │      1      │   45s    │    45s
///     2  │      2      │   30s    │    75s
///     3  │      1      │    0s    │    75s
///     4  │      3      │  120s    │   195s
///     5  │      1      │    0s    │   195s
///     6  │      1      │   20s    │   215s
/// ```
///
/// ## Topological Ordering
///
/// Topological sort provides valid execution orderings:
///
/// ```
/// Topological Sort:
///
/// Graph:
///     A → C
///     B → C
///     C → D
///
/// Valid orderings:
///   1. A → B → C → D
///   2. B → A → C → D
///   Invalid: C → A → B → D  (violates A → C)
///
/// Algorithm (Kahn's):
///   1. Compute in-degree for each node
///   2. Queue nodes with in-degree 0
///   3. While queue not empty:
///      a. Dequeue node, add to result
///      b. Decrease in-degree of neighbors
///      c. Enqueue neighbors with in-degree 0
///   4. If result.size < graph.size: cycle detected
///
/// Multiple valid orderings enable optimization:
///   - Prefer steps with high fan-out (unlock more parallelism)
///   - Prefer steps on critical path (minimize total time)
///   - Prefer steps with shared resources (better locality)
/// ```
///
/// ## Resource Scheduling
///
/// The graph supports resource-constrained scheduling:
///
/// ```
/// Resource-Constrained Scheduling:
///
/// Available: 4 CPU cores, 8 GB memory
///
/// Steps:
///   A: 2 cores, 4 GB, 10s
///   B: 1 core,  2 GB, 20s
///   C: 2 cores, 3 GB, 15s
///   D: 3 cores, 5 GB, 10s
///
/// Dependencies: None (all can run in parallel ideally)
///
/// Naive Schedule (ignoring resources):
///   Start all at t=0 → EXCEEDS CAPACITY
///
/// Resource-Aware Schedule:
///   t=0s:   Start A (2c, 4GB) + B (1c, 2GB) + C (2c, 3GB)
///           Total: 5c, 9GB → EXCEEDS MEMORY
///
///   Adjusted:
///   t=0s:   Start A (2c, 4GB) + B (1c, 2GB)  [3c, 6GB used]
///   t=10s:  A finishes, start C (2c, 3GB)    [3c, 5GB used]
///   t=20s:  B finishes, start D (3c, 5GB)    [5c, 8GB used]
///   t=25s:  C finishes                        [3c, 5GB used]
///   t=30s:  D finishes                        [0c, 0GB used]
///
/// Total time: 30s (vs 20s if unlimited resources)
/// ```
///
/// ## Subgraph Extraction
///
/// Extract subgraphs for analysis or partial execution:
///
/// ```
/// Subgraph Operations:
///
/// Full Graph:
///   A → B → D
///   A → C → D
///
/// Subgraph [A, B, D]:
///   A → B → D
///   (C excluded)
///
/// Forward Closure from B:
///   B → D
///   (nodes reachable from B)
///
/// Backward Closure to D:
///   A → B → D
///   A → C → D
///   (nodes that reach D)
///
/// Transitive Reduction:
///   A → B → C
///   A → C      ← Remove redundant edge
///   Result: A → B → C
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Critical Path Analysis
///
/// ```java
/// ExecutionPlan plan = testPlan.commit();
/// ExecutionGraph graph = plan.executionGraph();
///
/// List<AtomicStep> criticalPath = graph.criticalPath();
/// Duration criticalPathDuration = graph.criticalPathDuration();
///
/// System.out.printf("Critical path: %d steps, %s duration%n",
///     criticalPath.size(), criticalPathDuration);
///
/// for (AtomicStep step : criticalPath) {
///     System.out.printf("  → %s (%s)%n",
///         step.description(),
///         step.estimatedDuration().orElse(Duration.ZERO));
/// }
///
/// // Calculate potential speedup
/// Duration sequentialDuration = graph.totalDuration();
/// double speedup = sequentialDuration.toMillis() /
///                  (double) criticalPathDuration.toMillis();
/// System.out.printf("Theoretical speedup: %.2fx%n", speedup);
/// ```
///
/// ### Example 2: Parallelism Analysis
///
/// ```java
/// ExecutionGraph graph = plan.executionGraph();
///
/// int maxParallelism = graph.maximumParallelism();
/// double avgParallelism = graph.averageParallelism();
///
/// System.out.printf("Maximum parallelism: %d steps%n", maxParallelism);
/// System.out.printf("Average parallelism: %.2f steps%n", avgParallelism);
///
/// // Get parallel waves
/// Map<Integer, List<AtomicStep>> waves = graph.parallelWaves();
/// waves.forEach((wave, steps) -> {
///     System.out.printf("Wave %d: %d parallel steps%n",
///         wave, steps.size());
///     steps.forEach(s -> System.out.printf("  - %s%n", s.description()));
/// });
/// ```
///
/// ### Example 3: Resource-Constrained Scheduling
///
/// ```java
/// ExecutionGraph graph = plan.executionGraph();
///
/// // Define resource limits
/// ResourceLimits limits = ResourceLimits.builder()
///     .maxCpu(8.0)
///     .maxMemoryGb(16.0)
///     .build();
///
/// // Compute schedule respecting limits
/// Schedule schedule = graph.computeSchedule(limits);
///
/// System.out.printf("Scheduled duration: %s%n", schedule.duration());
/// System.out.printf("Resource utilization: %.1f%%%n",
///     schedule.averageUtilization() * 100);
///
/// // Display schedule timeline
/// for (ScheduledStep ss : schedule.steps()) {
///     System.out.printf("%s: %s starts at %s%n",
///         ss.startTime(),
///         ss.step().description(),
///         ss.startTime());
/// }
/// ```
///
/// ### Example 4: Dependency Analysis
///
/// ```java
/// ExecutionGraph graph = plan.executionGraph();
/// AtomicStep step = graph.findStep("execute_trial_42").orElseThrow();
///
/// // Find all dependencies (transitive)
/// Set<AtomicStep> allDeps = graph.transitiveDependencies(step);
/// System.out.printf("Step %s depends on %d other steps%n",
///     step.id(), allDeps.size());
///
/// // Find immediate dependents
/// Set<AtomicStep> dependents = graph.dependents(step);
/// System.out.printf("Step %s is required by %d other steps%n",
///     step.id(), dependents.size());
///
/// // Check if two steps can run in parallel
/// AtomicStep step2 = graph.findStep("execute_trial_43").orElseThrow();
/// boolean canParallel = graph.canExecuteConcurrently(step, step2);
/// System.out.printf("Steps can run in parallel: %s%n", canParallel);
/// ```
///
/// ### Example 5: Subgraph Extraction
///
/// ```java
/// ExecutionGraph graph = plan.executionGraph();
///
/// // Extract subgraph for specific element
/// String elementId = "cache_instance_1";
/// ExecutionGraph subgraph = graph.subgraphForElement(elementId);
///
/// System.out.printf("Subgraph for %s:%n", elementId);
/// System.out.printf("  Steps: %d%n", subgraph.steps().size());
/// System.out.printf("  Duration: %s%n",
///     subgraph.criticalPathDuration());
///
/// // Extract subgraph for trial range
/// List<String> trialIds = List.of("t1", "t2", "t3");
/// ExecutionGraph trialSubgraph = graph.subgraphForTrials(trialIds);
/// ```
///
/// ## Contract Requirements
///
/// ### Graph Properties
/// - Graph MUST be acyclic (DAG)
/// - All nodes MUST be reachable from a root node
/// - All leaf nodes MUST eventually reach an end node
/// - Edge weights MUST be non-negative
///
/// ### Correctness Guarantees
/// - Critical path MUST be valid (exists and satisfies dependencies)
/// - Topological sort MUST produce valid ordering
/// - Parallelism analysis MUST respect dependencies
/// - Subgraphs MUST preserve dependency relationships
///
/// ### Performance
/// - Critical path computation SHOULD be O(V + E) where V=nodes, E=edges
/// - Topological sort SHOULD be O(V + E)
/// - Parallelism analysis SHOULD be O(V + E)
/// - Subgraph extraction SHOULD be O(V + E) in subgraph size
///
/// @see ExecutionPlan
/// @see AtomicStep
/// @see Barrier
///
public interface ExecutionGraph {

    ///
    /// Returns all steps in the execution graph.
    ///
    /// @return All atomic steps (unmodifiable)
    ///
    List<AtomicStep> steps();

    ///
    /// Returns all edges in the graph as (source, target) pairs.
    ///
    /// @return Dependency edges (unmodifiable)
    ///
    List<Edge> edges();

    ///
    /// Finds a step by ID.
    ///
    /// @param stepId Step identifier
    /// @return Step if found
    ///
    Optional<AtomicStep> findStep(String stepId);

    ///
    /// Returns immediate dependencies of a step.
    ///
    /// @param step Step to query
    /// @return Steps that must complete before this step (unmodifiable)
    ///
    Set<AtomicStep> dependencies(AtomicStep step);

    ///
    /// Returns transitive dependencies of a step (recursive).
    ///
    /// @param step Step to query
    /// @return All steps in dependency closure (unmodifiable)
    ///
    Set<AtomicStep> transitiveDependencies(AtomicStep step);

    ///
    /// Returns immediate dependents of a step.
    ///
    /// @param step Step to query
    /// @return Steps that depend on this step (unmodifiable)
    ///
    Set<AtomicStep> dependents(AtomicStep step);

    ///
    /// Returns transitive dependents of a step (recursive).
    ///
    /// @param step Step to query
    /// @return All steps in dependent closure (unmodifiable)
    ///
    Set<AtomicStep> transitiveDependents(AtomicStep step);

    ///
    /// Returns the critical path (longest dependency chain).
    ///
    /// @return Steps on critical path in execution order
    ///
    List<AtomicStep> criticalPath();

    ///
    /// Returns the duration of the critical path.
    ///
    /// This is the minimum time to complete execution with unlimited parallelism.
    ///
    /// @return Critical path duration
    ///
    Duration criticalPathDuration();

    ///
    /// Returns the total duration if all steps executed sequentially.
    ///
    /// @return Sum of all step durations
    ///
    Duration totalDuration();

    ///
    /// Returns a valid topological ordering of steps.
    ///
    /// @return Steps in topologically sorted order
    ///
    List<AtomicStep> topologicalSort();

    ///
    /// Returns steps grouped into parallel waves.
    ///
    /// Wave 0 contains steps with no dependencies.
    /// Wave n contains steps whose dependencies are all in waves < n.
    ///
    /// @return Map from wave number to steps in that wave
    ///
    Map<Integer, List<AtomicStep>> parallelWaves();

    ///
    /// Returns the maximum number of steps that can execute concurrently.
    ///
    /// @return Maximum parallelism (size of largest wave)
    ///
    int maximumParallelism();

    ///
    /// Returns the average parallelism across execution.
    ///
    /// Calculated as: total_duration / critical_path_duration
    ///
    /// @return Average number of concurrent steps
    ///
    double averageParallelism();

    ///
    /// Checks if two steps can execute concurrently.
    ///
    /// Steps can execute concurrently if neither depends on the other
    /// (directly or transitively).
    ///
    /// @param step1 First step
    /// @param step2 Second step
    /// @return True if steps can run in parallel
    ///
    boolean canExecuteConcurrently(AtomicStep step1, AtomicStep step2);

    ///
    /// Computes a resource-constrained schedule.
    ///
    /// @param limits Resource limits to respect
    /// @return Schedule respecting resource constraints
    ///
    Schedule computeSchedule(ResourceLimits limits);

    ///
    /// Extracts subgraph containing only specified steps.
    ///
    /// Preserves dependency edges between included steps.
    ///
    /// @param stepIds Steps to include
    /// @return Subgraph containing only specified steps
    ///
    ExecutionGraph subgraph(Set<String> stepIds);

    ///
    /// Extracts subgraph for a specific element.
    ///
    /// Includes deploy, trials using element, and teardown.
    ///
    /// @param elementId Element identifier
    /// @return Subgraph for element lifecycle
    ///
    ExecutionGraph subgraphForElement(String elementId);

    ///
    /// Extracts subgraph for specific trials.
    ///
    /// Includes dependencies needed to execute these trials.
    ///
    /// @param trialIds Trial identifiers
    /// @return Subgraph for executing specified trials
    ///
    ExecutionGraph subgraphForTrials(List<String> trialIds);

    ///
    /// Checks if the graph is acyclic.
    ///
    /// @return True if no cycles exist
    ///
    boolean isAcyclic();

    ///
    /// Returns graph statistics.
    ///
    /// @return Statistical summary of graph properties
    ///
    GraphStatistics statistics();

    ///
    /// Dependency edge in the execution graph.
    ///
    /// @param source Source step (must complete first)
    /// @param target Target step (depends on source)
    /// @param weight Edge weight (typically source step duration)
    ///
    record Edge(AtomicStep source, AtomicStep target, Duration weight) {}

    ///
    /// Resource limits for scheduling.
    ///
    record ResourceLimits(
        double maxCpu,
        double maxMemoryGb,
        double maxStorageGb,
        double maxNetworkGbps
    ) {
        public static Builder builder() {
            throw new UnsupportedOperationException(
                "ResourceLimits.builder() requires a concrete implementation");
        }

        public interface Builder {
            Builder maxCpu(double cpu);
            Builder maxMemoryGb(double memoryGb);
            Builder maxStorageGb(double storageGb);
            Builder maxNetworkGbps(double networkGbps);
            ResourceLimits build();
        }
    }

    ///
    /// Computed schedule respecting resource constraints.
    ///
    interface Schedule {
        Duration duration();
        List<ScheduledStep> steps();
        double averageUtilization();
        Map<String, List<ResourceUsagePoint>> resourceUsageTimeline();
    }

    ///
    /// Step with scheduled start time.
    ///
    record ScheduledStep(AtomicStep step, Duration startTime, Duration endTime) {}

    ///
    /// Resource usage at a point in time.
    ///
    record ResourceUsagePoint(Duration time, double cpu, double memoryGb, double storageGb) {}

    ///
    /// Graph statistics.
    ///
    record GraphStatistics(
        int nodeCount,
        int edgeCount,
        int maxDepth,
        int maxFanOut,
        int maxFanIn,
        double averageDegree,
        Duration criticalPathDuration,
        Duration totalDuration,
        int maximumParallelism,
        double averageParallelism
    ) {}
}
