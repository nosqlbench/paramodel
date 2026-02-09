package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.execution.RuntimeTCK;

///
/// Runs RuntimeTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockRuntimeTest extends RuntimeTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
