package io.nosqlbench.paramodel.sequence;

import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.plan.ExecutionPlan;

import java.util.Iterator;
import java.util.List;

///
/// An ordered collection of trials representing a systematic exploration of a parameter space.
///
/// ## Concept
///
/// A {@code Sequence} defines the complete set of trials to execute and their order.
/// Sequences are:
/// - **Immutable**: Once built, cannot be modified
/// - **Ordered**: Trial execution order is fixed
/// - **Validated**: Should be validated before execution
/// - **Deterministic**: Same inputs produce same sequence
///
/// ## Structure
///
/// ```
/// Sequence
/// ├── trials(): List<Trial>
/// │   └── Ordered, immutable list of all trials
/// │
/// ├── validate(): ValidationResult
/// │   └── Check all trials satisfy constraints
/// │
/// └── iterator(): Iterator<Trial>
///     └── Sequential access to trials
/// ```
///
/// ## Sequence Generation Strategies
///
/// Different strategies produce different trial orderings:
///
/// ```
/// Strategy          Order               Coverage          Best For
/// ────────────────────────────────────────────────────────────────────────
/// Exhaustive        Systematic          100%              Small spaces
/// Random            Shuffled            Probabilistic     Quick sampling
/// Edge-First        Boundaries first    Progressive       Early feedback
/// Pairwise          Covering arrays     All pairs         Interaction bugs
/// Boundary          Extrema only        Minimal           Edge cases
/// Adaptive          Result-driven       Dynamic           Optimization
/// ```
///
/// ## Parameter Space Coverage
///
/// Given parameters P1, P2, ..., Pn:
///
/// ```
/// Full Space:
///   |Space| = |P1| × |P2| × ... × |Pn|
///
/// Sequence Coverage:
///   coverage = |sequence.trials()| / |Space|
///
/// Examples:
///   Exhaustive:  coverage = 100%
///   Pairwise:    coverage ≈ O(n²)/|Space|
///   Random(100): coverage = min(100/|Space|, 1.0)
/// ```
///
/// ## Immutability Guarantee
///
/// ```
/// Sequence seq = builder.build();
///
/// // These always return the same trials
/// List<Trial> trials1 = seq.trials();
/// List<Trial> trials2 = seq.trials();
/// assert trials1.equals(trials2);
///
/// // Iterator produces same order
/// List<String> ids1 = StreamSupport.stream(seq.spliterator(), false)
///     .map(Trial::id)
///     .toList();
/// List<String> ids2 = seq.trials().stream()
///     .map(Trial::id)
///     .toList();
/// assert ids1.equals(ids2);
/// ```
///
/// ## Validation
///
/// Sequences SHOULD be validated before execution:
///
/// ```
/// Validation Checks:
///   1. Trial Completeness
///      - Every trial has all parameter assignments
///
///   2. Individual Trial Validation
///      - Each trial.validate() passes
///
///   3. Global Constraints
///      - Sequence-level invariants satisfied
///
///   4. Resource Feasibility
///      - Required resources are available (Simplica)
/// ```
///
/// ## Usage Example: Simple Sequence
///
/// ```java
/// // Build sequence
/// Sequence seq = Sequence.builder()
///     .withParameter(ageParam)
///     .withParameter(platformParam)
///     .generatePairwise()
///     .build();
///
/// // Validate
/// ValidationResult result = seq.validate();
/// if (result.isFailed()) {
///     throw new InvalidSequenceException(result);
/// }
///
/// // Execute
/// for (Trial trial : seq) {
///     TrialResult outcome = executor.execute(trial);
///     store.save(outcome);
/// }
///
/// ```
///
/// ## Usage Example: Edge-First Strategy
///
/// ```java
/// // 3D parameter space
/// Parameter<Integer> x = range("x", 0, 10);  // 11 values
/// Parameter<Integer> y = range("y", 0, 10);  // 11 values
/// Parameter<Integer> z = range("z", 0, 10);  // 11 values
/// // Total: 11³ = 1331 trials
///
/// Sequence seq = Sequence.builder()
///     .withParameter(x)
///     .withParameter(y)
///     .withParameter(z)
///     .generateEdgeFirst()
///     .build();
///
/// // Execution order:
/// // 1. 8 corner trials: (0,0,0), (0,0,10), (0,10,0), ...
/// // 2. Edge trials: boundaries of each dimension
/// // 3. Face trials: boundaries of each plane
/// // 4. Interior trials: gap-filling heuristic
///
/// List<Trial> trials = seq.trials();
/// assert trials.size() == 1331;
///
/// // First trial is a corner
/// Trial first = trials.get(0);
/// Map<String, Value<?>> assignments = first.assignments();
/// int xVal = (Integer) assignments.get("x").value();
/// int yVal = (Integer) assignments.get("y").value();
/// int zVal = (Integer) assignments.get("z").value();
/// assert (xVal == 0 || xVal == 10) &&
///        (yVal == 0 || yVal == 10) &&
///        (zVal == 0 || zVal == 10);
/// ```
///
/// ## Progressive Execution
///
/// Sequences support progressive execution with early stopping:
///
/// ```java
/// Sequence seq = builder.generateEdgeFirst().build();
/// Iterator<Trial> iter = seq.iterator();
///
/// while (iter.hasNext() && !shouldStop()) {
///     Trial trial = iter.next();
///     TrialResult result = executor.execute(trial);
///
///     // Early stopping based on results
///     if (result.meetsThreshold()) {
///         System.out.println("Threshold met, stopping early");
///         break;
///     }
/// }
/// ```
///
/// ## Relationship to Simplica
///
/// Paramodel sequences are abstract:
///
/// ```
/// Paramodel Sequence:
///   - Ordered trials
///   - Parameter assignments
///   - Validation
///
///   ↓ compiled into
///
/// Simplica ExecutionPlan:
///   - Atomic steps
///   - Resource barriers
///   - Element dependencies
///   - Retry policies
///   - Scheduling constraints
/// ```
///
/// @see SequenceBuilder
/// @see Trial
/// @see ExecutionPlan
/// @since 0.1.0
///
public interface Sequence extends Iterable<Trial> {

