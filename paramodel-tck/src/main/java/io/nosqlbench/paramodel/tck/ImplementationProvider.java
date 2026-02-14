package io.nosqlbench.paramodel.tck;

import io.nosqlbench.paramodel.compilation.*;
import io.nosqlbench.paramodel.elements.*;
import io.nosqlbench.paramodel.execution.*;
import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.persistence.*;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.security.*;
import io.nosqlbench.paramodel.util.*;
import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;
import io.nosqlbench.paramodel.sequence.*;

import java.time.Duration;
import java.util.List;

///
/// Provider interface for supplying implementation instances to TCK tests.
///
/// Implementations under test must provide a concrete implementation of this
/// interface to enable TCK validation.
///
/// ```java
/// public class MockImplementationProvider implements ImplementationProvider {
///     @Override
///     public <T> Parameter<T> createParameter(String name, Domain<T> domain) {
///         return new MockParameter<>(name, domain);
///     }
///     // ... implement remaining methods
/// }
/// ```
///
public interface ImplementationProvider {

    // Core contracts
    <T> Parameter<T> createParameter(String name, Domain<T> domain);
    <T> Domain<T> createDiscreteDomain(Iterable<T> values);
    <T extends Comparable<T>> Domain<T> createRangeDomain(T min, T max);
    <T> Value<T> createValue(T value, String parameterName);
    ValidationResult createValidationResult(boolean valid, String message);

    // Sequence contracts
    Trial createTrial(String id);
    TrialBuilder createTrialBuilder();
    Sequence createSequence(Iterable<Trial> trials);
    SequenceBuilder createSequenceBuilder();

    // Plan contracts
    TestPlan createTestPlan();
    TestPlanBuilder createTestPlanBuilder();
    Axis<?> createAxis(String name, Iterable<Element> elements);
    Element createElement(String parameterName);
    ExecutionPlan createExecutionPlan(TestPlan testPlan);
    AtomicStep createAtomicStep(String id, Trial trial);
    ExecutionGraph createExecutionGraph();

    // Element contracts (typed and configured)
    Element createTypedElement(String name, String type);
    Element createElementWithDependencies(String name, List<Element> dependencies);
    Element createElementWithHealthCheck(String name, Element.HealthCheckSpec healthCheck);
    Element createElementWithScope(String name, Element.InstancingScope scope);
    Element.HealthCheckSpec createHealthCheckSpec(Duration timeout);

    // Typed axis contract
    <T> Axis<T> createTypedAxis(String name, List<T> values);

    // Barrier contract
    Barrier createBarrier(String id);

    // Trial result contracts
    TrialResult createTrialResult(Trial trial, TrialStatus status);
    TrialResult createFailedTrialResult(Trial trial, String errorMessage);

    // Execution policies contract
    ExecutionPolicies createExecutionPolicies();

    // Compilation contracts
    Compiler createCompiler();
    CompilationContext createCompilationContext(TestPlan plan);
    CompilationStage createCompilationStage(String name);
    OptimizationPass createOptimizationPass(String name);

    // Execution contracts
    Runtime createRuntime();
    Runtime.DeploymentRequest createDeploymentRequest(Element element, String instanceId);
    Runtime.TrialExecutionRequest createTrialExecutionRequest(Trial trial);
    Executor createExecutor();
    Scheduler createScheduler();
    ResourceManager createResourceManager();
    ResourceManager.ResourceRequest createResourceRequest(double cpu, double memoryGb,
                                                          double storageGb, String owner);
    ArtifactCollector createArtifactCollector();

    // Persistence contracts
    ArtifactStore createArtifactStore();
    ResultStore createResultStore();
    ResultStore.Query createResultQuery(TrialStatus status);
    MetadataStore createMetadataStore();
    CheckpointStore createCheckpointStore();
    ExecutionRepository createExecutionRepository();

    // Persistence supporting types
    TestPlanMetadata createTestPlanMetadata(String name, String fingerprint);
    ExecutionPlanMetadata createExecutionPlanMetadata(String id, String testPlanFingerprint);
    Executor.Checkpoint createCheckpoint(String checkpointId, String executionPlanId);
    ArtifactCollector.Artifact createArtifact(String id, String name, String trialId);

    // Security contracts
    AccessControl createAccessControl();
    AuditLog createAuditLog();
    AuditLog.AuditEntry createAuditEntry(String userId, String action,
                                          String resource, boolean success);
    AuditLog.AuditQuery createAuditQuery(String userId, String action);
    CredentialManager createCredentialManager();
    CredentialManager.Credential createCredential(CredentialManager.CredentialType type,
                                                   String value);

    // Utility contracts
    ConfigurationManager createConfigurationManager();
    SerializationUtil createSerializationUtil();
}
