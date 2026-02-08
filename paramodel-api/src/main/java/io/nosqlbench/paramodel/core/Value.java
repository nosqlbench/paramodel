package io.nosqlbench.paramodel.core;

import io.nosqlbench.paramodel.sequence.Trial;

import java.time.Instant;
import java.util.Optional;

///
/// A parameter assignment with provenance metadata.
///
/// ## Concept
///
/// A {@code Value<T>} is a wrapper around a concrete value of type {@code T},
/// enriched with metadata about:
/// - **Where** it came from (parameter name)
/// - **When** it was generated (timestamp)
/// - **How** it was generated (generator metadata)
/// - **Why** it's valid (fingerprint for traceability)
///
/// ## Structure
///
/// ```
/// Value<T>
/// ├── value: T                          - The actual value
/// ├── parameterName: String             - Source parameter identifier
/// ├── generatedAt: Instant              - Generation timestamp
/// └── generatorMetadata: Optional<String> - Generation strategy/context
/// ```
///
/// ## Value Lifecycle
///
/// ```
/// Generation Phase:
///   Parameter<T>.generate()
///         ↓
///   Value<T> created
///   ├── value = generated T
///   ├── parameterName = parameter.name()
///   ├── generatedAt = Instant.now()
///   └── generatorMetadata = strategy info
///
/// Usage Phase:
///   Value<T> passed to trial
///         ↓
///   Trial executes with value
///         ↓
///   Results linked via fingerprint
/// ```
///
/// ## Provenance Chain
///
/// Values form a provenance chain that enables tracing results back to generation:
///
/// ```
/// Parameter Definition
///       ↓ generates
/// Value<T> (with timestamp, metadata)
///       ↓ used in
/// Trial (axis assignments)
///       ↓ produces
/// TrialResult (with provenance envelope)
///       ↓ contains
/// ProvenanceEnvelope (fingerprints all values)
/// ```
///
/// ## Immutability
///
/// Values are immutable value objects (typically Java records).
/// Once created, they cannot be modified. This ensures:
/// - Thread safety
/// - Reliable provenance
/// - Reproducible results
///
/// ## Example
///
/// ```java
/// // Generate a value
/// Parameter<Integer> age = ...;
/// Integer rawValue = age.generate();
///
/// // Wrap in Value with metadata
/// Value<Integer> valueObj = new Value<>(
///     rawValue,
///     "age",
///     Instant.now(),
///     Optional.of("random generator with seed 42")
/// );
///
/// // Use in trial
/// Trial trial = Trial.builder()
///     .assign("age", valueObj)
///     .build();
///
/// // Later, trace back
/// String fingerprint = valueObj.fingerprint();  // Cryptographic hash
/// ```
///
/// ## Fingerprinting
///
/// Each value can be fingerprinted (hashed) for traceability:
///
/// ```
/// fingerprint(value) = SHA-256(
///     value.toString(),
///     parameterName,
///     value.getClass().getName()
/// )
/// ```
///
/// Fingerprints enable:
/// - Detecting duplicate values
/// - Linking results to exact configurations
/// - Verifying provenance integrity
///
/// @param <T> the type of the wrapped value
/// @see Parameter
/// @see Trial
/// @see com.paramodel.api.versioning.ProvenanceService
/// @since 0.1.0
///
public interface Value<T> {

    ///
    /// Returns the actual value.
    ///
    /// ## Contract
    ///
    /// - MAY return null if the domain allows null
    /// - MUST be immutable (defensive copy if mutable type)
    /// - MUST remain constant for lifetime of Value object
    ///
    /// @return the wrapped value, possibly null
    ///
    T value();

    ///
    /// Returns the name of the parameter that generated this value.
    ///
    /// Used for:
    /// - Identifying which parameter this value belongs to
    /// - Labeling results in trial records
    /// - Human-readable descriptions
    ///
    /// ## Contract
    ///
    /// - MUST return non-null, non-empty string
    /// - SHOULD match {@link Parameter#name()} of source parameter
    /// - MUST remain constant
    ///
    /// @return parameter name, never null or empty
    ///
    String parameterName();

