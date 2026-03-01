/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nosqlbench.paramodel.mock.execution;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.execution.ExecutionStateManager;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.mock.persistence.MockCheckpointStore;
import io.nosqlbench.paramodel.mock.persistence.MockJournalStore;
import io.nosqlbench.paramodel.persistence.CheckpointStore;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Instant;
import java.util.*;

///
/// In-memory implementation of {@link ExecutionStateManager} for testing.
///
/// Uses {@link MockJournalStore} and {@link MockCheckpointStore} as backing
/// stores and exposes recorded events for test assertions.
///
/// ## Test Inspection
///
/// - {@link #recordedEvents()} returns all events recorded across all executions
/// - {@link #journalStore()} and {@link #checkpointStore()} expose the underlying stores
///
/// @since 0.1.0
///
public class MockExecutionStateManager implements ExecutionStateManager {

    private final MockJournalStore journalStore;
    private final MockCheckpointStore checkpointStore;
    private final List<JournalEvent> allRecordedEvents = new ArrayList<>();
    private final Map<String, TrialResult> trialResults = new LinkedHashMap<>();
    private final Map<String, Set<String>> executionTrialIds = new LinkedHashMap<>();

    /// Creates a new mock execution state manager with fresh backing stores.
    public MockExecutionStateManager() {
        this(new MockJournalStore(), new MockCheckpointStore());
    }

    /// Creates a new mock execution state manager with the given backing stores.
    ///
    /// @param journalStore the journal store to use; must not be null
    /// @param checkpointStore the checkpoint store to use; must not be null
    public MockExecutionStateManager(MockJournalStore journalStore, MockCheckpointStore checkpointStore) {
        this.journalStore = Objects.requireNonNull(journalStore, "journalStore must not be null");
        this.checkpointStore = Objects.requireNonNull(checkpointStore, "checkpointStore must not be null");
    }

    @Override
    public void recordEvent(JournalEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        journalStore.append(event);
        allRecordedEvents.add(event);
    }

    @Override
    public void checkpoint(Executor.Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        // Step 2: Persist checkpoint
        checkpointStore.saveCheckpoint(checkpoint);
        // Step 3: Truncate journal (find the CheckpointCreated event)
        long truncateSeq = findCheckpointSequence(
            checkpoint.executionPlanId(), checkpoint.checkpointId());
        if (truncateSeq > 0) {
            journalStore.truncateBefore(checkpoint.executionPlanId(), truncateSeq);
        }
    }

    @Override
    public RecoveryResult recover(String executionId, ExecutionPlan plan) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(plan, "plan must not be null");

        // Load latest checkpoint as base state
        Optional<Executor.Checkpoint> latestCheckpoint =
            checkpointStore.getLatestCheckpoint(plan.id());

        Set<String> completedStepIds = new LinkedHashSet<>();
        Set<String> failedStepIds = new LinkedHashSet<>();
        Set<String> skippedStepIds = new LinkedHashSet<>();
        Map<String, JournalEvent.StepStarted> inFlightSteps = new LinkedHashMap<>();
        Set<String> completedTrialIds = new LinkedHashSet<>();
        Set<String> inFlightTrialIds = new LinkedHashSet<>();
        Map<String, Element.OperationalState> elementStates = new LinkedHashMap<>();
        boolean wasCleanShutdown = false;

        // Init from checkpoint
        if (latestCheckpoint.isPresent()) {
            Executor.Checkpoint cp = latestCheckpoint.get();
            completedStepIds.addAll(cp.completedStepIds());
            completedTrialIds.addAll(cp.completedTrialIds());
        }

        // Find replay start point
        long replayAfterSequence = 0;
        if (latestCheckpoint.isPresent()) {
            replayAfterSequence = findCheckpointSequence(
                executionId, latestCheckpoint.get().checkpointId());
        }

        // Replay events
        List<JournalEvent> events = journalStore.replay(executionId, replayAfterSequence).toList();
        for (JournalEvent event : events) {
            wasCleanShutdown = false; // reset each iteration
            switch (event) {
                case JournalEvent.StepStarted e -> inFlightSteps.put(e.stepId(), e);
                case JournalEvent.StepCompleted e -> {
                    inFlightSteps.remove(e.stepId());
                    completedStepIds.add(e.stepId());
                }
                case JournalEvent.StepFailed e -> {
                    inFlightSteps.remove(e.stepId());
                    failedStepIds.add(e.stepId());
                }
                case JournalEvent.StepSkipped e -> {
                    inFlightSteps.remove(e.stepId());
                    skippedStepIds.add(e.stepId());
                }
                case JournalEvent.TrialStarting e -> inFlightTrialIds.add(e.trialId());
                case JournalEvent.TrialEnded e -> {
                    inFlightTrialIds.remove(e.trialId());
                    completedTrialIds.add(e.trialId());
                }
                case JournalEvent.ElementStateChanged e ->
                    elementStates.put(e.elementName(), e.toState());
                case JournalEvent.ExecutionSuspended e -> wasCleanShutdown = true;
                default -> { /* other events don't affect recovery state */ }
            }
        }

