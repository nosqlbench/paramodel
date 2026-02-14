package io.nosqlbench.paramodel.persistence;

import io.nosqlbench.paramodel.execution.Executor;

import java.util.List;
import java.util.Optional;

///
/// # CheckpointStore
///
/// Persists checkpoints for resumable execution. Together with
/// {@link JournalStore}, implements the WAL + Snapshot pattern for
/// robust state rehydration after crashes or planned restarts.
///
/// A checkpoint is a point-in-time snapshot of completed work
/// (see {@link io.nosqlbench.paramodel.execution.Executor.Checkpoint
/// Executor.Checkpoint}). The engine periodically writes checkpoints
/// and uses them as the starting point for journal replay during
/// recovery.
///
/// ## Durability Contract
///
/// After {@link #saveCheckpoint} returns, the checkpoint must
/// survive process crashes. This store must use the same durability
/// guarantees as the paired {@link JournalStore} — a durable journal
/// with a non-durable checkpoint store (or vice versa) can lead to
/// inconsistent recovery.
///
/// ## Write Ordering with JournalStore
///
/// The engine writes events and checkpoints in this order:
///
/// 1. Write {@code CheckpointCreated} event to {@link JournalStore}
/// 2. Write the checkpoint to this store via {@link #saveCheckpoint}
/// 3. Call {@link JournalStore#truncateBefore} to compact the journal
///
/// This ordering ensures that a crash between steps 1 and 2 is
/// safe — the journal event exists but the checkpoint doesn't, so
/// recovery replays from the previous checkpoint.
///
/// ## Implementing Executor.Checkpoint
///
/// Host systems must provide concrete implementations of
/// {@link io.nosqlbench.paramodel.execution.Executor.Checkpoint
/// Executor.Checkpoint}. The checkpoint interface provides:
///
/// - **checkpointId()**: unique identifier for the checkpoint
/// - **executionPlanId()**: which plan this checkpoint belongs to
/// - **createdAt()**: when the checkpoint was created
/// - **completedTrialIds()**: trials completed before this checkpoint
/// - **completedStepIds()**: steps completed before this checkpoint
/// - **state()**: opaque map of additional state
///
/// The serialization format is up to the host system. JSON or a
/// database row per checkpoint are common choices.
///
public interface CheckpointStore {

    static CheckpointStore create() {
        throw new UnsupportedOperationException(
            "CheckpointStore.create() requires a concrete implementation");
    }

    void saveCheckpoint(Executor.Checkpoint checkpoint);

    Optional<Executor.Checkpoint> getCheckpoint(String checkpointId);

    Optional<Executor.Checkpoint> getLatestCheckpoint(String executionPlanId);

    List<Executor.Checkpoint> listCheckpoints(String executionPlanId);

    void deleteCheckpoint(String checkpointId);
}
