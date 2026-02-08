package io.nosqlbench.paramodel.core.metadata;

import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.cost.CostEstimator;
import io.nosqlbench.paramodel.plan.TrialOrdering;
import io.nosqlbench.paramodel.sequence.Sequence;
import io.nosqlbench.paramodel.sequence.SequenceBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

///
/// Descriptive metadata about a sequence's generation, validation, and characteristics.
///
/// ## Concept
///
/// {@code SequenceMetadata} captures information about how a sequence was created,
/// what strategy was used, and contextual information for understanding the sequence.
///
/// Unlike {@link Value} which describes individual values,
/// SequenceMetadata describes the entire ordered collection of trials.
///
/// ## Structure
///
/// ```
/// SequenceMetadata
/// ├── generatedAt: Instant                - When sequence was generated
/// ├── generatedBy: Optional<String>       - Who/what generated it
/// ├── orderingStrategy: String            - Trial ordering algorithm
/// ├── totalTrials: int                    - Number of trials in sequence
/// ├── estimatedDuration: Optional<Duration> - Expected execution time
/// ├── tags: Map<String, String>           - Arbitrary labels
/// └── validationStatus: ValidationStatus  - Was sequence validated?
/// ```
///
/// ## Metadata Through Lifecycle
///
/// ```
/// Sequence Generation:
///   SequenceBuilder.build()
///         ↓
///   SequenceMetadata created
///   ├── generatedAt = now
///   ├── orderingStrategy = "edge-first"
///   ├── totalTrials = computed
///   └── validationStatus = NOT_VALIDATED
///
/// Sequence Validation:
///   Sequence.validate()
///         ↓
///   Metadata updated (immutably)
///         ↓
///   validationStatus = VALIDATED / FAILED
///
/// Sequence Execution:
///   Metadata used for planning
///   ├── totalTrials → progress tracking
///   ├── estimatedDuration → time estimates
///   └── orderingStrategy → execution order
/// ```
///
/// ## Ordering Strategies
///
/// Metadata captures which strategy was used:
///
/// ```
/// Strategy          Description
/// ───────────────────────────────────────────────────────
/// "exhaustive"      All combinations in lexicographic order
/// "random"          Random permutation of trials
/// "edge-first"      Boundaries first, then interior fill
/// "pairwise"        All pairs of parameter values covered
/// "boundary"        Only boundary/extrema values
/// "user-defined"    Explicit trial list provided
/// "adaptive"        Dynamic based on results
/// ```
///
/// ## Use Cases
///
/// ### Progress Tracking
///
/// ```java
/// SequenceMetadata meta = sequence.metadata();
/// int total = meta.totalTrials();
/// int completed = executor.completedCount();
/// double progress = (double) completed / total * 100;
/// System.out.printf("Progress: %.1f%% (%d/%d)%n", progress, completed, total);
/// ```
///
/// ### Time Estimation
///
/// ```java
/// SequenceMetadata meta = sequence.metadata();
/// Optional<Duration> estimated = meta.estimatedDuration();
/// estimated.ifPresent(duration ->
///     System.out.println("Estimated completion: " + duration)
/// );
/// ```
///
/// ### Strategy Analysis
///
/// ```java
/// // Compare strategies
/// SequenceMetadata edgeFirst = seq1.metadata();
/// SequenceMetadata random = seq2.metadata();
///
/// System.out.println("Edge-first: " + edgeFirst.totalTrials() + " trials");
/// System.out.println("Random: " + random.totalTrials() + " trials");
/// ```
///
/// ### Validation Checking
///
/// ```java
/// SequenceMetadata meta = sequence.metadata();
/// if (meta.validationStatus() != ValidationStatus.VALIDATED) {
///     throw new IllegalStateException("Sequence not validated");
/// }
/// ```
///
/// @see Sequence
/// @see SequenceBuilder
/// @since 0.1.0
///
public interface SequenceMetadata {

    ///
    /// Returns when this sequence was generated.
    ///
    /// @return generation timestamp, never null
    ///
    Instant generatedAt();

    ///
    /// Returns who or what generated this sequence.
    ///
    /// ## Common Values
    ///
    /// ```
    /// "user@example.com"           - User-initiated generation
    /// "simplica-planner"           - Test plan compilation
    /// "sequence-builder"           - Programmatic API
    /// "test-framework"             - Testing tool
    /// ```
    ///
    /// @return generator identifier if known
    ///
    Optional<String> generatedBy();

