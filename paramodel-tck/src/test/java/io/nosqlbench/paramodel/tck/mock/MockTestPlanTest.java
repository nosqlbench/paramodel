package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.plan.TestPlanTCK;

/**
 * Validates mock TestPlan implementation against TCK.
 */
public class MockTestPlanTest extends TestPlanTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
