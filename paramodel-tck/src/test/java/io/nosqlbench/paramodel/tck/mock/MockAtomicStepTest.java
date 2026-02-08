package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.plan.AtomicStepTCK;

/**
 * Validates mock AtomicStep implementation against TCK.
 */
public class MockAtomicStepTest extends AtomicStepTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
