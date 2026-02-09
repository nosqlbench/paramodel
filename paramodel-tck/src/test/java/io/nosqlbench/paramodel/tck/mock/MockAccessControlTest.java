package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.security.AccessControlTCK;

///
/// Runs AccessControlTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockAccessControlTest extends AccessControlTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
