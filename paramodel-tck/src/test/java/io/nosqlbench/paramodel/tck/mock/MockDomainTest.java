package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.parameters.DomainTCK;

/**
 * Validates mock Domain implementation against TCK.
 */
public class MockDomainTest extends DomainTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
