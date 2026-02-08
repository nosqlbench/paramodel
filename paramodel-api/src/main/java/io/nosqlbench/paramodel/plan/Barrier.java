package io.nosqlbench.paramodel.plan;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

///
/// # Barrier
///
/// Synchronization primitive within an {@link ExecutionPlan} that coordinates
/// concurrent execution by enforcing dependency relationships and managing resource
/// lifecycles. Barriers ensure correct ordering of operations while maximizing
/// parallelism opportunities.
///
/// ## Barrier Semantics
///
/// A barrier represents a synchronization point where execution must wait until
/// all prerequisite conditions are satisfied:
///
/// ```
/// Barrier Behavior:
///
///   Step A ──┐
///   Step B ──┼──→ BARRIER ──→ Step D
///   Step C ──┘
///
/// Execution Flow:
///   1. Steps A, B, C execute concurrently (if resources available)
///   2. BARRIER blocks until ALL of A, B, C complete
///   3. Step D begins only after barrier releases
///   4. Multiple steps may wait on same barrier
///
/// Barrier States:
///   PENDING    → Waiting for dependencies to complete
///   SATISFIED  → All dependencies met, barrier released
///   FAILED     → One or more dependencies failed
///   TIMEOUT    → Dependencies didn't complete within timeout
/// ```
///
/// ## Barrier Types
///
/// Different barrier types support various coordination patterns:
///
/// ```
/// Barrier Type Taxonomy:
///
/// ELEMENT_READY
///   Purpose: Signal element deployment completion
///   Releases: Trials waiting for this element
///   Example: DB deployment → trials can execute
///
/// ELEMENT_SCOPE_END
///   Purpose: Signal all trials using element completed
///   Releases: Element teardown can proceed
///   Example: All cache trials done → teardown cache
///
/// TRIAL_BATCH
///   Purpose: Group trials into batches for checkpointing
///   Releases: Next batch can start, checkpoint created
///   Example: First 100 trials → checkpoint → next 100
///
/// CHECKPOINT_BOUNDARY
///   Purpose: Force synchronization at checkpoint points
///   Releases: Execution continues after state persisted
///   Example: Every 10 minutes → checkpoint → continue
///
/// CUSTOM
///   Purpose: Application-specific synchronization
///   Releases: User-defined conditions
///   Example: Await external approval → continue
/// ```
///
/// ## Dependency Graph Integration
///
/// Barriers integrate into the execution dependency graph:
///
/// ```
/// Execution Graph with Barriers:
///
///   START
///     │
///     └─→ DEPLOY_ELEMENT(db) ──→ BARRIER(db_ready)
///           │                         │
///           │                         ├─→ DEPLOY_ELEMENT(cache_1) ──┐
///           │                         │                               │
///           │                         └─→ DEPLOY_ELEMENT(cache_2) ──┤
///           │                                                         │
///           │                                                         ├─→ BARRIER(caches_ready)
///           │                                                         │        │
///           ├─────────────────────────────────────────────────────────┘        │
///           │                                                                  │
///           │    ┌─────────────────────────────────────────────────────────────┘
///           │    │
///           │    ├─→ EXECUTE_TRIAL(t1) ──┐
///           │    ├─→ EXECUTE_TRIAL(t2) ──┼─→ BARRIER(trials_done)
///           │    └─→ EXECUTE_TRIAL(t3) ──┘        │
///           │                                      │
///           └──────────────────────────────────────┴─→ TEARDOWN_ALL → END
///
/// Barrier Dependencies:
///   - db_ready depends on: [DEPLOY_ELEMENT(db)]
///   - caches_ready depends on: [DEPLOY_ELEMENT(cache_1), DEPLOY_ELEMENT(cache_2)]
///   - trials_done depends on: [EXECUTE_TRIAL(t1), EXECUTE_TRIAL(t2), EXECUTE_TRIAL(t3)]
/// ```
///
/// ## Fan-Out and Fan-In Patterns
///
/// Barriers enable classic parallel patterns:
///
/// ```
/// Fan-Out Pattern (1 → N):
///
///   BARRIER(resource_ready)
///       │
///       ├─→ Task 1 (parallel)
///       ├─→ Task 2 (parallel)
///       ├─→ Task 3 (parallel)
///       └─→ Task N (parallel)
///
/// Fan-In Pattern (N → 1):
///
///   Task 1 ──┐
///   Task 2 ──┤
///   Task 3 ──┼─→ BARRIER(all_tasks_done)
///   Task N ──┘        │
///                     └─→ Aggregate Results
///
/// Fork-Join Pattern (1 → N → 1):
///
///   BARRIER(fork)
///       │
///       ├─→ Task A ──┐
///       ├─→ Task B ──┼─→ BARRIER(join)
///       └─→ Task C ──┘        │
///                             └─→ Continue
/// ```
///
/// ## Timeout Handling
///
/// Barriers can specify timeout policies for stuck dependencies:
///
/// ```
/// Timeout Behavior:
///
/// BARRIER(element_ready, timeout=5m)
///   │
///   ├─ t=0s:   Dependencies: [deploy_db] → PENDING
///   ├─ t=30s:  deploy_db still running → PENDING
///   ├─ t=2m:   deploy_db still running → PENDING (warning emitted)
///   ├─ t=5m:   deploy_db still running → TIMEOUT
///   │          Action: Fail barrier, cancel dependent steps
///   │
///   OR
///   │
///   ├─ t=45s:  deploy_db completes → SATISFIED
///   └─         Dependent steps released
///
/// Timeout Actions:
///   FAIL_FAST:     Immediately fail execution plan
///   SKIP_DEPENDENT: Mark dependent steps as SKIPPED, continue others
///   WAIT_FOREVER:   No timeout, wait indefinitely
///   RETRY:          Retry failed dependencies up to limit
/// ```
///
/// ## Resource Lifecycle Management
///
/// Barriers coordinate element instance lifecycles:
///
/// ```
/// Element Lifecycle Coordination:
///
/// DEPLOY_ELEMENT(cache_instance_1)
///   │
///   └─→ BARRIER(cache_1_ready)
///         │
///         ├─→ EXECUTE_TRIAL(t1, cache=cache_1) ──┐
///         ├─→ EXECUTE_TRIAL(t2, cache=cache_1) ──┼─→ BARRIER(cache_1_scope_end)
///         └─→ EXECUTE_TRIAL(t3, cache=cache_1) ──┘        │
///                                                          │
///                                                          └─→ TEARDOWN_ELEMENT(cache_1)
///
/// Lifecycle Rules:
///   - Element teardown waits for scope_end barrier
///   - Scope_end barrier waits for all trials using element
///   - Trials can only start after ready barrier satisfied
///   - Early teardown triggers FAILED state for pending trials
/// ```
///
/// ## Barrier Observability
///
/// Barriers emit events for monitoring:
///
/// ```
/// Event Stream:
///
/// BarrierCreated(id="db_ready", dependencies=["deploy_db"], timestamp=T0)
///
/// BarrierWaiting(id="db_ready", pending=["deploy_db"], timestamp=T1)
///   ├─ satisfied: 0/1
///   └─ estimated_wait: 30s
///
/// DependencySatisfied(id="db_ready", completed="deploy_db", timestamp=T2)
///   ├─ satisfied: 1/1
///   └─ duration: 45s
///
/// BarrierReleased(id="db_ready", timestamp=T2, total_wait=45s)
///   └─ released_steps: ["deploy_cache_1", "deploy_cache_2"]
///
/// BarrierTimeout(id="slow_barrier", timestamp=T3, timeout=5m)
///   ├─ satisfied: 2/3
///   ├─ pending: ["slow_step"]
///   └─ action: FAIL_FAST
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Inspecting Barrier Dependencies
///
/// ```java
/// ExecutionPlan plan = testPlan.commit();
/// List<Barrier> barriers = plan.barriers();
///
/// for (Barrier barrier : barriers) {
///     System.out.printf("Barrier: %s [%s]%n", barrier.id(), barrier.type());
///     System.out.printf("  Dependencies: %d steps%n", barrier.dependencies().size());
///
///     barrier.timeout().ifPresent(timeout ->
///         System.out.printf("  Timeout: %s%n", timeout));
///
///     System.out.printf("  Dependent steps: %d%n",
///         barrier.dependentSteps().size());
/// }
/// ```
///
/// ### Example 2: Monitoring Barrier Progress
///
/// ```java
/// Barrier barrier = plan.barriers().stream()
///     .filter(b -> b.id().equals("trials_batch_1_done"))
///     .findFirst()
///     .orElseThrow();
///
/// BarrierMonitor monitor = new BarrierMonitor(barrier);
///
/// while (!monitor.isSatisfied()) {
///     BarrierStatus status = monitor.status();
///
///     System.out.printf("Progress: %d/%d dependencies satisfied%n",
///         status.satisfiedCount(),
///         status.totalCount());
///
///     System.out.printf("Pending: %s%n",
///         status.pendingDependencies());
///
///     Thread.sleep(1000);
/// }
///
/// System.out.printf("Barrier satisfied after %s%n",
///     monitor.totalWaitTime());
/// ```
///
/// ### Example 3: Custom Barrier with Manual Release
///
/// ```java
/// // Create execution plan with custom barrier
/// ExecutionPlan plan = testPlan.commit();
///
/// // Custom barrier requires external approval
/// Barrier approvalBarrier = plan.barriers().stream()
///     .filter(b -> b.type() == BarrierType.CUSTOM)
///     .filter(b -> b.id().equals("security_approval"))
///     .findFirst()
///     .orElseThrow();
///
/// // Execute plan asynchronously
/// Future<ExecutionResults> execution = executor.submit(() ->
///     plan.execute());
///
/// // Wait for approval barrier to be reached
/// approvalBarrier.awaitPending();
///
/// // Perform external approval workflow
/// boolean approved = requestSecurityApproval(plan);
///
/// if (approved) {
///     approvalBarrier.release(); // Manually satisfy barrier
/// } else {
///     approvalBarrier.fail("Security approval denied");
/// }
///
/// // Execution continues or fails based on approval
/// ExecutionResults results = execution.get();
/// ```
///
/// ### Example 4: Analyzing Barrier Criticality
///
/// ```java
/// // Find barriers on critical path
/// ExecutionGraph graph = plan.executionGraph();
/// List<AtomicStep> criticalPath = graph.criticalPath();
///
/// Set<String> criticalStepIds = criticalPath.stream()
///     .map(AtomicStep::id)
///     .collect(Collectors.toSet());
///
/// List<Barrier> criticalBarriers = plan.barriers().stream()
///     .filter(b -> b.dependencies().stream()
///         .anyMatch(criticalStepIds::contains))
///     .toList();
///
/// System.out.printf("Critical barriers: %d/%d%n",
///     criticalBarriers.size(), plan.barriers().size());
///
/// for (Barrier b : criticalBarriers) {
///     System.out.printf("  %s: %d dependencies%n",
///         b.id(), b.dependencies().size());
/// }
/// ```
///
/// ### Example 5: Barrier-Based Checkpointing
///
/// ```java
/// // Configure checkpointing at barriers
/// ExecutionPlan plan = testPlan.commit();
///
/// plan.execute(new ExecutionObserver() {
///     @Override
///     public void onBarrierReached(Barrier barrier) {
///         if (barrier.type() == BarrierType.TRIAL_BATCH) {
///             // Create checkpoint at each batch boundary
///             String checkpointId = "checkpoint_" + barrier.id();
///             createCheckpoint(checkpointId, plan);
///
///             System.out.printf("Checkpoint created at barrier: %s%n",
///                 barrier.id());
///         }
///     }
/// });
/// ```
///
/// ## Contract Requirements
///
/// ### Synchronization Guarantees
/// - Barriers MUST block until ALL dependencies are satisfied
/// - Barriers MUST release atomically (all or nothing)
/// - Barriers MUST be thread-safe for concurrent dependency satisfaction
///
/// ### Correctness
/// - Barrier dependency sets MUST be acyclic (no deadlocks)
/// - Barriers MUST fail if any dependency fails (unless policy overrides)
/// - Barrier release MUST happen exactly once
///
/// ### Performance
/// - Dependency checking SHOULD be O(1) per dependency
/// - Barrier release SHOULD be O(n) where n is number of dependent steps
/// - Barriers SHOULD NOT introduce unnecessary serialization
///
/// ### Observability
/// - Barriers MUST emit events on state transitions
/// - Barriers SHOULD track wait times and dependency satisfaction order
/// - Barriers MUST provide current status on demand
///
/// @see ExecutionPlan
/// @see AtomicStep
/// @see ExecutionGraph
///
public interface Barrier {

