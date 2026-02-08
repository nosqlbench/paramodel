package io.nosqlbench.paramodel.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

///
/// # AuditLog
///
/// Records all security-relevant actions for compliance and auditing.
///
public interface AuditLog {

    static AuditLog create() {
        throw new UnsupportedOperationException(
            "AuditLog.create() requires a concrete implementation");
    }

    void log(AuditEntry entry);

    List<AuditEntry> query(AuditQuery query);

    interface AuditEntry {
        Instant timestamp();
        String userId();
        String action();
        String resource();
        boolean success();
        Map<String, String> metadata();
    }

    interface AuditQuery {
        static Builder builder() {
            throw new UnsupportedOperationException(
                "AuditQuery.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder userId(String userId);
            Builder action(String action);
            Builder after(Instant after);
            Builder before(Instant before);
            AuditQuery build();
        }
    }
}
