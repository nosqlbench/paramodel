package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.util.ConfigurationManagerTCK;

///
/// Runs ConfigurationManagerTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockConfigurationManagerTest extends ConfigurationManagerTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
