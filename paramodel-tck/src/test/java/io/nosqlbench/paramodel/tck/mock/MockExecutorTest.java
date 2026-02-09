package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.execution.ExecutorTCK;

///
/// Runs ExecutorTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockExecutorTest extends ExecutorTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
