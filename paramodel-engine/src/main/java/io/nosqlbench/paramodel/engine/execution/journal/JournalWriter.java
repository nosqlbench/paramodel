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
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.sequence.TrialStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

///
/// Manages sequence numbering and provides typed write methods for
/// each journal event type. Thread-safe via synchronized append.
///
/// ## Usage
///
/// Create a {@code JournalWriter} for each execution, passing the
/// execution identity and a {@link JournalStore}. The writer
/// auto-increments sequence numbers starting from 1 (or from a
/// specified resume point).
///
/// ```java
/// JournalWriter writer = new JournalWriter(store, "exec-1", "plan-1");
/// writer.writeExecutionStarted(Optional.empty(), Map.of());
/// writer.writePhaseTransition(INITIALIZING, DEPLOYING);
/// writer.writeStepStarted("step-1", StepType.DEPLOY_ELEMENT, Optional.empty());
/// ```
///
/// @see JournalStore
/// @since 0.1.0
///
public class JournalWriter {

    private final JournalStore store;
    private final String executionId;
    private final String executionPlanId;
    private long nextSequence;

    /// Creates a writer starting at sequence number 1.
    ///
    /// @param store the journal store to write to
    /// @param executionId the execution identifier
    /// @param executionPlanId the execution plan identifier
    public JournalWriter(JournalStore store, String executionId, String executionPlanId) {
        this(store, executionId, executionPlanId, 1);
    }

    /// Creates a writer starting at a specified sequence number.
    /// Used when resuming to continue from the last known sequence.
    ///
    /// @param store the journal store to write to
    /// @param executionId the execution identifier
    /// @param executionPlanId the execution plan identifier
    /// @param startSequence the first sequence number to use
    public JournalWriter(JournalStore store, String executionId,
                         String executionPlanId, long startSequence) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.executionId = Objects.requireNonNull(executionId, "executionId must not be null");
        this.executionPlanId = Objects.requireNonNull(executionPlanId, "executionPlanId must not be null");
        if (startSequence < 1) {
            throw new IllegalArgumentException("startSequence must be >= 1, was " + startSequence);
        }
        this.nextSequence = startSequence;
    }

    /// Returns the current sequence number (the next one that will be used).
    public synchronized long currentSequence() {
        return nextSequence;
    }

    /// Writes an {@link JournalEvent.ExecutionStarted} event.
    public synchronized void writeExecutionStarted(
            Optional<String> resumedFromCheckpointId,
            Map<String, Object> configuration) {
        store.append(new JournalEvent.ExecutionStarted(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            resumedFromCheckpointId, configuration));
    }

    /// Writes a {@link JournalEvent.PhaseTransition} event.
    public synchronized void writePhaseTransition(
            Executor.ExecutionPhase fromPhase,
            Executor.ExecutionPhase toPhase) {
        store.append(new JournalEvent.PhaseTransition(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            fromPhase, toPhase));
    }

    /// Writes a {@link JournalEvent.StepStarted} event.
    public synchronized void writeStepStarted(
            String stepId,
            AtomicStep.StepType stepType,
            Optional<Instant> deadline) {
        store.append(new JournalEvent.StepStarted(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            stepId, stepType, deadline));
    }

    /// Writes a {@link JournalEvent.StepCompleted} event.
    public synchronized void writeStepCompleted(
            String stepId,
            AtomicStep.StepType stepType,
            Duration duration,
            Map<String, Object> outputs) {
        store.append(new JournalEvent.StepCompleted(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            stepId, stepType, duration, outputs));
    }

    /// Writes a {@link JournalEvent.StepFailed} event.
    public synchronized void writeStepFailed(
            String stepId,
            AtomicStep.StepType stepType,
            String errorType,
            String errorMessage,
            boolean isTransient,
            int attemptNumber) {
        store.append(new JournalEvent.StepFailed(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            stepId, stepType, errorType, errorMessage, isTransient, attemptNumber));
    }

    /// Writes a {@link JournalEvent.StepRetrying} event.
    public synchronized void writeStepRetrying(
            String stepId,
            int attemptNumber,
            Duration backoffDuration) {
        store.append(new JournalEvent.StepRetrying(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            stepId, attemptNumber, backoffDuration));
    }

    /// Writes a {@link JournalEvent.StepSkipped} event.
    public synchronized void writeStepSkipped(String stepId, String reason) {
        store.append(new JournalEvent.StepSkipped(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            stepId, reason));
    }

    /// Writes a {@link JournalEvent.ElementStateChanged} event.
    public synchronized void writeElementStateChanged(
            String elementName,
            Element.OperationalState fromState,
            Element.OperationalState toState,
            String summary) {
        store.append(new JournalEvent.ElementStateChanged(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            elementName, fromState, toState, summary));
    }

    /// Writes a {@link JournalEvent.TrialStarting} event.
    public synchronized void writeTrialStarting(String trialId, String stepId) {
        store.append(new JournalEvent.TrialStarting(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            trialId, stepId));
    }

    /// Writes a {@link JournalEvent.TrialEnded} event.
    public synchronized void writeTrialEnded(
            String trialId,
            String stepId,
            TrialStatus outcome) {
        store.append(new JournalEvent.TrialEnded(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            trialId, stepId, outcome));
    }

    /// Writes a {@link JournalEvent.CheckpointCreated} event.
    public synchronized void writeCheckpointCreated(String checkpointId) {
        store.append(new JournalEvent.CheckpointCreated(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            checkpointId));
    }

    /// Writes a {@link JournalEvent.ExecutionSuspended} event.
    public synchronized void writeExecutionSuspended(String reason) {
        store.append(new JournalEvent.ExecutionSuspended(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            reason));
    }

    /// Writes a {@link JournalEvent.ExecutionCompleted} event.
    public synchronized void writeExecutionCompleted(
            Executor.ExecutionPhase finalPhase,
            int completedTrialCount,
            int totalTrialCount) {
        store.append(new JournalEvent.ExecutionCompleted(
            nextSequence++, executionId, executionPlanId, Instant.now(),
            finalPhase, completedTrialCount, totalTrialCount));
    }
}