    ///
    /// Returns the trial ordering strategy used to generate this sequence.
    ///
    /// ## Strategy Semantics
    ///
    /// The ordering strategy determines:
    /// - **Which trials** are included
    /// - **In what order** they execute
    /// - **Why** that order was chosen
    ///
    /// ## Standard Strategies
    ///
    /// ```
    /// "exhaustive"    : All combinations, complete coverage
    /// "edge-first"    : Boundaries outline space first
    /// "random"        : Random order for unbiased coverage
    /// "pairwise"      : All pairs covered, fewer total trials
    /// "boundary"      : Only extrema, minimal trials
    /// "adaptive"      : Order changes based on results
    /// ```
    ///
    /// ## Custom Strategies
    ///
    /// Implementations may define custom strategies:
    /// ```
    /// "latin-hypercube"           - Space-filling design
    /// "sobol-sequence"            - Low-discrepancy sequence
    /// "importance-sampling"       - Weighted by importance
    /// "pareto-frontier"           - Multi-objective optimization
    /// ```
    ///
    /// @return ordering strategy name, never null or empty
    /// @see TrialOrdering
    ///
    String orderingStrategy();

    ///
    /// Returns the total number of trials in this sequence.
    ///
    /// ## Cardinality
    ///
    /// ```
    /// totalTrials = |sequence.trials()|
    /// ```
    ///
    /// This is the **fixed size** of the sequence at generation time.
    /// It does not change during execution (even if trials fail).
    ///
    /// ## Use Cases
    ///
    /// - Progress tracking: completed / total
    /// - Resource planning: allocate resources for N trials
    /// - Time estimation: average duration * total trials
    ///
    /// @return number of trials, always ≥ 0
    ///
    int totalTrials();

    ///
    /// Returns an estimated total duration for executing this sequence.
    ///
    /// ## Estimation Basis
    ///
    /// Estimates may come from:
    /// - **Historical data**: Similar sequences in the past
    /// - **Cost estimation**: Telemetry-based prediction
    /// - **User input**: Explicitly provided estimates
    /// - **Heuristics**: Rule-based approximation
    ///
    /// ## Confidence
    ///
    /// If no historical data exists, this returns empty:
    /// ```
    /// Optional.empty() → No basis for estimation
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// SequenceMetadata meta = sequence.metadata();
    /// meta.estimatedDuration().ifPresentOrElse(
    ///     duration -> System.out.println("ETA: " + duration),
    ///     () -> System.out.println("Duration unknown")
    /// );
    /// ```
    ///
    /// @return estimated execution duration if available
    /// @see CostEstimator
    ///
    Optional<java.time.Duration> estimatedDuration();

    ///
    /// Returns arbitrary key-value tags for categorization and filtering.
    ///
    /// ## Common Tags
    ///
    /// ```
    /// "experiment" → "model-comparison-2024"
    /// "priority" → "high"
    /// "team" → "ml-research"
    /// "cost-center" → "PROJECT-123"
    /// "environment" → "production"
    /// ```
    ///
    /// @return immutable tag map, never null
    ///
    Map<String, String> tags();

    ///
    /// Returns the validation status of this sequence.
    ///
    /// ## Validation States
    ///
    /// ```
    /// ValidationStatus
    /// ├── NOT_VALIDATED : Sequence created but not validated
    /// ├── VALIDATED     : All validation checks passed
    /// ├── FAILED        : Validation found errors
    /// └── SKIPPED       : Validation explicitly skipped
    /// ```
    ///
    /// ## Validation Requirements
    ///
    /// Before execution, sequences SHOULD be validated to ensure:
    /// - All trials satisfy constraints
    /// - No impossible parameter combinations
    /// - Dependencies are satisfiable
    /// - Resource requirements are feasible
    ///
    /// ## Example
    ///
    /// ```java
    /// Sequence seq = builder.build();
    /// assert seq.metadata().validationStatus() == ValidationStatus.NOT_VALIDATED;
    ///
    /// ValidationResult result = seq.validate();
    /// assert seq.metadata().validationStatus() ==
    ///     (result.isPassed() ? ValidationStatus.VALIDATED : ValidationStatus.FAILED);
    /// ```
    ///
    /// @return current validation status, never null
    ///
    ValidationStatus validationStatus();

    ///
    /// Validation status for sequences.
    ///
    /// ## State Transitions
    ///
    /// ```
    ///     NOT_VALIDATED
    ///           ↓
    ///     validate() called
    ///           ↓
    ///     ┌─────┴─────┐
    ///     ↓           ↓
    /// VALIDATED    FAILED
    ///
    /// SKIPPED (manual override)
    /// ```
    ///
    enum ValidationStatus {
        /// Sequence has not been validated yet
        NOT_VALIDATED,

        /// Sequence passed all validation checks
        VALIDATED,

        /// Sequence failed validation
        FAILED,

        /// Validation was explicitly skipped
        SKIPPED
    }
}
