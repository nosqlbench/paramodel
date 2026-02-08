package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.List;

///
/// A named parameter dimension in a Simplica study with ordered discrete values.
///
/// ## Concept
///
/// An {@code Axis<T>} is a parameter elevated to study context. It represents
/// one dimension of the trial space in a Test Plan.
///
/// ## Paramodel Parameter → Simplica Axis
///
/// ```
/// Paramodel:
///   Parameter<T>           - Abstract parameter with domain
///   ↓
/// Simplica:
///   Axis<T>                - Study-specific parameter dimension
///     ├── Has ordered values (not just a domain)
///     ├── Has study context (name, description)
///     └── Used in trial space calculation
/// ```
///
/// ## Axis vs Parameter
///
/// ```
/// Parameter<T>:
///   - Domain-based (continuous ranges, predicates)
///   - Value generation on-demand
///   - Abstract, reusable
///
/// Axis<T>:
///   - Explicit ordered list of values
///   - All values pre-determined
///   - Study-specific, concrete
/// ```
///
/// ## Structure
///
/// ```
/// Axis<T>
/// ├── name: String
/// │   └── Unique identifier in study
/// │
/// ├── values: List<T>
/// │   └── Ordered, discrete values to test
/// │
/// ├── description: Optional<String>
/// │   └── Human-readable description
/// │
/// └── underlyingParameter: Parameter<T>
///     └── Link back to paramodel parameter
/// ```
///
/// ## Trial Space Calculation
///
/// Given axes A1, A2, ..., An:
///
/// ```
/// Trial Space = A1 × A2 × ... × An
///
/// |Trial Space| = |A1.values| × |A2.values| × ... × |An.values|
///
/// Example:
///   model: ["gpt-4", "claude-3"]              → 2 values
///   temperature: [0.0, 0.5, 1.0]              → 3 values
///   max_tokens: [100, 500, 1000, 2000]        → 4 values
///
///   Total trials = 2 × 3 × 4 = 24
/// ```
///
/// ## Axis Ordering
///
/// The order axes are defined determines:
/// - Default iteration order (major → minor dimensions)
/// - Edge-first prioritization (vary major axes first)
/// - Grouping for resource sharing
///
/// ```
/// Example: 2 axes
///   Axis1 (major): [A, B]
///   Axis2 (minor): [1, 2, 3]
///
/// Lexicographic order:
///   (A,1), (A,2), (A,3), (B,1), (B,2), (B,3)
///
/// Edge-first order:
///   (A,1), (A,3), (B,1), (B,3),  ← Corners first
///   (A,2), (B,2)                 ← Interior second
/// ```
///
/// ## Usage Example: Simple Axes
///
/// ```java
/// // Discrete axis from explicit values
/// Axis<String> modelAxis = Axis.discrete("model",
///     List.of("gpt-4", "claude-3", "gemini-pro")
/// );
///
/// // Numeric axis from range
/// Axis<Double> tempAxis = Axis.range("temperature",
///     0.0, 1.0, 0.1  // min, max, step
/// );
///
/// // Boolean axis
/// Axis<Boolean> streamingAxis = Axis.discrete("streaming",
///     List.of(true, false)
/// );
/// ```
///
/// ## Usage Example: From Parameter
///
/// ```java
/// // Start with paramodel parameter
/// Parameter<Integer> ageParam = DiscreteParameter.range("age", 18, 65);
///
/// // Convert to axis with explicit values
/// Axis<Integer> ageAxis = Axis.fromParameter(ageParam)
///     .withValues(List.of(18, 25, 35, 50, 65))  // Sample from domain
///     .build();
///
/// // Or enumerate all values (if domain is finite)
/// Axis<String> platformAxis = Axis.fromParameter(platformParam)
///     .enumerateAll()  // Uses parameter.domain().enumerate()
///     .build();
/// ```
///
/// ## Usage Example: With Description
///
/// ```java
/// Axis<Integer> batchSizeAxis = Axis.discrete("batch_size",
///     List.of(16, 32, 64, 128, 256))
///     .withDescription(
///         "Training batch size controlling memory usage and convergence speed. " +
///         "Larger batches = more memory, faster training, potentially worse generalization."
///     )
///     .build();
/// ```
///
/// ## Axis Reordering
///
/// TestPlan allows axis reordering to see compilation effects:
///
/// ```java
/// TestPlan plan = TestPlan.builder()
///     .withAxis(modelAxis)       // 1st: major axis
///     .withAxis(tempAxis)        // 2nd: minor axis
///     .build();
///
/// ExecutionPlan exec1 = plan.commit();
/// // Edge-first varies modelAxis first
///
/// // Reorder axes
/// TestPlan reordered = plan.reorderAxes(
///     List.of("temperature", "model")  // Now temp is major
/// );
///
/// ExecutionPlan exec2 = reordered.commit();
/// // Edge-first now varies temperature first
/// // Different trial ordering!
/// ```
///
/// ## Boundary Values
///
/// Axes support boundary value identification:
///
/// ```java
/// Axis<Integer> axis = Axis.range("param", 0, 100, 10);
/// // values: [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
///
/// List<Integer> boundaries = axis.boundaryValues();
/// // [0, 100] - first and last
///
/// // Used in edge-first strategy
/// ```
///
/// ## Validation
///
/// Axes must satisfy:
///
/// ```
/// 1. Non-empty values list
/// 2. Unique name within TestPlan
/// 3. Values compatible with type T
/// 4. No duplicate values (each value appears once)
/// ```
///
/// ## Relationship to Trial
///
/// ```
/// Axis Definition:
///   model: ["gpt-4", "claude-3"]
///   temperature: [0.0, 0.5, 1.0]
///
/// Becomes Trials:
///   Trial 1: {model → "gpt-4",    temperature → 0.0}
///   Trial 2: {model → "gpt-4",    temperature → 0.5}
///   Trial 3: {model → "gpt-4",    temperature → 1.0}
///   Trial 4: {model → "claude-3", temperature → 0.0}
///   ...
/// ```
///
/// @param <T> the type of values along this axis
/// @see TestPlan
/// @see Parameter
/// @see Trial
/// @since 0.1.0
///
public interface Axis<T> {

