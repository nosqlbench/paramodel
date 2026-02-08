package io.nosqlbench.paramodel.sequence;

import io.nosqlbench.paramodel.core.Constraint;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.plan.TrialOrdering;

import java.util.Map;

///
/// Fluent API for building sequences with various generation strategies.
///
/// ## Concept
///
/// {@code SequenceBuilder} provides a composable DSL for creating sequences.
/// It allows chaining configuration methods to specify:
/// - Which parameters to vary
/// - Which generation strategy to use
/// - Which constraints to apply
/// - How many trials to generate
///
/// ## Builder Pattern Flow
///
/// ```
/// Creation:
///   SequenceBuilder builder = Sequence.builder()
///
/// Configuration:
///   builder
///     .withParameter(p1)      ← Add parameters
///     .withParameter(p2)
///     .constraint(c)          ← Add constraints
///     .generateStrategy()     ← Choose strategy
///
/// Finalization:
///   Sequence seq = builder.build()  ← Create immutable sequence
/// ```
///
/// ## Generation Strategies
///
/// ```
/// Strategy Method          Description                      Coverage
/// ─────────────────────────────────────────────────────────────────────
/// generateExhaustive()     All combinations                 100%
/// generateRandom(n)        n random samples                 Variable
/// generateEdgeFirst()      Boundaries → interior            Progressive
/// generatePairwise()       All parameter pairs              O(n²)
/// generateBoundary()       Extrema only                     Minimal
/// generateFromSeed(seed)   Deterministic random             Seeded
/// ```
///
/// ## Usage Example: Basic Sequence
///
/// ```java
/// Parameter<Integer> age = DiscreteParameter.range("age", 18, 65);
/// Parameter<String> platform = DiscreteParameter.of(
///     "platform", "linux", "windows", "macos"
/// );
///
/// Sequence seq = Sequence.builder()
///     .withParameter(age)
///     .withParameter(platform)
///     .generatePairwise()      // All (age, platform) pairs
///     .build();
///
/// // Results in: 48 × 3 = 144 trials? No!
/// // Pairwise covers all pairs efficiently: ~O(max(48, 3)) trials
/// ```
///
/// ## Usage Example: Constrained Sequence
///
/// ```java
/// Parameter<Integer> min = range("min", 0, 100);
/// Parameter<Integer> max = range("max", 0, 100);
///
/// Sequence seq = Sequence.builder()
///     .withParameter(min)
///     .withParameter(max)
///     .constraint(assignments -> {
///         int minVal = (Integer) assignments.get("min").value();
///         int maxVal = (Integer) assignments.get("max").value();
///         return minVal <= maxVal;  // min must not exceed max
///     })
///     .generateRandom(100)
///     .build();
///
/// // Only trials where min ≤ max are generated
/// ```
///
/// ## Usage Example: Deterministic Sequence
///
/// ```java
/// // Same seed produces same sequence
/// Sequence seq1 = Sequence.builder()
///     .withParameter(param)
///     .generateFromSeed(42)
///     .build();
///
/// Sequence seq2 = Sequence.builder()
///     .withParameter(param)
///     .generateFromSeed(42)
///     .build();
///
/// assert seq1.trials().equals(seq2.trials());  // Same trials, same order
/// ```
///
/// ## Constraint Application
///
/// Constraints filter out invalid combinations:
///
/// ```
/// Without Constraint:
///   Space = P1 × P2 × ... × Pn
///   |Trials| = |P1| × |P2| × ... × |Pn|
///
/// With Constraint C:
///   Space = {(v1, v2, ..., vn) ∈ P1 × P2 × ... × Pn | C(v1, v2, ..., vn)}
///   |Trials| ≤ |P1| × |P2| × ... × |Pn|
/// ```
///
/// ## Strategy Selection Guidelines
///
/// ```
/// Choose:
///   Exhaustive       - When space is small (< 1000 trials)
///   Edge-First       - When you want early boundary feedback
///   Pairwise         - When testing parameter interactions
///   Random(n)        - When you need quick sampling
///   Boundary         - When testing edge cases only
/// ```
///
/// ## Builder Immutability
///
/// Each builder method returns a NEW builder:
///
/// ```java
/// SequenceBuilder b1 = Sequence.builder().withParameter(p1);
/// SequenceBuilder b2 = b1.withParameter(p2);
/// SequenceBuilder b3 = b2.generateRandom(100);
///
/// // b1, b2, b3 are independent (functional style)
/// // OR they may be the same builder (mutating style)
/// // Implementation decides, but build() MUST produce immutable Sequence
/// ```
///
/// ## Error Handling
///
/// Build-time errors:
///
/// ```java
/// try {
///     Sequence seq = Sequence.builder()
///         // No parameters added!
///         .generateExhaustive()
///         .build();
/// } catch (IllegalStateException e) {
///     // "Cannot build sequence with no parameters"
/// }
///
/// try {
///     Sequence seq = Sequence.builder()
///         .withParameter(p)
///         // No generation strategy specified!
///         .build();
/// } catch (IllegalStateException e) {
///     // "Generation strategy not specified"
/// }
/// ```
///
/// @see Sequence
/// @see Parameter
/// @see Trial
/// @since 0.1.0
///
public interface SequenceBuilder {

