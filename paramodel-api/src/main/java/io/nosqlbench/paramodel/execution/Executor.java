package io.nosqlbench.paramodel.execution;

import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

///
/// # Executor
///
/// Executes an {@link ExecutionPlan}, orchestrating resource provisioning, trial execution,
/// result collection, and cleanup. The executor manages concurrency, handles failures,
/// enforces resource limits, and provides observability throughout execution.
///
/// ## Executor Responsibilities
///
/// The executor coordinates all aspects of execution:
///
/// ```
/// Executor Responsibilities:
///
/// Resource Management
///   ├─ Provision infrastructure (databases, services, etc.)
///   ├─ Monitor resource health
///   ├─ Enforce resource limits
///   └─ Cleanup on completion
///
/// Execution Control
///   ├─ Schedule steps respecting dependencies
///   ├─ Manage concurrency and parallelism
///   ├─ Handle retries and failures
///   └─ Support pause/resume/cancel
///
/// Result Collection
///   ├─ Capture trial results
///   ├─ Collect metrics and logs
///   ├─ Persist artifacts
///   └─ Generate execution report
///
/// Observability
///   ├─ Emit progress events
///   ├─ Track resource utilization
///   ├─ Report errors and warnings
///   └─ Provide real-time status
/// ```
///
/// ## Execution Lifecycle
///
/// Execution proceeds through well-defined phases:
///
/// ```
/// Execution Lifecycle:
///
/// INITIALIZING
///   ├─ Validate execution plan
///   ├─ Check resource availability
///   ├─ Initialize runtime state
///   └─ Prepare result storage
///   ↓
/// DEPLOYING
///   ├─ Execute DEPLOY_ELEMENT steps
///   ├─ Wait for health checks
///   ├─ Establish connectivity
///   └─ Verify element readiness
///   ↓
/// EXECUTING
///   ├─ Execute trial steps
///   ├─ Collect results
///   ├─ Handle failures/retries
///   └─ Update progress
///   ↓
/// TEARING_DOWN
///   ├─ Execute TEARDOWN_ELEMENT steps
///   ├─ Collect final artifacts
///   ├─ Release resources
///   └─ Clean up state
///   ↓
/// COMPLETED / FAILED / CANCELLED
///   ├─ Finalize results
///   ├─ Generate report
///   ├─ Persist execution record
///   └─ Notify observers
/// ```
///
/// ## Concurrency Management
///
/// The executor manages parallel execution within resource constraints:
///
/// ```
/// Concurrency Model:
///
/// Execution Plan: 100 trials, max 10 concurrent
///
/// Timeline:
///   t=0s:   Deploy shared elements
///   t=30s:  Elements ready
///   t=30s:  Start trials [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10]
///           (10 concurrent - at limit)
///   t=45s:  t3 completes → Start t11
///   t=52s:  t1 completes → Start t12
///   t=58s:  t7 completes → Start t13
///   ...
///   t=15m:  All 100 trials complete
///   t=15m:  Start teardown
///   t=16m:  Teardown complete → COMPLETED
///
/// Concurrency Control:
///   - Track active step count
///   - Queue pending steps
///   - Start steps when slots available
///   - Respect resource limits
/// ```
///
/// ## Failure Handling
///
/// The executor handles failures according to policies:
///
/// ```
/// Failure Scenarios:
///
/// Trial Failure (with retry):
///   trial_42 fails with transient error
///   ↓
///   Retry policy: 3 attempts, exponential backoff
///   ↓
///   Attempt 2 after 1s → Still fails
///   ↓
///   Attempt 3 after 2s → Success
///   ↓
///   Continue execution
///
/// Element Deployment Failure:
///   cache deployment fails
///   ↓
///   Policy: FAIL_FAST
///   ↓
///   Cancel dependent trials
///   ↓
///   Teardown successfully deployed elements
///   ↓
///   FAILED state
///
/// Partial Execution:
///   80/100 trials complete, infrastructure fails
///   ↓
///   Policy: CHECKPOINT_AND_RESUME
///   ↓
///   Save checkpoint with 80 results
///   ↓
///   User can resume later to run remaining 20
/// ```
///
/// ## Checkpointing and Recovery
///
/// The executor supports incremental execution:
///
/// ```
/// Checkpoint Strategy:
///
/// Execution with checkpoints every 10 trials:
///
///   t=0:     Start execution
///   t=5m:    10 trials complete → CHECKPOINT_1
///   t=10m:   20 trials complete → CHECKPOINT_2
///   t=15m:   30 trials complete → CHECKPOINT_3
///   t=18m:   CRASH
///
/// Recovery:
///   Load CHECKPOINT_3
///   ↓
///   Resume from trial 31
///   ↓
///   Continue execution
///   ↓
///   Complete remaining 70 trials
///
/// Checkpoint Contents:
///   - Completed trial IDs
///   - Trial results
///   - Element instance states
///   - Execution metadata
///   - Progress metrics
/// ```
///
/// ## Resource Limits
///
/// The executor enforces resource constraints:
///
/// ```
/// Resource Limit Enforcement:
///
/// Configured Limits:
///   CPU: 16 cores
///   Memory: 64 GB
///   Storage: 200 GB
///
/// Admission Control:
///   Pending step requires 4 cores, 8 GB
///   ↓
///   Current usage: 14 cores, 58 GB
///   ↓
///   Available: 2 cores, 6 GB
///   ↓
///   Insufficient resources → Queue step
///   ↓
///   Wait for resources to free
///   ↓
///   Step completes, releases 6 cores, 12 GB
///   ↓
///   Available: 8 cores, 18 GB
///   ↓
///   Sufficient resources → Start queued step
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Basic Execution
///
/// ```java
/// ExecutionPlan plan = testPlan.commit();
/// Executor executor = Executor.create();
///
/// ExecutionResult result = executor.execute(plan);
///
/// if (result.isSuccess()) {
///     System.out.printf("Execution completed: %d/%d trials successful%n",
///         result.successfulTrialCount(),
///         result.totalTrialCount());
///
///     System.out.printf("Duration: %s%n", result.duration());
///     System.out.printf("Cost: $%.2f%n", result.actualCost());
/// } else {
///     System.err.printf("Execution failed: %s%n",
///         result.error().getMessage());
/// }
/// ```
///
/// ### Example 2: Execution with Progress Tracking
///
/// ```java
/// Executor executor = Executor.create();
///
/// ExecutionHandle handle = executor.executeAsync(plan);
///
/// handle.onProgress(event -> {
///     System.out.printf("[%s] %s: %d/%d trials complete%n",
///         event.timestamp(),
///         event.phase(),
///         event.completedTrials(),
///         event.totalTrials());
/// });
///
/// handle.onTrialComplete(result -> {
///     System.out.printf("Trial %s: %s (%.2fs)%n",
///         result.trial().id(),
///         result.status(),
///         result.timing().duration().toMillis() / 1000.0);
/// });
///
/// // Wait for completion
/// ExecutionResult result = handle.await();
/// ```
///
/// ### Example 3: Execution with Resource Limits
///
/// ```java
/// ExecutorConfig config = ExecutorConfig.builder()
///     .maxConcurrentTrials(10)
///     .maxCpu(16.0)
///     .maxMemoryGb(64.0)
///     .maxStorageGb(200.0)
///     .build();
///
/// Executor executor = Executor.create(config);
///
/// ExecutionResult result = executor.execute(plan);
///
/// System.out.printf("Peak resource usage:%n");
/// System.out.printf("  CPU: %.1f cores%n",
///     result.metrics().peakCpuUsage());
/// System.out.printf("  Memory: %.1f GB%n",
///     result.metrics().peakMemoryUsageGb());
/// ```
///
/// ### Example 4: Execution with Checkpointing
///
/// ```java
/// ExecutorConfig config = ExecutorConfig.builder()
///     .checkpointInterval(Duration.ofMinutes(10))
///     .checkpointOnBarriers(true)
///     .build();
///
/// Executor executor = Executor.create(config);
///
/// try {
///     ExecutionResult result = executor.execute(plan);
/// } catch (ExecutionFailedException e) {
///     System.err.println("Execution failed, but checkpoint saved");
///
///     // Resume from last checkpoint
///     Optional<Checkpoint> checkpoint = executor.latestCheckpoint(plan);
///     if (checkpoint.isPresent()) {
///         System.out.println("Resuming from checkpoint...");
///         ExecutionResult resumed = executor.resume(plan, checkpoint.get());
///     }
/// }
/// ```
///
/// ### Example 5: Execution Control
///
/// ```java
/// Executor executor = Executor.create();
/// ExecutionHandle handle = executor.executeAsync(plan);
///
/// // Monitor in separate thread
/// new Thread(() -> {
///     while (!handle.isDone()) {
///         ExecutionStatus status = handle.status();
///         System.out.printf("Progress: %.1f%%%n",
///             status.progressPercentage());
///
///         if (userRequestsPause()) {
///             handle.pause();
///             System.out.println("Execution paused");
///             waitForUserResume();
///             handle.resume();
///         }
///
///         if (userRequestsCancel()) {
///             handle.cancel();
///             System.out.println("Execution cancelled");
///             break;
///         }
///
///         Thread.sleep(1000);
///     }
/// }).start();
///
/// ExecutionResult result = handle.await();
/// ```
///
/// ## Contract Requirements
///
/// ### Correctness
/// - Executor MUST respect execution plan dependencies
/// - Executor MUST enforce resource limits
/// - Executor MUST preserve result integrity
///
/// ### Fault Tolerance
/// - Executor MUST handle transient failures gracefully
/// - Executor MUST support checkpoint/resume
/// - Executor MUST clean up resources on failure
///
/// ### Observability
/// - Executor MUST emit progress events
/// - Executor MUST track resource utilization
/// - Executor MUST provide execution status
///
/// ### Performance
/// - Executor SHOULD maximize parallelism within constraints
/// - Executor SHOULD minimize overhead
/// - Executor SHOULD support cancellation
///
/// @see ExecutionPlan
/// @see ExecutionResult
/// @see ExecutionHandle
/// @see ExecutorConfig
///
public interface Executor {

