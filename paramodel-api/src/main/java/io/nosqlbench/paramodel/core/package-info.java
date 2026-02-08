///
/// Core paramodel contracts for parameter modeling, domains, constraints, and values.
///
/// ## Overview
///
/// This package provides the foundational algebraic types for systematic parameter modeling.
/// All types are designed to compose predictably following well-defined algebraic laws.
///
/// ## Type Hierarchy
///
/// ```
/// Parameter<T>
///   ├── defines → Domain<T>
///   ├── validates with → Constraint<T>
///   └── generates → Value<T>
///
/// Domain<T> (sealed)
///   ├── Discrete<T>      - Finite set of values
///   ├── Range<T>         - Min/max bounds for ordered types
///   ├── Composite<T>     - Structured with named fields
///   └── Custom<T>        - User-defined membership predicate
///
/// Constraint<T>
///   ├── and(Constraint<T>) → Constraint<T>
///   ├── or(Constraint<T>) → Constraint<T>
///   └── negate() → Constraint<T>
///
/// ValidationResult (sealed)
///   ├── Passed
///   ├── Failed(violations)
///   └── Warning(message, underlying)
/// ```
///
/// ## Algebraic Properties
///
/// ### Constraints form a Boolean Algebra
///
/// ```
/// Associativity:  (c1 ∧ c2) ∧ c3  =  c1 ∧ (c2 ∧ c3)
/// Commutativity:  c1 ∧ c2  =  c2 ∧ c1
/// Identity:       c ∧ TRUE  =  c
/// Absorption:     c ∧ (c ∨ d)  =  c
/// De Morgan:      ¬(c1 ∧ c2)  =  ¬c1 ∨ ¬c2
/// ```
///
/// ### Parameters compose predictably
///
/// ```
/// Parameter<A> + Parameter<B> → Parameter<(A, B)>
/// Domain<A> × Domain<B> → Domain<(A, B)>
/// ```
///
/// ## Usage Example
///
/// ```java
/// // Define a parameter
/// Parameter<Integer> ageParam = ...;
/// Domain<Integer> domain = Domain.range(0, 120);
/// Constraint<Integer> constraint = age -> age >= 0 && age <= 120;
///
/// // Generate and validate
/// Integer value = ageParam.generate();
/// ValidationResult result = ageParam.validate(value);
///
/// // Compose constraints
/// Constraint<Integer> positive = n -> n > 0;
/// Constraint<Integer> even = n -> n % 2 == 0;
/// Constraint<Integer> positiveEven = positive.and(even);
/// ```
///
/// @see Parameter
/// @see Domain
/// @see Constraint
/// @see Value
/// @see ValidationResult
/// @since 0.1.0
///
package io.nosqlbench.paramodel.core;
