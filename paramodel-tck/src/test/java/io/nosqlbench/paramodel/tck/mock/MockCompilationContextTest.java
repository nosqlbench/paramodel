package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.compilation.CompilationContextTCK;

///
/// Runs CompilationContextTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockCompilationContextTest extends CompilationContextTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
