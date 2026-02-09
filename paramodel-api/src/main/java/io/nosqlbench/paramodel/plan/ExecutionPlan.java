package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

///
/// # ExecutionPlan
///
/// Immutable, compiled execution plan derived from a {@link TestPlan} through the
/// commit operation. The ExecutionPlan represents the "HOW to execute" - a complete,
/// deterministic specification of execution steps, ordering, resource allocation,
/// and synchronization barriers.
///
/// ## Fundamental Relationship
///
/// ```
/// TestPlan.commit() → ExecutionPlan
///
/// TestPlan (Mutable)          ExecutionPlan (Immutable)
///   │                              │
///   ├─ Axes                        ├─ AtomicSteps
///   ├─ Elements                    ├─ ExecutionGraph
///   ├─ Relationships    ────→      ├─ Barriers
///   ├─ Policies                    ├─ TrialOrdering
///   └─ [User Intent]               └─ [Compiled Strategy]
///
/// The commit() operation performs:
///   1. TestPlan validation
///   2. Trial space enumeration
///   3. Element instantiation planning
///   4. Concurrency graph construction
///   5. Barrier placement
///   6. Step ordering optimization
///   7. Resource allocation
///   8. Checkpoint planning
/// ```
///
/// ## Execution Model: Atomic Steps
///
/// An ExecutionPlan decomposes execution into atomic steps that can be
/// executed, checkpointed, and recovered independently:
///
/// ```
/// Atomic Step Types:
///
/// DEPLOY_ELEMENT(element_id, config)
///   ├─ Provisions infrastructure
///   ├─ Starts services
///   └─ Waits for health checks
///
/// EXECUTE_TRIAL(trial_id, element_bindings)
///   ├─ Binds trial to element instances
///   ├─ Executes trial logic
///   └─ Collects results
///
/// TEARDOWN_ELEMENT(element_id)
///   ├─ Graceful shutdown
///   ├─ Resource cleanup
///   └─ Artifact collection
///
/// BARRIER(barrier_id, dependencies)
///   ├─ Synchronization point
///   ├─ Waits for all dependencies
///   └─ Enables subsequent steps
///
/// CHECKPOINT(checkpoint_id)
///   ├─ Persists execution state
///   ├─ Captures intermediate results
///   └─ Enables resumption
/// ```
///
/// ## Execution Graph Structure
///
/// The execution graph captures dependencies and parallelism opportunities:
///
/// ```
/// Example Execution Graph:
///
/// TestPlan:
///   - Axes: cache_size={128,256}, concurrency={10,50}
///   - Elements: db (SHARED), cache (INSTANCED_PER concurrency), app (INSTANCED_PER trial)
///   - Relationships: db←SHARED→cache, cache←INSTANCED_PER→app
///
/// Compiled ExecutionPlan Graph:
///
/// START
///   │
///   └─→ DEPLOY_ELEMENT(db)
///         │
///         ├─→ DEPLOY_ELEMENT(cache_10)     ├─→ DEPLOY_ELEMENT(cache_50)
///         │     │                            │     │
///         │     ├─→ DEPLOY_ELEMENT(app_t1)  │     ├─→ DEPLOY_ELEMENT(app_t3)
///         │     │     │                      │     │     │
///         │     │     └─→ EXECUTE_TRIAL(t1) │     │     └─→ EXECUTE_TRIAL(t3)
///         │     │           │                │     │           │
///         │     ├─→ DEPLOY_ELEMENT(app_t2)  │     ├─→ DEPLOY_ELEMENT(app_t4)
///         │     │     │                      │     │     │
///         │     │     └─→ EXECUTE_TRIAL(t2) │     │     └─→ EXECUTE_TRIAL(t4)
///         │     │           │                │     │           │
///         │     └───────────┴─────────→ BARRIER(cache_10_done)
///         │                                  │
///         │                                  └─────────────────┴─────────→ BARRIER(cache_50_done)
///         │                                                                      │
///         └─────────────────────────────────────────────────────────────────────┴─→ TEARDOWN_ALL
///                                                                                         │
///                                                                                         └─→ END
///
/// Concurrency Opportunities:
///   - cache_10 and cache_50 can deploy in parallel (different instances)
///   - app_t1 and app_t2 can deploy in parallel (same cache instance)
///   - t1 and t2 can execute in parallel (different app instances)
///   - cache_10 group and cache_50 group can execute in parallel
/// ```
///
/// ## Trial Ordering Strategies
///
/// ExecutionPlan applies ordering strategies from TestPlan policies:
///
/// ```
/// Trial Ordering Modes:
///
/// SEQUENTIAL:
///   t1 → t2 → t3 → t4 → ...
///   No parallelism, simplest debugging
///
/// SHUFFLED:
///   t3 → t1 → t4 → t2 → ...
///   Randomized order to avoid bias
///
/// EDGE_FIRST:
///   boundaries(axis_1) → boundaries(axis_2) → ... → interior
///   Progressive refinement strategy
///
/// DEPENDENCY_OPTIMIZED:
///   Group trials by element dependencies
///   Minimize deployment churn
///
/// COST_OPTIMIZED:
///   Schedule expensive trials early
///   Fail-fast on costly errors
/// ```
///
/// ## Barrier Synchronization
///
/// Barriers coordinate concurrent execution and manage resource lifecycles:
///
/// ```
/// Barrier Types:
///
/// ELEMENT_READY(element_id)
///   - Fired when element deployment completes
///   - Unblocks trials depending on this element
///
/// TRIAL_COMPLETE(trial_id)
///   - Fired when trial execution finishes
///   - Updates progress tracking
///
/// ELEMENT_SCOPE_END(element_id)
///   - Fired when all trials using element complete
///   - Triggers element teardown
///
/// CHECKPOINT_BOUNDARY(checkpoint_id)
///   - Fired at designated checkpoint points
///   - Triggers state persistence
///
/// Example Barrier Coordination:
///
///   DEPLOY_ELEMENT(cache) → BARRIER(cache_ready)
///                               │
///                               ├─→ EXECUTE_TRIAL(t1) ─┐
///                               ├─→ EXECUTE_TRIAL(t2) ─┼─→ BARRIER(cache_trials_done)
///                               └─→ EXECUTE_TRIAL(t3) ─┘         │
///                                                                 └─→ TEARDOWN_ELEMENT(cache)
/// ```
///
/// ## Checkpoint and Recovery
///
/// ExecutionPlans support incremental execution with checkpointing:
///
/// ```
/// Checkpoint Strategy:
///
/// Execution Timeline:
///   [Deploy] → [Execute Batch 1] → CHECKPOINT → [Execute Batch 2] → CHECKPOINT → [Teardown]
///
/// Checkpoint Contents:
///   - Completed step IDs
///   - Pending step IDs
///   - Element instance states
///   - Partial results
///   - Execution metadata
///
/// Recovery Process:
///   1. Load checkpoint
///   2. Identify completed steps
///   3. Resume from next pending step
///   4. Reuse existing element instances if still healthy
///   5. Continue execution
///
/// Example Recovery Scenario:
///
///   Initial Run:
///     ✓ DEPLOY_ELEMENT(db)
///     ✓ DEPLOY_ELEMENT(cache)
///     ✓ EXECUTE_TRIAL(t1)
///     ✓ EXECUTE_TRIAL(t2)
///     ✗ CRASH → CHECKPOINT written
///
///   Recovery Run:
///     ↻ Load checkpoint
///     ↻ db and cache still healthy → reuse
///     ↻ Resume at EXECUTE_TRIAL(t3)
///     ✓ EXECUTE_TRIAL(t3)
///     ✓ EXECUTE_TRIAL(t4)
///     ✓ TEARDOWN_ALL
/// ```
///
/// ## Resource Allocation and Limits
///
/// ExecutionPlans specify resource requirements and constraints:
///
/// ```
/// Resource Allocation:
///
/// Per-Element Resources:
///   element_id → {
///     cpu: 2.0 cores
///     memory: 4096 MB
///     storage: 10 GB
///     network: 1 Gbps
///   }
///
/// Aggregate Resources:
///   Total CPU: ∑(element_cpu × element_instances)
///   Total Memory: ∑(element_memory × element_instances)
///   Peak Concurrency: max_parallel_trials × trial_overhead
///
/// Resource Scheduling:
///   - Respect platform limits (max_cpu, max_memory)
///   - Apply admission control
///   - Queue trials when resources exhausted
///   - Scale down during idle periods
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Inspecting Execution Plan Structure
///
/// ```java
/// TestPlan testPlan = /* ... */;
/// ExecutionPlan execPlan = testPlan.commit();
///
/// System.out.printf("Execution Plan: %s%n", execPlan.id());
/// System.out.printf("Total steps: %d%n", execPlan.steps().size());
/// System.out.printf("Barriers: %d%n", execPlan.barriers().size());
/// System.out.printf("Estimated duration: %s%n",
///     execPlan.estimatedDuration().orElse(Duration.ZERO));
///
/// // Analyze concurrency potential
/// ExecutionGraph graph = execPlan.executionGraph();
/// int maxParallelism = graph.maximumParallelism();
/// System.out.printf("Max parallel trials: %d%n", maxParallelism);
/// ```
///
/// ### Example 2: Execution with Progress Tracking
///
/// ```java
/// ExecutionPlan plan = testPlan.commit();
///
/// plan.execute(new ExecutionObserver() {
///     @Override
///     public void onStepStarted(AtomicStep step) {
///         System.out.printf("[%s] Starting: %s%n",
///             Instant.now(), step.description());
///     }
///
///     @Override
///     public void onStepCompleted(AtomicStep step, Duration elapsed) {
///         System.out.printf("[%s] Completed: %s (took %s)%n",
///             Instant.now(), step.description(), elapsed);
///     }
///
///     @Override
///     public void onBarrierReached(Barrier barrier) {
///         System.out.printf("Barrier reached: %s (%d dependencies satisfied)%n",
///             barrier.id(), barrier.dependencies().size());
///     }
///
///     @Override
///     public void onCheckpoint(String checkpointId) {
///         System.out.printf("Checkpoint created: %s%n", checkpointId);
///     }
/// });
/// ```
///
/// ### Example 3: Recovery from Checkpoint
///
/// ```java
/// // Initial execution that crashes
/// ExecutionPlan plan = testPlan.commit();
/// try {
///     plan.executeWithCheckpoints(Duration.ofMinutes(10)); // Checkpoint every 10 min
/// } catch (ExecutionException e) {
///     System.err.println("Execution failed: " + e.getMessage());
/// }
///
/// // Recovery
/// Optional<Checkpoint> lastCheckpoint = plan.latestCheckpoint();
/// if (lastCheckpoint.isPresent()) {
///     System.out.printf("Recovering from checkpoint: %s%n",
///         lastCheckpoint.get().id());
///
///     ExecutionPlan resumedPlan = plan.resumeFrom(lastCheckpoint.get());
///     resumedPlan.execute(); // Continues from checkpoint
/// }
/// ```
///
/// ### Example 4: Resource-Constrained Execution
///
/// ```java
/// ExecutionPlan plan = testPlan.commit();
///
/// // Check resource requirements
/// ResourceRequirements requirements = plan.resourceRequirements();
/// System.out.printf("Peak CPU: %.1f cores%n", requirements.peakCpu());
/// System.out.printf("Peak Memory: %d MB%n", requirements.peakMemoryMb());
///
/// // Adjust concurrency based on available resources
/// double availableCpu = Runtime.getRuntime().availableProcessors();
/// double availableMemoryMb = 16384.0;
///
/// if (requirements.peakCpu() > availableCpu ||
///     requirements.peakMemoryMb() > availableMemoryMb) {
///     // Throttle execution
///     plan = plan.withMaxConcurrency(
///         (int) (availableCpu / requirements.peakCpu() * plan.estimatedMaxParallelism()));
/// }
///
/// plan.execute();
/// ```
///
/// ### Example 5: Analyzing Execution Graph
///
/// ```java
/// ExecutionPlan plan = testPlan.commit();
/// ExecutionGraph graph = plan.executionGraph();
///
/// // Find critical path
/// List<AtomicStep> criticalPath = graph.criticalPath();
/// Duration criticalPathDuration = criticalPath.stream()
///     .map(AtomicStep::estimatedDuration)
///     .reduce(Duration.ZERO, Duration::plus);
///
/// System.out.printf("Critical path: %d steps, %s duration%n",
///     criticalPath.size(), criticalPathDuration);
///
/// // Find parallelism opportunities
/// Map<Integer, List<AtomicStep>> parallelWaves = graph.parallelWaves();
/// parallelWaves.forEach((wave, steps) -> {
///     System.out.printf("Wave %d: %d parallel steps%n", wave, steps.size());
/// });
/// ```
///
/// ## Contract Requirements
///
/// ### Immutability and Determinism
/// - ExecutionPlan instances MUST be deeply immutable
/// - Same TestPlan MUST produce equivalent ExecutionPlan on repeated commit()
/// - Execution order MUST be deterministic (modulo explicit randomization policies)
///
/// ### Correctness Guarantees
/// - Execution graph MUST be acyclic (no circular dependencies)
/// - All barriers MUST have finite dependency sets
/// - Resource allocation MUST not exceed declared element requirements
/// - Trial ordering MUST respect element relationship constraints
///
/// ### Recovery and Fault Tolerance
/// - Checkpoints MUST be self-contained and resumable
/// - Resumed execution MUST produce equivalent results to uninterrupted execution
/// - Element instance reuse MUST be safe (health-checked)
///
/// ### Performance and Scalability
/// - Execution graph construction SHOULD be O(n log n) in trial count
/// - Step scheduling SHOULD maximize parallelism within resource constraints
/// - Barrier overhead SHOULD be O(1) per synchronization point
///
/// @see TestPlan
/// @see AtomicStep
/// @see ExecutionGraph
/// @see Barrier
/// @see TrialOrdering
/// @see ExecutionPlanMetadata
///
public interface ExecutionPlan {