    ///
    /// Creates an executor with default configuration.
    ///
    /// @return Executor instance
    ///
    static Executor create() {
        throw new UnsupportedOperationException(
            "Executor.create() requires a concrete implementation");
    }

    ///
    /// Creates an executor with specified configuration.
    ///
    /// @param config Executor configuration
    /// @return Executor instance
    ///
    static Executor create(ExecutorConfig config) {
        throw new UnsupportedOperationException(
            "Executor.create(config) requires a concrete implementation");
    }

    ///
    /// Executes an execution plan synchronously.
    ///
    /// Blocks until execution completes or fails.
    ///
    /// @param plan Execution plan to run
    /// @return Execution result
    /// @throws ExecutionFailedException if execution fails
    ///
    ExecutionResult execute(ExecutionPlan plan) throws ExecutionFailedException;

    ///
    /// Executes an execution plan asynchronously.
    ///
    /// Returns immediately with a handle for monitoring and control.
    ///
    /// @param plan Execution plan to run
    /// @return Execution handle
    ///
    ExecutionHandle executeAsync(ExecutionPlan plan);

    ///
    /// Resumes execution from a checkpoint.
    ///
    /// @param plan Execution plan
    /// @param checkpoint Checkpoint to resume from
    /// @return Execution result
    /// @throws ExecutionFailedException if execution fails
    ///
    ExecutionResult resume(ExecutionPlan plan, Checkpoint checkpoint)
        throws ExecutionFailedException;

