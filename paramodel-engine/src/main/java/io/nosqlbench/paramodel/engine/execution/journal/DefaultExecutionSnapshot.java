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
package io.nosqlbench.paramodel.engine.execution.journal;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.journal.JournalEvent;

import java.time.Instant;
import java.util.*;

///
/// Mutable builder that processes journal events into an immutable
/// {@link ExecutionSnapshot}.
///
/// Each {@code apply*} method handles one event type, updating internal
/// state accordingly. After all events have been applied, the snapshot
/// methods return the accumulated state.
///
/// @see JournalStateReconstructor
/// @since 0.1.0
///
public class DefaultExecutionSnapshot implements ExecutionSnapshot {

    private String executionId;
    private String executionPlanId;
    private Executor.ExecutionPhase currentPhase = Executor.ExecutionPhase.INITIALIZING;

    private final Set<String> completedStepIds = new LinkedHashSet<>();
    private final Set<String> failedStepIds = new LinkedHashSet<>();
    private final Set<String> skippedStepIds = new LinkedHashSet<>();
    private final Map<String, JournalEvent.StepStarted> inFlightSteps = new LinkedHashMap<>();

    private final Set<String> completedTrialIds = new LinkedHashSet<>();
    private final Set<String> inFlightTrialIds = new LinkedHashSet<>();

    private final Map<String, Element.OperationalState> elementStates = new LinkedHashMap<>();

    private long lastSequenceNumber;
    private String baseCheckpointId;
    private boolean wasCleanShutdown;
    private Instant lastEventTimestamp = Instant.EPOCH;

    /// Creates a new empty snapshot builder.
    public DefaultExecutionSnapshot() {}

    /// Initializes the snapshot from a checkpoint's base state.
    ///
    /// @param checkpoint the checkpoint to use as base
    public void initFromCheckpoint(Executor.Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        this.executionPlanId = checkpoint.executionPlanId();
        this.baseCheckpointId = checkpoint.checkpointId();
        this.completedStepIds.addAll(checkpoint.completedStepIds());
        this.completedTrialIds.addAll(checkpoint.completedTrialIds());
    }

    /// Applies a journal event to this snapshot, updating internal state.
    ///
    /// @param event the event to apply
    public void apply(JournalEvent event) {
        this.lastSequenceNumber = event.sequenceNumber();
        this.lastEventTimestamp = event.timestamp();
        if (this.executionId == null) {
            this.executionId = event.executionId();
        }
        if (this.executionPlanId == null) {
            this.executionPlanId = event.executionPlanId();
        }

        // Reset clean shutdown flag — only set by ExecutionSuspended
        this.wasCleanShutdown = false;

        switch (event) {
            case JournalEvent.ExecutionStarted e -> applyExecutionStarted(e);
            case JournalEvent.PhaseTransition e -> applyPhaseTransition(e);
            case JournalEvent.StepStarted e -> applyStepStarted(e);
            case JournalEvent.StepCompleted e -> applyStepCompleted(e);
            case JournalEvent.StepFailed e -> applyStepFailed(e);
            case JournalEvent.StepRetrying e -> applyStepRetrying(e);
            case JournalEvent.StepSkipped e -> applyStepSkipped(e);
            case JournalEvent.ElementStateChanged e -> applyElementStateChanged(e);
            case JournalEvent.TrialStarting e -> applyTrialStarting(e);
            case JournalEvent.TrialEnded e -> applyTrialEnded(e);
            case JournalEvent.CheckpointCreated e -> applyCheckpointCreated(e);
            case JournalEvent.ExecutionSuspended e -> applyExecutionSuspended(e);
            case JournalEvent.ExecutionCompleted e -> applyExecutionCompleted(e);
        }
    }

    private void applyExecutionStarted(JournalEvent.ExecutionStarted e) {
        this.executionId = e.executionId();
        this.executionPlanId = e.executionPlanId();
        e.resumedFromCheckpointId().ifPresent(id -> this.baseCheckpointId = id);
    }

    private void applyPhaseTransition(JournalEvent.PhaseTransition e) {
        this.currentPhase = e.toPhase();
    }

    private void applyStepStarted(JournalEvent.StepStarted e) {
        inFlightSteps.put(e.stepId(), e);
    }

    private void applyStepCompleted(JournalEvent.StepCompleted e) {
        inFlightSteps.remove(e.stepId());
        completedStepIds.add(e.stepId());
    }

    private void applyStepFailed(JournalEvent.StepFailed e) {
        inFlightSteps.remove(e.stepId());
        failedStepIds.add(e.stepId());
    }

    private void applyStepRetrying(JournalEvent.StepRetrying e) {
        // Step stays in-flight during retry; the next StepStarted
        // will replace the entry with updated info
    }

    private void applyStepSkipped(JournalEvent.StepSkipped e) {
        inFlightSteps.remove(e.stepId());
        skippedStepIds.add(e.stepId());
    }

    private void applyElementStateChanged(JournalEvent.ElementStateChanged e) {
        elementStates.put(e.elementName(), e.toState());
    }

    private void applyTrialStarting(JournalEvent.TrialStarting e) {
        inFlightTrialIds.add(e.trialId());
    }

    private void applyTrialEnded(JournalEvent.TrialEnded e) {
        inFlightTrialIds.remove(e.trialId());
        completedTrialIds.add(e.trialId());
    }

    private void applyCheckpointCreated(JournalEvent.CheckpointCreated e) {
        this.baseCheckpointId = e.checkpointId();
    }

    private void applyExecutionSuspended(JournalEvent.ExecutionSuspended e) {
        this.wasCleanShutdown = true;
    }

    private void applyExecutionCompleted(JournalEvent.ExecutionCompleted e) {
        this.currentPhase = e.finalPhase();
    }

    // -----------------------------------------------------------------------
    // ExecutionSnapshot interface
    // -----------------------------------------------------------------------

    @Override
    public String executionId() {
        return executionId;
    }

    @Override
    public String executionPlanId() {
        return executionPlanId;
    }

    @Override
    public Executor.ExecutionPhase currentPhase() {
        return currentPhase;
    }

    @Override
    public Set<String> completedStepIds() {
        return Collections.unmodifiableSet(completedStepIds);
    }

    @Override
    public Set<String> failedStepIds() {
        return Collections.unmodifiableSet(failedStepIds);
    }

    @Override
    public Set<String> skippedStepIds() {
        return Collections.unmodifiableSet(skippedStepIds);
    }

    @Override
    public Set<String> inFlightStepIds() {
        return Collections.unmodifiableSet(inFlightSteps.keySet());
    }

    @Override
    public Set<String> completedTrialIds() {
        return Collections.unmodifiableSet(completedTrialIds);
    }

    @Override
    public Set<String> inFlightTrialIds() {
        return Collections.unmodifiableSet(inFlightTrialIds);
    }

    @Override
    public Map<String, Element.OperationalState> elementStates() {
        return Collections.unmodifiableMap(elementStates);
    }

    @Override
    public Map<String, JournalEvent.StepStarted> inFlightStepDetails() {
        return Collections.unmodifiableMap(inFlightSteps);
    }

    @Override
    public long lastSequenceNumber() {
        return lastSequenceNumber;
    }

    @Override
    public Optional<String> baseCheckpointId() {
        return Optional.ofNullable(baseCheckpointId);
    }

    @Override
    public boolean wasCleanShutdown() {
        return wasCleanShutdown;
    }

    @Override
    public Instant lastEventTimestamp() {
        return lastEventTimestamp;
    }
}
