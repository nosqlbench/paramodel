package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.plan.ExecutionPlanTCK;

/**
 * Validates mock ExecutionPlan implementation against TCK.
 */
public class MockExecutionPlanTest extends ExecutionPlanTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
