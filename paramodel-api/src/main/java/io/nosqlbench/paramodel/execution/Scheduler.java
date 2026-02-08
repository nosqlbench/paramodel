package io.nosqlbench.paramodel.execution;

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// # Scheduler
///
/// Determines the execution order and timing of {@link AtomicStep}s within an
/// {@link ExecutionGraph}, managing resource allocation, dependency resolution,
/// and concurrency control to maximize throughput while respecting constraints.
///
/// ## Scheduler Responsibilities
///
/// The scheduler bridges the gap between logical dependencies and physical execution:
///
/// ```
/// Scheduler Functions:
///
/// Dependency Resolution
///   ├─ Track step dependencies
///   ├─ Determine when steps are runnable
///   ├─ Maintain dependency graph state
///   └─ Detect dependency violations
///
/// Resource-Aware Scheduling
///   ├─ Track available resources
///   ├─ Check resource requirements
///   ├─ Apply admission control
///   └─ Queue steps when resources exhausted
///
/// Concurrency Management
///   ├─ Maximize parallelism
///   ├─ Respect concurrency limits
///   ├─ Balance load across workers
///   └─ Avoid resource contention
///
/// Priority and Fairness
///   ├─ Apply scheduling policies
///   ├─ Prioritize critical path
///   ├─ Ensure fairness across trials
///   └─ Handle preemption
/// ```
///
/// ## Scheduling Algorithms
///
/// Different algorithms optimize for different objectives:
///
/// ```
/// Scheduling Algorithm Comparison:
///
/// FIFO (First In First Out):
///   Policy: Execute steps in order added
///   Pros: Simple, fair, predictable
///   Cons: Ignores priorities, may delay critical path
///   Use case: Simple workloads, debugging
///
/// PRIORITY (Critical Path First):
///   Policy: Execute critical path steps first
///   Pros: Minimizes total execution time
///   Cons: May starve non-critical steps
///   Use case: Time-sensitive studies
///
/// FAIR (Round Robin):
///   Policy: Distribute resources fairly across trials
///   Pros: Prevents starvation, fair progress
///   Cons: May increase total time
///   Use case: Multi-user environments
///
/// RESOURCE_AWARE (Best Fit):
///   Policy: Pack steps to maximize resource utilization
///   Pros: High resource efficiency
///   Cons: Complex, may increase latency
///   Use case: Cost-sensitive studies
///
/// Example Impact (100 trials):
///
///   FIFO:
///     Duration: 4h 30m
///     CPU Util: 58%
///     Fairness: High
///
///   PRIORITY:
///     Duration: 3h 45m (17% faster)
///     CPU Util: 62%
///     Fairness: Low
///
///   RESOURCE_AWARE:
///     Duration: 4h 10m
///     CPU Util: 78% (35% better)
///     Fairness: Medium
/// ```
///
/// ## Dependency-Aware Scheduling
///
/// The scheduler respects dependency relationships:
///
/// ```
/// Dependency Tracking:
///
/// Execution Graph:
///   A → B → D
///   A → C → D
///
/// Step States:
///   A: PENDING → dependencies: []
///   B: BLOCKED → dependencies: [A]
///   C: BLOCKED → dependencies: [A]
///   D: BLOCKED → dependencies: [B, C]
///
/// Scheduling Timeline:
///   t=0:   A is runnable (no dependencies)
///          → Schedule A
///
///   t=10:  A completes
///          → B becomes runnable (A satisfied)
///          → C becomes runnable (A satisfied)
///          → Schedule B and C in parallel
///
///   t=30:  B completes (C still running)
///          → D still blocked (waiting for C)
///
///   t=35:  C completes
///          → D becomes runnable (B and C satisfied)
///          → Schedule D
///
///   t=50:  D completes
/// ```
///
/// ## Resource-Aware Scheduling
///
/// The scheduler performs admission control based on resources:
///
/// ```
/// Resource-Based Admission:
///
/// Available: 8 CPU cores, 16 GB memory
///
/// Queue:
///   Step A: 2 cores, 4 GB  [runnable]
///   Step B: 4 cores, 8 GB  [runnable]
///   Step C: 3 cores, 6 GB  [runnable]
///   Step D: 2 cores, 4 GB  [blocked on dependencies]
///
/// Scheduling Decision:
///   Option 1: Schedule A + B
///     Usage: 6 cores, 12 GB
///     Remaining: 2 cores, 4 GB
///     Utilization: 75% CPU, 75% memory
///
///   Option 2: Schedule A + C
///     Usage: 5 cores, 10 GB
///     Remaining: 3 cores, 6 GB
///     Utilization: 62% CPU, 62% memory
///
///   Option 3: Schedule B + C
///     Usage: 7 cores, 14 GB
///     Remaining: 1 core, 2 GB
///     Utilization: 87% CPU, 87% memory ← Best
///
/// Selected: Option 3 (maximizes utilization)
/// ```
///
/// ## Work Stealing
///
/// The scheduler supports work stealing for load balancing:
///
/// ```
/// Work Stealing:
///
/// Worker 1: [A, B, C, D, E] (5 pending)
/// Worker 2: [F] (1 pending)
/// Worker 3: [] (0 pending - idle)
///
/// Work Stealing Event:
///   Worker 3 is idle
///   → Check other workers for stealable work
///   → Worker 1 has 5 pending (imbalanced)
///   → Steal steps C and D from Worker 1
///
/// After Stealing:
/// Worker 1: [A, B, E] (3 pending)
/// Worker 2: [F] (1 pending)
/// Worker 3: [C, D] (2 pending)
///
/// Load Balance: Much better (3-1-2 vs 5-1-0)
/// ```
///
/// ## Preemption
///
/// The scheduler may preempt long-running steps:
///
/// ```
/// Preemption Scenario:
///
/// Running:
///   Step A: Long-running (30 minutes)
///   Step B: Medium (10 minutes)
///
/// Queue:
///   Step C: Critical path, high priority
///
/// Resources:
///   Available: 0 cores (all allocated)
///
/// Preemption Decision:
///   Step C is critical and high priority
///   → Preempt Step A (longest running, not critical)
///   → Save Step A state
///   → Release Step A resources
///   → Schedule Step C
///   → Reschedule Step A later
///
/// Timeline:
///   t=0:    Start A, B
///   t=10:   B completes, start C (preempts A)
///   t=20:   C completes
///   t=20:   Resume A
///   t=40:   A completes
///
/// Note: Total time same, but critical path improved
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Basic Scheduling
///
/// ```java
/// ExecutionGraph graph = executionPlan.executionGraph();
/// Scheduler scheduler = Scheduler.create(SchedulingPolicy.PRIORITY);
///
/// // Initialize scheduler with graph
/// scheduler.initialize(graph);
///
/// // Main scheduling loop
/// while (!scheduler.isComplete()) {
///     List<AtomicStep> runnable = scheduler.nextSteps();
///
///     for (AtomicStep step : runnable) {
///         executor.submit(() -> {
///             executeStep(step);
///             scheduler.markCompleted(step);
///         });
///     }
///
///     Thread.sleep(100); // Poll interval
/// }
/// ```
///
/// ### Example 2: Resource-Aware Scheduling
///
/// ```java
/// SchedulerConfig config = SchedulerConfig.builder()
///     .policy(SchedulingPolicy.RESOURCE_AWARE)
///     .maxCpu(16.0)
///     .maxMemoryGb(64.0)
///     .build();
///
/// Scheduler scheduler = Scheduler.create(config);
/// scheduler.initialize(graph);
///
/// while (!scheduler.isComplete()) {
///     ResourceAvailability available = getAvailableResources();
///     List<AtomicStep> runnable = scheduler.nextSteps(available);
///
///     for (AtomicStep step : runnable) {
///         System.out.printf("Scheduling %s (requires %.1f cores, %.1f GB)%n",
///             step.id(),
///             step.resourceRequirements().cpu(),
///             step.resourceRequirements().memoryMb() / 1024.0);
///
///         executeStep(step);
///     }
/// }
/// ```
///
/// ### Example 3: Priority-Based Scheduling
///
/// ```java
/// Scheduler scheduler = Scheduler.create(SchedulingPolicy.PRIORITY);
///
/// // Set priorities (critical path gets high priority)
/// List<AtomicStep> criticalPath = graph.criticalPath();
/// for (AtomicStep step : criticalPath) {
///     scheduler.setPriority(step, Priority.HIGH);
/// }
///
/// scheduler.initialize(graph);
///
/// while (!scheduler.isComplete()) {
///     List<AtomicStep> runnable = scheduler.nextSteps();
///
///     // Critical path steps scheduled first
///     for (AtomicStep step : runnable) {
///         Priority priority = scheduler.getPriority(step);
///         System.out.printf("Scheduling %s (priority: %s)%n",
///             step.id(), priority);
///         executeStep(step);
///     }
/// }
/// ```
///
/// ### Example 4: Monitoring Scheduler State
///
/// ```java
/// Scheduler scheduler = Scheduler.create();
/// scheduler.initialize(graph);
///
/// // Periodic monitoring
/// ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
/// monitor.scheduleAtFixedRate(() -> {
///     SchedulerState state = scheduler.state();
///
///     System.out.printf("Scheduler State:%n");
///     System.out.printf("  Pending: %d%n", state.pendingCount());
///     System.out.printf("  Running: %d%n", state.runningCount());
///     System.out.printf("  Completed: %d%n", state.completedCount());
///     System.out.printf("  Queue depth: %d%n", state.queueDepth());
///     System.out.printf("  Resource util: %.1f%%%n",
///         state.resourceUtilization() * 100);
/// }, 0, 5, TimeUnit.SECONDS);
/// ```
///
/// ### Example 5: Work Stealing
///
/// ```java
/// SchedulerConfig config = SchedulerConfig.builder()
///     .policy(SchedulingPolicy.FAIR)
///     .enableWorkStealing(true)
///     .workerCount(4)
///     .build();
///
/// Scheduler scheduler = Scheduler.create(config);
/// scheduler.initialize(graph);
///
/// // Workers automatically steal work when idle
/// for (int i = 0; i < 4; i++) {
///     int workerId = i;
///     executor.submit(() -> {
///         while (!scheduler.isComplete()) {
///             Optional<AtomicStep> step = scheduler.nextStep(workerId);
///
///             if (step.isPresent()) {
///                 executeStep(step.get());
///                 scheduler.markCompleted(step.get());
///             } else {
///                 // Idle - work stealing happens automatically
///                 Thread.sleep(100);
///             }
///         }
///     });
/// }
/// ```
///
/// ## Contract Requirements
///
/// ### Correctness
/// - Scheduler MUST respect dependencies (no premature execution)
/// - Scheduler MUST NOT schedule blocked steps
/// - Scheduler MUST mark steps complete exactly once
///
/// ### Resource Safety
/// - Scheduler MUST NOT overallocate resources
/// - Scheduler MUST release resources on step completion
/// - Scheduler MUST handle resource exhaustion gracefully
///
/// ### Fairness and Performance
/// - Scheduler SHOULD maximize resource utilization
/// - Scheduler SHOULD avoid starvation
/// - Scheduler SHOULD minimize makespan (total time)
///
/// @see ExecutionGraph
/// @see AtomicStep
/// @see Executor
///
public interface Scheduler {