    ///
    /// Returns the complete, ordered list of trials in this sequence.
    ///
    /// ## Order Guarantee
    ///
    /// The returned list defines the execution order:
    /// ```
    /// trials().get(0)  → First trial to execute
    /// trials().get(1)  → Second trial to execute
    /// ...
    /// trials().get(n-1) → Last trial to execute
    /// ```
    ///
    /// ## Immutability
    ///
    /// - MUST return unmodifiable list
    /// - MUST return same trials on every call
    /// - MUST maintain same order on every call
    ///
    /// ## Performance Note
    ///
    /// For large sequences (thousands of trials), consider using
    /// {@link #iterator()} for streaming access rather than loading
    /// all trials into memory.
    ///
    /// @return immutable, ordered list of trials, never null
    ///
    List<Trial> trials();

    ///
    /// Returns the number of trials in this sequence.
    ///
    /// ## Equivalence
    ///
    /// ```
    /// size() = trials().size()
    /// ```
    ///
    /// This method is provided for convenience and may be more efficient
    /// than loading all trials.
    ///
    /// @return trial count, always ≥ 0
    ///
    default int size() {
        return trials().size();
    }

    ///
    /// Checks if this sequence contains no trials.
    ///
    /// ## Empty Sequences
    ///
    /// Empty sequences may occur when:
    /// - Constraints are impossible to satisfy
    /// - Domain intersection is empty
    /// - Explicit empty sequence created for testing
    ///
    /// ## Contract
    ///
    /// ```
    /// isEmpty() = true ⟺ size() = 0
    /// ```
    ///
    /// @return true if sequence has no trials
    ///
    default boolean isEmpty() {
        return size() == 0;
    }

    ///
    /// Validates all trials in this sequence against their constraints.
    ///
    /// ## Validation Process
    ///
    /// ```
    /// validate():
    ///   1. For each trial in sequence:
    ///      a. Validate trial.assignments completeness
    ///      b. Validate each value against parameter constraints
    ///      c. Validate cross-parameter constraints
    ///
    ///   2. Check global sequence constraints:
    ///      a. No duplicate trial ids
    ///      b. All required parameters present in every trial
    ///      c. Sequence-level invariants satisfied
    ///
    ///   3. Aggregate results:
    ///      - Passed if all trials valid
    ///      - Failed with all violations if any invalid
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Sequence seq = builder.build();
    /// ValidationResult result = seq.validate();
    ///
    /// switch (result) {
    ///     case ValidationResult.Passed p -> {
    ///         System.out.println("Sequence valid, ready to execute");
    ///         // Proceed with execution
    ///     }
    ///     case ValidationResult.Failed f -> {
    ///         System.err.println("Sequence validation failed:");
    ///         f.violations().forEach(v -> System.err.println("  - " + v));
    ///         // Fix sequence or abort
    ///     }
    ///     case ValidationResult.Warning w -> {
    ///         System.out.println("Warning: " + w.message());
    ///         // Proceed with caution
    ///     }
    /// }
    /// ```
    ///
    /// ## Performance Note
    ///
    /// Validation may be expensive for large sequences. Results SHOULD be cached.
    /// Once validated, a sequence's validity doesn't change (immutability).
    ///
    /// @return validation result with any violations, never null
    /// @see ValidationResult
    ///
    ValidationResult validate();

    ///
    /// Returns an iterator over trials in execution order.
    ///
    /// ## Iteration Order
    ///
    /// ```
    /// iterator() returns trials in same order as trials() list:
    ///
    /// Iterator<Trial> iter = seq.iterator();
    /// List<Trial> list = seq.trials();
    ///
    /// for (int i = 0; iter.hasNext(); i++) {
    ///     assert iter.next().equals(list.get(i));
    /// }
    /// ```
    ///
    /// ## Streaming
    ///
    /// Use iterator for memory-efficient streaming:
    /// ```java
    /// // Good: streaming (constant memory)
    /// for (Trial trial : sequence) {
    ///     process(trial);
    /// }
    ///
    /// // Less good: materializes all trials (O(n) memory)
    /// List<Trial> all = sequence.trials();
    /// for (Trial trial : all) {
    ///     process(trial);
    /// }
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return independent iterator (multiple iterators OK)
    /// - MUST produce trials in consistent order
    /// - MUST NOT modify sequence during iteration
    ///
    /// @return iterator over trials in execution order
    ///
    @Override
    Iterator<Trial> iterator();
}