    ///
    /// Returns the unique identifier for this execution plan.
    ///
    /// @return Execution plan ID (non-null, unique)
    ///
    String id();

    ///
    /// Returns the fingerprint of the source TestPlan.
    ///
    /// This links the execution plan back to the exact test plan version
    /// that generated it, ensuring full provenance.
    ///
    /// @return Source TestPlan fingerprint
    ///
    String testPlanFingerprint();

    ///
    /// Returns all atomic steps in this execution plan.
    ///
    /// Steps are returned in topological order respecting dependencies.
    /// Multiple valid orderings may exist; the implementation chooses one.
    ///
    /// @return Ordered list of atomic steps (unmodifiable)
    ///
    List<AtomicStep> steps();

    ///
    /// Returns all synchronization barriers in this execution plan.
    ///
    /// @return All barriers (unmodifiable)
    ///
    List<Barrier> barriers();

    ///
    /// Returns the execution graph representing dependencies and parallelism.
    ///
    /// @return Execution graph
    ///
    ExecutionGraph executionGraph();

    ///
    /// Returns the trial ordering strategy used in this plan.
    ///
    /// @return Trial ordering mode
    ///
    TrialOrdering trialOrdering();

    ///
    /// Returns the estimated wall-clock duration to complete this plan.
    ///
    /// Accounts for parallelism, critical path, and resource constraints.
    ///
    /// @return Estimated duration if calculable
    ///
    Optional<Duration> estimatedDuration();

