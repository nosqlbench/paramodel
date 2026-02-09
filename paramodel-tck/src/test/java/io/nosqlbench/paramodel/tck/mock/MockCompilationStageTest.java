package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.compilation.CompilationStageTCK;

///
/// Runs CompilationStageTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockCompilationStageTest extends CompilationStageTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