    ///
    /// Adds a parameter to vary in this sequence.
    ///
    /// Parameters define the dimensions of the space to explore.
    /// Order matters for some strategies (e.g., prioritization in edge-first).
    ///
    /// ## Example
    ///
    /// ```java
    /// builder
    ///     .withParameter(ageParam)      // 1st dimension
    ///     .withParameter(platformParam) // 2nd dimension
    ///     .withParameter(regionParam);  // 3rd dimension
    /// // Results in 3D parameter space
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST accept non-null parameter
    /// - SHOULD check for duplicate parameter names
    /// - MAY return this or new builder instance
    ///
    /// @param parameter the parameter to add
    /// @return builder for chaining
    /// @throws NullPointerException if parameter is null
    /// @throws IllegalArgumentException if parameter name already added
    ///
    SequenceBuilder withParameter(Parameter<?> parameter);

    ///
    /// Adds multiple parameters at once.
    ///
    /// Convenience method equivalent to:
    /// ```java
    /// for (Parameter<?> p : parameters) {
    ///     builder.withParameter(p);
    /// }
    /// ```
    ///
    /// @param parameters parameters to add
    /// @return builder for chaining
    ///
    SequenceBuilder withParameters(Parameter<?>... parameters);

    ///
    /// Adds a cross-parameter constraint that all trials must satisfy.
    ///
    /// ## Constraint Application
    ///
    /// The constraint receives a map of all parameter assignments:
    /// ```java
    /// builder.constraint(assignments -> {
    ///     int age = (Integer) assignments.get("age").value();
    ///     boolean hasConsent = (Boolean) assignments.get("hasConsent").value();
    ///     return age >= 18 || hasConsent;
    /// });
    /// ```
    ///
    /// ## Multiple Constraints
    ///
    /// Multiple constraints are AND'ed together:
    /// ```java
    /// builder
    ///     .constraint(c1)  // Must satisfy c1
    ///     .constraint(c2)  // AND c2
    ///     .constraint(c3); // AND c3
    /// ```
    ///
    /// @param constraint cross-parameter constraint
    /// @return builder for chaining
    ///
    SequenceBuilder constraint(Constraint<Map<String, Value<?>>> constraint);

    ///
    /// Generates all possible combinations (Cartesian product).
    ///
    /// ## Exhaustive Coverage
    ///
    /// ```
    /// For parameters P1, P2, ..., Pn:
    ///   Generates: P1 × P2 × ... × Pn
    ///   Size: |P1| × |P2| × ... × |Pn| trials
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Parameter<Integer> x = range("x", 0, 2);  // {0, 1, 2}
    /// Parameter<String> y = of("y", "A", "B"); // {A, B}
    ///
    /// Sequence seq = Sequence.builder()
    ///     .withParameter(x)
    ///     .withParameter(y)
    ///     .generateExhaustive()
    ///     .build();
    ///
    /// // Generates 3 × 2 = 6 trials:
    /// // (0,A), (0,B), (1,A), (1,B), (2,A), (2,B)
    /// ```
    ///
    /// ## Warning
    ///
    /// Exhaustive generation explodes quickly:
    /// - 10 parameters with 10 values each = 10^10 trials!
    /// Use only for small spaces.
    ///
    /// @return builder for chaining
    ///
    SequenceBuilder generateExhaustive();

    ///
    /// Generates n random trials sampled uniformly from the parameter space.
    ///
    /// ## Random Sampling
    ///
    /// ```
    /// For each trial:
    ///   1. For each parameter, sample from its domain
    ///   2. Check constraints
    ///   3. If valid: add to sequence
    ///   4. If invalid: retry (up to max attempts)
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Sequence seq = Sequence.builder()
    ///     .withParameter(ageParam)       // [0, 120]
    ///     .withParameter(platformParam)  // {linux, windows, macos}
    ///     .generateRandom(100)           // 100 random trials
    ///     .build();
    ///
    /// assert seq.size() == 100;
    /// ```
    ///
    /// ## Determinism
    ///
    /// By default, uses non-deterministic seed. For reproducibility,
    /// use {@link #generateFromSeed(long)} instead.
    ///
    /// @param count number of trials to generate
    /// @return builder for chaining
    /// @throws IllegalArgumentException if count < 0
    ///
    SequenceBuilder generateRandom(int count);

    ///
    /// Generates sequence with deterministic random sampling using a seed.
    ///
    /// ## Reproducibility
    ///
    /// Same seed → same sequence:
    /// ```java
    /// Sequence s1 = builder.generateFromSeed(42).build();
    /// Sequence s2 = builder.generateFromSeed(42).build();
    /// assert s1.trials().equals(s2.trials());
    /// ```
    ///
    /// ## Use Cases
    ///
    /// - Reproducible experiments
    /// - Debugging (replay same sequence)
    /// - Testing (predictable test data)
    ///
    /// @param seed random seed for generation
    /// @return builder for chaining
    ///
    SequenceBuilder generateFromSeed(long seed);

