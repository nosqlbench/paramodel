package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.plan.ExecutionGraphTCK;

/**
 * Validates mock ExecutionGraph implementation against TCK.
 */
public class MockExecutionGraphTest extends ExecutionGraphTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
