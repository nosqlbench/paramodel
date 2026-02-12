package io.nosqlbench.paramodel.mock.security;

import io.nosqlbench.paramodel.security.AuditLog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

///
/// In-memory audit log for testing.
///
/// Stores audit entries in a list with basic query filtering
/// by user ID, action, and time range.
///
/// @see AuditLog
/// @since 0.1.0
///
public class MockAuditLog implements AuditLog {
    private final List<AuditEntry> entries = new ArrayList<>();

    /// Creates a new empty audit log.
    public MockAuditLog() {}

    @Override
    public void log(AuditEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<AuditEntry> query(AuditQuery query) {
        if (query instanceof MockAuditQuery mq) {
            Stream<AuditEntry> stream = entries.stream();
            if (mq.userId != null) {
                stream = stream.filter(e -> mq.userId.equals(e.userId()));
            }
            if (mq.action != null) {
                stream = stream.filter(e -> mq.action.equals(e.action()));
            }
            if (mq.after != null) {
                stream = stream.filter(e -> e.timestamp().isAfter(mq.after));
            }
            if (mq.before != null) {
                stream = stream.filter(e -> e.timestamp().isBefore(mq.before));
            }
            return stream.toList();
        }
        return List.copyOf(entries);
    }

    ///
    /// Creates a new audit query builder.
    ///
    /// @return a new builder
    ///
    public static MockAuditQuery.Builder queryBuilder() {
        return new MockAuditQuery.Builder();
    }

    ///
    /// Creates a new audit entry.
    ///
    /// @param userId user performing the action
    /// @param action the action performed
    /// @param resource the resource affected
    /// @param success whether the action succeeded
    /// @return audit entry
    ///
    public static AuditEntry entry(String userId, String action, String resource, boolean success) {
        return new MockAuditEntry(Instant.now(), userId, action, resource, success, Map.of());
    }

    ///
    /// Simple audit entry implementation.
    ///
    public record MockAuditEntry(
        Instant timestamp,
        String userId,
        String action,
        String resource,
        boolean success,
        Map<String, String> metadata
    ) implements AuditEntry {}

    ///
    /// Simple audit query implementation.
    ///
    public static class MockAuditQuery implements AuditQuery {
        final String userId;
        final String action;
        final Instant after;
        final Instant before;

        MockAuditQuery(String userId, String action, Instant after, Instant before) {
            this.userId = userId;
            this.action = action;
            this.after = after;
            this.before = before;
        }

        ///
        /// Builder for MockAuditQuery.
        ///
        public static class Builder implements AuditQuery.Builder {
            /// Creates a new audit query builder.
            public Builder() {}

            private String userId;
            private String action;
            private Instant after;
            private Instant before;

            @Override
            public AuditQuery.Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            @Override
            public AuditQuery.Builder action(String action) {
                this.action = action;
                return this;
            }

            @Override
            public AuditQuery.Builder after(Instant after) {
                this.after = after;
                return this;
            }

            @Override
            public AuditQuery.Builder before(Instant before) {
                this.before = before;
                return this;
            }

            @Override
            public AuditQuery build() {
                return new MockAuditQuery(userId, action, after, before);
            }
        }
    }
}
