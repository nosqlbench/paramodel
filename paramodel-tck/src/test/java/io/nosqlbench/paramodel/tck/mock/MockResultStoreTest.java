package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.persistence.ResultStoreTCK;

///
/// Runs ResultStoreTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockResultStoreTest extends ResultStoreTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