    ///
    /// Creates a scheduler with default policy (FIFO).
    ///
    /// @return Scheduler instance
    ///
    static Scheduler create() {
        throw new UnsupportedOperationException(
            "Scheduler.create() requires a concrete implementation");
    }

    ///
    /// Creates a scheduler with specified policy.
    ///
    /// @param policy Scheduling policy
    /// @return Scheduler instance
    ///
    static Scheduler create(SchedulingPolicy policy) {
        throw new UnsupportedOperationException(
            "Scheduler.create(policy) requires a concrete implementation");
    }

    ///
    /// Creates a scheduler with specified configuration.
    ///
    /// @param config Scheduler configuration
    /// @return Scheduler instance
    ///
    static Scheduler create(SchedulerConfig config) {
        throw new UnsupportedOperationException(
            "Scheduler.create(config) requires a concrete implementation");
    }

    ///
    /// Initializes the scheduler with an execution graph.
    ///
    /// @param graph Execution graph to schedule
    ///
    void initialize(ExecutionGraph graph);

    ///
    /// Returns the next steps that can be executed.
    ///
    /// Returns steps that are runnable (all dependencies satisfied)
    /// and fit within current resource constraints.
    ///
    /// @return Runnable steps
    ///
    List<AtomicStep> nextSteps();

