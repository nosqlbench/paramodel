package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.sequence.TrialResultTCK;

///
/// Validates mock TrialResult implementation against TCK.
///
public class MockTrialResultTest extends TrialResultTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
