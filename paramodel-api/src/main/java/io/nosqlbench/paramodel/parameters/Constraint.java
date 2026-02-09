package io.nosqlbench.paramodel.parameters;

import io.nosqlbench.paramodel.plan.TestPlan;

///
/// A predicate that parameter values must satisfy, with algebraic composition operators.
///
/// ## Concept
///
/// A {@code Constraint<T>} is a boolean-valued function over type {@code T}.
/// Constraints express requirements, invariants, and business rules that values must obey.
///
/// ## Constraint as Boolean Algebra
///
/// Constraints form a complete Boolean algebra with composition operators:
///
/// ```
/// Operations:
///   ∧ (AND)      : c1.and(c2)
///   ∨ (OR)       : c1.or(c2)
///   ¬ (NOT)      : c.negate()
///
/// Constants:
///   ⊤ (TRUE)     : Always satisfied
///   ⊥ (FALSE)    : Never satisfied
///
/// Laws:
///   Associativity:   (c1 ∧ c2) ∧ c3 = c1 ∧ (c2 ∧ c3)
///   Commutativity:   c1 ∧ c2 = c2 ∧ c1
///   Distributivity:  c1 ∧ (c2 ∨ c3) = (c1 ∧ c2) ∨ (c1 ∧ c3)
///   Identity:        c ∧ ⊤ = c
///   Annihilation:    c ∧ ⊥ = ⊥
///   Idempotence:     c ∧ c = c
///   Absorption:      c ∧ (c ∨ d) = c
///   De Morgan:       ¬(c1 ∧ c2) = ¬c1 ∨ ¬c2
///   Double Negation: ¬¬c = c
/// ```
///
/// ## Constraint Categories
///
/// ```
/// Constraint<T>
/// │
/// ├── PreCondition<T>
/// │   └── Must hold BEFORE an operation
/// │   └── Example: buffer != null, age >= 0
/// │
/// ├── PostCondition<T>
/// │   └── Must hold AFTER an operation
/// │   └── Example: result.length > 0, balance >= 0
/// │
/// ├── Invariant<T>
/// │   └── Must ALWAYS hold
/// │   └── Example: size == items.count(), sorted(list)
/// │
/// └── CrossParameter
///     └── Relates multiple parameters
///     └── Example: startDate < endDate, width * height <= maxArea
/// ```
///
/// ## Composition Example
///
/// ```java
/// Constraint<Integer> positive = n -> n > 0;
/// Constraint<Integer> even = n -> n % 2 == 0;
/// Constraint<Integer> lessThan100 = n -> n < 100;
///
/// // Compose with AND
/// Constraint<Integer> positiveEven = positive.and(even);
/// assert positiveEven.test(4);   // true
/// assert !positiveEven.test(3);  // false (not even)
/// assert !positiveEven.test(-2); // false (not positive)
///
/// // Compose with OR
/// Constraint<Integer> oddOrNegative = positive.negate().or(even.negate());
///
/// // Complex composition
/// Constraint<Integer> complex = positive
///     .and(lessThan100)
///     .and(even.or(n -> n % 3 == 0));  // positive, <100, and (even or divisible by 3)
/// ```
///
/// ## Constraint Satisfaction Problem
///
/// When multiple constraints are composed, we form a CSP:
///
/// ```
/// CSP = (Variables, Domains, Constraints)
///
/// Variables:    {x1, x2, ..., xn}
/// Domains:      {D1, D2, ..., Dn}
/// Constraints:  {C1, C2, ..., Cm}
///
/// Solution:     An assignment {x1=v1, x2=v2, ...} where
///               ∀i: vi ∈ Di ∧ ∀j: Cj(v1, v2, ...) = true
/// ```
///
/// ## Validation vs Satisfaction
///
/// Two related but distinct operations:
///
/// ```
/// test(value)              : Tests if a specific value satisfies the constraint
/// satisfies(domain)        : Tests if constraint is satisfiable within domain
///
/// Example:
///   Constraint<Integer> positive = n -> n > 0;
///
///   positive.test(5)                           → true (5 satisfies)
///   positive.test(-3)                          → false (-3 violates)
///
///   Domain<Integer> naturals = [0, ∞)
///   positive.satisfies(naturals)               → true (domain compatible)
///
///   Domain<Integer> negatives = (-∞, 0)
///   positive.satisfies(negatives)              → false (contradiction)
/// ```
///
/// ## Contract Requirements
///
/// All implementations MUST:
///
/// 1. **Purity**: {@link #test(Object)} MUST be a pure function (no side effects)
/// 2. **Consistency**: Same input MUST always produce same output
/// 3. **Thread Safety**: All methods MUST be safe for concurrent invocation
/// 4. **Composition Correctness**: Algebraic laws MUST hold
/// 5. **Null Handling**: Clearly define behavior for null inputs
///
/// ## Usage in Paramodel
///
/// Constraints are used throughout the framework:
///
/// ```
/// Parameter Level:
///   Parameter<T>.validate(value) checks constraints
///   Parameter<T>.satisfies(constraint) tests compatibility
///
/// Sequence Level:
///   Sequence.validate() checks all trials satisfy constraints
///
/// Plan Level (Simplica):
///   TestPlan compilation validates constraint coherence
///   ExecutionPlan bakes constraints into barriers
/// ```
///
/// @param <T> the type of values this constraint tests
/// @see Parameter
/// @see ValidationResult
/// @see TestPlan
/// @since 0.1.0
///
@FunctionalInterface
public interface Constraint<T> {

