package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.tck.ImplementationProvider;
import io.nosqlbench.paramodel.tck.security.AuditLogTCK;

///
/// Runs AuditLogTCK tests against the mock implementation.
///
/// @since 0.1.0
///
class MockAuditLogTest extends AuditLogTCK {
    @Override
    protected ImplementationProvider getProvider() {
        return new MockImplementationProvider();
    }
}
