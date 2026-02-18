package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.compilation.*;
import io.nosqlbench.paramodel.elements.*;
import io.nosqlbench.paramodel.execution.*;
import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.mock.compilation.MockCompilationContext;
import io.nosqlbench.paramodel.mock.compilation.MockCompilationStage;
import io.nosqlbench.paramodel.mock.compilation.MockCompiler;
import io.nosqlbench.paramodel.mock.compilation.MockOptimizationPass;
import io.nosqlbench.paramodel.mock.elements.MockHealthCheckSpec;
import io.nosqlbench.paramodel.mock.execution.*;
import io.nosqlbench.paramodel.mock.parameters.MockDomain;
import io.nosqlbench.paramodel.mock.parameters.MockParameter;
import io.nosqlbench.paramodel.mock.parameters.MockRangeDomain;
import io.nosqlbench.paramodel.mock.parameters.MockValidationResult;
import io.nosqlbench.paramodel.mock.parameters.MockValue;
import io.nosqlbench.paramodel.mock.persistence.*;
import io.nosqlbench.paramodel.mock.plan.*;
import io.nosqlbench.paramodel.mock.security.MockAccessControl;
import io.nosqlbench.paramodel.mock.security.MockAuditLog;
import io.nosqlbench.paramodel.mock.security.MockCredentialManager;
import io.nosqlbench.paramodel.mock.util.MockConfigurationManager;
import io.nosqlbench.paramodel.mock.util.MockSerializationUtil;
import io.nosqlbench.paramodel.mock.sequence.*;
import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.persistence.*;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.security.*;
import io.nosqlbench.paramodel.util.*;
import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;
import io.nosqlbench.paramodel.sequence.*;
import io.nosqlbench.paramodel.tck.ImplementationProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation provider for mock implementation TCK validation.
 */
public class MockImplementationProvider implements ImplementationProvider {

    @Override
    public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
        return MockParameter.of(name, domain);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Domain<T> createDiscreteDomain(Iterable<T> values) {
        List<T> list = new ArrayList<>();
        values.forEach(list::add);
        return (Domain<T>) MockDomain.of(list.toArray());
    }

    @Override
    public <T extends Comparable<T>> Domain<T> createRangeDomain(T min, T max) {
        return MockRangeDomain.of(min, max);
    }

    @Override
    public <T> Value<T> createValue(T value, String parameterName) {
        return MockValue.of(value, parameterName);
    }

    @Override
    public ValidationResult createValidationResult(boolean valid, String message) {
        if (valid) {
            return MockValidationResult.passed();
        } else {
            return MockValidationResult.failed(message);
        }
    }

    @Override
    public Trial createTrial(String id) {
        return MockTrial.builder().id(id).build();
    }

    @Override
    public TrialBuilder createTrialBuilder() {
        return new MockTrialBuilder();
    }

    @Override
    public Sequence createSequence(Iterable<Trial> trials) {
        List<Trial> list = new ArrayList<>();
        trials.forEach(list::add);
        return MockSequence.of(list);
    }

    @Override
    public SequenceBuilder createSequenceBuilder() {
        return MockSequenceBuilder.create();
    }

    @Override
    public TestPlan createTestPlan() {
        return MockTestPlan.builder().build();
    }

    @Override
    public TestPlanBuilder createTestPlanBuilder() {
        return new MockTestPlanBuilder();
    }

    @Override
    public Axis<?> createAxis(String name, Iterable<Element> elements) {
        List<Element> list = new ArrayList<>();
        elements.forEach(list::add);
        return MockAxis.of(name, list.toArray(new Element[0]));
    }

    @Override
    public Element createElement(String parameterName) {
        return MockElement.of(parameterName);
    }

    @Override
    public ExecutionPlan createExecutionPlan(TestPlan testPlan) {
        return new MockExecutionPlan(
            java.util.UUID.randomUUID().toString(),
            java.util.UUID.randomUUID().toString());
    }

    @Override
    public AtomicStep createAtomicStep(String id, Trial trial) {
        return MockAtomicStep.trialStep(id, trial.id());
    }

    @Override
    public ExecutionGraph createExecutionGraph() {
        return new MockExecutionGraph();
    }

