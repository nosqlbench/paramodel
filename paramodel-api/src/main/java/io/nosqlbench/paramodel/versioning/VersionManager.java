package io.nosqlbench.paramodel.versioning;

import io.nosqlbench.paramodel.plan.TestPlan;

import java.util.List;

///
/// # VersionManager
///
/// Manages versions of test plans and execution plans.
///
public interface VersionManager {

    static VersionManager create() {
        throw new UnsupportedOperationException(
            "VersionManager.create() requires a concrete implementation");
    }

    void saveVersion(TestPlan plan, String version);

    TestPlan getVersion(String fingerprint, String version);

    List<String> listVersions(String fingerprint);

    String latestVersion(String fingerprint);
}
