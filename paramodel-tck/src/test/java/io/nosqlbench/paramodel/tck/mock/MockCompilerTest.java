package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.compilation.CompilerTCK;

///
/// Runs CompilerTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockCompilerTest extends CompilerTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