    ///
    /// Tests whether a value satisfies this constraint.
    ///
    /// ## Semantics
    ///
    /// ```
    /// test(value) = true   ⟺  value satisfies constraint
    /// test(value) = false  ⟺  value violates constraint
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST be a pure function (no side effects, no state mutation)
    /// - MUST return consistent results for same input
    /// - MUST handle null appropriately (document behavior)
    /// - MUST NOT throw exceptions for normal values (use false instead)
    /// - MAY throw for truly exceptional conditions (type mismatch, etc.)
    /// - MUST be thread-safe
    ///
    /// ## Implementation Note
    ///
    /// This is the single abstract method (SAM) allowing lambda syntax:
    ///
    /// ```java
    /// Constraint<Integer> positive = n -> n > 0;
    /// Constraint<String> nonEmpty = s -> s != null && !s.isEmpty();
    /// ```
    ///
    /// @param value the value to test, may be null
    /// @return true if value satisfies constraint, false otherwise
    ///
    boolean test(T value);

    ///
    /// Composes this constraint with another using logical AND.
    ///
    /// ## Semantics
    ///
    /// ```
    /// (c1 ∧ c2).test(v) = c1.test(v) ∧ c2.test(v)
    ///
    /// Result is true only if BOTH constraints are satisfied.
    /// ```
    ///
    /// ## Truth Table
    ///
    /// ```
    /// c1     c2     c1 ∧ c2
    /// ────────────────────────
    /// true   true   true
    /// true   false  false
    /// false  true   false
    /// false  false  false
    /// ```
    ///
    /// ## Algebraic Properties
    ///
    /// ```
    /// Associativity:   (c1 ∧ c2) ∧ c3 = c1 ∧ (c2 ∧ c3)
    /// Commutativity:   c1 ∧ c2 = c2 ∧ c1
    /// Idempotence:     c ∧ c = c
    /// Identity:        c ∧ TRUE = c
    /// Annihilation:    c ∧ FALSE = FALSE
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Constraint<Integer> positive = n -> n > 0;
    /// Constraint<Integer> even = n -> n % 2 == 0;
    /// Constraint<Integer> small = n -> n < 100;
    ///
    /// Constraint<Integer> positiveEvenSmall = positive.and(even).and(small);
    ///
    /// assert positiveEvenSmall.test(4);    // ✓ positive, even, small
    /// assert !positiveEvenSmall.test(3);   // ✗ not even
    /// assert !positiveEvenSmall.test(-2);  // ✗ not positive
    /// assert !positiveEvenSmall.test(200); // ✗ not small
    /// ```
    ///
    /// ## Short-Circuit Evaluation
    ///
    /// Implementations SHOULD short-circuit:
    /// ```
    /// If c1.test(v) = false, do not evaluate c2.test(v)
    /// ```
    ///
    /// @param other the constraint to AND with this one
    /// @return a new constraint representing the conjunction
    /// @throws NullPointerException if other is null
    ///
    default Constraint<T> and(Constraint<? super T> other) {
        return value -> this.test(value) && other.test(value);
    }

