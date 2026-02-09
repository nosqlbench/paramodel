package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.persistence.ArtifactStoreTCK;

///
/// Runs ArtifactStoreTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockArtifactStoreTest extends ArtifactStoreTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
