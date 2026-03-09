package io.nosqlbench.paramodel.sequence;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// A single point in the parameter space representing one set of parameter assignments.
///
/// ## Concept
///
/// A {@code Trial} is a concrete assignment of values to parameters that will be
/// executed to produce results. It represents one test case in a study.
///
/// ## Structure
///
/// ```
/// Trial
/// ├── id: String
/// │   └── Unique identifier for this trial
/// │
/// ├── assignments: Map<String, Map<String, Value<?>>>
/// │   └── elementName → parameterName → Value<T>
/// │   └── Two-level map: element → param → value
/// │
/// ├── constraints: List<Constraint<?>>
/// │   └── Cross-parameter constraints this trial must satisfy
/// │
/// └── metadata: Optional<TrialMetadata>
///     └── Additional context (index, group, etc.)
/// ```
///
/// ## Trial in Parameter Space
///
/// ```
/// Parameter Space (Cartesian product):
///   P1 × P2 × ... × Pn
///
/// A Trial is a single point:
///   t = (v1, v2, ..., vn) where vi ∈ Pi
///
/// Example:
///   Elements: {server, client}
///   Parameters: server.threads ∈ [1, 64], client.mode ∈ {sync, async}
///   Trial: {"server" → {"threads" → 42}, "client" → {"mode" → "async"}}
/// ```
///
/// ## Trial Lifecycle
///
/// ```
/// Generation:
///   Sequence generation algorithm
///         ↓
///   Trial created with assignments
///   ├── id assigned (UUID or sequential)
///   ├── values selected from parameter domains
///   └── constraints attached
///
/// Validation:
///   Trial.validate()
///         ↓
///   Check all constraints satisfied
///         ↓
///   ValidationResult (Passed/Failed)
///
/// Execution:
///   TrialExecutor.execute(trial)
///         ↓
///   Run test with assigned values
///         ↓
///   TrialResult (metrics + artifacts)
///
/// Persistence:
///   ResultStore.save(result)
///         ↓
///   Trial linked to results via id
/// ```
///
/// ## Trial Identity
///
/// Each trial has a unique identifier used for:
/// - Linking results back to trials
/// - Tracking execution status
/// - Resuming after failures
/// - Deduplication
///
/// ## Cross-Parameter Constraints
///
/// Trials can have constraints relating multiple parameters:
///
/// ```
/// Example:
///   Parameters: startDate, endDate
///   Constraint: startDate < endDate
///
///   Trial {
///     "timer" → {startDate → 2026-01-01, endDate → 2026-12-31},
///     constraints: [startDate < endDate]
///   }
/// ```
///
/// ## Assignment Completeness
///
/// Every trial MUST have a value for every parameter:
///
/// ```
/// Parameters: {p1, p2, p3}
/// Trial assignments: {"elem" → {p1 → v1, p2 → v2, p3 → v3}}
///
/// Partial assignments are NOT allowed:
///   Trial {"elem" → {p1 → v1}} ✗ Missing p2, p3
/// ```
///
/// ## Example: Simple Trial
///
/// ```java
/// // Create trial with element-structured assignments
/// Trial trial = Trial.builder()
///     .id("trial-001")
///     .assignment("server", "threads", threadValue)
///     .assignment("server", "heap", heapValue)
///     .assignment("client", "mode", modeValue)
///     .build();
///
/// // Validate
/// ValidationResult result = trial.validate();
/// assert result.isPassed();
///
/// // Access assignments by element
/// Map<String, Value<?>> serverParams = trial.assignments().get("server");
/// Value<?> threads = trial.assignment("server", "threads").orElseThrow();
/// ```
///
/// ## Relationship to Simplica
///
/// In Simplica, trials are enhanced with execution context:
///
/// ```
/// Paramodel Trial:
///   - Parameter assignments
///   - Constraints
///   - Validation
///
/// Simplica ExecutionPlan Trial:
///   - Everything from Paramodel Trial
///   - Element dependencies
///   - Resource requirements
///   - Retry policies
///   - Execution barriers
/// ```
///
/// @see Sequence
/// @see Value
/// @see TrialResult
/// @since 0.1.0
///
public interface Trial {

    ///
    /// Returns the unique identifier for this trial.
    ///
    /// ## Identity Requirements
    ///
    /// - MUST be unique within a sequence
    /// - SHOULD be unique across all sequences (recommend UUID)
    /// - MUST be stable (never changes)
    /// - MUST be serializable
    ///
    /// ## Common Formats
    ///
    /// ```
    /// UUID:              "550e8400-e29b-41d4-a716-446655440000"
    /// Sequential:        "trial-001", "trial-002", ...
    /// Hierarchical:      "seq-1/trial-5"
    /// Content-based:     SHA-256(assignments) - deterministic
    /// ```
    ///
    /// @return trial identifier, never null or empty
    ///
    String id();

