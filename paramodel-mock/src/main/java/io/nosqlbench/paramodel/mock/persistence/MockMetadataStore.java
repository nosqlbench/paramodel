package io.nosqlbench.paramodel.mock.persistence;

import io.nosqlbench.paramodel.persistence.MetadataStore;
import io.nosqlbench.paramodel.plan.ExecutionPlanMetadata;
import io.nosqlbench.paramodel.plan.TestPlanMetadata;

import java.util.*;

///
/// In-memory metadata store for testing.
///
/// Stores test plan and execution plan metadata in separate
/// maps keyed by fingerprint and plan ID respectively.
///
/// @see MetadataStore
/// @since 0.1.0
///
public class MockMetadataStore implements MetadataStore {
    private final Map<String, TestPlanMetadata> testPlanMetadata = new LinkedHashMap<>();
    private final Map<String, ExecutionPlanMetadata> executionPlanMetadata = new LinkedHashMap<>();

    /// Creates a new empty metadata store.
    public MockMetadataStore() {}

    @Override
    public void saveTestPlanMetadata(TestPlanMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        testPlanMetadata.put(metadata.fingerprint(), metadata);
    }

    @Override
    public Optional<TestPlanMetadata> getTestPlanMetadata(String fingerprint) {
        return Optional.ofNullable(testPlanMetadata.get(fingerprint));
    }

    @Override
    public void saveExecutionPlanMetadata(ExecutionPlanMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        executionPlanMetadata.put(metadata.id(), metadata);
    }

    @Override
    public Optional<ExecutionPlanMetadata> getExecutionPlanMetadata(String planId) {
        return Optional.ofNullable(executionPlanMetadata.get(planId));
    }

    @Override
    public List<TestPlanMetadata> listTestPlans() {
        return List.copyOf(testPlanMetadata.values());
    }

    @Override
    public List<ExecutionPlanMetadata> listExecutionPlans() {
        return List.copyOf(executionPlanMetadata.values());
    }
}