    ///
    /// Returns the unique name of this axis within the study.
    ///
    /// ## Contract
    ///
    /// - MUST be non-null, non-empty
    /// - MUST be unique within TestPlan
    /// - SHOULD be human-readable (used in results, UIs)
    /// - SHOULD follow naming conventions (snake_case or camelCase)
    ///
    /// ## Common Names
    ///
    /// ```
    /// "model"
    /// "temperature"
    /// "batch_size"
    /// "learning_rate"
    /// "optimizer"
    /// "dataset_size"
    /// ```
    ///
    /// @return axis name, never null or empty
    ///
    String name();

    ///
    /// Returns the ordered list of discrete values along this axis.
    ///
    /// ## Order Significance
    ///
    /// The order determines:
    /// - Trial generation sequence (with other axes)
    /// - Boundary identification (first, last)
    /// - Edge-first prioritization
    ///
    /// ## Contract
    ///
    /// - MUST return non-null, unmodifiable list
    /// - MUST contain at least one value
    /// - SHOULD NOT contain duplicates
    /// - Order MUST be consistent (deterministic)
    ///
    /// ## Example
    ///
    /// ```java
    /// Axis<Double> axis = Axis.range("temp", 0.0, 1.0, 0.5);
    /// List<Double> values = axis.values();
    /// // [0.0, 0.5, 1.0]
    /// ```
    ///
    /// @return immutable, ordered list of values, never null or empty
    ///
    List<T> values();

