package io.nosqlbench.paramodel.persistence;

import io.nosqlbench.paramodel.plan.TestPlanMetadata;
import io.nosqlbench.paramodel.plan.ExecutionPlanMetadata;

import java.util.List;
import java.util.Optional;

///
/// # MetadataStore
///
/// Persists test plan and execution plan metadata.
///
public interface MetadataStore {

    static MetadataStore create() {
        throw new UnsupportedOperationException(
            "MetadataStore.create() requires a concrete implementation");
    }

    void saveTestPlanMetadata(TestPlanMetadata metadata);

    Optional<TestPlanMetadata> getTestPlanMetadata(String fingerprint);

    void saveExecutionPlanMetadata(ExecutionPlanMetadata metadata);

    Optional<ExecutionPlanMetadata> getExecutionPlanMetadata(String planId);

    List<TestPlanMetadata> listTestPlans();

    List<ExecutionPlanMetadata> listExecutionPlans();
}
