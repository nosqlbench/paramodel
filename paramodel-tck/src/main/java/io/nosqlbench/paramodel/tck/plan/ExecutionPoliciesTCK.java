package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for ExecutionPolicies contract.
///
/// Validates that implementations correctly:
/// - Provide trial and element retry policies
/// - Support timeout configuration
/// - Specify intervention mode and partial run behavior
///
/// @see ExecutionPolicies
/// @since 0.1.0
///
public abstract class ExecutionPoliciesTCK {
    protected ExecutionPoliciesTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testDefaultPoliciesExist() {
        ExecutionPolicies policies = getProvider().createExecutionPolicies();

        assertThat(policies).isNotNull();
    }

    @Test
    public void testPoliciesHaveTrialRetryPolicy() {
        ExecutionPolicies policies = getProvider().createExecutionPolicies();

        assertThat(policies.trialRetryPolicy()).isNotNull();
        assertThat(policies.trialRetryPolicy().maxAttempts()).isGreaterThanOrEqualTo(1);
        assertThat(policies.trialRetryPolicy().backoff()).isNotNull();
    }

    @Test
    public void testPoliciesHaveElementDeploymentRetryPolicy() {
        ExecutionPolicies policies = getProvider().createExecutionPolicies();

        assertThat(policies.elementDeploymentRetryPolicy()).isNotNull();
        assertThat(policies.elementDeploymentRetryPolicy().maxAttempts()).isGreaterThanOrEqualTo(1);
        assertThat(policies.elementDeploymentRetryPolicy().backoff()).isNotNull();
    }

    @Test
    public void testPoliciesTrialTimeout() {
        ExecutionPolicies policies = getProvider().createExecutionPolicies();

        // trialTimeout() should return a non-null Optional
        assertThat(policies.trialTimeout()).isNotNull();
    }

    @Test
    public void testPoliciesElementStartTimeout() {
        ExecutionPolicies policies = getProvider().createExecutionPolicies();

        // elementStartTimeout() should return a non-null Optional
        assertThat(policies.elementStartTimeout()).isNotNull();
    }

    @Test
    public void testPoliciesInterventionMode() {
        ExecutionPolicies policies = getProvider().createExecutionPolicies();

        assertThat(policies.interventionMode()).isNotNull();
    }

    @Test
    public void testPoliciesPartialRunBehavior() {
        ExecutionPolicies policies = getProvider().createExecutionPolicies();

        assertThat(policies.partialRunBehavior()).isNotNull();
    }
}