    ///
    /// Returns the estimated maximum number of parallel trials.
    ///
    /// This is the peak concurrency level based on element relationships
    /// and resource constraints.
    ///
    /// @return Maximum parallel trial count
    ///
    int estimatedMaxParallelism();

    ///
    /// Returns resource requirements for executing this plan.
    ///
    /// @return Aggregate resource requirements
    ///
    ResourceRequirements resourceRequirements();

    ///
    /// Returns checkpoint strategy for this execution plan.
    ///
    /// Empty if checkpointing is disabled.
    ///
    /// @return Checkpoint strategy if enabled
    ///
    Optional<CheckpointStrategy> checkpointStrategy();

    ///
    /// Returns the latest checkpoint for this execution plan.
    ///
    /// Empty if no checkpoints have been created yet.
    ///
    /// @return Latest checkpoint if exists
    ///
    Optional<Checkpoint> latestCheckpoint();

    ///
    /// Returns all checkpoints for this execution plan in chronological order.
    ///
    /// @return All checkpoints (unmodifiable)
    ///
    List<Checkpoint> checkpoints();

    ///
    /// Executes this execution plan to completion.
    ///
    /// Blocks until all steps complete or execution fails.
    ///
    /// @return Execution results
    /// @throws ExecutionException if execution fails
    ///
    ExecutionResults execute() throws ExecutionException;