    ///
    /// Returns the timestamp when this value was generated.
    ///
    /// ## Uses
    ///
    /// - Provenance tracking
    /// - Debugging (when was this value created?)
    /// - Ordering values temporally
    /// - Detecting stale values
    ///
    /// ## Contract
    ///
    /// - MUST return non-null Instant
    /// - SHOULD be close to actual generation time
    /// - MUST remain constant
    ///
    /// @return generation timestamp, never null
    ///
    Instant generatedAt();

    ///
    /// Returns optional metadata about how this value was generated.
    ///
    /// ## Metadata Examples
    ///
    /// ```
    /// "random generator with seed 42"
    /// "boundary value (min)"
    /// "explicit user-provided value"
    /// "exhaustive enumeration index 17"
    /// "edge-first scaffold point"
    /// ```
    ///
    /// ## Uses
    ///
    /// - Debugging generation strategies
    /// - Reproducing specific values
    /// - Understanding trial ordering
    /// - Documentation
    ///
    /// ## Contract
    ///
    /// - MUST return non-null Optional
    /// - MAY be empty if no metadata available
    /// - SHOULD be human-readable
    ///
    /// @return generator metadata if available, empty otherwise
    ///
    Optional<String> generatorMetadata();

    ///
    /// Validates this value against a constraint.
    ///
    /// ## Convenience Method
    ///
    /// This is equivalent to:
    /// ```java
    /// constraint.test(value.value())
    ///     ? ValidationResult.passed()
    ///     : ValidationResult.failed(...)
    /// ```
    ///
    /// ## Semantics
    ///
    /// ```
    /// validate(constraint):
    ///   1. Extract wrapped value
    ///   2. Apply constraint.test(value)
    ///   3. Wrap result in ValidationResult
    ///   4. Include parameter name in violations
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Value<Integer> age = new Value<>(150, "age", Instant.now(), Optional.empty());
    /// Constraint<Integer> validAge = n -> n >= 0 && n <= 120;
    ///
    /// ValidationResult result = age.validate(validAge);
    /// // result is Failed("age parameter value 150 exceeds max 120")
    /// ```
    ///
    /// @param constraint the constraint to validate against
    /// @return validation result with parameter context
    ///
    ValidationResult validate(Constraint<T> constraint);

    ///
    /// Computes a cryptographic fingerprint of this value.
    ///
    /// ## Fingerprint Calculation
    ///
    /// ```
    /// fingerprint = SHA-256(
    ///     parameterName +
    ///     ":" +
    ///     value.getClass().getName() +
    ///     ":" +
    ///     value.toString()
    /// )
    /// ```
    ///
    /// ## Properties
    ///
    /// - **Deterministic**: Same value → same fingerprint
    /// - **Unique**: Different values → different fingerprints (with high probability)
    /// - **Tamper-evident**: Any change produces different fingerprint
    ///
    /// ## Uses
    ///
    /// - Deduplication: Detect identical values
    /// - Provenance: Link results to exact value configuration
    /// - Caching: Use as cache key
    /// - Integrity: Verify value hasn't been modified
    ///
    /// ## Example
    ///
    /// ```java
    /// Value<Integer> v1 = new Value<>(42, "age", Instant.now(), Optional.empty());
    /// Value<Integer> v2 = new Value<>(42, "age", Instant.now().plusSeconds(1), Optional.empty());
    ///
    /// // Same fingerprint (value and name are same)
    /// assert v1.fingerprint().equals(v2.fingerprint());
    ///
    /// Value<Integer> v3 = new Value<>(43, "age", Instant.now(), Optional.empty());
    /// // Different fingerprint (value differs)
    /// assert !v1.fingerprint().equals(v3.fingerprint());
    /// ```
    ///
    /// ## Implementation Note
    ///
    /// Implementations SHOULD use SHA-256 or stronger.
    /// Result format: hex string (64 characters for SHA-256).
    ///
    /// @return hex-encoded fingerprint, never null
    /// @see com.paramodel.api.versioning.ProvenanceService
    ///
    String fingerprint();
}
