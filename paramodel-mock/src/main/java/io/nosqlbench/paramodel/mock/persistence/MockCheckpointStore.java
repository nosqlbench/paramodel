package io.nosqlbench.paramodel.mock.persistence;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.persistence.CheckpointStore;

import java.util.*;

///
/// In-memory checkpoint store for testing.
///
/// Stores checkpoints in a map keyed by checkpoint ID. Supports
/// listing and finding latest checkpoints by execution plan ID.
///
/// @see CheckpointStore
/// @since 0.1.0
///
public class MockCheckpointStore implements CheckpointStore {
    private final Map<String, Executor.Checkpoint> checkpoints = new LinkedHashMap<>();

    /// Creates a new empty checkpoint store.
    public MockCheckpointStore() {}

    @Override
    public void saveCheckpoint(Executor.Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        checkpoints.put(checkpoint.checkpointId(), checkpoint);
    }

    @Override
    public Optional<Executor.Checkpoint> getCheckpoint(String checkpointId) {
        return Optional.ofNullable(checkpoints.get(checkpointId));
    }

    @Override
    public Optional<Executor.Checkpoint> getLatestCheckpoint(String executionPlanId) {
        return checkpoints.values().stream()
            .filter(cp -> executionPlanId.equals(cp.executionPlanId()))
            .max(Comparator.comparing(Executor.Checkpoint::createdAt));
    }

    @Override
    public List<Executor.Checkpoint> listCheckpoints(String executionPlanId) {
        return checkpoints.values().stream()
            .filter(cp -> executionPlanId.equals(cp.executionPlanId()))
            .sorted(Comparator.comparing(Executor.Checkpoint::createdAt))
            .toList();
    }

    @Override
    public void deleteCheckpoint(String checkpointId) {
        checkpoints.remove(checkpointId);
    }
}
