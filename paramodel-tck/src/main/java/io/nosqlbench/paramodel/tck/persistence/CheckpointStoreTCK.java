package io.nosqlbench.paramodel.tck.persistence;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.persistence.CheckpointStore;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link CheckpointStore} implementations.
///
/// Validates save, get, getLatest, list, and delete operations
/// for checkpoint persistence.
///
/// @since 0.1.0
///
public abstract class CheckpointStoreTCK {

    /// Creates a new TCK test instance.
    protected CheckpointStoreTCK() {}

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private CheckpointStore store;

    @BeforeEach
    void setUp() {
        store = getProvider().createCheckpointStore();
    }

    @Test
    void testSaveAndGetCheckpoint() {
        Executor.Checkpoint checkpoint = getProvider().createCheckpoint(
            "cp-1", "exec-plan-1");
        store.saveCheckpoint(checkpoint);

        Optional<Executor.Checkpoint> retrieved = store.getCheckpoint("cp-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().checkpointId()).isEqualTo("cp-1");
        assertThat(retrieved.get().executionPlanId()).isEqualTo("exec-plan-1");
    }

    @Test
    void testGetLatestCheckpoint() {
        Executor.Checkpoint cp1 = getProvider().createCheckpoint("cp-a", "exec-plan-1");
        Executor.Checkpoint cp2 = getProvider().createCheckpoint("cp-b", "exec-plan-1");

        store.saveCheckpoint(cp1);
        store.saveCheckpoint(cp2);

        Optional<Executor.Checkpoint> latest = store.getLatestCheckpoint("exec-plan-1");
        assertThat(latest).isPresent();
        assertThat(latest.get().createdAt()).isNotNull();
    }

    @Test
    void testListCheckpoints() {
        store.saveCheckpoint(getProvider().createCheckpoint("cp-l1", "exec-plan-1"));
        store.saveCheckpoint(getProvider().createCheckpoint("cp-l2", "exec-plan-1"));
        store.saveCheckpoint(getProvider().createCheckpoint("cp-l3", "exec-plan-2"));

        List<Executor.Checkpoint> plan1Checkpoints = store.listCheckpoints("exec-plan-1");
        assertThat(plan1Checkpoints).hasSize(2);

        List<Executor.Checkpoint> plan2Checkpoints = store.listCheckpoints("exec-plan-2");
        assertThat(plan2Checkpoints).hasSize(1);
    }

    @Test
    void testDeleteCheckpoint() {
        store.saveCheckpoint(getProvider().createCheckpoint("cp-del", "exec-plan-1"));
        assertThat(store.getCheckpoint("cp-del")).isPresent();

        store.deleteCheckpoint("cp-del");
        assertThat(store.getCheckpoint("cp-del")).isEmpty();
    }

    @Test
    void testGetLatestForNonExistentPlan() {
        Optional<Executor.Checkpoint> latest = store.getLatestCheckpoint("nonexistent");
        assertThat(latest).isEmpty();
    }
}
