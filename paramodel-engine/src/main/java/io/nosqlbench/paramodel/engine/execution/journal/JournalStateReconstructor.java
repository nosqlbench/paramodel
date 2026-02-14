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

import io.nosqlbench.paramodel.persistence.CheckpointStore;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.ExecutionPlan;

///
/// Reconstructs execution state from a journal and optional checkpoint.
///
/// This is the core recovery mechanism. After a crash or planned
/// restart, the engine calls {@link #reconstruct} to rebuild the
/// exact execution state at the moment of interruption.
///
/// ## Algorithm
///
/// 1. Load the latest checkpoint from {@link CheckpointStore} (if any)
///    to seed the base state (completed steps and trials).
/// 2. Find the {@link io.nosqlbench.paramodel.execution.journal.JournalEvent.CheckpointCreated
///    CheckpointCreated} event's sequence number in the journal (or 0
///    if no checkpoint exists).
/// 3. Replay all events after that sequence via
///    {@link JournalStore#replay(String, long)}.
/// 4. For each event, update mutable state:
///    - {@code StepStarted}: add to in-flight set
///    - {@code StepCompleted}: move from in-flight to completed set
///    - {@code StepFailed}: move from in-flight to failed set
///    - {@code StepSkipped}: add to skipped set
///    - {@code PhaseTransition}: update current phase
///    - {@code ElementStateChanged}: update element state map
///    - {@code TrialStarting}/{@code TrialEnded}: track trial state
///    - {@code ExecutionSuspended}: set clean shutdown flag
///    - {@code CheckpointCreated}: update base checkpoint ID
/// 5. Return the resulting {@link ExecutionSnapshot}, which may then
///    be passed to {@link InFlightStepResolver} to resolve any
///    interrupted work.
///
/// ## Correctness Properties
///
/// - **Idempotent**: calling {@code reconstruct} multiple times with
///   the same journal produces identical snapshots.
/// - **Monotonic**: replaying a prefix of the journal produces a
///   snapshot that is a subset of the full replay.
/// - **Crash-safe**: partial journals (where the process crashed
///   mid-write) are handled gracefully — incomplete events are not
///   present in the journal because {@link JournalStore#append}
///   guarantees atomic, durable writes.
///
/// ## Usage
///
/// The engine provides {@link DefaultJournalStateReconstructor} as
/// the default implementation. Host systems typically use this
/// directly and do not need to provide a custom implementation.
///
/// @see ExecutionSnapshot
/// @see InFlightStepResolver
/// @since 0.1.0
///
public interface JournalStateReconstructor {

    /// Reconstructs execution state by replaying journal events.
    ///
    /// @param executionId the execution to reconstruct
    /// @param plan the execution plan providing structural context
    /// @param journalStore the journal to replay from
    /// @param checkpointStore the checkpoint store for base state lookup
    /// @return the reconstructed execution snapshot
    ExecutionSnapshot reconstruct(
        String executionId,
        ExecutionPlan plan,
        JournalStore journalStore,
        CheckpointStore checkpointStore
    );
}
