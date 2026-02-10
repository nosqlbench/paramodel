package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.parameters.SamplingStrategy;

import java.util.Map;

///
/// Per-axis sampling configuration stored in {@link io.nosqlbench.paramodel.compilation.CompilationContext}
/// via {@code put("samplingConfig", config)}.
///
/// The {@link TrialEnumerationStage} reads this to control how axes are sampled,
/// nested, and repeated during trial generation.
///
/// ## Fields
///
/// - **strategies**: axis name → sampling strategy (defaults to Grid if absent)
/// - **nesting**: axis name → nesting level (0 = outermost loop)
/// - **repetitions**: axis name → repetition count (1 = no repetition)
///
/// @param strategies  per-axis sampling strategy
/// @param nesting     per-axis nesting level for loop ordering
/// @param repetitions per-axis repetition count
///
public record SamplingConfig(
    Map<String, SamplingStrategy> strategies,
    Map<String, Integer> nesting,
    Map<String, Integer> repetitions
) {
    public SamplingConfig {
        strategies = Map.copyOf(strategies);
        nesting = Map.copyOf(nesting);
        repetitions = Map.copyOf(repetitions);
    }

    /// Creates an empty config (all defaults: Grid sampling, natural order, no repetitions).
    public static SamplingConfig defaults() {
        return new SamplingConfig(Map.of(), Map.of(), Map.of());
    }
}
