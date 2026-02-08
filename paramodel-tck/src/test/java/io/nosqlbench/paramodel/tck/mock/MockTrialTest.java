package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.sequence.TrialTCK;

/**
 * Validates mock Trial implementation against TCK.
 */
public class MockTrialTest extends TrialTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
