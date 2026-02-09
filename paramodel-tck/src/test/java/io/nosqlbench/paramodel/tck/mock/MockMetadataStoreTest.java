package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.persistence.MetadataStoreTCK;

///
/// Runs MetadataStoreTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockMetadataStoreTest extends MetadataStoreTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
