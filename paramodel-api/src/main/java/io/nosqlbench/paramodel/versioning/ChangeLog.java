package io.nosqlbench.paramodel.versioning;

import java.time.Instant;
import java.util.List;

///
/// # ChangeLog
///
/// Tracks changes between versions of test plans.
///
public interface ChangeLog {

    static ChangeLog create() {
        throw new UnsupportedOperationException(
            "ChangeLog.create() requires a concrete implementation");
    }

    void recordChange(Change change);

    List<Change> getChanges(String fingerprint);

    List<Change> getChanges(String fingerprint, String fromVersion, String toVersion);

    interface Change {
        String fingerprint();
        String version();
        ChangeType type();
        String description();
        Instant timestamp();
        String author();
    }

    enum ChangeType {
        ADDED,
        MODIFIED,
        REMOVED,
        RENAMED
    }
}