    ///
    /// Returns the next steps given available resources.
    ///
    /// @param available Available resources
    /// @return Runnable steps that fit within available resources
    ///
    List<AtomicStep> nextSteps(Runtime.ResourceAvailability available);

    ///
    /// Returns the next step for a specific worker (work stealing).
    ///
    /// @param workerId Worker identifier
    /// @return Next step for this worker, or empty if none available
    ///
    Optional<AtomicStep> nextStep(int workerId);

    ///
    /// Marks a step as started.
    ///
    /// @param step Step that started executing
    /// @param startTime Start timestamp
    ///
    void markStarted(AtomicStep step, Instant startTime);

    ///
    /// Marks a step as completed.
    ///
    /// @param step Step that completed
    ///
    void markCompleted(AtomicStep step);

    ///
    /// Marks a step as failed.
    ///
    /// @param step Step that failed
    /// @param error Failure error
    ///
    void markFailed(AtomicStep step, Throwable error);

    ///
    /// Sets the priority for a step.
    ///
    /// @param step Step to prioritize
    /// @param priority Priority level
    ///
    void setPriority(AtomicStep step, Priority priority);

    ///
    /// Gets the priority of a step.
    ///
    /// @param step Step to query
    /// @return Priority level
    ///
    Priority getPriority(AtomicStep step);