    ///
    /// Generates edge-first sequence: boundaries first, then interior fill.
    ///
    /// ## Edge-First Algorithm
    ///
    /// ```
    /// Phase 1: Extrema (corners of hypercube)
    ///   - All combinations of min/max for each parameter
    ///   - Count: 2^n for n parameters
    ///
    /// Phase 2: Edge scaffolding
    ///   - Points on edges of hypercube
    ///   - One parameter varies, others at extrema
    ///
    /// Phase 3: Interior fill
    ///   - Gap-filling heuristic
    ///   - Maximize distance from existing points
    /// ```
    ///
    /// ## Example: 2D Space
    ///
    /// ```java
    /// Parameter<Integer> x = range("x", 0, 10);  // 11 values
    /// Parameter<Integer> y = range("y", 0, 10);  // 11 values
    ///
    /// Sequence seq = builder.withParameter(x).withParameter(y)
    ///     .generateEdgeFirst().build();
    ///
    /// // Order:
    /// // 1. Corners: (0,0), (0,10), (10,0), (10,10)
    /// // 2. Edges: (0,5), (5,0), (5,10), (10,5), ...
    /// // 3. Interior: (5,5), (2,7), (8,3), ...
    /// ```
    ///
    /// ## Benefits
    ///
    /// - Early boundary feedback
    /// - Progressive refinement
    /// - Informative partial results if stopped early
    ///
    /// @return builder for chaining
    /// @see TrialOrdering Edge-first in Simplica
    ///
    SequenceBuilder generateEdgeFirst();

    ///
    /// Generates pairwise covering sequence ensuring all parameter pairs are tested.
    ///
    /// ## Pairwise Testing
    ///
    /// ```
    /// Goal: Every pair of parameter values appears together in at least one trial.
    ///
    /// For parameters P1, P2, ..., Pn:
    ///   ∀ i,j where i ≠ j:
    ///     ∀ vi ∈ Pi, vj ∈ Pj:
    ///       ∃ trial t: t.assignments[i] = vi ∧ t.assignments[j] = vj
    /// ```
    ///
    /// ## Efficiency
    ///
    /// ```
    /// Exhaustive: |P1| × |P2| × ... × |Pn| trials
    /// Pairwise:   O(max(|P1|, |P2|, ..., |Pn|)²) trials
    /// ```
    ///
    /// Dramatic reduction for many parameters!
    ///
    /// ## Example
    ///
    /// ```java
    /// // 3 parameters with 10 values each
    /// Parameter<Integer> p1 = range("p1", 0, 9);
    /// Parameter<Integer> p2 = range("p2", 0, 9);
    /// Parameter<Integer> p3 = range("p3", 0, 9);
    ///
    /// // Exhaustive: 10³ = 1000 trials
    /// Sequence exhaustive = builder.withParameters(p1, p2, p3)
    ///     .generateExhaustive().build();
    /// assert exhaustive.size() == 1000;
    ///
    /// // Pairwise: ~100 trials (10x reduction!)
    /// Sequence pairwise = builder.withParameters(p1, p2, p3)
    ///     .generatePairwise().build();
    /// assert pairwise.size() < 150;  // Much smaller!
    /// ```
    ///
    /// @return builder for chaining
    ///
    SequenceBuilder generatePairwise();

    ///
    /// Generates only boundary (extrema) values for all parameters.
    ///
    /// ## Boundary Coverage
    ///
    /// ```
    /// For each parameter, use only:
    ///   - Minimum value
    ///   - Maximum value
    ///
    /// Trials = 2^n for n parameters
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Parameter<Integer> age = range("age", 18, 65);
    /// Parameter<Integer> income = range("income", 0, 200000);
    ///
    /// Sequence seq = builder.withParameter(age).withParameter(income)
    ///     .generateBoundary().build();
    ///
    /// // Generates 2² = 4 trials:
    /// // (18, 0), (18, 200000), (65, 0), (65, 200000)
    /// ```
    ///
    /// ## Use Case
    ///
    /// Quick edge case testing without interior exploration.
    ///
    /// @return builder for chaining
    ///
    SequenceBuilder generateBoundary();

    ///
    /// Builds the final immutable sequence.
    ///
    /// ## Build Requirements
    ///
    /// Builder MUST have:
    /// 1. At least one parameter added
    /// 2. A generation strategy selected
    ///
    /// ## Build Process
    ///
    /// ```
    /// build():
    ///   1. Validate builder state
    ///   2. Generate trials using selected strategy
    ///   3. Apply constraints (filter invalid trials)
    ///   4. Create metadata
    ///   5. Return immutable Sequence
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Sequence seq = Sequence.builder()
    ///     .withParameter(param1)
    ///     .withParameter(param2)
    ///     .generatePairwise()
    ///     .build();  // ← Immutable sequence created
    ///
    /// // Sequence is now fixed
    /// List<Trial> trials = seq.trials();  // Never changes
    /// ```
    ///
    /// @return immutable sequence
    /// @throws IllegalStateException if builder state invalid
    ///         (e.g., no parameters or no strategy)
    ///
    Sequence build();
}
