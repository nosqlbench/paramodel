package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.compilation.OptimizationPassTCK;

///
/// Runs OptimizationPassTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockOptimizationPassTest extends OptimizationPassTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