    ///
    /// Returns the unique identifier for this barrier.
    ///
    /// @return Barrier ID (non-null, unique within execution plan)
    ///
    String id();

    ///
    /// Returns the type of this barrier.
    ///
    /// @return Barrier type
    ///
    BarrierType type();

    ///
    /// Returns a human-readable description of this barrier.
    ///
    /// @return Barrier description
    ///
    String description();

    ///
    /// Returns the IDs of steps that must complete before this barrier is satisfied.
    ///
    /// @return Dependency step IDs (unmodifiable)
    ///
    List<String> dependencies();

    ///
    /// Returns the IDs of steps that depend on this barrier.
    ///
    /// These steps will be released when the barrier is satisfied.
    ///
    /// @return Dependent step IDs (unmodifiable)
    ///
    List<String> dependentSteps();

    ///
    /// Returns the timeout for this barrier.
    ///
    /// Empty if no timeout is configured (wait indefinitely).
    ///
    /// @return Timeout duration if configured
    ///
    Optional<Duration> timeout();

    ///
    /// Returns the action to take if this barrier times out.
    ///
    /// @return Timeout action
    ///
    TimeoutAction timeoutAction();

    ///
    /// Returns the current state of this barrier.
    ///
    /// @return Current barrier state
    ///
    BarrierState state();

