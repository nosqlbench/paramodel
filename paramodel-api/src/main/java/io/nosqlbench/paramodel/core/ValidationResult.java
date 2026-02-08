package io.nosqlbench.paramodel.core;

import java.util.List;
import java.util.Optional;

///
/// Represents the outcome of validating a value against constraints.
///
/// ## Concept
///
/// Validation checks whether a value satisfies all required constraints.
/// The result captures not just pass/fail, but also:
/// - Specific violations that occurred
/// - Warnings for suspicious but technically valid values
/// - Contextual information for debugging
///
/// ## Result Type Hierarchy
///
/// ```
/// ValidationResult (sealed)
/// │
/// ├── Passed
/// │   └── All constraints satisfied
/// │   └── No violations or warnings
/// │
/// ├── Failed
/// │   ├── message: String              - Human-readable summary
/// │   └── violations: List<String>     - Specific constraint failures
/// │
/// └── Warning
///     ├── message: String              - Warning description
///     └── underlying: ValidationResult - Actual result (usually Passed)
/// ```
///
/// ## State Transition Model
///
/// ```
/// Validation Process:
///
///   Value + Constraints
///         ↓
///   ┌─────────────┐
///   │ Check domain│
///   │ membership  │
///   └─────┬───────┘
///         │ ✓ in domain
///         ↓
///   ┌─────────────┐
///   │ Check type  │
///   │ correctness │
///   └─────┬───────┘
///         │ ✓ correct type
///         ↓
///   ┌─────────────┐
///   │ Check each  │
///   │ constraint  │
///   └─────┬───────┘
///         │
///         ├─→ All pass → Passed
///         │
///         ├─→ Some fail → Failed(violations)
///         │
///         └─→ Pass with caveats → Warning(Passed)
/// ```
///
/// ## Usage Patterns
///
/// ### Basic Validation
///
/// ```java
/// Parameter<Integer> age = ...;
/// ValidationResult result = age.validate(42);
///
/// switch (result) {
///     case Passed p -> System.out.println("Valid!");
///     case Failed f -> System.err.println("Violations: " + f.violations());
///     case Warning w -> System.out.println("Warning: " + w.message());
/// }
/// ```
///
/// ### Programmatic Checks
///
/// ```java
/// if (result.isPassed()) {
///     // Proceed with value
/// } else if (result.isFailed()) {
///     throw new ValidationException(result.message().orElse("Invalid"));
/// }
/// ```
///
/// ### Collecting Violations
///
/// ```java
/// List<String> allViolations = result.violations();
/// if (!allViolations.isEmpty()) {
///     log.error("Validation failed: {}", String.join(", ", allViolations));
/// }
/// ```
///
/// ## Contract Requirements
///
/// All implementations MUST:
///
/// 1. **Immutability**: Results MUST be immutable
/// 2. **Thread Safety**: All methods MUST be safe for concurrent access
/// 3. **Non-null**: Message and violations MUST never return null (use Optional/empty list)
/// 4. **Consistency**: State methods (isPassed, isFailed) MUST be consistent with actual type
///
/// @see Parameter#validate(Object)
/// @see Constraint#test(Object)
/// @since 0.1.0
///
public sealed interface ValidationResult
    permits ValidationResult.Passed,
            ValidationResult.Failed,
            ValidationResult.Warning {

    ///
    /// Checks if validation passed successfully.
    ///
    /// ## Semantics
    ///
    /// ```
    /// isPassed() = true ⟺ result is Passed or Warning(Passed)
    /// ```
    ///
    /// Note: Warnings are considered a passing result with caveats.
    ///
    /// @return true if validation passed (possibly with warnings)
    ///
    boolean isPassed();

    ///
    /// Checks if validation failed.
    ///
    /// ## Semantics
    ///
    /// ```
    /// isFailed() = true ⟺ result is Failed or Warning(Failed)
    /// ```
    ///
    /// @return true if validation failed
    ///
    boolean isFailed();

    ///
    /// Returns the primary message describing the validation result.
    ///
    /// ## Return Values
    ///
    /// ```
    /// Passed:              Optional.empty()
    /// Failed(message):     Optional.of(message)
    /// Warning(message):    Optional.of(message)
    /// ```
    ///
    /// @return message if present, empty otherwise
    ///
    Optional<String> message();

    ///
    /// Returns the list of specific constraint violations.
    ///
    /// ## Return Values
    ///
    /// ```
    /// Passed:                    []
    /// Failed(_, violations):     violations
    /// Warning(_, Passed):        []
    /// Warning(_, Failed):        underlying violations
    /// ```
    ///
    /// ## Violation Format
    ///
    /// Each violation string SHOULD describe:
    /// - Which constraint failed
    /// - What value was tested
    /// - Why it failed
    ///
    /// Example: "Constraint 'positive' failed: value -5 is not > 0"
    ///
    /// @return list of violations, empty if none
    ///
    List<String> violations();

    ///
    /// Validation passed successfully with no violations or warnings.
    ///
    /// ## Semantics
    ///
    /// ```
    /// Passed ⟺ ∀c ∈ constraints: c.test(value) = true
    /// ```
    ///
    /// This is the "happy path" outcome.
    ///
    /// ## Example
    ///
    /// ```java
    /// Parameter<Integer> age = Domain.range(0, 120);
    /// ValidationResult result = age.validate(42);
    /// // result is Passed
    ///
    /// assert result.isPassed();
    /// assert !result.isFailed();
    /// assert result.message().isEmpty();
    /// assert result.violations().isEmpty();
    /// ```
    ///
    record Passed() implements ValidationResult {
        @Override
        public boolean isPassed() {
            return true;
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        @Override
        public Optional<String> message() {
            return Optional.empty();
        }

        @Override
        public List<String> violations() {
            return List.of();
        }
    }

    ///
    /// Validation failed due to constraint violations.
    ///
    /// ## Semantics
    ///
    /// ```
    /// Failed ⟺ ∃c ∈ constraints: c.test(value) = false
    /// ```
    ///
    /// Contains detailed information about what went wrong.
    ///
    /// ## Structure
    ///
    /// ```
    /// Failed
    /// ├── message: String
    /// │   └── High-level summary (e.g., "Value 150 out of range")
    /// │
    /// └── violations: List<String>
    ///     ├── "Constraint 'range' failed: 150 > max(120)"
    ///     ├── "Constraint 'even' failed: 150 is odd"
    ///     └── ...
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Constraint<Integer> positive = n -> n > 0;
    /// Constraint<Integer> even = n -> n % 2 == 0;
    /// Constraint<Integer> combined = positive.and(even);
    ///
    /// ValidationResult result = validate(-5, combined);
    /// // result is Failed
    ///
    /// assert result.isFailed();
    /// assert result.message().isPresent();
    /// assert result.violations().contains("positive constraint failed");
    /// ```
    ///
    /// @param message high-level failure summary
    /// @param violations specific constraint failures
    ///
    record Failed(String message, List<String> violations) implements ValidationResult {
        public Failed {
            if (message == null) {
                throw new IllegalArgumentException("message must not be null");
            }
            if (violations == null) {
                violations = List.of();
            }
        }

        @Override
        public boolean isPassed() {
            return false;
        }

        @Override
        public boolean isFailed() {
            return true;
        }

        @Override
        public Optional<String> message() {
            return Optional.of(message);
        }

        @Override
        public List<String> violations() {
            return violations;
        }
    }

    ///
    /// Validation passed but with warnings about suspicious values.
    ///
    /// ## Semantics
    ///
    /// ```
    /// Warning ⟺ Technically valid but raises concerns
    ///
    /// Examples:
    ///   - Value at extreme boundary (warn: "Value 120 is at max limit")
    ///   - Unusual but legal combination (warn: "Age 0 with active=true")
    ///   - Deprecated value usage (warn: "API version 'v1' is deprecated")
    /// ```
    ///
    /// ## Structure
    ///
    /// ```
    /// Warning
    /// ├── message: String
    /// │   └── Description of the warning
    /// │
    /// └── underlying: ValidationResult
    ///     └── Usually Passed, but can wrap Failed
    /// ```
    ///
    /// ## Use Cases
    ///
    /// Warnings allow validation to succeed while still alerting users:
    /// - Boundary values that are technically valid but risky
    /// - Deprecated but still supported values
    /// - Unusual combinations that deserve scrutiny
    /// - Performance concerns (e.g., very large values)
    ///
    /// ## Example
    ///
    /// ```java
    /// Parameter<Integer> age = Domain.range(0, 120);
    /// ValidationResult result = age.validate(120);
    ///
    /// // Implementation might return:
    /// // new Warning("Value at maximum boundary", new Passed())
    ///
    /// assert result.isPassed();  // Still passes
    /// assert result.message().isPresent();  // But has warning
    /// ```
    ///
    /// ## Warning vs Failed
    ///
    /// ```
    /// Decision Flow:
    ///
    ///   Value violates hard constraint?
    ///   ├─ Yes → Failed
    ///   └─ No → Passes
    ///       ↓
    ///       Value raises concern?
    ///       ├─ Yes → Warning(Passed)
    ///       └─ No → Passed
    /// ```
    ///
    /// @param message warning description
    /// @param underlying the actual validation result
    ///
    record Warning(String message, ValidationResult underlying) implements ValidationResult {
        public Warning {
            if (message == null) {
                throw new IllegalArgumentException("message must not be null");
            }
            if (underlying == null) {
                throw new IllegalArgumentException("underlying result must not be null");
            }
            // Prevent nested warnings (flatten)
            if (underlying instanceof Warning w) {
                underlying = w.underlying();
            }
        }

        @Override
        public boolean isPassed() {
            return underlying.isPassed();
        }

        @Override
        public boolean isFailed() {
            return underlying.isFailed();
        }

        @Override
        public Optional<String> message() {
            return Optional.of(message);
        }

        @Override
        public List<String> violations() {
            return underlying.violations();
        }
    }
}
