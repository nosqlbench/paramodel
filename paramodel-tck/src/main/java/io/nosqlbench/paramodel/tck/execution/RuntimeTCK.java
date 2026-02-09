package io.nosqlbench.paramodel.tck.execution;

import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for Runtime contract.
///
/// Validates that implementations correctly:
/// - Deploy and teardown element instances
/// - Check instance health
/// - Execute trials
/// - Manage resources
/// - Provide configuration
///
/// @see Runtime
/// @since 0.1.0
///
public abstract class RuntimeTCK {
    protected RuntimeTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testRuntimeConfig() {
        Runtime runtime = getProvider().createRuntime();

        Runtime.RuntimeConfig config = runtime.config();

        assertThat(config).isNotNull();
        assertThat(config.defaultHealthCheckTimeout()).isNotNull();
        assertThat(config.defaultDeploymentTimeout()).isNotNull();
        assertThat(config.defaultTrialTimeout()).isNotNull();
        assertThat(config.customConfig()).isNotNull();
    }

    @Test
    public void testRuntimeAvailableResources() {
        Runtime runtime = getProvider().createRuntime();

        Runtime.ResourceAvailability avail = runtime.availableResources();

        assertThat(avail).isNotNull();
        assertThat(avail.cpu()).isGreaterThanOrEqualTo(0);
        assertThat(avail.memoryGb()).isGreaterThanOrEqualTo(0);
        assertThat(avail.storageGb()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testRuntimeAllocateResources() throws Exception {
        Runtime runtime = getProvider().createRuntime();

        Runtime.ResourceAllocation alloc = runtime.allocateResources(
            Runtime.Resources.of(1.0, 2.0, 5.0));

        assertThat(alloc).isNotNull();
        assertThat(alloc.allocationId()).isNotNull();
        assertThat(alloc.resources()).isNotNull();
        assertThat(alloc.allocatedAt()).isNotNull();

        runtime.releaseResources(alloc);
    }

    @Test
    public void testRuntimeDeploy() throws Exception {
        Runtime runtime = getProvider().createRuntime();
        var element = getProvider().createElement("test-element");

        Runtime.ElementInstance instance = runtime.deploy(
            getProvider().createDeploymentRequest(element, "instance-1"));

        assertThat(instance).isNotNull();
        assertThat(instance.instanceId()).isEqualTo("instance-1");
        assertThat(instance.element()).isSameAs(element);
        assertThat(instance.endpoint()).isNotNull();
        assertThat(instance.state()).isNotNull();
        assertThat(instance.deployedAt()).isNotNull();

        runtime.teardown(instance, false);
    }

    @Test
    public void testRuntimeCheckHealth() throws Exception {
        Runtime runtime = getProvider().createRuntime();
        var element = getProvider().createElement("test-element");
        Runtime.ElementInstance instance = runtime.deploy(
            getProvider().createDeploymentRequest(element, "instance-2"));

        Runtime.HealthStatus health = runtime.checkHealth(instance);

        assertThat(health).isNotNull();
        assertThat(health.details()).isNotNull();

        runtime.teardown(instance, false);
    }

    @Test
    public void testRuntimeTeardown() throws Exception {
        Runtime runtime = getProvider().createRuntime();
        var element = getProvider().createElement("test-element");
        Runtime.ElementInstance instance = runtime.deploy(
            getProvider().createDeploymentRequest(element, "instance-3"));

        assertThatCode(() -> runtime.teardown(instance, false))
            .doesNotThrowAnyException();
    }

    @Test
    public void testRuntimeExecuteTrial() throws Exception {
        Runtime runtime = getProvider().createRuntime();
        var trial = getProvider().createTrial("trial-1");

        var result = runtime.executeTrial(
            getProvider().createTrialExecutionRequest(trial));

        assertThat(result).isNotNull();
        assertThat(result.trial()).isNotNull();
        assertThat(result.status()).isNotNull();
    }

    @Test
    public void testRuntimeMetricsCollector() {
        Runtime runtime = getProvider().createRuntime();

        Runtime.MetricsCollector collector = runtime.metricsCollector();

        assertThat(collector).isNotNull();
    }
}
