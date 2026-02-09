package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.security.CredentialManagerTCK;

///
/// Runs CredentialManagerTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockCredentialManagerTest extends CredentialManagerTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