    ///
    /// Returns the latest checkpoint for a plan.
    ///
    /// @param plan Execution plan
    /// @return Latest checkpoint if exists
    ///
    Optional<Checkpoint> latestCheckpoint(ExecutionPlan plan);

    ///
    /// Returns all checkpoints for a plan.
    ///
    /// @param plan Execution plan
    /// @return All checkpoints in chronological order
    ///
    List<Checkpoint> checkpoints(ExecutionPlan plan);

    ///
    /// Returns the executor configuration.
    ///
    /// @return Executor configuration
    ///
    ExecutorConfig config();

    ///
    /// Execution result.
    ///
    interface ExecutionResult {
        String executionId();
        ExecutionPlan plan();
        ExecutionStatus finalStatus();
        Instant startedAt();
        Instant completedAt();
        Duration duration();
        List<TrialResult> trialResults();
        int totalTrialCount();
        int successfulTrialCount();
        int failedTrialCount();
        boolean isSuccess();
        Optional<Throwable> error();
        ExecutionMetrics metrics();
        double actualCost();
        Map<String, Object> metadata();
    }

    ///
    /// Execution handle for async execution.
    ///
    interface ExecutionHandle {
        String executionId();
        ExecutionStatus status();
        CompletableFuture<ExecutionResult> future();
        void pause();
        void resume();
        void cancel();
        boolean isPaused();
        boolean isCancelled();
        boolean isDone();
        ExecutionResult await() throws InterruptedException;
        ExecutionResult await(Duration timeout) throws InterruptedException;
        void onProgress(ProgressListener listener);
        void onTrialComplete(TrialCompleteListener listener);
        void onStepComplete(StepCompleteListener listener);
    }

