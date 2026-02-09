package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.execution.ArtifactCollectorTCK;

///
/// Runs ArtifactCollectorTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockArtifactCollectorTest extends ArtifactCollectorTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