    ///
    /// Returns the parameter assignments for this trial, structured by element.
    ///
    /// ## Assignment Map
    ///
    /// ```
    /// Map<String, Map<String, Value<?>>>
    ///   ↓
    /// elementName → parameterName → Value<T>
    ///
    /// Example:
    ///   {
    ///     "server" → {
    ///       "threads" → Value<Integer>(42),
    ///       "heap" → Value<Integer>(512)
    ///     },
    ///     "client" → {
    ///       "mode" → Value<String>("async")
    ///     }
    ///   }
    /// ```
    ///
    /// ## Completeness Invariant
    ///
    /// For a trial to be valid:
    /// ```
    /// ∀ element e, parameter p ∈ e.parameters:
    ///   ∃ assignment a ∈ trial.assignments().get(e.name()): a.key = p.name
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null, unmodifiable map
    /// - Outer keys are element names
    /// - Inner keys are parameter names within that element
    /// - Values MUST be non-null
    ///
    /// @return immutable nested assignment map, never null
    ///
    Map<String, Map<String, Value<?>>> assignments();

    ///
    /// Returns the value assigned to a specific parameter on a specific element.
    ///
    /// ## Convenience Accessor
    ///
    /// This is equivalent to:
    /// ```java
    /// Optional.ofNullable(trial.assignments()
    ///     .getOrDefault(elementName, Map.of())
    ///     .get(parameterName))
    /// ```
    ///
    /// ## Type Safety
    ///
    /// The returned value is untyped {@code Value<?>}. Callers must cast:
    /// ```java
    /// Optional<Value<?>> opt = trial.assignment("server", "threads");
    /// Value<Integer> threads = (Value<Integer>) opt.orElseThrow();
    /// Integer threadCount = threads.value();
    /// ```
    ///
    /// @param elementName the element to look up
    /// @param parameterName the parameter to look up within that element
    /// @return assigned value if present
    ///
    Optional<Value<?>> assignment(String elementName, String parameterName);

    ///
    /// Returns cross-parameter constraints that this trial must satisfy.
    ///
    /// ## Cross-Parameter Constraints
    ///
    /// These constraints relate multiple parameters:
    ///
    /// ```
    /// Single-parameter:     age ≥ 18
    /// Cross-parameter:      startDate < endDate
    /// Cross-parameter:      width * height ≤ maxArea
    /// ```
    ///
    /// ## Constraint Application
    ///
    /// Constraints receive the full assignment map:
    /// ```java
    /// Constraint<Map<String, Value<?>>> c = assignments -> {
    ///     int age = (Integer) assignments.get("age").value();
    ///     boolean hasConsent = (Boolean) assignments.get("hasConsent").value();
    ///     return age >= 18 || hasConsent;
    /// };
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null, unmodifiable list
    /// - MAY be empty if no cross-parameter constraints
    /// - Constraints MUST be applicable to the assignment map
    ///
    /// @return immutable constraint list, never null
    ///
    List<Constraint<Map<String, Value<?>>>> constraints();

    ///
    /// Validates this trial by checking all constraints.
    ///
    /// ## Validation Process
    ///
    /// ```
    /// validate():
    ///   1. Check assignment completeness
    ///      - All required parameters have values
    ///
    ///   2. Validate each individual value
    ///      - value.validate(parameter.constraints())
    ///
    ///   3. Check cross-parameter constraints
    ///      - ∀ c ∈ constraints: c.test(assignments)
    ///
    ///   4. Return aggregated result
    ///      - Passed if all checks pass
    ///      - Failed with violations if any check fails
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Trial trial = ...;
    /// ValidationResult result = trial.validate();
    ///
    /// if (result.isFailed()) {
    ///     System.err.println("Trial validation failed:");
    ///     result.violations().forEach(System.err::println);
    ///     throw new InvalidTrialException(result);
    /// }
    ///
    /// // Proceed with execution
    /// TrialResult outcome = executor.execute(trial);
    /// ```
    ///
    /// @return validation result with details
    /// @see ValidationResult
    ///
    ValidationResult validate();

    ///
    /// Returns optional metadata about this trial's context and generation.
    ///
    /// ## Metadata Examples
    ///
    /// - Sequence index: "Trial 42 of 100"
    /// - Generation strategy: "Edge-first boundary point"
    /// - Grouping: "Part of parameter sweep group A"
    /// - Priority: "High-priority trial"
    ///
    /// @return trial metadata if available
    ///
    Optional<TrialMetadata> metadata();

    ///
    /// Descriptive metadata about a trial's context within a sequence.
    ///
    /// ## Metadata Fields
    ///
    /// - **Index**: Position in sequence (0-based)
    /// - **Group**: Logical grouping for related trials
    /// - **Generation**: How this trial was selected
    /// - **Priority**: Execution priority hint
    ///
    interface TrialMetadata {
        /// Returns the 0-based index of this trial in its sequence
        Optional<Integer> sequenceIndex();

        /// Returns the logical group identifier (e.g., for batching)
        Optional<String> group();

        /// Returns how this trial was generated
        Optional<String> generationMethod();

        /// Returns execution priority (higher = more important)
        Optional<Integer> priority();
    }
}