    @Override
    public Element createTypedElement(String name, String type) {
        return MockElement.ofType(name, type);
    }

    @Override
    public Element createElementWithDependencies(String name, List<Element> dependencies) {
        MockElement.Builder builder = MockElement.builder(name);
        for (Element dep : dependencies) {
            builder.dependency(dep);
        }
        return builder.build();
    }

    @Override
    public Element createElementWithHealthCheck(String name, Element.HealthCheckSpec healthCheck) {
        return MockElement.builder(name)
            .healthCheck(healthCheck)
            .build();
    }

    @Override
    public Element.HealthCheckSpec createHealthCheckSpec(Duration timeout) {
        return new MockHealthCheckSpec(timeout, 3, Duration.ofSeconds(5));
    }

    @Override
    public <T> Axis<T> createTypedAxis(String name, List<T> values) {
        return MockAxis.of(name, values);
    }

    @Override
    public Barrier createBarrier(String id) {
        return MockBarrier.builder(id).build();
    }

    @Override
    public TrialResult createTrialResult(Trial trial, TrialStatus status) {
        return MockTrialResult.builder(trial)
            .status(status)
            .build();
    }

    @Override
    public TrialResult createFailedTrialResult(Trial trial, String errorMessage) {
        return MockTrialResult.failed(trial, errorMessage);
    }

    @Override
    public ExecutionPolicies createExecutionPolicies() {
        return MockExecutionPolicies.defaults();
    }

    @Override
    public Compiler createCompiler() {
        return new MockCompiler();
    }

    @Override
    public CompilationContext createCompilationContext(TestPlan plan) {
        return new MockCompilationContext(plan);
    }

    @Override
    public CompilationStage createCompilationStage(String name) {
        return MockCompilationStage.of(name);
    }

    @Override
    public OptimizationPass createOptimizationPass(String name) {
        return MockOptimizationPass.of(name);
    }

    @Override
    public Runtime createRuntime() {
        return new MockRuntime();
    }

    @Override
    public Runtime.DeploymentRequest createDeploymentRequest(Element element, String instanceId) {
        return new MockDeploymentRequest(element, instanceId,
            Runtime.Resources.of(1.0, 2.0, 5.0), java.util.Map.of());
    }

    @Override
    public Runtime.TrialExecutionRequest createTrialExecutionRequest(Trial trial) {
        return new MockTrialExecutionRequest(trial, java.util.Map.of(),
            java.time.Duration.ofMinutes(5), Runtime.Resources.of(1.0, 2.0, 5.0));
    }

    @Override
    public Executor createExecutor() {
        return new MockExecutor();
    }

    @Override
    public Scheduler createScheduler() {
        return new MockScheduler();
    }

    @Override
    public ResourceManager createResourceManager() {
        return new MockResourceManager();
    }

    @Override
    public ResourceManager.ResourceRequest createResourceRequest(double cpu, double memoryGb,
                                                                  double storageGb, String owner) {
        return new MockResourceRequest(cpu, memoryGb, storageGb,
            java.util.Optional.empty(), owner, java.util.Optional.empty());
    }

    @Override
    public ArtifactCollector createArtifactCollector() {
        return new MockArtifactCollector();
    }

    @Override
    public ArtifactStore createArtifactStore() {
        return new MockArtifactStore();
    }

    @Override
    public ResultStore createResultStore() {
        return new MockResultStore();
    }

    @Override
    public ResultStore.Query createResultQuery(TrialStatus status) {
        MockResultStore.MockQuery.Builder builder = MockResultStore.queryBuilder();
        if (status != null) {
            builder.status(status);
        }
        return builder.build();
    }

    @Override
    public MetadataStore createMetadataStore() {
        return new MockMetadataStore();
    }

    @Override
    public CheckpointStore createCheckpointStore() {
        return new MockCheckpointStore();
    }

    @Override
    public ExecutionRepository createExecutionRepository() {
        return new MockExecutionRepository();
    }

    @Override
    public TestPlanMetadata createTestPlanMetadata(String name, String fingerprint) {
        return new MockStandaloneTestPlanMetadata(name, fingerprint);
    }

