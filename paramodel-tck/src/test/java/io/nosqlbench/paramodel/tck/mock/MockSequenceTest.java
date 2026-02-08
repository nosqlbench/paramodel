package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.sequence.SequenceTCK;

/**
 * Validates mock Sequence implementation against TCK.
 */
public class MockSequenceTest extends SequenceTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
