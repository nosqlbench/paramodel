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
package io.nosqlbench.paramodel.execution;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

///
/// # ExecutionStateManager
///
/// Unified contract for durable execution state management, providing
/// resumability and idempotency across system restarts.
///
/// When an external system embeds paramodel and uses its execution engine,
/// this interface is the single contract it needs to implement for state
/// management. It unifies the lower-level {@link io.nosqlbench.paramodel.persistence.JournalStore
/// JournalStore} and {@link io.nosqlbench.paramodel.persistence.CheckpointStore
/// CheckpointStore} SPIs behind a higher-level abstraction focused on
/// the operations the executor actually needs.
///
/// ## Core Operations
///
/// | Operation | Purpose |
/// |-----------|---------|
/// | {@link #recordEvent} | Durably persists a journal event |
/// | {@link #checkpoint} | Atomic checkpoint protocol: journal → checkpoint → truncate |
/// | {@link #recover} | Reconstructs execution state from persisted data |
/// | {@link #isStepCompleted} | Fast-path idempotency check for a single step |
/// | {@link #recordSuspension} | Records a clean shutdown sentinel event |
/// | {@link #saveTrialResult} | Durably persists a trial result |
/// | {@link #getTrialResult} | Retrieves a trial result by trial ID |
/// | {@link #getTrialResults} | Retrieves all trial results for an execution |
/// | {@link #cleanup} | Removes all persisted state for a completed execution |
///
/// ## Resumability Contract
///
/// After a crash or planned restart, calling {@link #recover} with the
/// same execution ID and plan must reconstruct the exact state at the
/// point of interruption. The returned {@link RecoveryResult} provides:
///
/// - Which steps/trials completed, failed, or were skipped
/// - Which steps were in-flight (started but not completed)
/// - How each in-flight step should be resolved (RETRY, FAIL, TIMED_OUT, RESUME)
/// - Element operational states at the time of interruption
///
/// ## Idempotency Classification
///
/// The {@link #stepIdempotencyClass} method determines whether a step type
/// is safe to re-execute after an interrupted attempt. Embedders can override
/// this method to apply domain-specific knowledge about their step types.
///
/// ## Testing
///
/// Implementations should be validated using the
/// {@code ExecutionStateManagerTCK} conformance test suite, which verifies
/// event recording, checkpoint protocol, crash recovery, clean shutdown
/// recovery, idempotency classification, and cleanup.
///
/// ## No-Op Implementation
///
/// For testing or dry-run scenarios, use {@link #noop()} to obtain an
/// implementation where all writes are discarded, recovery returns empty
/// state, and idempotency checks return false.
///
/// @see io.nosqlbench.paramodel.persistence.JournalStore
/// @see io.nosqlbench.paramodel.persistence.CheckpointStore
/// @since 0.1.0
///
public interface ExecutionStateManager {

    /// Durably records a journal event.
    ///
    /// After this method returns, the event must survive process crashes.
    /// The implementation must maintain contiguous sequence numbering
    /// per execution.
    ///
    /// @param event the event to record; must not be null
    /// @throws StateRecordingException if the event cannot be durably persisted
    void recordEvent(JournalEvent event);

    /// Executes the atomic checkpoint protocol.
    ///
    /// The protocol is:
    /// 1. Write a {@link JournalEvent.CheckpointCreated} journal event
    /// 2. Persist the checkpoint to durable storage
    /// 3. Truncate the journal up to the checkpoint event's sequence number
    ///
    /// If a crash occurs between steps 1 and 2, recovery replays from the
    /// previous checkpoint (safe, just slightly more replay). The checkpoint
    /// event written in step 1 acts as a sentinel.
    ///
    /// @param checkpoint the checkpoint to persist; must not be null
    /// @throws StateRecordingException if any step of the protocol fails
    void checkpoint(Executor.Checkpoint checkpoint);

    /// Reconstructs execution state from persisted journal and checkpoint data.
    ///
    /// Loads the latest checkpoint (if any) as base state, then replays
    /// journal events after that checkpoint to reconstruct the complete
    /// picture. In-flight steps (started but never completed) are resolved
    /// according to the idempotency classification and shutdown context.
    ///
    /// @param executionId the execution run identifier; must not be null
    /// @param plan the execution plan providing step metadata; must not be null
    /// @return the reconstructed recovery result; never null
    RecoveryResult recover(String executionId, ExecutionPlan plan);

    /// Fast-path idempotency check for a single step.
    ///
    /// Returns {@code true} if the step has already been completed for the
    /// given execution. This allows the executor to skip already-completed
    /// steps without performing a full recovery.
    ///
    /// @param executionId the execution run identifier; must not be null
    /// @param stepId the step identifier to check; must not be null
    /// @return {@code true} if the step has been completed
    boolean isStepCompleted(String executionId, String stepId);

    /// Records a clean shutdown sentinel event.
    ///
    /// When the executor performs a graceful suspension, this method writes
    /// an {@link JournalEvent.ExecutionSuspended} event. During recovery,
    /// the presence of this event as the last journal event indicates that
    /// the shutdown was orderly and in-flight steps can be resolved as
    /// RESUME rather than RETRY or FAIL.
    ///
    /// @param executionId the execution run identifier; must not be null
    /// @param executionPlanId the execution plan identifier; must not be null
    /// @param reason why execution was suspended; must not be null
    /// @throws StateRecordingException if the event cannot be durably persisted
    void recordSuspension(String executionId, String executionPlanId, String reason);

    /// Durably persists a trial result.
    ///
    /// After this method returns, the result must survive process crashes.
    /// If a result already exists for the same trial ID, it is overwritten.
    ///
    /// @param executionId the execution run identifier; must not be null
    /// @param result the trial result to persist; must not be null
    /// @throws StateRecordingException if the result cannot be durably persisted
    void saveTrialResult(String executionId, TrialResult result);

