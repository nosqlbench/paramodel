package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.util.SerializationUtilTCK;

///
/// Runs SerializationUtilTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockSerializationUtilTest extends SerializationUtilTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
