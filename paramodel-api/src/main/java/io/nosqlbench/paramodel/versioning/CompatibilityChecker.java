package io.nosqlbench.paramodel.versioning;

import io.nosqlbench.paramodel.plan.TestPlan;

///
/// # CompatibilityChecker
///
/// Checks compatibility between different versions of test plans and execution plans.
///
public interface CompatibilityChecker {

    static CompatibilityChecker create() {
        throw new UnsupportedOperationException(
            "CompatibilityChecker.create() requires a concrete implementation");
    }

    boolean isCompatible(String version1, String version2);

    CompatibilityReport checkCompatibility(TestPlan plan1, TestPlan plan2);

    interface CompatibilityReport {
        boolean isCompatible();
        String message();
        CompatibilityLevel level();
    }

    enum CompatibilityLevel {
        FULLY_COMPATIBLE,
        BACKWARD_COMPATIBLE,
        FORWARD_COMPATIBLE,
        INCOMPATIBLE
    }
}