    /// Retrieves a trial result by trial ID.
    ///
    /// @param trialId the trial identifier; must not be null
    /// @return the trial result if found, empty otherwise
    Optional<TrialResult> getTrialResult(String trialId);

    /// Retrieves all trial results for a given execution.
    ///
    /// @param executionId the execution run identifier; must not be null
    /// @return all trial results for the execution; never null, may be empty
    List<TrialResult> getTrialResults(String executionId);

    /// Cleans up all persisted state for an execution after it completes.
    ///
    /// Removes all journal events, checkpoints, and trial results associated
    /// with the given execution. After this call, {@link #recover} will return
    /// empty state, {@link #isStepCompleted} will return false for any step,
    /// and {@link #getTrialResults} will return an empty list.
    ///
    /// @param executionId the execution to clean up; must not be null
    void cleanup(String executionId);

    /// Returns the idempotency classification for a step type.
    ///
    /// The default classification treats {@code DEPLOY_ELEMENT} and
    /// {@code TRIAL_STEP} as idempotent (safe to re-execute), and all
    /// other step types as non-idempotent.
    ///
    /// Embedders may override this method to apply domain-specific
    /// knowledge about their step types.
    ///
    /// @param stepType the step type to classify; must not be null
    /// @return the idempotency classification; never null
    default IdempotencyClass stepIdempotencyClass(AtomicStep.StepType stepType) {
        return switch (stepType) {
            case DEPLOY_ELEMENT, TRIAL_STEP -> IdempotencyClass.IDEMPOTENT;
            case BARRIER_SYNC, CHECKPOINT_STATE, TEARDOWN_ELEMENT,
                 NOTIFY_TRIAL_START, NOTIFY_TRIAL_END, AWAIT_ELEMENT -> IdempotencyClass.NON_IDEMPOTENT;
        };
    }

    /// Returns a no-op implementation suitable for testing or dry-run scenarios.
    ///
    /// All writes are discarded, recovery returns empty state, and
    /// idempotency checks return false.
    ///
    /// @return a no-op execution state manager; never null
    static ExecutionStateManager noop() {
        return NoopExecutionStateManager.INSTANCE;
    }

    // ── Inner types ──

    /// Reconstructed execution state from persisted journal and checkpoint data.
    ///
    /// This is a plain data carrier that avoids exposing mutable engine-level
    /// interfaces in the API module.
    ///
    /// @param completedStepIds IDs of steps that completed successfully
    /// @param failedStepIds IDs of steps that failed permanently
    /// @param skippedStepIds IDs of steps that were deliberately skipped
    /// @param inFlightStepIds IDs of steps that were started but never completed
    /// @param completedTrialIds IDs of trials that completed
    /// @param inFlightTrialIds IDs of trials that were started but never completed
    /// @param elementStates operational state of each element at interruption
    /// @param wasCleanShutdown whether the interruption was orderly
    /// @param inFlightResolutions resolution action for each in-flight step
    record RecoveryResult(
        Set<String> completedStepIds,
        Set<String> failedStepIds,
        Set<String> skippedStepIds,
        Set<String> inFlightStepIds,
        Set<String> completedTrialIds,
        Set<String> inFlightTrialIds,
        Map<String, Element.OperationalState> elementStates,
        boolean wasCleanShutdown,
        Map<String, InFlightResolution> inFlightResolutions
    ) {
        /// Creates a recovery result with defensive copies.
        public RecoveryResult {
            completedStepIds = Set.copyOf(completedStepIds);
            failedStepIds = Set.copyOf(failedStepIds);
            skippedStepIds = Set.copyOf(skippedStepIds);
            inFlightStepIds = Set.copyOf(inFlightStepIds);
            completedTrialIds = Set.copyOf(completedTrialIds);
            inFlightTrialIds = Set.copyOf(inFlightTrialIds);
            elementStates = Map.copyOf(elementStates);
            inFlightResolutions = Map.copyOf(inFlightResolutions);
        }

        /// Returns an empty recovery result representing no prior state.
        public static RecoveryResult empty() {
            return new RecoveryResult(
                Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Map.of(), false, Map.of());
        }
    }

    /// Describes how an in-flight step should be resolved after recovery.
    ///
    /// @param stepId the step identifier
    /// @param action the resolution action to take
    /// @param reason human-readable explanation of the resolution
    record InFlightResolution(
        String stepId,
        ResolutionAction action,
        String reason
    ) {}

    /// Classification of step types by idempotency.
    enum IdempotencyClass {
        /// Safe to re-execute after an interrupted attempt.
        IDEMPOTENT,
        /// Not safe to re-execute; must fail or be manually resolved.
        NON_IDEMPOTENT
    }

    /// Actions for resolving in-flight steps after crash or clean shutdown.
    enum ResolutionAction {
        /// Idempotent step with retries remaining; re-execute from scratch.
        RETRY,
        /// Non-idempotent step or retries exhausted; mark as failed.
        FAIL,
        /// Step deadline has passed.
        TIMED_OUT,
        /// Clean shutdown; step can be continued from where it left off.
        RESUME
    }

    /// Exception thrown when a state recording operation fails.
    ///
    /// This indicates that a journal event or checkpoint could not be
    /// durably persisted. The engine treats this as a fatal error for
    /// the current execution.
    class StateRecordingException extends RuntimeException {
        /// Creates a state recording exception.
        ///
        /// @param message description of the recording failure
        public StateRecordingException(String message) {
            super(message);
        }

        /// Creates a state recording exception with a cause.
        ///
        /// @param message description of the recording failure
        /// @param cause the underlying exception
        public StateRecordingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