    ///
    /// Returns the set of dependencies that have been satisfied.
    ///
    /// @return Satisfied dependency IDs (unmodifiable)
    ///
    Set<String> satisfiedDependencies();

    ///
    /// Returns the set of dependencies still pending.
    ///
    /// @return Pending dependency IDs (unmodifiable)
    ///
    Set<String> pendingDependencies();

    ///
    /// Returns the timestamp when this barrier was created.
    ///
    /// @return Creation timestamp
    ///
    Instant createdAt();

    ///
    /// Returns the timestamp when this barrier was satisfied.
    ///
    /// Empty if barrier is not yet satisfied.
    ///
    /// @return Satisfaction timestamp if satisfied
    ///
    Optional<Instant> satisfiedAt();

    ///
    /// Returns the total time spent waiting for this barrier.
    ///
    /// Empty if barrier is not yet satisfied.
    ///
    /// @return Wait duration if satisfied
    ///
    Optional<Duration> waitDuration();

    ///
    /// Returns arbitrary metadata attached to this barrier.
    ///
    /// @return Barrier metadata (unmodifiable)
    ///
    Map<String, Object> metadata();

    ///
    /// Checks if this barrier is satisfied (all dependencies met).
    ///
    /// @return True if satisfied
    ///
    boolean isSatisfied();

