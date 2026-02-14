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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

///
/// Reconstructed execution state derived from replaying journal events
/// since the last checkpoint.
///
/// An {@code ExecutionSnapshot} provides the complete picture of an
/// execution's state at the moment it was interrupted, including:
///
/// - **Completed work**: steps and trials that finished successfully,
///   which should be skipped during resume
/// - **Failed work**: steps that failed permanently, which determine
///   whether the execution can continue
/// - **In-flight work**: steps and trials that were started but never
///   completed — the key indicator of interrupted work
/// - **Element states**: the operational state of each element
///   (e.g., RUNNING, STOPPED), needed to resume element management
/// - **Execution phase**: where in the lifecycle the execution was
///   interrupted
/// - **Clean shutdown flag**: whether the interruption was orderly
///   (via {@link JournalEvent.ExecutionSuspended}) or a crash
///
/// ## In-Flight Detection
///
/// Steps in {@link #inFlightStepIds()} were started (a
/// {@link JournalEvent.StepStarted} event exists) but never completed
/// (no matching {@link JournalEvent.StepCompleted} or
/// {@link JournalEvent.StepFailed}). The {@link InFlightStepResolver}
/// uses {@link #inFlightStepDetails()} to inspect the step's type
/// and deadline, then applies the resolution strategy:
///
/// - Timed-out steps (deadline passed) → `TIMED_OUT`
/// - Clean shutdown → `RESUME`
/// - Idempotent steps after crash → `RETRY`
/// - Non-idempotent steps after crash → `FAIL`
///
/// This ensures every in-flight step gets a deterministic resolution
/// and no step remains stuck in an unresolvable state.
///
/// ## Usage
///
/// Host systems do not typically implement this interface. The engine
/// provides {@link DefaultExecutionSnapshot} as the mutable builder
/// and {@link DefaultJournalStateReconstructor} as the reconstruction
/// algorithm. Host systems interact with snapshots when handling
/// resolution callbacks or inspecting execution state for monitoring.
///
/// @see JournalStateReconstructor
/// @see InFlightStepResolver
/// @since 0.1.0
///
public interface ExecutionSnapshot {

    /// Returns the execution run identifier.
    String executionId();

    /// Returns the execution plan identifier.
    String executionPlanId();

    /// Returns the reconstructed current execution phase.
    Executor.ExecutionPhase currentPhase();

    /// Returns IDs of steps that completed successfully.
    Set<String> completedStepIds();

    /// Returns IDs of steps that failed permanently.
    Set<String> failedStepIds();

    /// Returns IDs of steps that were skipped.
    Set<String> skippedStepIds();

    /// Returns IDs of steps that were started but never completed
    /// (interrupted work).
    Set<String> inFlightStepIds();

    /// Returns IDs of trials that completed.
    Set<String> completedTrialIds();

    /// Returns IDs of trials that were started but never completed.
    Set<String> inFlightTrialIds();

    /// Returns the reconstructed operational state of each element.
    Map<String, Element.OperationalState> elementStates();

    /// Returns the {@link JournalEvent.StepStarted} details for each
    /// in-flight step, enabling deadline checking during resolution.
    Map<String, JournalEvent.StepStarted> inFlightStepDetails();

    /// Returns the highest sequence number processed.
    long lastSequenceNumber();

    /// Returns the checkpoint ID that served as the base for
    /// reconstruction, or empty if reconstructed from the beginning.
    Optional<String> baseCheckpointId();

    /// Returns {@code true} if the last event was
    /// {@link JournalEvent.ExecutionSuspended}, indicating a clean shutdown.
    boolean wasCleanShutdown();

    /// Returns the timestamp of the last event processed.
    Instant lastEventTimestamp();
}
