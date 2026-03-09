package io.nosqlbench.paramodel.parameters;

import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.TrialOrdering;

import java.util.Optional;

///
/// A testable parameter dimension with a domain, constraints, and value generation capabilities.
///
/// ## Concept
///
/// A {@code Parameter<T>} represents a single dimension in a parameter space. It defines:
/// - **Name**: Unique identifier within a scope
/// - **Domain**: The set of valid values (see {@link Domain})
/// - **Type**: The value type {@code T}
/// - **Constraints**: Predicates that values must satisfy
/// - **Generation**: Methods for producing valid values
///
/// ## Parameter Lifecycle
///
/// ```
/// Definition Phase:
///   Parameter<T> created
///   ├── Domain<T> specified
///   └── Constraints added
///
/// Usage Phase:
///   ├── generate() → produces Value<T>
///   ├── validate(value) → checks constraints
///   └── satisfies(constraint) → tests predicate
/// ```
///
/// ## Type Categories
///
/// Built-in parameter types are available in
/// {@link io.nosqlbench.paramodel.parameters.types}:
///
/// ```
/// Parameter<T>
/// +-- IntegerParameter           - Integer ranges or discrete sets
/// |   +-- IntegerParameter.range("threads", 1, 64)
/// |   +-- IntegerParameter.of("batch", Set.of(32, 64, 128))
/// +-- DoubleParameter            - Continuous double ranges
/// |   +-- DoubleParameter.range("temperature", 0.0, 1.0)
/// +-- BooleanParameter           - Boolean {true, false}
/// |   +-- BooleanParameter.of("enable_cache")
/// +-- SelectionParameter         - Single or multi-select from strings
///     +-- SelectionParameter.of("region", Set.of("us-east-1", "eu-west-1"))
///     +-- SelectionParameter.external("model", resolver)
/// ```
///
/// ## Algebraic Properties
///
/// Parameters compose to form higher-dimensional spaces:
///
/// ```
/// Parameter<A> ⊗ Parameter<B> = Parameter<(A, B)>
///
/// Where (A, B) represents a tuple type and ⊗ is the composition operator.
/// ```
///
/// The composed parameter's domain is the Cartesian product:
///
/// ```
/// Domain<A> × Domain<B> = Domain<(A, B)>
/// |Domain<(A,B)>| = |Domain<A>| × |Domain<B>|
/// ```
///
/// ## Contract Requirements
///
/// All implementations MUST ensure:
///
/// 1. **Domain Consistency**: Generated values MUST be within the declared domain
/// 2. **Constraint Satisfaction**: Generated values SHOULD satisfy all constraints (best-effort)
/// 3. **Validation Correctness**: {@code validate()} MUST accurately check constraints
/// 4. **Immutability**: Parameter definitions MUST be immutable after creation
/// 5. **Thread Safety**: All methods MUST be safe for concurrent calls
///
/// ## Usage Example
///
/// ```java
/// // Create a discrete integer parameter
/// Parameter<Integer> ageParam = ...;
///
/// // Check domain
/// Domain<Integer> domain = ageParam.domain();
/// assert domain.contains(42);
///
/// // Generate values
/// Integer value1 = ageParam.generate();          // Any valid value
/// Integer boundary = ageParam.generateBoundary(); // Extrema value
/// Integer random = ageParam.generateRandom();    // Random within domain
///
/// // Validate
/// ValidationResult result = ageParam.validate(value1);
/// if (result instanceof ValidationResult.Passed) {
///     // Value satisfies all constraints
/// }
///
/// // Test constraints
/// Constraint<Integer> positive = n -> n > 0;
/// boolean satisfies = ageParam.satisfies(positive);
/// ```
///
/// ## Relationship to Simplica
///
/// In Simplica, parameters become **Axes** when used in Test Plans:
///
/// ```
/// Parameter<T> (paramodel) → Axis<T> (Simplica Test Plan)
///
/// Trial Space = Cartesian Product of all Axes
/// |Trial Space| = ∏(|Axis_i.domain()|)
/// ```
///
/// @param <T> the type of values this parameter produces
/// @see Domain
/// @see Constraint
/// @see Value
/// @see Axis
/// @since 0.1.0
///
public interface Parameter<T> {

    ///
    /// Returns the unique name of this parameter within its scope.
    ///
    /// Names are used for:
    /// - Identifying parameters in composite structures
    /// - Labeling values in result records
    /// - Creating human-readable descriptions
    ///
    /// ## Contract
    ///
    /// - MUST return a non-null, non-empty string
    /// - SHOULD be unique within a parameter set
    /// - MUST remain constant for the lifetime of the parameter
    ///
    /// @return the parameter name, never null or empty
    ///
    String name();

    ///
    /// Returns the domain defining valid values for this parameter.
    ///
    /// ## Domain Relationship
    ///
    /// ```
    /// Parameter<T>
    ///     ↓ defines
    /// Domain<T>
    ///     ↓ contains
    /// Set of Valid Values
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return a non-null domain
    /// - All generated values MUST be members of this domain
    /// - Domain MUST remain constant (immutable)
    ///
    /// @return the value domain, never null
    /// @see Domain
    ///
    Domain<T> domain();

