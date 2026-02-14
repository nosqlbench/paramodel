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
package io.nosqlbench.paramodel.persistence;

import io.nosqlbench.paramodel.execution.journal.JournalEvent;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

///
/// # JournalStore
///
/// Persists execution journal events for durable, append-only recording
/// of execution state changes. Together with {@link CheckpointStore},
/// implements the WAL (Write-Ahead Log) + Snapshot pattern for robust
/// state rehydration after crashes or planned restarts.
///
/// This is the primary persistence SPI that a host system must implement
/// to enable journal-based recovery. The engine writes events through
/// this interface via {@code JournalWriter}, and reads them back during
/// reconstruction via {@code JournalStateReconstructor}.
///
/// ## Durability Contract
///
/// After {@link #append(JournalEvent)} returns normally, the event
/// **must survive process crashes**. This typically means:
///
/// - **File-based implementations**: call `fsync` / `fdatasync` on the
///   underlying file descriptor before returning from {@code append()}.
///   Buffered writes without sync do NOT satisfy this contract — a kernel
///   crash or power loss would lose buffered-but-unflushed events.
/// - **Database-backed implementations**: commit the transaction before
///   returning. Auto-commit mode with durable storage is acceptable.
/// - **Distributed implementations**: ensure the event is replicated to
///   the required number of nodes before returning (i.e., the write
///   must be "committed" in the distributed system's consistency model).
///
/// If the implementation cannot guarantee durability for a given write,
/// it **must** throw {@link JournalWriteException} rather than returning
/// normally. A silent loss of an event will cause incorrect state
/// reconstruction and potentially stuck executions.
///
/// ## Contiguity Enforcement
///
/// {@link #append(JournalEvent)} **must reject** events whose sequence
/// numbers are not contiguous with the latest event for that execution:
///
/// - The first event for an execution must have sequence number **1**.
/// - Each subsequent event must have sequence number exactly
///   `previous + 1`.
/// - Gaps (e.g., 1, 2, 4) indicate a lost event and must be rejected
///   with {@link IllegalArgumentException}.
/// - Duplicates (e.g., 1, 2, 2) must also be rejected.
///
/// This enforcement catches bugs in the writer and prevents silent
/// corruption of the event stream. The engine relies on gap-free
/// sequences for correct reconstruction.
///
/// ## Concurrency
///
/// A single execution is written to by exactly one {@code JournalWriter}
/// at a time (the writer is synchronized internally). However, the store
/// must support concurrent operations across **different** execution IDs
/// (e.g., appending to execution "A" while replaying execution "B").
/// Within a single execution, reads and writes may overlap — for
/// instance, a monitoring thread may call {@link #latestEvent(String)}
/// while the executor is appending. Implementations should handle this
/// safely, though strict serializability is not required for reads
/// during active writing.
///
/// ## Serialization Guidance
///
/// {@link JournalEvent} is a sealed interface with 13 record subtypes.
/// Each record is a plain data carrier with no behavior. Implementations
/// must serialize and deserialize all 13 subtypes faithfully:
///
/// - **JSON**: use a discriminator field (e.g., `"type": "StepStarted"`)
///   and serialize each record's components as named fields. The
///   {@link io.nosqlbench.paramodel.plan.AtomicStep.StepType StepType},
///   {@link io.nosqlbench.paramodel.execution.Executor.ExecutionPhase
///   ExecutionPhase},
///   {@link io.nosqlbench.paramodel.elements.Element.OperationalState
///   OperationalState}, and
///   {@link io.nosqlbench.paramodel.sequence.TrialStatus TrialStatus}
///   enums should be serialized by name. {@code Optional} fields should
///   be serialized as null-or-value. {@code Map<String, Object>} fields
///   (e.g., configuration, outputs) contain arbitrary user data and
///   should be serialized as JSON objects.
/// - **Binary formats**: ensure that `Instant`, `Duration`, `Optional`,
///   and `Map` are round-trip safe. Sequence numbers are positive longs.
///
/// The sealed nature of {@code JournalEvent} guarantees that no
/// subtypes can be added outside the declaring file, so exhaustive
/// deserialization switches are stable.
///
/// ## Compaction
///
/// {@link #truncateBefore(String, long)} removes events that have been
/// superseded by a checkpoint, preventing unbounded journal growth.
/// The engine calls this after successfully writing a checkpoint:
///
/// 1. Engine writes {@code CheckpointCreated} event (sequence N)
/// 2. Engine writes the actual checkpoint to {@link CheckpointStore}
/// 3. Engine calls {@code truncateBefore(executionId, N)}
///
/// After truncation, only events at or after sequence N remain. The
/// reconstruction algorithm handles partial truncation gracefully — if
/// a crash occurs between steps 1 and 3, the journal simply has extra
/// events that will be replayed (correct, just slightly more replay).
///
/// Implementations may perform truncation lazily (e.g., marking a
/// low-watermark and cleaning up in the background) as long as
/// truncated events are never returned by {@link #replay} or
/// {@link #allEvents}.
///
/// ## Storage Sizing
///
/// A typical execution produces 3–5 events per atomic step
/// ({@code StepStarted}, optionally {@code TrialStarting}/{@code TrialEnded},
/// {@code StepCompleted}), plus phase transitions and element state
/// changes. For a plan with 1,000 steps, expect roughly 5,000 events
/// between checkpoints. Each event serializes to approximately 200–500
/// bytes in JSON, so 1–2 MB per checkpoint interval is typical.
/// Implementations should be prepared for plans with up to 100,000
/// steps between checkpoints (~50 MB journal segments).
///
/// ## Interaction with CheckpointStore
///
/// The journal and checkpoint stores work together:
///
/// - **Normal operation**: events accumulate in the journal. Periodically,
///   the engine writes a checkpoint (a snapshot of completed work) and
///   truncates the journal up to that point.
/// - **Recovery**: the engine loads the latest checkpoint from
///   {@link CheckpointStore}, finds the corresponding
///   {@code CheckpointCreated} event's sequence number in the journal,
///   and replays events after that point to reconstruct the exact state
///   at the time of interruption.
/// - **No checkpoint**: if no checkpoint exists, the entire journal is
///   replayed from sequence 1.
///
/// Both stores must use the same underlying durability guarantees.
/// A durable journal with a non-durable checkpoint store (or vice
/// versa) can lead to inconsistent recovery.
///
/// ## Example: File-Based Implementation Sketch
///
/// ```java
/// public class FileJournalStore implements JournalStore {
///     private final Path journalDir;
///
///     public void append(JournalEvent event) {
///         Path file = journalDir.resolve(event.executionId() + ".journal");
///         // Validate contiguity against last line
///         String json = serialize(event);
///         // Append line + fsync
///         try (var out = Files.newOutputStream(file, APPEND, CREATE, SYNC)) {
///             out.write((json + "\n").getBytes(UTF_8));
///         }
///     }
///
///     public Stream<JournalEvent> replay(String executionId, long afterSequence) {
///         Path file = journalDir.resolve(executionId + ".journal");
///         return Files.lines(file)
///             .map(this::deserialize)
///             .filter(e -> e.sequenceNumber() > afterSequence);
///     }
///     // ... remaining methods
/// }
/// ```
///
/// @see JournalEvent
/// @see CheckpointStore
/// @since 0.1.0
///
public interface JournalStore {

