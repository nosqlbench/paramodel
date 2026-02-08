package io.nosqlbench.paramodel.versioning;

import io.nosqlbench.paramodel.plan.TestPlan;

///
/// # MigrationStrategy
///
/// Defines strategies for migrating test plans between versions.
///
public interface MigrationStrategy {

    TestPlan migrate(TestPlan plan, String fromVersion, String toVersion);

    boolean canMigrate(String fromVersion, String toVersion);
}
