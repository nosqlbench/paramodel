package io.nosqlbench.paramodel.tck.core;

import io.nosqlbench.paramodel.core.ValidationResult;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for ValidationResult contract.
 *
 * Validates that implementations correctly:
 * - Report validation status
 * - Provide error messages
 * - List violations
 * - Support warnings
 */
public abstract class ValidationResultTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testValidResult() {
        ValidationResult result = getProvider().createValidationResult(true, "All checks passed");

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    public void testInvalidResult() {
        ValidationResult result = getProvider().createValidationResult(false, "Validation failed");

        assertThat(result.isFailed()).isTrue();
    }

    @Test
    public void testResultHasMessage() {
        ValidationResult result = getProvider().createValidationResult(false, "Missing required field");

        assertThat(result.message()).isPresent();
        assertThat(result.message().get()).contains("Missing");
    }

    @Test
    public void testResultHasViolations() {
        ValidationResult result = getProvider().createValidationResult(false, "Multiple errors");

        assertThat(result.violations()).isNotNull();
        // Violations list should be present even if empty
    }

    @Test
    public void testValidResultMayHaveEmptyViolations() {
        ValidationResult result = getProvider().createValidationResult(true, "OK");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    public void testResultMessageFormat() {
        String message = "Parameter 'threads' must be positive";
        ValidationResult result = getProvider().createValidationResult(false, message);

        assertThat(result.message()).isPresent();
        assertThat(result.message().get()).isEqualTo(message);
    }
}
