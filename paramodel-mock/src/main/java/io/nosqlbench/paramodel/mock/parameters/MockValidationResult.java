package io.nosqlbench.paramodel.mock.parameters;

import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.List;

/**
 * Factory methods for creating ValidationResult instances in mock implementations.
 */
public class MockValidationResult {

    private MockValidationResult() {
        // Utility class
    }

    public static ValidationResult passed() {
        return new ValidationResult.Passed();
    }

    public static ValidationResult failed(String message) {
        return new ValidationResult.Failed(message, List.of(message));
    }

    public static ValidationResult failed(String message, List<String> violations) {
        return new ValidationResult.Failed(message, violations);
    }

    public static ValidationResult warning(String message) {
        return new ValidationResult.Warning(message, new ValidationResult.Passed());
    }

    public static ValidationResult warning(String message, ValidationResult underlying) {
        return new ValidationResult.Warning(message, underlying);
    }
}
