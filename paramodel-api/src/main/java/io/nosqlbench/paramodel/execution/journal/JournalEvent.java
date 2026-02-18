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
package io.nosqlbench.paramodel.execution.journal;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.sequence.TrialStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

///
/// Sealed event hierarchy for the execution event journal.
///
/// Each event carries a monotonically increasing {@link #sequenceNumber()},
/// an {@link #executionId()}, an {@link #executionPlanId()}, and a
/// {@link #timestamp()}. Together these fields provide the total ordering
/// and provenance needed for deterministic state reconstruction.
///
/// ## Event Types
///
/// | Event | Purpose |
/// |-------|---------|
/// | {@link ExecutionStarted} | Establishes execution identity |
/// | {@link PhaseTransition} | Tracks execution phase changes |
/// | {@link StepStarted} | Marks a step as in-flight |
/// | {@link StepCompleted} | Records step completion |
/// | {@link StepFailed} | Records step failure |
/// | {@link StepRetrying} | Tracks retry state |
/// | {@link StepSkipped} | Records deliberate skips |
/// | {@link ElementStateChanged} | Tracks element state transitions |
/// | {@link TrialStarting} | Marks a trial as in-flight |
/// | {@link TrialEnded} | Records trial outcome |
/// | {@link CheckpointCreated} | Marks compaction boundary |
/// | {@link ExecutionSuspended} | Distinguishes clean pause from crash |
/// | {@link ExecutionCompleted} | Terminal marker |
///
/// ## Reconstruction Semantics
///
/// On restart, events are replayed from the last checkpoint to reconstruct
/// execution state. The key invariant is: a {@link StepStarted} without a
/// matching {@link StepCompleted} or {@link StepFailed} indicates
/// interrupted work. The resolution strategy depends on:
///
/// 1. **Deadline**: if {@link StepStarted#deadline()} is present and has
///    passed, the step is resolved as `TIMED_OUT`.
/// 2. **Clean shutdown**: if an {@link ExecutionSuspended} event is the
///    last event, in-flight steps are resolved as `RESUME`.
/// 3. **Idempotency**: steps of type `DEPLOY_ELEMENT` or `TRIAL_STEP`
///    are idempotent and resolved as `RETRY`.
/// 4. **Otherwise**: the step is resolved as `FAIL`.
///
/// ## Serialization Guide for JournalStore Implementors
///
/// All 13 subtypes are Java records — plain data carriers with no
/// behavior. Host systems implementing
/// {@link io.nosqlbench.paramodel.persistence.JournalStore JournalStore}
/// must serialize and deserialize these records faithfully.
///
/// ### Field types and their serialization
///
/// | Java type | Serialization guidance |
/// |-----------|----------------------|
/// | `long` (sequenceNumber) | Positive integer, starting at 1 |
/// | `String` | UTF-8 string, never null |
/// | `Instant` | ISO-8601 timestamp (e.g., `2024-01-15T10:30:00Z`) |
/// | `Duration` | ISO-8601 duration (e.g., `PT5S`) or milliseconds |
/// | `Optional<T>` | Null-or-value in JSON; presence flag in binary |
/// | `Map<String, Object>` | JSON object; values are strings, numbers, booleans, or nested maps |
/// | `enum` types | Serialize by name (e.g., `"DEPLOY_ELEMENT"`) |
/// | `boolean` | Standard boolean |
/// | `int` | Standard integer (attempt numbers are 1-based) |
///
/// ### Type discriminator
///
/// Use the simple class name (e.g., `"StepStarted"`, `"TrialEnded"`)
/// as a type discriminator field during serialization. Since the
/// interface is sealed, the set of subtypes is fixed and exhaustive.
///
/// ### Enum types referenced by events
///
/// - {@link io.nosqlbench.paramodel.plan.AtomicStep.StepType StepType}:
///   `DEPLOY_ELEMENT`, `TRIAL_STEP`, `TEARDOWN_ELEMENT`,
///   `BARRIER_SYNC`, `CHECKPOINT_STATE`
/// - {@link io.nosqlbench.paramodel.execution.Executor.ExecutionPhase
///   ExecutionPhase}: `INITIALIZING`, `DEPLOYING`, `EXECUTING`,
///   `TEARING_DOWN`, `COMPLETED`, `FAILED`, `SUSPENDED`
/// - {@link io.nosqlbench.paramodel.elements.Element.OperationalState
///   OperationalState}: `INACTIVE`, `PROVISIONING`, `READY`,
///   `RUNNING`, `STOPPING`, `STOPPED`, `FAILED`, `DEPROVISIONING`,
///   `DEPROVISIONED`
/// - {@link io.nosqlbench.paramodel.sequence.TrialStatus TrialStatus}:
///   `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `SKIPPED`,
///   `TIMED_OUT`, `CANCELLED`
///
/// ### Defensive copies
///
/// The record compact constructors make defensive copies of mutable
/// fields ({@code Map.copyOf}). Deserialized events will also be
/// immutable as long as the maps are constructed with {@code Map.of}
/// or {@code Map.copyOf}.
///
/// @see io.nosqlbench.paramodel.persistence.JournalStore
/// @since 0.1.0
///
public sealed interface JournalEvent
    permits JournalEvent.ExecutionStarted,
            JournalEvent.PhaseTransition,
            JournalEvent.StepStarted,
            JournalEvent.StepCompleted,
            JournalEvent.StepFailed,
            JournalEvent.StepRetrying,
            JournalEvent.StepSkipped,
            JournalEvent.ElementStateChanged,
            JournalEvent.TrialStarting,
            JournalEvent.TrialEnded,
            JournalEvent.CheckpointCreated,
            JournalEvent.ExecutionSuspended,
            JournalEvent.ExecutionCompleted {

    /// Returns the monotonically increasing, gap-free sequence number
    /// within this execution (starts at 1).
    long sequenceNumber();

    /// Returns the execution run identifier.
    String executionId();

    /// Returns the execution plan identifier.
    String executionPlanId();

    /// Returns the instant this event occurred.
    Instant timestamp();

    /// Establishes execution identity; marks fresh vs resumed execution.
    ///
    /// This is always the first event written for an execution (sequence
    /// number 1 for a fresh start, or the first sequence after the last
    /// known event for a resumed execution). The
    /// {@code resumedFromCheckpointId} field distinguishes fresh
    /// executions from resumed ones — during reconstruction, this tells
    /// the engine which checkpoint to load as base state.
    ///
    /// The {@code configuration} map captures the execution configuration
    /// at the time of launch, enabling audit and diagnostics. Host
    /// systems should serialize this map as an opaque JSON object.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param resumedFromCheckpointId checkpoint ID if resuming, empty if fresh start
    /// @param configuration execution configuration snapshot
    record ExecutionStarted(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        Optional<String> resumedFromCheckpointId,
        Map<String, Object> configuration
    ) implements JournalEvent {
        public ExecutionStarted {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(resumedFromCheckpointId, "resumedFromCheckpointId must not be null");
            Objects.requireNonNull(configuration, "configuration must not be null");
            configuration = Map.copyOf(configuration);
        }
    }

    /// Records a transition between execution phases.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param fromPhase the phase being left
    /// @param toPhase the phase being entered
    record PhaseTransition(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        Executor.ExecutionPhase fromPhase,
        Executor.ExecutionPhase toPhase
    ) implements JournalEvent {
        public PhaseTransition {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(fromPhase, "fromPhase must not be null");
            Objects.requireNonNull(toPhase, "toPhase must not be null");
        }
    }

    /// Marks a step as in-flight. A {@code StepStarted} without a
    /// matching {@link StepCompleted} or {@link StepFailed} indicates
    /// interrupted work on reconstruction.
    ///
    /// The {@code deadline} field is critical for stuck-state prevention.
    /// When present, it specifies an absolute time after which the step
    /// should be considered timed out during reconstruction. This
    /// prevents an interrupted step from being retried indefinitely if
    /// the underlying operation has a natural time limit (e.g., a
    /// deployment with a 5-minute timeout). Host systems must serialize
    /// the deadline as an ISO-8601 instant.
    ///
    /// The {@code stepType} determines the idempotency of the step
    /// during resolution:
    /// - {@code DEPLOY_ELEMENT} and {@code TRIAL_STEP} are
    ///   considered idempotent and safe to retry.
    /// - {@code BARRIER_SYNC}, {@code CHECKPOINT_STATE}, and
    ///   {@code TEARDOWN_ELEMENT} are non-idempotent and will be
    ///   resolved as `FAIL` if interrupted without a clean shutdown.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param stepId step identifier
    /// @param stepType the type of atomic step
    /// @param deadline optional absolute deadline; if present and in the
    ///        past during reconstruction, the step is resolved as timed out
    record StepStarted(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String stepId,
        AtomicStep.StepType stepType,
        Optional<Instant> deadline
    ) implements JournalEvent {
        public StepStarted {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(stepId, "stepId must not be null");
            Objects.requireNonNull(stepType, "stepType must not be null");
            Objects.requireNonNull(deadline, "deadline must not be null");
        }
    }

    /// Records successful completion of a step.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param stepId step identifier
    /// @param stepType the type of atomic step
    /// @param duration how long the step took
    /// @param outputs step output map
    record StepCompleted(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String stepId,
        AtomicStep.StepType stepType,
        Duration duration,
        Map<String, Object> outputs
    ) implements JournalEvent {
        public StepCompleted {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(stepId, "stepId must not be null");
            Objects.requireNonNull(stepType, "stepType must not be null");
            Objects.requireNonNull(duration, "duration must not be null");
            Objects.requireNonNull(outputs, "outputs must not be null");
            outputs = Map.copyOf(outputs);
        }
    }

    /// Records step failure, distinguishing transient from permanent errors.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param stepId step identifier
    /// @param stepType the type of atomic step
    /// @param errorType error class name or category
    /// @param errorMessage human-readable error description
    /// @param isTransient whether the error is transient (retryable)
    /// @param attemptNumber which attempt this failure represents (1-based)
    record StepFailed(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String stepId,
        AtomicStep.StepType stepType,
        String errorType,
        String errorMessage,
        boolean isTransient,
        int attemptNumber
    ) implements JournalEvent {
        public StepFailed {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(stepId, "stepId must not be null");
            Objects.requireNonNull(stepType, "stepType must not be null");
            Objects.requireNonNull(errorType, "errorType must not be null");
            Objects.requireNonNull(errorMessage, "errorMessage must not be null");
        }
    }

    /// Records that a step is being retried after a transient failure.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param stepId step identifier
    /// @param attemptNumber the upcoming attempt number (1-based)
    /// @param backoffDuration how long to wait before retrying
    record StepRetrying(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String stepId,
        int attemptNumber,
        Duration backoffDuration
    ) implements JournalEvent {
        public StepRetrying {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(stepId, "stepId must not be null");
            Objects.requireNonNull(backoffDuration, "backoffDuration must not be null");
        }
    }

    /// Records that a step was deliberately skipped.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param stepId step identifier
    /// @param reason why the step was skipped
    record StepSkipped(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String stepId,
        String reason
    ) implements JournalEvent {
        public StepSkipped {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(stepId, "stepId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /// Records an element operational state transition.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param elementName element name
    /// @param fromState previous operational state
    /// @param toState new operational state
    /// @param summary human-readable summary of the transition
    record ElementStateChanged(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String elementName,
        Element.OperationalState fromState,
        Element.OperationalState toState,
        String summary
    ) implements JournalEvent {
        public ElementStateChanged {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(elementName, "elementName must not be null");
            Objects.requireNonNull(fromState, "fromState must not be null");
            Objects.requireNonNull(toState, "toState must not be null");
            Objects.requireNonNull(summary, "summary must not be null");
        }
    }

    /// Marks a trial as in-flight.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param trialId trial identifier
    /// @param stepId the step that started this trial
    record TrialStarting(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String trialId,
        String stepId
    ) implements JournalEvent {
        public TrialStarting {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(trialId, "trialId must not be null");
            Objects.requireNonNull(stepId, "stepId must not be null");
        }
    }

    /// Records trial completion with its outcome.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param trialId trial identifier
    /// @param stepId the step that ran this trial
    /// @param outcome terminal trial status
    record TrialEnded(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String trialId,
        String stepId,
        TrialStatus outcome
    ) implements JournalEvent {
        public TrialEnded {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(trialId, "trialId must not be null");
            Objects.requireNonNull(stepId, "stepId must not be null");
            Objects.requireNonNull(outcome, "outcome must not be null");
        }
    }

    /// Marks a compaction boundary linking journal to checkpoint.
    ///
    /// **Write ordering is critical**: this event is written to the
    /// journal **before** the actual checkpoint is written to
    /// {@link io.nosqlbench.paramodel.persistence.CheckpointStore
    /// CheckpointStore}. This ordering ensures safe recovery:
    ///
    /// - If a crash occurs after this event but before the checkpoint
    ///   is written: the event exists but the checkpoint does not, so
    ///   recovery replays from the previous checkpoint — correct, just
    ///   slightly more replay.
    /// - The reverse ordering (checkpoint first, then event) could lose
    ///   track of the compaction boundary, potentially causing replay
    ///   to start from the wrong point.
    ///
    /// During reconstruction, the engine scans for the
    /// {@code CheckpointCreated} event matching the latest checkpoint's
    /// ID, then replays events after that sequence number.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param checkpointId the checkpoint identifier, matching the ID
    ///        in {@link io.nosqlbench.paramodel.persistence.CheckpointStore
    ///        CheckpointStore}
    record CheckpointCreated(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String checkpointId
    ) implements JournalEvent {
        public CheckpointCreated {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(checkpointId, "checkpointId must not be null");
        }
    }

    /// Distinguishes a clean pause from a crash. When present as the
    /// last event in the journal, reconstruction knows the shutdown was
    /// orderly and all in-flight steps can be resolved as `RESUME`
    /// rather than `RETRY` or `FAIL`.
    ///
    /// If this event is **not** the last event (i.e., the journal ends
    /// with a {@link StepStarted} or other mid-operation event), the
    /// engine infers that the process crashed and applies the
    /// crash-recovery resolution strategy.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param reason why execution was suspended (e.g., "User requested
    ///        pause", "Shutdown signal received")
    record ExecutionSuspended(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        String reason
    ) implements JournalEvent {
        public ExecutionSuspended {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /// Terminal marker for a finished execution.
    ///
    /// @param sequenceNumber monotonically increasing sequence number
    /// @param executionId execution run identifier
    /// @param executionPlanId execution plan identifier
    /// @param timestamp when the event occurred
    /// @param finalPhase the terminal execution phase
    /// @param completedTrialCount number of trials that completed
    /// @param totalTrialCount total number of trials in the plan
    record ExecutionCompleted(
        long sequenceNumber,
        String executionId,
        String executionPlanId,
        Instant timestamp,
        Executor.ExecutionPhase finalPhase,
        int completedTrialCount,
        int totalTrialCount
    ) implements JournalEvent {
        public ExecutionCompleted {
            requireCommonFields(sequenceNumber, executionId, executionPlanId, timestamp);
            Objects.requireNonNull(finalPhase, "finalPhase must not be null");
        }
    }

    /// Validates the common fields shared by all journal events.
    private static void requireCommonFields(long sequenceNumber, String executionId,
                                            String executionPlanId, Instant timestamp) {
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be >= 1, was " + sequenceNumber);
        }
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(executionPlanId, "executionPlanId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
