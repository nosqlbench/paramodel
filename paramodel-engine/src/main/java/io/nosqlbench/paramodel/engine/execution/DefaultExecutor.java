package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.engine.CompactId;
import io.nosqlbench.paramodel.engine.execution.journal.DefaultInFlightStepResolver;
import io.nosqlbench.paramodel.engine.execution.journal.DefaultJournalStateReconstructor;
import io.nosqlbench.paramodel.engine.execution.journal.ExecutionSnapshot;
import io.nosqlbench.paramodel.engine.execution.journal.InFlightStepResolver;
import io.nosqlbench.paramodel.engine.execution.journal.JournalStateReconstructor;
import io.nosqlbench.paramodel.engine.execution.journal.JournalWriter;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.persistence.CheckpointStore;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.TrialResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

///
/// Default executor implementation with concurrent execution support.
///
/// This is a stub implementation that provides the basic structure
/// for executing plans. When fully implemented, this executor will:
///
/// ## Element State Observation
///
/// During the DEPLOYING phase, the executor registers
/// {@link io.nosqlbench.paramodel.elements.OperationalStateObservable.StateTransitionListener
/// state observers} on each deployed element. This enables event-driven
/// detection of element state changes (e.g. a command element completing,
/// or a service element failing unexpectedly) without polling.
///
/// ## Trial Lifecycle Notifications
///
/// During the EXECUTING phase, the executor delivers
/// {@link io.nosqlbench.paramodel.elements.TrialLifecycleParticipant trial lifecycle}
/// notifications to elements in dependency order:
///
/// - **onTrialStarting**: outermost → innermost
/// - **onTrialEnding**: innermost → outermost
///
/// ## Journal-Based Recovery
///
/// When a {@link JournalStore} is provided via the {@link Builder},
/// the executor emits {@link io.nosqlbench.paramodel.execution.journal.JournalEvent
/// journal events} at each lifecycle point (step start/complete/fail,
/// phase transitions, trial boundaries, element state changes,
/// checkpoints, and execution start/complete/suspend).
///
/// On resume, the executor uses a {@link JournalStateReconstructor}
/// to replay journal events from the last checkpoint, building an
/// {@link ExecutionSnapshot} that identifies completed, failed,
/// skipped, and in-flight work. An {@link InFlightStepResolver}
/// then deterministically resolves each interrupted step (retry,
/// fail, timeout, or resume).
///
/// ## Checkpoint and Rehydration
///
/// When resuming from a {@link Checkpoint}, the executor:
///
/// 1. Reconstructs element models from the checkpoint's
///    {@link Checkpoint#state() state map}
/// 2. Sets each element's operational state from persisted values
/// 3. Re-registers state observers on each element (which immediately
///    receive the current state via registration-as-catchup semantics)
/// 4. Resumes execution from the first incomplete trial
///
/// The rehydration process is idempotent: resuming with the same
/// checkpoint multiple times produces the same result.
///
/// ### Checkpoint-Journal Write Ordering
///
/// The {@link io.nosqlbench.paramodel.execution.journal.JournalEvent.CheckpointCreated
/// CheckpointCreated} event is written BEFORE the checkpoint itself.
/// If a crash occurs between them, the event exists but the checkpoint
/// does not, so recovery replays from the previous checkpoint — correct,
/// just slightly more replay.
///
/// ### Required Checkpoint State
///
/// For rehydration to succeed, the checkpoint state map must include:
///
/// - **Per-element operational state**: The
///   {@link io.nosqlbench.paramodel.elements.Element.OperationalState
///   OperationalState} of each deployed element at checkpoint time
/// - **Active trial context**: Which trial was in progress and the
///   element stack ordering
/// - **Element dependency graph**: Sufficient structure to reconstruct
///   notification ordering
///
/// @see io.nosqlbench.paramodel.elements.OperationalStateObservable
/// @see io.nosqlbench.paramodel.elements.TrialLifecycleParticipant
/// @see JournalWriter
/// @see JournalStateReconstructor
/// @see InFlightStepResolver
///
public class DefaultExecutor implements Executor {
    private static final Logger log = LoggerFactory.getLogger(DefaultExecutor.class);

    private final ExecutorConfig config;
    private final JournalStore journalStore;
    private final CheckpointStore checkpointStore;
    private final JournalStateReconstructor reconstructor;
    private final InFlightStepResolver stepResolver;

    /// Creates a new executor with the given configuration and optional journal support.
    ///
    /// @param config executor configuration
    /// @param journalStore journal store for durable event recording, or null to disable
    /// @param checkpointStore checkpoint store for state snapshots, or null to disable
    public DefaultExecutor(ExecutorConfig config, JournalStore journalStore,
                           CheckpointStore checkpointStore) {
        this.config = Objects.requireNonNull(config);
        this.journalStore = journalStore;
        this.checkpointStore = checkpointStore;
        this.reconstructor = new DefaultJournalStateReconstructor();
        this.stepResolver = new DefaultInFlightStepResolver();
    }

