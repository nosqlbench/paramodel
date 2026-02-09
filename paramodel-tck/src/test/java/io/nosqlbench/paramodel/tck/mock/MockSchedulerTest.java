package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.execution.SchedulerTCK;

///
/// Runs SchedulerTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockSchedulerTest extends SchedulerTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
