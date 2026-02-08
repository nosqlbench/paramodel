package io.nosqlbench.paramodel.util;

import io.nosqlbench.paramodel.core.ValidationResult;

///
/// # ValidationUtil
///
/// Utility methods for validation operations.
///
public interface ValidationUtil {

    static ValidationResult validate(Object object) {
        throw new UnsupportedOperationException(
            "ValidationUtil.validate() requires a concrete implementation");
    }

    static boolean isValid(Object object) {
        return validate(object).isPassed();
    }
}
