package io.nosqlbench.paramodel.plan;

///
/// Enumeration of optimization strategies for test plan compilation.
///
/// ## Concept
///
/// {@code OptimizationStrategy} controls how aggressive the compiler should be
/// when optimizing the execution plan. Different strategies trade off between:
/// - Compilation time
/// - Runtime performance
/// - Plan complexity
/// - Resource usage
///
/// ## Strategies
///
/// ```
/// NONE:
///   - No optimizations applied
///   - Fastest compilation
///   - Useful for debugging
///
/// BASIC:
///   - Simple optimizations only
///   - Fast compilation
///   - Modest runtime improvements
///
/// PRUNE_REDUNDANT:
///   - Remove redundant operations
///   - Eliminate duplicate trials
///   - Moderate compilation time
///
/// AGGRESSIVE:
///   - All available optimizations
///   - Slower compilation
///   - Best runtime performance
/// ```
///
/// ## Usage Example
///
/// ```java
/// TestPlan plan = TestPlan.builder()
///     .name("optimized-study")
///     .axis(...)
///     .element(...)
///     .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
///     .build();
/// ```
///
/// @see TestPlan
/// @see io.nosqlbench.paramodel.compilation.OptimizationPass
/// @since 0.1.0
///
public enum OptimizationStrategy {
    ///
    /// No optimizations applied.
    ///
    /// Use for:
    /// - Debugging
    /// - Fastest compilation
    /// - Understanding raw plan structure
    ///
    NONE,

    ///
    /// Basic optimizations only.
    ///
    /// Use for:
    /// - Quick compilation
    /// - Simple plans
    /// - Development iterations
    ///
    BASIC,

    ///
    /// Prune redundant operations and trials.
    ///
    /// Use for:
    /// - Production plans
    /// - Eliminating duplicates
    /// - Balanced compilation/runtime
    ///
    PRUNE_REDUNDANT,

    ///
    /// All available optimizations.
    ///
    /// Use for:
    /// - Long-running studies
    /// - Maximum runtime efficiency
    /// - One-time compilation acceptable
    ///
    AGGRESSIVE
}