    ///
    /// Execution status.
    ///
    interface ExecutionStatus {
        ExecutionPhase phase();
        int completedTrials();
        int totalTrials();
        int completedSteps();
        int totalSteps();
        double progressPercentage();
        Optional<Duration> estimatedTimeRemaining();
        Map<String, Object> currentMetrics();
    }

    ///
    /// Execution phase.
    ///
    enum ExecutionPhase {
        INITIALIZING,
        DEPLOYING,
        EXECUTING,
        TEARING_DOWN,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    ///
    /// Execution metrics.
    ///
    interface ExecutionMetrics {
        double peakCpuUsage();
        double averageCpuUsage();
        double peakMemoryUsageGb();
        double averageMemoryUsageGb();
        long totalNetworkBytesTransferred();
        long totalStorageBytesWritten();
        Map<String, Double> customMetrics();
    }

    ///
    /// Checkpoint for resumable execution.
    ///
    interface Checkpoint {
        String checkpointId();
        String executionPlanId();
        Instant createdAt();
        List<String> completedTrialIds();
        List<String> completedStepIds();
        Map<String, Object> state();
    }

    ///
    /// Progress listener.
    ///
    @FunctionalInterface
    interface ProgressListener {
        void onProgress(ProgressEvent event);
    }

    ///
    /// Progress event.
    ///
    interface ProgressEvent {
        Instant timestamp();
        ExecutionPhase phase();
        int completedTrials();
        int totalTrials();
        double progressPercentage();
    }

    ///
    /// Trial completion listener.
    ///
    @FunctionalInterface
    interface TrialCompleteListener {
        void onTrialComplete(TrialResult result);
    }

    ///
    /// Step completion listener.
    ///
    @FunctionalInterface
    interface StepCompleteListener {
        void onStepComplete(AtomicStep step, Duration duration);
    }

    ///
    /// Executor configuration.
    ///
    interface ExecutorConfig {
        int maxConcurrentTrials();
        double maxCpu();
        double maxMemoryGb();
        double maxStorageGb();
        Optional<Duration> checkpointInterval();
        boolean checkpointOnBarriers();
        Map<String, Object> customConfig();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "ExecutorConfig.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder maxConcurrentTrials(int max);
            Builder maxCpu(double cpu);
            Builder maxMemoryGb(double memoryGb);
            Builder maxStorageGb(double storageGb);
            Builder checkpointInterval(Duration interval);
            Builder checkpointOnBarriers(boolean enabled);
            Builder customConfig(String key, Object value);
            ExecutorConfig build();
        }
    }

    ///
    /// Exception thrown when execution fails.
    ///
    class ExecutionFailedException extends Exception {
        private final String executionId;
        private final ExecutionPhase failedPhase;

        public ExecutionFailedException(String executionId, ExecutionPhase failedPhase,
                                        String message) {
            super(message);
            this.executionId = executionId;
            this.failedPhase = failedPhase;
        }

        public ExecutionFailedException(String executionId, ExecutionPhase failedPhase,
                                        String message, Throwable cause) {
            super(message, cause);
            this.executionId = executionId;
            this.failedPhase = failedPhase;
        }

        public String executionId() {
            return executionId;
        }

        public ExecutionPhase failedPhase() {
            return failedPhase;
        }
    }
}
