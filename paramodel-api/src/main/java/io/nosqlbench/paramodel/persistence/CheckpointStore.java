package io.nosqlbench.paramodel.persistence;

import io.nosqlbench.paramodel.execution.Executor;

import java.util.List;
import java.util.Optional;

///
/// # CheckpointStore
///
/// Persists checkpoints for resumable execution.
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
