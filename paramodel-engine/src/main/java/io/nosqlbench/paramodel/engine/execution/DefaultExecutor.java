package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.CompactId;
import io.nosqlbench.paramodel.engine.compiler.DefaultLiveElementGraph;
import io.nosqlbench.paramodel.engine.compiler.DefaultLiveExecutionGraph;
import io.nosqlbench.paramodel.engine.execution.journal.DefaultInFlightStepResolver;
import io.nosqlbench.paramodel.engine.execution.journal.DefaultJournalStateReconstructor;
import io.nosqlbench.paramodel.engine.execution.journal.ExecutionSnapshot;
import io.nosqlbench.paramodel.engine.execution.journal.InFlightStepResolver;
import io.nosqlbench.paramodel.engine.execution.journal.JournalStateReconstructor;
import io.nosqlbench.paramodel.engine.execution.journal.JournalWriter;
import io.nosqlbench.paramodel.execution.ExecutionStateManager;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.persistence.CheckpointStore;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.persistence.ResultStore;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.ExecutionState;
import io.nosqlbench.paramodel.plan.ImmutableExecutionState;
import io.nosqlbench.paramodel.plan.LiveElementGraph;
import io.nosqlbench.paramodel.plan.LiveExecutionGraph;
import io.nosqlbench.paramodel.plan.StepStatus;
import io.nosqlbench.paramodel.sequence.TrialResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
    private final ExecutionStateManager stateManager;

    /// Creates a new executor with the given configuration and optional journal support.
    ///
    /// @param config executor configuration
    /// @param journalStore journal store for durable event recording, or null to disable
    /// @param checkpointStore checkpoint store for state snapshots, or null to disable
    /// @param resultStore result store for trial result persistence, or null to disable
    public DefaultExecutor(ExecutorConfig config, JournalStore journalStore,
                           CheckpointStore checkpointStore, ResultStore resultStore) {
        this.config = Objects.requireNonNull(config);
        this.journalStore = journalStore;
        this.checkpointStore = checkpointStore;
        this.reconstructor = new DefaultJournalStateReconstructor();
        this.stepResolver = new DefaultInFlightStepResolver();
        // Construct DefaultExecutionStateManager if stores are available; noop otherwise
        if (journalStore != null && checkpointStore != null && resultStore != null) {
            this.stateManager = new DefaultExecutionStateManager(journalStore, checkpointStore, resultStore);
        } else {
            this.stateManager = ExecutionStateManager.noop();
        }
    }

    /// Creates a new executor with an explicit {@link ExecutionStateManager}.
    ///
    /// When an {@code ExecutionStateManager} is provided directly, it is used
    /// for all state management operations. The journal and checkpoint stores
    /// are still accepted for backward compatibility with code that accesses
    /// them directly.
    ///
    /// @param config executor configuration
    /// @param stateManager the execution state manager; must not be null
    /// @param journalStore journal store, or null if managed by stateManager
    /// @param checkpointStore checkpoint store, or null if managed by stateManager
    public DefaultExecutor(ExecutorConfig config, ExecutionStateManager stateManager,
                           JournalStore journalStore, CheckpointStore checkpointStore) {
        this.config = Objects.requireNonNull(config);
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager must not be null");
        this.journalStore = journalStore;
        this.checkpointStore = checkpointStore;
        this.reconstructor = new DefaultJournalStateReconstructor();
        this.stepResolver = new DefaultInFlightStepResolver();
    }

    /// Returns the {@link ExecutionStateManager} used by this executor.
    ///
    /// @return the execution state manager; never null
    public ExecutionStateManager stateManager() {
        return stateManager;
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

    @Override
    public SteppingHandle executeStepping(ExecutionPlan plan) {
        return executeStepping(plan, 0);
    }

    @Override
    public SteppingHandle executeStepping(ExecutionPlan plan, int initialPermits) {
        log.info("Starting stepping session for plan {} with {} initial permits",
            plan.id(), initialPermits);
        return new DefaultSteppingHandle(plan, initialPermits);
    }

    /// Asynchronous stepping handle backed by a semaphore and a background
    /// daemon thread.
    ///
    /// The background thread loops: acquire a permit, pick the first frontier
    /// step, execute it, update internal state, recompute the
    /// {@link LiveExecutionGraph}, and enqueue a {@link StepOutcome}. Callers
    /// control pacing via {@link #advance(int)} and observe results via
    /// {@link #awaitNextOutcome()}.
    private final class DefaultSteppingHandle implements SteppingHandle {

        private final ExecutionPlan plan;
        private final ExecutionGraph graph;
        private final List<AtomicStep> topoOrder;
        private final Set<String> completedStepIds = new LinkedHashSet<>();
        private final Set<String> failedStepIds = new LinkedHashSet<>();
        private final Set<String> skippedStepIds = new LinkedHashSet<>();
        private final Set<String> inFlightStepIds = new LinkedHashSet<>();
        private final Set<String> completedTrialIds = new LinkedHashSet<>();
        private final Set<String> inFlightTrialIds = new LinkedHashSet<>();
        private final Map<String, Element.OperationalState> elementStates = new LinkedHashMap<>();

        private final Semaphore semaphore;
        private final LinkedBlockingQueue<StepOutcome> outcomeQueue = new LinkedBlockingQueue<>();
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        private final Thread backgroundThread;

        private volatile LiveExecutionGraph currentLiveGraph;
        private volatile LiveElementGraph currentLiveElementGraph;
        private volatile boolean cancelled;

        DefaultSteppingHandle(ExecutionPlan plan, int initialPermits) {
            this.plan = plan;
            this.graph = plan.executionGraph();
            this.topoOrder = graph.topologicalSort();
            this.semaphore = new Semaphore(initialPermits);
            this.currentLiveGraph = recomputeLiveGraph();
            this.currentLiveElementGraph = recomputeLiveElementGraph();

            this.backgroundThread = Thread.ofPlatform()
                .daemon(true)
                .name("stepping-" + plan.id())
                .start(this::executionLoop);
        }

        @Override
        public LiveExecutionGraph liveGraph() {
            return currentLiveGraph;
        }

        @Override
        public LiveElementGraph liveElementGraph() {
            return currentLiveElementGraph;
        }

        @Override
        public ExecutionState currentState() {
            return buildState();
        }

        @Override
        public List<AtomicStep> frontier() {
            LiveExecutionGraph snap = currentLiveGraph;
            Set<AtomicStep> frontierSet = snap.frontier();
            List<AtomicStep> ordered = new ArrayList<>();
            for (AtomicStep step : topoOrder) {
                if (frontierSet.contains(step)) {
                    ordered.add(step);
                }
            }
            return List.copyOf(ordered);
        }

        @Override
        public boolean isComplete() {
            return currentLiveGraph.isComplete();
        }

        @Override
        public void advance(int permits) {
            semaphore.release(permits);
        }

        @Override
        public Optional<StepOutcome> awaitNextOutcome() {
            try {
                if (isComplete() && outcomeQueue.isEmpty()) {
                    return Optional.empty();
                }
                StepOutcome outcome = outcomeQueue.take();
                return Optional.of(outcome);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        @Override
        public Optional<StepOutcome> awaitNextOutcome(Duration timeout) {
            try {
                StepOutcome outcome = outcomeQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
                return Optional.ofNullable(outcome);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        @Override
        public void awaitCompletion() throws InterruptedException {
            completionLatch.await();
        }

        @Override
        public boolean awaitCompletion(Duration timeout) throws InterruptedException {
            return completionLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void cancel() {
            cancelled = true;
            backgroundThread.interrupt();
            completionLatch.countDown();
        }

        /// Background execution loop: acquires permits and executes frontier
        /// steps until the session is complete or cancelled.
        private void executionLoop() {
            try {
                while (!cancelled && !isComplete()) {
                    semaphore.acquire();
                    if (cancelled) break;

                    List<AtomicStep> currentFrontier = frontier();
                    if (currentFrontier.isEmpty()) {
                        if (isComplete()) break;
                        // No work available but not complete — release permit
                        // and yield so state can change (e.g. another thread
                        // completing a step may unblock new frontier steps).
                        semaphore.release();
                        Thread.yield();
                        continue;
                    }

                    AtomicStep step = currentFrontier.getFirst();
                    StepOutcome outcome = executeStep(step);
                    outcomeQueue.put(outcome);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completionLatch.countDown();
            }
        }

        /// Executes a single step synchronously, updates internal state, and
        /// recomputes the live graph.
        private synchronized StepOutcome executeStep(AtomicStep step) {
            inFlightStepIds.add(step.id());
            if (step instanceof AtomicStep.TrialStep ts) {
                inFlightTrialIds.add(ts.trialId());
            }
            currentLiveGraph = recomputeLiveGraph();
            currentLiveElementGraph = recomputeLiveElementGraph();

            Instant start = Instant.now();
            StepStatus resultStatus;
            try {
                step.execute(null);
                resultStatus = StepStatus.COMPLETED;
                completedStepIds.add(step.id());
                if (step instanceof AtomicStep.TrialStep ts) {
                    completedTrialIds.add(ts.trialId());
                    inFlightTrialIds.remove(ts.trialId());
                }
            } catch (Exception e) {
                resultStatus = StepStatus.FAILED;
                failedStepIds.add(step.id());
                if (step instanceof AtomicStep.TrialStep ts) {
                    inFlightTrialIds.remove(ts.trialId());
                }
            } finally {
                inFlightStepIds.remove(step.id());
            }

            Duration elapsed = Duration.between(start, Instant.now());
            currentLiveGraph = recomputeLiveGraph();
            currentLiveElementGraph = recomputeLiveElementGraph();
            return new StepOutcome(step, resultStatus, currentLiveGraph,
                currentLiveElementGraph, elapsed);
        }

        private LiveExecutionGraph recomputeLiveGraph() {
            return DefaultLiveExecutionGraph.create(graph, buildState());
        }

        private LiveElementGraph recomputeLiveElementGraph() {
            return DefaultLiveElementGraph.create(plan, buildState());
        }

        private ExecutionState buildState() {
            return new ImmutableExecutionState(
                Set.copyOf(completedStepIds),
                Set.copyOf(failedStepIds),
                Set.copyOf(skippedStepIds),
                Set.copyOf(inFlightStepIds),
                Set.copyOf(completedTrialIds),
                Set.copyOf(inFlightTrialIds),
                Map.copyOf(elementStates)
            );
        }
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

    /// Builder for {@link DefaultExecutor} with optional journal, checkpoint,
    /// and result store support.
    public static class Builder {
        private ExecutorConfig config;
        private JournalStore journalStore;
        private CheckpointStore checkpointStore;
        private ResultStore resultStore;
        private ExecutionStateManager stateManager;

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

        /// Sets the result store for trial result persistence.
        ///
        /// @param resultStore the result store, or null to disable
        /// @return this builder
        public Builder resultStore(ResultStore resultStore) {
            this.resultStore = resultStore;
            return this;
        }

        /// Sets the execution state manager.
        ///
        /// When provided, this manager is used for all state management
        /// operations. If not provided, a {@link DefaultExecutionStateManager}
        /// is constructed from the journal and checkpoint stores (if both
        /// are present), or a no-op manager is used.
        ///
        /// @param stateManager the execution state manager
        /// @return this builder
        public Builder stateManager(ExecutionStateManager stateManager) {
            this.stateManager = stateManager;
            return this;
        }

        /// Builds the executor.
        public DefaultExecutor build() {
            if (config == null) {
                config = new DefaultExecutorConfig();
            }
            if (stateManager != null) {
                return new DefaultExecutor(config, stateManager, journalStore, checkpointStore);
            }
            return new DefaultExecutor(config, journalStore, checkpointStore, resultStore);
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

    /// Default executor configuration with sensible defaults.
    private static class DefaultExecutorConfig implements ExecutorConfig {
        @Override public int maxConcurrentTrials() { return 10; }
        @Override public double maxCpu() { return 16.0; }
        @Override public double maxMemoryGb() { return 64.0; }
        @Override public double maxStorageGb() { return 200.0; }
        @Override public Optional<Duration> checkpointInterval() { return Optional.empty(); }
        @Override public boolean checkpointOnBarriers() { return false; }
        @Override public Map<String, Object> customConfig() { return Map.of(); }
    }
}