    ///
    /// Returns the number of values along this axis.
    ///
    /// ## Equivalence
    ///
    /// ```
    /// cardinality() = values().size()
    /// ```
    ///
    /// This contributes to total trial space size:
    /// ```
    /// |TrialSpace| = ∏ axis.cardinality() for all axes
    /// ```
    ///
    /// @return number of values, always ≥ 1
    ///
    default int cardinality() {
        return values().size();
    }

    ///
    /// Returns boundary values (extrema) of this axis.
    ///
    /// ## Boundary Definition
    ///
    /// For an ordered axis:
    /// ```
    /// boundaries = [first value, last value]
    /// ```
    ///
    /// ## Edge-First Usage
    ///
    /// Edge-first strategy uses boundaries to scaffold parameter space:
    /// ```
    /// Axis1 boundaries: [min1, max1]
    /// Axis2 boundaries: [min2, max2]
    ///
    /// Corner trials:
    ///   (min1, min2), (min1, max2), (max1, min2), (max1, max2)
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Axis<Integer> axis = Axis.discrete("param",
    ///     List.of(10, 20, 30, 40, 50)
    /// );
    ///
    /// List<Integer> boundaries = axis.boundaryValues();
    /// // [10, 50]
    /// ```
    ///
    /// @return boundary values (typically first and last), never null
    ///
    List<T> boundaryValues();

    ///
    /// Returns optional description of this axis's purpose and semantics.
    ///
    /// ## Description Guidelines
    ///
    /// Good descriptions explain:
    /// - What the axis controls
    /// - How values affect behavior
    /// - Valid value ranges/constraints
    /// - Performance/quality tradeoffs
    ///
    /// ## Example
    ///
    /// ```java
    /// Axis<Double> tempAxis = Axis.range("temperature", 0.0, 1.0, 0.1)
    ///     .withDescription(
    ///         "Model temperature parameter controlling output randomness. " +
    ///         "Higher values (→1.0) produce more creative, varied outputs. " +
    ///         "Lower values (→0.0) produce more deterministic, predictable outputs."
    ///     )
    ///     .build();
    /// ```
    ///
    /// @return axis description if provided, empty otherwise
    ///
    java.util.Optional<String> description();

    ///
    /// Returns the underlying paramodel parameter this axis is based on.
    ///
    /// ## Parameter Linkage
    ///
    /// Axes are derived from parameters:
    /// ```
    /// Parameter<T> (abstract domain)
    ///      ↓ sample/enumerate
    /// Axis<T> (concrete values)
    /// ```
    ///
    /// The underlying parameter provides:
    /// - Domain definition
    /// - Constraints
    /// - Generation metadata
    /// - Type information
    ///
    /// ## Use Cases
    ///
    /// - Access parameter constraints
    /// - Validate axis values against domain
    /// - Generate additional values
    /// - Link to parameter metadata
    ///
    /// @return underlying parameter if available, empty if axis created directly
    ///
    java.util.Optional<Parameter<T>> underlyingParameter();

    ///
    /// Checks if a value is present along this axis.
    ///
    /// ## Convenience Method
    ///
    /// Equivalent to:
    /// ```java
    /// axis.values().contains(value)
    /// ```
    ///
    /// @param value the value to check
    /// @return true if value is in axis values
    ///
    default boolean contains(T value) {
        return values().contains(value);
    }

    ///
    /// Returns the index of a value along this axis.
    ///
    /// ## Index Semantics
    ///
    /// ```
    /// values()[indexOf(value)] = value
    /// ```
    ///
    /// ## Use Cases
    ///
    /// - Coordinate calculations
    /// - Trial encoding
    /// - Ordering comparisons
    ///
    /// ## Example
    ///
    /// ```java
    /// Axis<String> axis = Axis.discrete("model",
    ///     List.of("gpt-4", "claude-3", "gemini-pro")
    /// );
    ///
    /// int idx = axis.indexOf("claude-3");
    /// // idx = 1
    /// ```
    ///
    /// @param value the value to find
    /// @return index (0-based) or -1 if not found
    ///
    default int indexOf(T value) {
        return values().indexOf(value);
    }
}