    ///
    /// Checks if this barrier has failed.
    ///
    /// @return True if failed
    ///
    boolean isFailed();

    ///
    /// Checks if this barrier has timed out.
    ///
    /// @return True if timed out
    ///
    boolean isTimedOut();

    ///
    /// Blocks until this barrier is satisfied or fails.
    ///
    /// @throws InterruptedException if interrupted while waiting
    /// @throws BarrierException if barrier fails or times out
    ///
    void await() throws InterruptedException, BarrierException;

    ///
    /// Blocks until this barrier is satisfied, fails, or timeout expires.
    ///
    /// @param timeout Maximum time to wait
    /// @return True if satisfied within timeout, false if timeout expired
    /// @throws InterruptedException if interrupted while waiting
    /// @throws BarrierException if barrier fails
    ///
    boolean await(Duration timeout) throws InterruptedException, BarrierException;

    ///
    /// Manually releases this barrier (for CUSTOM barriers).
    ///
    /// @throws IllegalStateException if barrier is not in a releasable state
    ///
    void release();

    ///
    /// Manually fails this barrier with an error message.
    ///
    /// @param reason Failure reason
    /// @throws IllegalStateException if barrier is already satisfied or failed
    ///
    void fail(String reason);

    ///
    /// Barrier types.
    ///
    enum BarrierType {
        ///
        /// Signals element deployment completion.
        ///
        ELEMENT_READY,

        ///
        /// Signals all trials using element completed.
        ///
        ELEMENT_SCOPE_END,

        ///
        /// Groups trials into batches.
        ///
        TRIAL_BATCH,

        ///
        /// Forces synchronization at checkpoint.
        ///
        CHECKPOINT_BOUNDARY,

        ///
        /// Application-specific synchronization.
        ///
        CUSTOM
    }

    ///
    /// Barrier states.
    ///
    enum BarrierState {
        ///
        /// Waiting for dependencies to complete.
        ///
        PENDING,

        ///
        /// All dependencies met, barrier released.
        ///
        SATISFIED,

        ///
        /// One or more dependencies failed.
        ///
        FAILED,

        ///
        /// Dependencies didn't complete within timeout.
        ///
        TIMEOUT
    }

    ///
    /// Actions to take on barrier timeout.
    ///
    enum TimeoutAction {
        ///
        /// Immediately fail entire execution plan.
        ///
        FAIL_FAST,

        ///
        /// Mark dependent steps as SKIPPED, continue others.
        ///
        SKIP_DEPENDENT,

        ///
        /// No timeout, wait indefinitely.
        ///
        WAIT_FOREVER,

        ///
        /// Retry failed dependencies.
        ///
        RETRY
    }

    ///
    /// Exception thrown when barrier fails.
    ///
    class BarrierException extends Exception {
        private final String barrierId;
        private final BarrierState finalState;

        public BarrierException(String barrierId, String message, BarrierState finalState) {
            super(message);
            this.barrierId = barrierId;
            this.finalState = finalState;
        }

        public BarrierException(String barrierId, String message, Throwable cause, BarrierState finalState) {
            super(message, cause);
            this.barrierId = barrierId;
            this.finalState = finalState;
        }

        public String barrierId() {
            return barrierId;
        }

        public BarrierState finalState() {
            return finalState;
        }
    }
}