    /// Appends an event to the journal with durable write semantics.
    ///
    /// The implementation must guarantee that after this method returns
    /// normally, the event is persisted and will survive process crashes.
    /// If durability cannot be guaranteed, throw {@link JournalWriteException}.
    ///
    /// The event's {@link JournalEvent#executionId()} determines which
    /// execution's event stream this event belongs to. The event's
    /// {@link JournalEvent#sequenceNumber()} must be exactly one greater
    /// than the latest sequence number for that execution (or 1 if this
    /// is the first event for the execution).
    ///
    /// @param event the event to append; must not be null
    /// @throws JournalWriteException if the event cannot be durably written
    ///         (e.g., disk full, I/O error, replication failure)
    /// @throws IllegalArgumentException if the event's sequence number is
    ///         not contiguous with the latest event for that execution,
    ///         or if the event is null
    void append(JournalEvent event);

    /// Replays events for an execution starting after the given sequence
    /// number, in strictly ascending sequence order.
    ///
    /// This is the primary method used during state reconstruction. The
    /// engine calls this with the sequence number of the last checkpoint's
    /// {@code CheckpointCreated} event (or 0 if no checkpoint exists) to
    /// replay only the events that occurred after the checkpoint.
    ///
    /// The returned stream must deliver events in ascending sequence
    /// number order. Events with sequence numbers less than or equal to
    /// {@code afterSequence} must be excluded. If truncation has removed
    /// events before {@code afterSequence}, this is transparent — only
    /// the surviving events are returned.
    ///
    /// The stream should be lazy where possible (e.g., backed by a file
    /// reader or database cursor) to avoid loading the entire journal
    /// into memory for large executions.
    ///
    /// @param executionId the execution to replay; must not be null
    /// @param afterSequence replay events with sequence numbers strictly
    ///        greater than this value; use 0 to replay from the beginning
    /// @return stream of events in ascending sequence order; empty stream
    ///         if no events exist after the given sequence
    Stream<JournalEvent> replay(String executionId, long afterSequence);