    ///
    /// Composes this constraint with another using logical OR.
    ///
    /// ## Semantics
    ///
    /// ```
    /// (c1 ∨ c2).test(v) = c1.test(v) ∨ c2.test(v)
    ///
    /// Result is true if AT LEAST ONE constraint is satisfied.
    /// ```
    ///
    /// ## Truth Table
    ///
    /// ```
    /// c1     c2     c1 ∨ c2
    /// ────────────────────────
    /// true   true   true
    /// true   false  true
    /// false  true   true
    /// false  false  false
    /// ```
    ///
    /// ## Algebraic Properties
    ///
    /// ```
    /// Associativity:   (c1 ∨ c2) ∨ c3 = c1 ∨ (c2 ∨ c3)
    /// Commutativity:   c1 ∨ c2 = c2 ∨ c1
    /// Idempotence:     c ∨ c = c
    /// Identity:        c ∨ FALSE = c
    /// Annihilation:    c ∨ TRUE = TRUE
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Constraint<Integer> negative = n -> n < 0;
    /// Constraint<Integer> large = n -> n > 1000;
    ///
    /// Constraint<Integer> extremeValues = negative.or(large);
    ///
    /// assert extremeValues.test(-5);    // ✓ negative
    /// assert extremeValues.test(5000);  // ✓ large
    /// assert !extremeValues.test(50);   // ✗ neither
    /// ```
    ///
    /// ## Short-Circuit Evaluation
    ///
    /// Implementations SHOULD short-circuit:
    /// ```
    /// If c1.test(v) = true, do not evaluate c2.test(v)
    /// ```
    ///
    /// @param other the constraint to OR with this one
    /// @return a new constraint representing the disjunction
    /// @throws NullPointerException if other is null
    ///
    default Constraint<T> or(Constraint<? super T> other) {
        return value -> this.test(value) || other.test(value);
    }

    ///
    /// Returns the logical negation of this constraint.
    ///
    /// ## Semantics
    ///
    /// ```
    /// (¬c).test(v) = ¬(c.test(v))
    ///
    /// Result is true if constraint is NOT satisfied.
    /// ```
    ///
    /// ## Truth Table
    ///
    /// ```
    /// c       ¬c
    /// ───────────
    /// true    false
    /// false   true
    /// ```
    ///
    /// ## Algebraic Properties
    ///
    /// ```
    /// Double Negation:    ¬¬c = c
    /// De Morgan:          ¬(c1 ∧ c2) = ¬c1 ∨ ¬c2
    /// De Morgan:          ¬(c1 ∨ c2) = ¬c1 ∧ ¬c2
    /// Contradiction:      c ∧ ¬c = FALSE
    /// Excluded Middle:    c ∨ ¬c = TRUE
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Constraint<Integer> positive = n -> n > 0;
    /// Constraint<Integer> nonPositive = positive.negate();
    ///
    /// assert nonPositive.test(0);   // ✓
    /// assert nonPositive.test(-5);  // ✓
    /// assert !nonPositive.test(5);  // ✗
    ///
    /// // De Morgan's law
    /// Constraint<Integer> even = n -> n % 2 == 0;
    /// Constraint<Integer> oddOrNegative =
    ///     even.and(positive).negate();  // ¬(even ∧ positive) = ¬even ∨ ¬positive
    /// ```
    ///
    /// @return a new constraint representing the negation
    ///
    default Constraint<T> negate() {
        return value -> !this.test(value);
    }

    ///
    /// Returns a human-readable description of this constraint.
    ///
    /// Used for:
    /// - Error messages when validation fails
    /// - Documentation generation
    /// - Debugging and diagnostics
    /// - UI display
    ///
    /// ## Contract
    ///
    /// - SHOULD return a concise, meaningful description
    /// - MAY return a technical representation (e.g., lambda source)
    /// - Default implementation returns class/lambda identity
    ///
    /// ## Example
    ///
    /// ```java
    /// Constraint<Integer> range = new Constraint<>() {
    ///     @Override
    ///     public boolean test(Integer n) {
    ///         return n >= 0 && n <= 100;
    ///     }
    ///
    ///     @Override
    ///     public String description() {
    ///         return "value in range [0, 100]";
    ///     }
    /// };
    /// ```
    ///
    /// @return constraint description, never null
    ///
    default String description() {
        return this.getClass().getSimpleName() + "@" +
               Integer.toHexString(System.identityHashCode(this));
    }
}