    @Override
    public ExecutionPlanMetadata createExecutionPlanMetadata(String id, String testPlanFingerprint) {
        return new MockExecutionPlanMetadata(
            id, testPlanFingerprint, Instant.now(), Duration.ZERO,
            "1.0", ExecutionPlanMetadata.OptimizationLevel.NONE,
            0, 0, 0, 0, Map.of());
    }

    @Override
    public Executor.Checkpoint createCheckpoint(String checkpointId, String executionPlanId) {
        return new MockCheckpoint(checkpointId, executionPlanId,
            Instant.now(), List.of(), List.of(), Map.of());
    }

    @Override
    public ArtifactCollector.Artifact createArtifact(String id, String name, String trialId) {
        return new MockArtifactRecord(id, name, ArtifactCollector.ArtifactType.LOG,
            0L, Instant.now(), trialId, Optional.of("text/plain"), Map.of());
    }

    @Override
    public AccessControl createAccessControl() {
        return new MockAccessControl();
    }

    @Override
    public AuditLog createAuditLog() {
        return new MockAuditLog();
    }

    @Override
    public AuditLog.AuditEntry createAuditEntry(String userId, String action,
                                                  String resource, boolean success) {
        return MockAuditLog.entry(userId, action, resource, success);
    }

    @Override
    public AuditLog.AuditQuery createAuditQuery(String userId, String action) {
        MockAuditLog.MockAuditQuery.Builder builder = MockAuditLog.queryBuilder();
        if (userId != null) {
            builder.userId(userId);
        }
        if (action != null) {
            builder.action(action);
        }
        return builder.build();
    }

    @Override
    public CredentialManager createCredentialManager() {
        return new MockCredentialManager();
    }

    @Override
    public CredentialManager.Credential createCredential(CredentialManager.CredentialType type,
                                                          String value) {
        return MockCredentialManager.credential(type, value);
    }

    @Override
    public ConfigurationManager createConfigurationManager() {
        return new MockConfigurationManager();
    }

    @Override
    public SerializationUtil createSerializationUtil() {
        return new MockSerializationUtil();
    }

    private record MockDeploymentRequest(
        Element element,
        String instanceId,
        Runtime.Resources resources,
        java.util.Map<String, Object> configuration
    ) implements Runtime.DeploymentRequest {}

    private record MockTrialExecutionRequest(
        Trial trial,
        java.util.Map<String, Runtime.ElementInstance> elementBindings,
        java.time.Duration timeout,
        Runtime.Resources resources
    ) implements Runtime.TrialExecutionRequest {}

    private record MockResourceRequest(
        double cpu,
        double memoryGb,
        double storageGb,
        java.util.Optional<String> pool,
        String owner,
        java.util.Optional<java.time.Duration> duration
    ) implements ResourceManager.ResourceRequest {}

    /**
     * Mock TrialBuilder implementation.
     */
    private static class MockTrialBuilder implements TrialBuilder {
        private final MockTrial.Builder delegate = MockTrial.builder();

        @Override
        public TrialBuilder id(String id) {
            delegate.id(id);
            return this;
        }

        @Override
        public TrialBuilder assignment(String name, Value<?> value) {
            delegate.assignment(name, value);
            return this;
        }

        @Override
        public TrialBuilder constraint(Constraint<java.util.Map<String, Value<?>>> constraint) {
            delegate.constraint(constraint);
            return this;
        }

        @Override
        public Trial build() {
            return delegate.build();
        }
    }

    /**
     * Mock TestPlanBuilder implementation.
     */
    private static class MockTestPlanBuilder implements TestPlanBuilder {
        private MockTestPlan.Builder delegate = MockTestPlan.builder();

        @Override
        public TestPlanBuilder name(String name) {
            delegate.name(name);
            return this;
        }

        @Override
        public <T> TestPlanBuilder withAxis(Axis<T> axis) {
            delegate.axis(axis);
            return this;
        }

        @Override
        public <T> TestPlanBuilder withAxisFromParameter(Parameter<T> parameter, int sampleSize) {
            return this;
        }

        @Override
        public TestPlanBuilder withAxes(List<Axis<?>> axes) {
            for (Axis<?> axis : axes) {
                delegate.axis(axis);
            }
            return this;
        }

