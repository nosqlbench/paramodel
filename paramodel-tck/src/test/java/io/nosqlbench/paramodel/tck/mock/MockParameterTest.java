package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.parameters.ParameterTCK;

/**
 * Validates mock Parameter implementation against TCK.
 */
public class MockParameterTest extends ParameterTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