    @Override
    public ExecutionResult execute(ExecutionPlan plan) throws ExecutionFailedException {
        log.info("Starting execution of plan {}", plan.id());

        String executionId = CompactId.next();
        JournalWriter writer = createWriter(executionId, plan.id());
        if (writer != null) {
            writer.writeExecutionStarted(Optional.empty(), Map.of());
            writer.writePhaseTransition(ExecutionPhase.INITIALIZING, ExecutionPhase.DEPLOYING);
            writer.writePhaseTransition(ExecutionPhase.DEPLOYING, ExecutionPhase.EXECUTING);
            writer.writePhaseTransition(ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED);
            writer.writeExecutionCompleted(ExecutionPhase.COMPLETED, 0, 0);
        }

        // Stub implementation - would execute the plan
        return new StubExecutionResult(plan, executionId);
    }

    @Override
    public ExecutionHandle executeAsync(ExecutionPlan plan) {
        log.info("Starting async execution of plan {}", plan.id());

        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return execute(plan);
            } catch (ExecutionFailedException e) {
                throw new RuntimeException(e);
            }
        });

        return new StubExecutionHandle(plan, future);
    }

    @Override
    public ExecutionResult resume(ExecutionPlan plan, Checkpoint checkpoint)
            throws ExecutionFailedException {
        log.info("Resuming execution from checkpoint {}", checkpoint.checkpointId());

        String executionId = CompactId.next();

        // If journal is available, reconstruct state and resolve in-flight work
        if (journalStore != null && checkpointStore != null) {
            // Find the original execution ID from events if available
            String originalExecutionId = findOriginalExecutionId(plan);
            if (originalExecutionId != null) {
                ExecutionSnapshot snapshot = reconstructor.reconstruct(
                    originalExecutionId, plan, journalStore, checkpointStore);

                Map<String, InFlightStepResolver.StepResolution> resolutions =
                    stepResolver.resolve(snapshot, plan);

                log.info("Reconstructed state: {} completed steps, {} in-flight steps, {} resolutions",
                    snapshot.completedStepIds().size(),
                    snapshot.inFlightStepIds().size(),
                    resolutions.size());

                // Emit resolution events in the new execution's journal
                JournalWriter writer = createWriter(executionId, plan.id());
                if (writer != null) {
                    writer.writeExecutionStarted(
                        Optional.of(checkpoint.checkpointId()), Map.of());

                    for (InFlightStepResolver.StepResolution resolution : resolutions.values()) {
                        log.info("Resolved in-flight step {}: {} ({})",
                            resolution.stepId(), resolution.action(), resolution.reason());
                    }
                }
            }
        }

        // Stub: delegate to execute for now
        return new StubExecutionResult(plan, executionId);
    }

    @Override
    public Optional<Checkpoint> latestCheckpoint(ExecutionPlan plan) {
        return Optional.empty();
    }

    @Override
    public List<Checkpoint> checkpoints(ExecutionPlan plan) {
        return List.of();
    }

    @Override
    public ExecutorConfig config() {
        return config;
    }

    /// Creates a {@link JournalWriter} if a journal store is configured.
    private JournalWriter createWriter(String executionId, String executionPlanId) {
        if (journalStore == null) {
            return null;
        }
        return new JournalWriter(journalStore, executionId, executionPlanId);
    }

    /// Attempts to find the original execution ID from journal events
    /// associated with this plan.
    private String findOriginalExecutionId(ExecutionPlan plan) {
        if (checkpointStore == null) {
            return null;
        }
        Optional<Checkpoint> latest = checkpointStore.getLatestCheckpoint(plan.id());
        if (latest.isEmpty()) {
            return null;
        }
        // The checkpoint's execution plan ID can be used to search for events
        // In a real implementation, the execution ID would be stored in the
        // checkpoint state map. For now, we look for any events with this plan.
        return latest.get().executionPlanId();
    }

    /// Returns the journal store, or null if journaling is disabled.
    public JournalStore journalStore() {
        return journalStore;
    }

    /// Returns the checkpoint store, or null if checkpointing is disabled.
    public CheckpointStore checkpointStore() {
        return checkpointStore;
    }

    /// Creates a new builder for constructing {@link DefaultExecutor} instances.
    public static Builder builder() {
        return new Builder();
    }

    /// Builder for {@link DefaultExecutor} with optional journal and checkpoint support.
    public static class Builder {
        private ExecutorConfig config;
        private JournalStore journalStore;
        private CheckpointStore checkpointStore;

        /// Sets the executor configuration.
        public Builder config(ExecutorConfig config) {
            this.config = config;
            return this;
        }

        /// Sets the journal store for durable event recording.
        /// When present, the executor emits journal events at each
        /// lifecycle point and supports journal-based state reconstruction
        /// on resume.
        ///
        /// @param journalStore the journal store, or null to disable
        /// @return this builder
        public Builder journalStore(JournalStore journalStore) {
            this.journalStore = journalStore;
            return this;
        }

        /// Sets the checkpoint store for state snapshots.
        ///
        /// @param checkpointStore the checkpoint store, or null to disable
        /// @return this builder
        public Builder checkpointStore(CheckpointStore checkpointStore) {
            this.checkpointStore = checkpointStore;
            return this;
        }

        /// Builds the executor.
        public DefaultExecutor build() {
            if (config == null) {
                config = ExecutorConfig.builder().build();
            }
            return new DefaultExecutor(config, journalStore, checkpointStore);
        }
    }

    private static class StubExecutionResult implements ExecutionResult {
        private final ExecutionPlan plan;
        private final String executionId;
        private final Instant startedAt;
        private final Instant completedAt;

        StubExecutionResult(ExecutionPlan plan, String executionId) {
            this.plan = plan;
            this.executionId = executionId;
            this.startedAt = Instant.now();
            this.completedAt = Instant.now();
        }

        @Override
        public String executionId() {
            return executionId;
        }

        @Override
        public ExecutionPlan plan() {
            return plan;
        }

        @Override
        public ExecutionStatus finalStatus() {
            return new StubExecutionStatus(ExecutionPhase.COMPLETED);
        }

        @Override
        public Instant startedAt() {
            return startedAt;
        }

        @Override
        public Instant completedAt() {
            return completedAt;
        }

        @Override
        public Duration duration() {
            return Duration.between(startedAt, completedAt);
        }

        @Override
        public List<TrialResult> trialResults() {
            return List.of();
        }

        @Override
        public int totalTrialCount() {
            return 0;
        }

        @Override
        public int successfulTrialCount() {
            return 0;
        }

        @Override
        public int failedTrialCount() {
            return 0;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Optional<Throwable> error() {
            return Optional.empty();
        }

        @Override
        public ExecutionMetrics metrics() {
            return new StubExecutionMetrics();
        }

        @Override
        public double actualCost() {
            return 0.0;
        }

        @Override
        public Map<String, Object> metadata() {
            return Map.of();
        }
    }

    private static class StubExecutionHandle implements ExecutionHandle {
        private final ExecutionPlan plan;
        private final CompletableFuture<ExecutionResult> future;
        private final String executionId;

        StubExecutionHandle(ExecutionPlan plan, CompletableFuture<ExecutionResult> future) {
            this.plan = plan;
            this.future = future;
            this.executionId = CompactId.next();
        }

        @Override
        public String executionId() {
            return executionId;
        }

        @Override
        public ExecutionStatus status() {
            return future.isDone()
                ? new StubExecutionStatus(ExecutionPhase.COMPLETED)
                : new StubExecutionStatus(ExecutionPhase.EXECUTING);
        }

        @Override
        public CompletableFuture<ExecutionResult> future() {
            return future;
        }

        @Override
        public void pause() {
            // Stub
        }

        @Override
        public void resume() {
            // Stub
        }

        @Override
        public void cancel() {
            future.cancel(true);
        }

        @Override
        public boolean isPaused() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public ExecutionResult await() throws InterruptedException {
            try {
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ExecutionResult await(Duration timeout) throws InterruptedException {
            try {
                return future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onProgress(ProgressListener listener) {
            // Stub
        }

        @Override
        public void onTrialComplete(TrialCompleteListener listener) {
            // Stub
        }

        @Override
        public void onStepComplete(StepCompleteListener listener) {
            // Stub
        }
    }

    private static class StubExecutionStatus implements ExecutionStatus {
        private final ExecutionPhase phase;

        StubExecutionStatus(ExecutionPhase phase) {
            this.phase = phase;
        }

        @Override
        public ExecutionPhase phase() {
            return phase;
        }

        @Override
        public int completedTrials() {
            return 0;
        }

        @Override
        public int totalTrials() {
            return 0;
        }

        @Override
        public int completedSteps() {
            return 0;
        }

        @Override
        public int totalSteps() {
            return 0;
        }

        @Override
        public double progressPercentage() {
            return phase == ExecutionPhase.COMPLETED ? 100.0 : 0.0;
        }

        @Override
        public Optional<Duration> estimatedTimeRemaining() {
            return Optional.empty();
        }

        @Override
        public Map<String, Object> currentMetrics() {
            return Map.of();
        }
    }

    private static class StubExecutionMetrics implements ExecutionMetrics {
        @Override
        public double peakCpuUsage() {
            return 0.0;
        }

        @Override
        public double averageCpuUsage() {
            return 0.0;
        }

        @Override
        public double peakMemoryUsageGb() {
            return 0.0;
        }

        @Override
        public double averageMemoryUsageGb() {
            return 0.0;
        }

        @Override
        public long totalNetworkBytesTransferred() {
            return 0;
        }

        @Override
        public long totalStorageBytesWritten() {
            return 0;
        }

        @Override
        public Map<String, Double> customMetrics() {
            return Map.of();
        }
    }
}
