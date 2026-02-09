package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.plan.ExecutionPoliciesTCK;

///
/// Validates mock ExecutionPolicies implementation against TCK.
///
public class MockExecutionPoliciesTest extends ExecutionPoliciesTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
