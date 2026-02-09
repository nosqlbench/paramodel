package io.nosqlbench.paramodel.tck.security;

import io.nosqlbench.paramodel.security.AuditLog;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link AuditLog} implementations.
///
/// Validates log entry recording and query filtering
/// for audit trail persistence.
///
/// @since 0.1.0
///
public abstract class AuditLogTCK {

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        auditLog = getProvider().createAuditLog();
    }

    @Test
    void testLogEntry() {
        AuditLog.AuditEntry entry = getProvider().createAuditEntry(
            "user1", "execute", "plan-abc", true);
        auditLog.log(entry);

        AuditLog.AuditQuery allQuery = getProvider().createAuditQuery(null, null);
        List<AuditLog.AuditEntry> results = auditLog.query(allQuery);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).userId()).isEqualTo("user1");
        assertThat(results.get(0).action()).isEqualTo("execute");
        assertThat(results.get(0).resource()).isEqualTo("plan-abc");
        assertThat(results.get(0).success()).isTrue();
    }

    @Test
    void testQueryByUser() {
        auditLog.log(getProvider().createAuditEntry("user1", "read", "plan-a", true));
        auditLog.log(getProvider().createAuditEntry("user2", "write", "plan-b", true));
        auditLog.log(getProvider().createAuditEntry("user1", "delete", "plan-c", false));

        AuditLog.AuditQuery query = getProvider().createAuditQuery("user1", null);
        List<AuditLog.AuditEntry> results = auditLog.query(query);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> "user1".equals(e.userId()));
    }

    @Test
    void testQueryByAction() {
        auditLog.log(getProvider().createAuditEntry("user1", "read", "plan-a", true));
        auditLog.log(getProvider().createAuditEntry("user2", "read", "plan-b", true));
        auditLog.log(getProvider().createAuditEntry("user1", "write", "plan-c", true));

        AuditLog.AuditQuery query = getProvider().createAuditQuery(null, "read");
        List<AuditLog.AuditEntry> results = auditLog.query(query);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> "read".equals(e.action()));
    }
}
