package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.core.ConstraintTCK;

/**
 * Validates constraint functionality against TCK.
 */
public class MockConstraintTest extends ConstraintTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
