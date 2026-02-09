package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.execution.ResourceManagerTCK;

///
/// Runs ResourceManagerTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockResourceManagerTest extends ResourceManagerTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