        // Resolve in-flight steps
        Map<String, InFlightResolution> resolutions = new LinkedHashMap<>();
        Instant now = Instant.now();
        Map<String, AtomicStep> stepLookup = new HashMap<>();
        for (AtomicStep step : plan.steps()) {
            stepLookup.put(step.id(), step);
        }

        for (Map.Entry<String, JournalEvent.StepStarted> entry : inFlightSteps.entrySet()) {
            String stepId = entry.getKey();
            JournalEvent.StepStarted started = entry.getValue();

            InFlightResolution resolution = resolveStep(
                stepId, started, wasCleanShutdown, stepLookup, now);
            resolutions.put(stepId, resolution);
        }

        return new RecoveryResult(
            completedStepIds, failedStepIds, skippedStepIds,
            inFlightSteps.keySet(), completedTrialIds, inFlightTrialIds,
            elementStates, wasCleanShutdown, resolutions);
    }

    @Override
    public boolean isStepCompleted(String executionId, String stepId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(stepId, "stepId must not be null");

        // Quick scan of journal events for step completion
        for (JournalEvent event : journalStore.allEvents(executionId)) {
            if (event instanceof JournalEvent.StepCompleted sc
                    && stepId.equals(sc.stepId())) {
                return true;
            }
        }
        // Also check checkpoints
        // Use executionId as a proxy for executionPlanId lookup
        return false;
    }

    @Override
    public void recordSuspension(String executionId, String executionPlanId, String reason) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(executionPlanId, "executionPlanId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        long nextSeq = journalStore.latestSequenceNumber(executionId) + 1;
        recordEvent(new JournalEvent.ExecutionSuspended(
            nextSeq, executionId, executionPlanId, Instant.now(), reason));
    }

    @Override
    public void saveTrialResult(String executionId, TrialResult result) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(result, "result must not be null");
        String trialId = result.trial().id();
        trialResults.put(trialId, result);
        executionTrialIds.computeIfAbsent(executionId, k -> new LinkedHashSet<>())
            .add(trialId);
    }

    @Override
    public Optional<TrialResult> getTrialResult(String trialId) {
        Objects.requireNonNull(trialId, "trialId must not be null");
        return Optional.ofNullable(trialResults.get(trialId));
    }

    @Override
    public List<TrialResult> getTrialResults(String executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Set<String> trialIds = executionTrialIds.getOrDefault(executionId, Set.of());
        List<TrialResult> results = new ArrayList<>();
        for (String trialId : trialIds) {
            TrialResult r = trialResults.get(trialId);
            if (r != null) {
                results.add(r);
            }
        }
        return List.copyOf(results);
    }

    @Override
    public void cleanup(String executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        journalStore.deleteAll(executionId);
        Set<String> trialIds = executionTrialIds.remove(executionId);
        if (trialIds != null) {
            for (String trialId : trialIds) {
                trialResults.remove(trialId);
            }
        }
    }

    /// Returns all events recorded through this manager, in order.
    ///
    /// @return unmodifiable list of all recorded events
    public List<JournalEvent> recordedEvents() {
        return Collections.unmodifiableList(allRecordedEvents);
    }

    /// Returns the underlying mock journal store.
    ///
    /// @return the journal store; never null
    public MockJournalStore journalStore() {
        return journalStore;
    }

    /// Returns the underlying mock checkpoint store.
    ///
    /// @return the checkpoint store; never null
    public MockCheckpointStore checkpointStore() {
        return checkpointStore;
    }

    private InFlightResolution resolveStep(
            String stepId,
            JournalEvent.StepStarted started,
            boolean wasCleanShutdown,
            Map<String, AtomicStep> stepLookup,
            Instant now) {

        // 1. Check deadline
        if (started.deadline().isPresent() && now.isAfter(started.deadline().get())) {
            return new InFlightResolution(stepId, ResolutionAction.TIMED_OUT,
                "Step deadline passed at " + started.deadline().get());
        }

        // 2. Check clean shutdown
        if (wasCleanShutdown) {
            return new InFlightResolution(stepId, ResolutionAction.RESUME,
                "Clean shutdown detected; step can be continued");
        }

        // 3. Check idempotent
        if (stepIdempotencyClass(started.stepType()) == IdempotencyClass.IDEMPOTENT) {
            return new InFlightResolution(stepId, ResolutionAction.RETRY,
                "Idempotent step type " + started.stepType() + "; safe to retry");
        }

        // 4. Otherwise fail
        return new InFlightResolution(stepId, ResolutionAction.FAIL,
            "Non-idempotent step type " + started.stepType()
                + " interrupted without clean shutdown");
    }

    private long findCheckpointSequence(String executionId, String checkpointId) {
        for (JournalEvent event : journalStore.allEvents(executionId)) {
            if (event instanceof JournalEvent.CheckpointCreated cc
                    && checkpointId.equals(cc.checkpointId())) {
                return cc.sequenceNumber();
            }
        }
        return 0;
    }
}