    /// Returns all events for an execution in ascending sequence order.
    ///
    /// Unlike {@link #replay}, this method loads all events into memory
    /// and returns a materialized list. Use this for short journals
    /// (e.g., inspecting a completed execution) or testing. For
    /// reconstruction of active executions, prefer {@link #replay} with
    /// an {@code afterSequence} filter.
    ///
    /// If {@link #truncateBefore} has removed early events, only the
    /// surviving events are returned.
    ///
    /// @param executionId the execution to query; must not be null
    /// @return all surviving events in ascending sequence order; empty
    ///         list if no events exist
    List<JournalEvent> allEvents(String executionId);

    /// Returns the most recent event for an execution (the event with
    /// the highest sequence number).
    ///
    /// Used by the engine to detect whether the last event was an
    /// {@link JournalEvent.ExecutionSuspended} (clean shutdown) or
    /// something else (crash). Also useful for monitoring and
    /// diagnostics.
    ///
    /// @param executionId the execution to query; must not be null
    /// @return the latest event, or empty if no events exist for this
    ///         execution (or all have been truncated)
    Optional<JournalEvent> latestEvent(String executionId);

    /// Returns the highest sequence number for an execution.
    ///
    /// Used by the engine when creating a {@code JournalWriter} for a
    /// resumed execution — the writer starts at
    /// {@code latestSequenceNumber + 1} to maintain contiguity.
    ///
    /// @param executionId the execution to query; must not be null
    /// @return the highest sequence number among surviving events, or
    ///         0 if no events exist for this execution
    long latestSequenceNumber(String executionId);

    /// Removes events with sequence numbers strictly less than the
    /// given value, freeing storage after a checkpoint compaction.
    ///
    /// The engine calls this after a checkpoint has been successfully
    /// written to {@link CheckpointStore}, passing the sequence number
    /// of the {@code CheckpointCreated} event. Events before that point
    /// are redundant because the checkpoint captures all completed work
    /// up to that moment.
    ///
    /// Implementations may perform truncation synchronously or
    /// asynchronously (e.g., marking a low-watermark and cleaning up
    /// in a background thread), as long as truncated events are never
    /// returned by subsequent {@link #replay}, {@link #allEvents},
    /// or {@link #latestEvent} calls.
    ///
    /// If no events exist for the given execution, or all events have
    /// sequence numbers greater than or equal to {@code beforeSequence},
    /// this method is a no-op.
    ///
    /// @param executionId the execution to compact; must not be null
    /// @param beforeSequence remove events with sequence numbers
    ///        strictly less than this value
    void truncateBefore(String executionId, long beforeSequence);

    /// Removes all events for an execution, freeing all associated
    /// storage.
    ///
    /// Called after an execution has completed and its results have
    /// been persisted elsewhere (e.g., to a {@code ResultStore}). After
    /// this call, all methods will behave as if no events were ever
    /// written for this execution.
    ///
    /// @param executionId the execution to clean up; must not be null
    void deleteAll(String executionId);

    /// Unchecked exception thrown when a journal event cannot be durably
    /// written.
    ///
    /// Implementations of {@link #append(JournalEvent)} should throw
    /// this when the underlying storage operation fails in a way that
    /// cannot guarantee the event was persisted. Common causes include:
    ///
    /// - Disk full or I/O error during file write
    /// - Database connection failure or transaction rollback
    /// - Replication failure in distributed storage
    /// - Timeout waiting for durable acknowledgment
    ///
    /// The engine treats this as a fatal error for the current
    /// execution — the execution will be halted rather than continuing
    /// with a gap in the journal.
    class JournalWriteException extends RuntimeException {
        /// Creates a journal write exception.
        ///
        /// @param message description of the write failure, including
        ///        the execution ID and sequence number if available
        public JournalWriteException(String message) {
            super(message);
        }

        /// Creates a journal write exception with a cause.
        ///
        /// @param message description of the write failure
        /// @param cause the underlying I/O, database, or network
        ///        exception that caused the write failure
        public JournalWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