    ///
    /// Checks if scheduling is complete.
    ///
    /// @return True if all steps completed or failed
    ///
    boolean isComplete();

    ///
    /// Returns the current scheduler state.
    ///
    /// @return Scheduler state snapshot
    ///
    SchedulerState state();

    ///
    /// Returns scheduler statistics.
    ///
    /// @return Scheduling statistics
    ///
    SchedulerStatistics statistics();

    ///
    /// Scheduling policy.
    ///
    enum SchedulingPolicy {
        FIFO,              // First in first out
        PRIORITY,          // Critical path first
        FAIR,              // Round robin across trials
        RESOURCE_AWARE     // Maximize resource utilization
    }

    ///
    /// Step priority.
    ///
    enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    ///
    /// Scheduler state snapshot.
    ///
    interface SchedulerState {
        int pendingCount();
        int runningCount();
        int completedCount();
        int failedCount();
        int queueDepth();
        double resourceUtilization();
        Map<String, Integer> stepsByPhase();
    }

    ///
    /// Scheduler statistics.
    ///
    interface SchedulerStatistics {
        Duration totalSchedulingTime();
        int totalStepsScheduled();
        int totalStepsCompleted();
        int totalStepsFailed();
        double averageQueueWaitTime();
        double averageResourceUtilization();
        Map<Priority, Integer> stepsByPriority();
    }

    ///
    /// Scheduler configuration.
    ///
    interface SchedulerConfig {
        SchedulingPolicy policy();
        double maxCpu();
        double maxMemoryGb();
        int maxConcurrency();
        boolean enableWorkStealing();
        int workerCount();
        Duration schedulingInterval();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "SchedulerConfig.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder policy(SchedulingPolicy policy);
            Builder maxCpu(double cpu);
            Builder maxMemoryGb(double memoryGb);
            Builder maxConcurrency(int max);
            Builder enableWorkStealing(boolean enable);
            Builder workerCount(int count);
            Builder schedulingInterval(Duration interval);
            SchedulerConfig build();
        }
    }
}
