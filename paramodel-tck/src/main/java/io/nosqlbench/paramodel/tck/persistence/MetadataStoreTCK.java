package io.nosqlbench.paramodel.tck.persistence;

import io.nosqlbench.paramodel.persistence.MetadataStore;
import io.nosqlbench.paramodel.plan.ExecutionPlanMetadata;
import io.nosqlbench.paramodel.plan.TestPlanMetadata;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link MetadataStore} implementations.
///
/// Validates save, get, and list operations for test plan
/// and execution plan metadata persistence.
///
/// @since 0.1.0
///
public abstract class MetadataStoreTCK {

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private MetadataStore store;

    @BeforeEach
    void setUp() {
        store = getProvider().createMetadataStore();
    }

    @Test
    void testSaveAndGetTestPlanMetadata() {
        TestPlanMetadata metadata = getProvider().createTestPlanMetadata(
            "test-plan-1", "fingerprint-abc123");
        store.saveTestPlanMetadata(metadata);

        Optional<TestPlanMetadata> retrieved = store.getTestPlanMetadata("fingerprint-abc123");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().name()).isEqualTo("test-plan-1");
        assertThat(retrieved.get().fingerprint()).isEqualTo("fingerprint-abc123");
    }

    @Test
    void testSaveAndGetExecutionPlanMetadata() {
        ExecutionPlanMetadata metadata = getProvider().createExecutionPlanMetadata(
            "exec-plan-1", "fingerprint-xyz789");
        store.saveExecutionPlanMetadata(metadata);

        Optional<ExecutionPlanMetadata> retrieved = store.getExecutionPlanMetadata("exec-plan-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo("exec-plan-1");
        assertThat(retrieved.get().testPlanFingerprint()).isEqualTo("fingerprint-xyz789");
    }

    @Test
    void testListTestPlans() {
        store.saveTestPlanMetadata(getProvider().createTestPlanMetadata("plan-a", "fp-a"));
        store.saveTestPlanMetadata(getProvider().createTestPlanMetadata("plan-b", "fp-b"));

        List<TestPlanMetadata> plans = store.listTestPlans();
        assertThat(plans).hasSize(2);
    }

    @Test
    void testListExecutionPlans() {
        store.saveExecutionPlanMetadata(
            getProvider().createExecutionPlanMetadata("ep-1", "fp-1"));
        store.saveExecutionPlanMetadata(
            getProvider().createExecutionPlanMetadata("ep-2", "fp-2"));
        store.saveExecutionPlanMetadata(
            getProvider().createExecutionPlanMetadata("ep-3", "fp-3"));

        List<ExecutionPlanMetadata> plans = store.listExecutionPlans();
        assertThat(plans).hasSize(3);
    }

    @Test
    void testGetNonExistentMetadata() {
        assertThat(store.getTestPlanMetadata("nonexistent")).isEmpty();
        assertThat(store.getExecutionPlanMetadata("nonexistent")).isEmpty();
    }
}