    ///
    /// Executes this execution plan with progress observation.
    ///
    /// @param observer Observer for execution events
    /// @return Execution results
    /// @throws ExecutionException if execution fails
    ///
    ExecutionResults execute(ExecutionObserver observer) throws ExecutionException;

    ///
    /// Executes with periodic checkpointing.
    ///
    /// @param checkpointInterval Time between checkpoints
    /// @return Execution results
    /// @throws ExecutionException if execution fails
    ///
    ExecutionResults executeWithCheckpoints(Duration checkpointInterval) throws ExecutionException;

    ///
    /// Resumes execution from a checkpoint.
    ///
    /// Validates checkpoint compatibility and continues from the checkpoint state.
    ///
    /// @param checkpoint Checkpoint to resume from
    /// @return New execution plan configured to resume from checkpoint
    /// @throws IllegalArgumentException if checkpoint is incompatible
    ///
    ExecutionPlan resumeFrom(Checkpoint checkpoint);

    ///
    /// Creates a modified execution plan with different concurrency limit.
    ///
    /// Useful for adapting to runtime resource availability.
    ///
    /// @param maxConcurrency Maximum parallel trials
    /// @return New execution plan with adjusted concurrency
    /// @throws IllegalArgumentException if maxConcurrency < 1
    ///
    ExecutionPlan withMaxConcurrency(int maxConcurrency);