        @Override
        public TestPlanBuilder withElement(Element element) {
            delegate.element(element);
            return this;
        }

        @Override
        public TestPlanBuilder withElements(List<Element> elements) {
            for (Element element : elements) {
                delegate.element(element);
            }
            return this;
        }

        @Override
        public TestPlanBuilder policies(io.nosqlbench.paramodel.plan.policies.ExecutionPolicies policies) {
            delegate.policies(policies);
            return this;
        }

        @Override
        public TestPlanBuilder policies(java.util.function.Consumer<io.nosqlbench.paramodel.plan.policies.ExecutionPolicies.Builder> configurator) {
            return this;
        }

        @Override
        public TestPlanBuilder optimizationStrategy(OptimizationStrategy strategy) {
            delegate.optimizationStrategy(strategy);
            return this;
        }

        @Override
        public TestPlanBuilder basedOn(TestPlan source) {
            return this;
        }

        @Override
        public TestPlanBuilder axisOrder(List<String> axisNames) {
            return this;
        }

        @Override
        public TestPlanBuilder metadata(String key, Object value) {
            return this;
        }

        @Override
        public TestPlanBuilder metadata(java.util.Map<String, Object> metadata) {
            return this;
        }

        @Override
        public io.nosqlbench.paramodel.parameters.ValidationResult validate() {
            return io.nosqlbench.paramodel.mock.parameters.MockValidationResult.passed();
        }

        @Override
        public long estimateTrialSpaceSize() {
            return 0;
        }

        @Override
        public List<Axis<?>> currentAxes() {
            return List.of();
        }

        @Override
        public List<Element> currentElements() {
            return List.of();
        }

        @Override
        public java.util.Optional<io.nosqlbench.paramodel.plan.policies.ExecutionPolicies> currentPolicies() {
            return java.util.Optional.empty();
        }

        @Override
        public TestPlan build() {
            return delegate.build();
        }

        @Override
        public TestPlanBuilder reset() {
            delegate = MockTestPlan.builder();
            return this;
        }
    }

    ///
    /// Mock implementation of the standalone TestPlanMetadata interface
    /// for persistence testing.
    ///
    private static class MockStandaloneTestPlanMetadata implements TestPlanMetadata {
        private final String name;
        private final String fingerprint;
        private final Instant createdAt = Instant.now();

        MockStandaloneTestPlanMetadata(String name, String fingerprint) {
            this.name = name;
            this.fingerprint = fingerprint;
        }

        @Override public String name() { return name; }
        @Override public String version() { return "1.0.0"; }
        @Override public SemanticVersion semanticVersion() { return SemanticVersion.parse("1.0.0"); }
        @Override public String fingerprint() { return fingerprint; }
        @Override public Optional<String> sourceFingerprint() { return Optional.empty(); }
        @Override public Instant createdAt() { return createdAt; }
        @Override public Instant modifiedAt() { return createdAt; }
        @Override public Optional<String> author() { return Optional.of("test"); }
        @Override public Optional<String> description() { return Optional.of("test plan"); }
        @Override public List<String> changesSinceSource() { return List.of(); }
        @Override public long trialSpaceSize() { return 0; }
        @Override public Optional<Double> estimatedCost() { return Optional.empty(); }
        @Override public Optional<Double> estimatedResourceHours() { return Optional.empty(); }
        @Override public Optional<Duration> estimatedDuration() { return Optional.empty(); }
        @Override public LifecycleState lifecycleState() { return LifecycleState.DRAFT; }
        @Override public boolean isCommitted() { return false; }
        @Override public Map<String, Object> customMetadata() { return Map.of(); }
        @Override public List<String> tags() { return List.of(); }
    }

    private record MockCheckpoint(
        String checkpointId,
        String executionPlanId,
        Instant createdAt,
        List<String> completedTrialIds,
        List<String> completedStepIds,
        Map<String, Object> state
    ) implements Executor.Checkpoint {}

    private record MockArtifactRecord(
        String id,
        String name,
        ArtifactCollector.ArtifactType type,
        long size,
        Instant collectedAt,
        String trialId,
        Optional<String> contentType,
        Map<String, String> metadata
    ) implements ArtifactCollector.Artifact {}
}
