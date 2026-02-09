package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.persistence.CheckpointStoreTCK;

///
/// Runs CheckpointStoreTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockCheckpointStoreTest extends CheckpointStoreTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