    ///
    /// Returns metadata for this execution plan.
    ///
    /// @return Execution plan metadata
    ///
    ExecutionPlanMetadata metadata();

    ///
    /// Resource requirements for execution.
    ///
    record ResourceRequirements(
        double peakCpu,
        long peakMemoryMb,
        long peakStorageGb,
        double peakNetworkGbps,
        Map<String, Object> customResources
    ) {}

    ///
    /// Checkpoint strategy configuration.
    ///
    record CheckpointStrategy(
        Duration interval,
        boolean checkpointOnBarriers,
        boolean checkpointOnErrors,
        int maxCheckpoints
    ) {}

    ///
    /// Checkpoint state for recovery.
    ///
    interface Checkpoint {
        String id();
        Instant createdAt();
        Set<String> completedStepIds();
        Set<String> pendingStepIds();
        Map<String, Object> executionState();
        String fingerprint();
    }

    ///
    /// Observer interface for execution events.
    ///
    interface ExecutionObserver {
        void onStepStarted(AtomicStep step);
        void onStepCompleted(AtomicStep step, Duration elapsed);
        void onStepFailed(AtomicStep step, Throwable error);
        void onBarrierReached(Barrier barrier);
        void onCheckpoint(String checkpointId);
    }

    ///
    /// Results from completed execution.
    ///
    interface ExecutionResults {
        String executionPlanId();
        Duration totalDuration();
        List<TrialResult> trialResults();
        Map<String, Object> aggregateMetrics();
        boolean isSuccess();
        Optional<Throwable> error();
    }

    ///
    /// Exception thrown during execution.
    ///
    class ExecutionException extends Exception {
        private final Optional<String> failedStepId;

        public ExecutionException(String message) {
            super(message);
            this.failedStepId = Optional.empty();
        }

        public ExecutionException(String message, Throwable cause) {
            super(message, cause);
            this.failedStepId = Optional.empty();
        }

        public ExecutionException(String message, String failedStepId, Throwable cause) {
            super(message, cause);
            this.failedStepId = Optional.of(failedStepId);
        }

        public Optional<String> failedStepId() {
            return failedStepId;
        }
    }
}
