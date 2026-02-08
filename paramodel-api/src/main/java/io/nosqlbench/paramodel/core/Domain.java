package io.nosqlbench.paramodel.core;

import java.util.Iterator;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

///
/// Specifies the valid value space for a parameter.
///
/// ## Concept
///
/// A {@code Domain<T>} defines the mathematical set of all valid values for a parameter.
/// Domains determine:
/// - Membership (is value X in the domain?)
/// - Cardinality (how many values exist?)
/// - Sampling (how to pick values?)
/// - Boundaries (what are the extrema?)
///
/// ## Domain Types
///
/// ```
/// Domain<T> (sealed interface)
/// │
/// ├── Discrete<T>
/// │   └── Finite set: {v1, v2, ..., vn}
/// │   └── Example: {RED, GREEN, BLUE}, {1, 2, 3, 5, 8}
/// │
/// ├── Range<T extends Comparable<T>>
/// │   └── Continuous or dense: [min, max]
/// │   └── Example: [0, 100], [0.0, 1.0], [startDate, endDate]
/// │
/// ├── Composite<T>
/// │   └── Structured: {field1: Domain<A>, field2: Domain<B>, ...}
/// │   └── Example: User{age: [0,120], name: String*, active: {true, false}}
/// │
/// └── Custom<T>
///     └── Predicate-defined: {v | P(v)}
///     └── Example: {n ∈ ℤ | isPrime(n)}, {s ∈ String | matches(regex)}
/// ```
///
/// ## Cardinality
///
/// ```
/// Domain Type          Cardinality              Enumerable?
/// ───────────────────────────────────────────────────────────
/// Discrete             |values|                 Yes
/// Range (integers)     max - min + 1            Yes
/// Range (floats)       ∞ (uncountable)          No
/// Composite            ∏|field domains|         If all fields enumerable
/// Custom               Variable or unknown       Depends on predicate
/// ```
///
/// ## Domain Algebra
///
/// Domains compose to form larger spaces:
///
/// ```
/// Cartesian Product:
///   Domain<A> × Domain<B> = Domain<(A, B)>
///   |A × B| = |A| × |B|
///
/// Union:
///   Domain<T> ∪ Domain<T> = Domain<T>
///   |A ∪ B| = |A| + |B| - |A ∩ B|
///
/// Intersection:
///   Domain<T> ∩ Domain<T> = Domain<T>
///   |A ∩ B| ≤ min(|A|, |B|)
/// ```
///
/// ## Usage Example
///
/// ```java
/// // Discrete domain
/// Domain<String> colors = Domain.discrete(Set.of("RED", "GREEN", "BLUE"));
/// assert colors.cardinality().equals(Optional.of(3L));
/// assert colors.contains("RED");
///
/// // Range domain
/// Domain<Integer> ages = Domain.range(0, 120);
/// assert ages.cardinality().equals(Optional.of(121L)); // includes both endpoints
///
/// // Custom domain
/// Domain<Integer> primes = Domain.custom(
///     n -> isPrime(n),
///     "Prime numbers"
/// );
/// assert primes.cardinality().isEmpty(); // infinite, not enumerable
/// ```
///
/// ## Contract Requirements
///
/// All implementations MUST:
/// 1. Provide consistent membership testing
/// 2. Return accurate cardinality when finite
/// 3. Support sampling with reasonable distribution
/// 4. Define boundary values appropriately
///
/// @param <T> the type of values in this domain
/// @see Parameter
/// @see Constraint
/// @since 0.1.0
///
public sealed interface Domain<T>
    permits Domain.Discrete, Domain.Range, Domain.Composite, Domain.Custom {

    ///
    /// Tests whether a value is a member of this domain.
    ///
    /// ## Membership Semantics
    ///
    /// ```
    /// For Discrete:     value ∈ {v1, v2, ..., vn}
    /// For Range:        min ≤ value ≤ max
    /// For Composite:    each field ∈ corresponding field domain
    /// For Custom:       predicate(value) = true
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return consistent results for same value
    /// - MUST handle null appropriately (typically false unless domain allows null)
    /// - MUST be thread-safe
    ///
    /// @param value the value to test, may be null
    /// @return true if value is in the domain, false otherwise
    ///
    boolean contains(T value);

    ///
    /// Returns the cardinality (number of values) of this domain.
    ///
    /// ## Return Values
    ///
    /// ```
    /// Optional.of(n)    : Finite domain with exactly n values
    /// Optional.empty()  : Infinite or uncountably large domain
    /// ```
    ///
    /// ## Examples
    ///
    /// ```
    /// Domain                           Cardinality
    /// ───────────────────────────────────────────────
    /// Discrete{A, B, C}             →  Optional.of(3)
    /// Range[0, 100] (integers)      →  Optional.of(101)
    /// Range[0.0, 1.0] (doubles)     →  Optional.empty()
    /// Custom{primes}                →  Optional.empty()
    /// ```
    ///
    /// @return cardinality if finite, empty if infinite
    ///
    Optional<Long> cardinality();

    ///
    /// Samples a value from this domain using the provided random number generator.
    ///
    /// ## Sampling Strategies
    ///
    /// ```
    /// Discrete:     Uniform random selection from set
    /// Range:        Uniform distribution over [min, max]
    /// Composite:    Recursively sample each field
    /// Custom:       Generate-and-test with retries
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return a value for which {@link #contains(Object)} returns true
    /// - SHOULD provide reasonable coverage over multiple calls
    /// - MUST be thread-safe with respect to the RNG
    ///
    /// @param rng random number generator for sampling
    /// @return a sampled value from the domain
    /// @throws ValueGenerationException if unable to sample after retries (for Custom domains)
    ///
    T sample(Random rng);

    ///
    /// Returns an iterator over all values in this domain.
    ///
    /// ## Enumerability
    ///
    /// ```
    /// Domain Type              Enumerable?
    /// ────────────────────────────────────
    /// Discrete                 Always
    /// Range (integers)         Yes
    /// Range (floats)           No (throws)
    /// Composite (all finite)   Yes
    /// Custom                   Usually No
    /// ```
    ///
    /// ## Contract
    ///
    /// - If {@link #cardinality()} returns a value, MUST be enumerable
    /// - If infinite or uncountable, MUST throw {@link UnsupportedOperationException}
    /// - Iterator order is implementation-defined but SHOULD be deterministic
    ///
    /// @return iterator over all values in the domain
    /// @throws UnsupportedOperationException if domain is not enumerable
    ///
    Iterator<T> enumerate();

    ///
    /// Returns the boundary values (extrema) of this domain.
    ///
    /// ## Boundary Semantics
    ///
    /// ```
    /// Domain Type              Boundaries
    /// ──────────────────────────────────────────────────
    /// Discrete{v1,...,vn}   →  {first, last}
    /// Range[min, max]       →  {min, max}
    /// Composite             →  Cartesian product of field boundaries
    /// Custom                →  Best-effort detection or sampling
    /// ```
    ///
    /// ## Use Case
    ///
    /// Boundary values are critical for:
    /// - Testing edge conditions
    /// - Edge-First trial ordering (Simplica)
    /// - Outlining parameter space
    ///
    /// @return set of boundary values, may be empty if boundaries undefined
    ///
    Set<T> boundaryValues();

    ///
    /// A discrete domain containing a finite set of explicit values.
    ///
    /// ## Structure
    ///
    /// ```
    /// Discrete<T> = {v1, v2, v3, ..., vn}
    ///
    /// Properties:
    ///   - Finite cardinality: n
    ///   - Always enumerable
    ///   - Order may or may not be significant
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Domain.Discrete<String> platforms = new Domain.Discrete<>(
    ///     Set.of("linux", "windows", "macos")
    /// );
    ///
    /// assert platforms.cardinality().equals(Optional.of(3L));
    /// assert platforms.contains("linux");
    /// assert !platforms.contains("freebsd");
    /// ```
    ///
    /// @param <T> the type of values in the set
    ///
    record Discrete<T>(Set<T> values) implements Domain<T> {
        // Implementation details omitted - contract only
    }

    ///
    /// A range domain with minimum and maximum bounds.
    ///
    /// ## Structure
    ///
    /// ```
    /// Range<T extends Comparable<T>> = [min, max]
    ///
    /// Membership:
    ///   value ∈ Range ⟺ min ≤ value ≤ max
    ///
    /// Cardinality:
    ///   - For discrete types (Integer, Long): max - min + 1
    ///   - For continuous types (Double, Float): ∞ (uncountable)
    /// ```
    ///
    /// ## Examples
    ///
    /// ```java
    /// // Integer range (enumerable)
    /// Domain.Range<Integer> ages = new Domain.Range<>(0, 120);
    /// assert ages.cardinality().equals(Optional.of(121L));
    ///
    /// // Double range (not enumerable)
    /// Domain.Range<Double> probabilities = new Domain.Range<>(0.0, 1.0);
    /// assert probabilities.cardinality().isEmpty();
    /// ```
    ///
    /// @param <T> the comparable type of values in the range
    ///
    record Range<T extends Comparable<T>>(T min, T max) implements Domain<T> {
        // Implementation details omitted - contract only
    }

    ///
    /// A composite domain representing structured values with named fields.
    ///
    /// ## Structure
    ///
    /// ```
    /// Composite<T> = {
    ///   field1: Domain<A>,
    ///   field2: Domain<B>,
    ///   ...
    ///   fieldn: Domain<Z>
    /// }
    ///
    /// Membership:
    ///   value.field1 ∈ Domain<A> ∧
    ///   value.field2 ∈ Domain<B> ∧ ...
    ///
    /// Cardinality:
    ///   |Composite| = |Domain<A>| × |Domain<B>| × ... × |Domain<Z>|
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// record User(int age, String role, boolean active) {}
    ///
    /// Domain.Composite<User> userDomain = Domain.composite(
    ///     Map.of(
    ///         "age", Domain.range(0, 120),
    ///         "role", Domain.discrete(Set.of("admin", "user", "guest")),
    ///         "active", Domain.discrete(Set.of(true, false))
    ///     )
    /// );
    ///
    /// // Cardinality: 121 × 3 × 2 = 726
    /// ```
    ///
    /// @param <T> the structured type
    ///
    record Composite<T>(java.util.Map<String, Domain<?>> fields) implements Domain<T> {
        // Implementation details omitted - contract only
    }

    ///
    /// A custom domain defined by a membership predicate.
    ///
    /// ## Structure
    ///
    /// ```
    /// Custom<T> = {v : T | predicate(v) = true}
    ///
    /// This is the most flexible domain type, allowing arbitrary
    /// mathematical sets to be defined.
    /// ```
    ///
    /// ## Use Cases
    ///
    /// - Prime numbers: {n ∈ ℤ | isPrime(n)}
    /// - Valid emails: {s ∈ String | matches(emailRegex)}
    /// - Business rules: {order | order.total > 0 ∧ order.items.nonEmpty}
    ///
    /// ## Limitations
    ///
    /// Custom domains generally:
    /// - Have unknown cardinality (may be infinite)
    /// - Are not enumerable
    /// - Require generate-and-test for sampling
    ///
    /// ## Example
    ///
    /// ```java
    /// Domain.Custom<Integer> primes = new Domain.Custom<>(
    ///     n -> n > 1 && IntStream.range(2, (int)Math.sqrt(n)+1)
    ///                            .noneMatch(i -> n % i == 0),
    ///     "Prime numbers"
    /// );
    ///
    /// assert primes.contains(7);
    /// assert !primes.contains(8);
    /// ```
    ///
    /// @param <T> the type of values
    ///
    record Custom<T>(
        java.util.function.Predicate<T> membership,
        String description
    ) implements Domain<T> {
        // Implementation details omitted - contract only
    }
}