    ///
    /// Generates a value from this parameter's domain.
    ///
    /// The generation strategy is implementation-defined but MUST respect:
    /// - Domain boundaries
    /// - Constraints (best-effort)
    /// - Determinism (if seeded)
    ///
    /// ## Generation Strategy
    ///
    /// ```
    /// generate() algorithm:
    ///   1. Select candidate from domain
    ///   2. Check constraints
    ///   3. If valid: return
    ///      If invalid: retry (up to max attempts) or throw
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return a value within {@link #domain()}
    /// - SHOULD satisfy all constraints when possible
    /// - MAY use random, sequential, or smart generation
    /// - MUST be thread-safe
    ///
    /// @return a generated value, never null
    /// @throws RuntimeException if unable to generate valid value after retries
    ///
    T generate();

    ///
    /// Generates a boundary value (extremum) from this parameter's domain.
    ///
    /// Boundary values are at the edges of the domain:
    /// - For ranges: minimum or maximum
    /// - For discrete sets: first or last element
    /// - For composite: recursively select boundaries
    ///
    /// ## Boundary Selection
    ///
    /// ```
    /// Domain Type         Boundaries
    /// ─────────────────────────────────────
    /// Range[0, 100]    → {0, 100}
    /// Discrete{A,B,C}  → {A, C}
    /// Composite        → {min fields, max fields}
    /// ```
    ///
    /// ## Use Case
    ///
    /// Boundary values are critical for:
    /// - Testing edge conditions
    /// - Edge-First trial ordering (Simplica)
    /// - Scaffolding the parameter space outline
    ///
    /// @return a boundary value from the domain
    /// @see TrialOrdering Edge-First ordering
    ///
    T generateBoundary();

    ///
    /// Generates a random value from this parameter's domain.
    ///
    /// Uses randomization to produce uniform or near-uniform coverage
    /// of the domain space.
    ///
    /// ## Contract
    ///
    /// - MUST return a value within {@link #domain()}
    /// - SHOULD provide good coverage of domain over multiple calls
    /// - MAY use seeded RNG for reproducibility
    ///
    /// @return a randomly generated value
    ///
    T generateRandom();

    ///
    /// Validates whether a value satisfies all constraints of this parameter.
    ///
    /// ## Validation Levels
    ///
    /// ```
    /// Validation checks (in order):
    ///   1. Domain membership: value ∈ domain
    ///   2. Type correctness: value is instance of T
    ///   3. Constraint satisfaction: ∀c ∈ constraints: c.test(value)
    /// ```
    ///
    /// ## Validation Result States
    ///
    /// ```
    /// ValidationResult
    /// ├── Passed              : All checks passed
    /// ├── Failed              : One or more checks failed
    /// │   └── violations      : List of failure reasons
    /// └── Warning             : Passed with warnings
    ///     └── underlying      : The actual result (usually Passed)
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null result
    /// - MUST check domain membership
    /// - MUST check all registered constraints
    /// - SHOULD provide detailed violation messages on failure
    ///
    /// @param value the value to validate, may be null
    /// @return validation result with details, never null
    /// @see ValidationResult
    ///
    ValidationResult validate(T value);

    ///
    /// Tests whether this parameter satisfies a given constraint.
    ///
    /// This checks if the constraint is compatible with the parameter's domain
    /// and existing constraints. It does NOT test a specific value.
    ///
    /// ## Semantic Difference
    ///
    /// ```
    /// validate(value)           : Tests a specific value
    /// satisfies(constraint)     : Tests parameter compatibility
    ///
    /// Example:
    ///   Parameter<Integer> ages with domain [0, 120]
    ///
    ///   ages.validate(42)              → checks if 42 is valid
    ///   ages.satisfies(n -> n >= 18)   → checks if constraint is satisfiable
    /// ```
    ///
    /// ## Use Case
    ///
    /// Used during constraint composition to detect contradictions:
    ///
    /// ```
    /// Constraint<Integer> positive = n -> n > 0;
    /// Constraint<Integer> negative = n -> n < 0;
    ///
    /// parameter.satisfies(positive.and(negative)) → false (contradiction)
    /// ```
    ///
    /// @param constraint the constraint to test
    /// @return true if the constraint is satisfiable by this parameter's domain
    ///
    boolean satisfies(Constraint<T> constraint);

    ///
    /// Returns the default value for this parameter, if one is defined.
    ///
    /// Used by {@link ParameterBinder} when no input is provided for this parameter.
    /// If empty, the parameter is considered required — binding fails without input
    /// unless the binding policy allows skipping.
    ///
    /// ## Contract
    ///
    /// - MUST return a non-null Optional
    /// - If present, the value MUST be within {@link #domain()}
    /// - MUST remain constant for the lifetime of the parameter
    ///
    /// @return the default value if defined, empty otherwise
    ///
    default Optional<T> defaultValue() {
        return Optional.empty();
    }

    ///
    /// Returns the type identifier for this parameter.
    ///
    /// The type string describes the kind of parameter (e.g. {@code "integer"},
    /// {@code "double"}, {@code "boolean"}, {@code "string"}, {@code "selection"}).
    /// Custom implementations may return their own type identifiers.
    ///
    /// @return the parameter type identifier, never null
    ///
    default String type() {
        return "unknown";
    }

}
