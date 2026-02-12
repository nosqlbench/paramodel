package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.parameters.SamplingStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/// Utility class that applies sampling strategies to axis value lists.
///
/// Extracts the shared sampling/expansion logic used by both paramodel's
/// `TrialEnumerationStage` and hyperplane's `TrialComposer`, eliminating
/// code duplication between the two projects.
///
/// All methods are stateless and deterministic for the same inputs.
public final class AxisExpander {
    private AxisExpander() {}

    /// Applies a sampling strategy to an axis value list.
    ///
    /// Dispatches to the strategy-specific method:
    /// - Grid: returns all values unchanged
    /// - Random: deterministically samples k values
    /// - Linspace: generates evenly-spaced values
    ///
    /// @param values   the full value list for the axis
    /// @param strategy the sampling strategy to apply
    /// @return the effective value list after sampling
    public static List<?> applyStrategy(List<?> values, SamplingStrategy strategy) {
        return switch (strategy) {
            case SamplingStrategy.Grid() -> values;
            case SamplingStrategy.Random(int count, long seed) -> sampleRandom(values, count, seed);
            case SamplingStrategy.Linspace(int count) -> generateLinspace(values, count);
        };
    }

    /// Randomly samples k values from the list using a deterministic seed.
    ///
    /// If k >= the list size, all values are returned unchanged.
    ///
    /// @param values the full value list
    /// @param k      number of samples to draw
    /// @param seed   random seed for reproducibility
    /// @return a sublist of k randomly-chosen values
    public static List<?> sampleRandom(List<?> values, int k, long seed) {
        if (k >= values.size()) {
            return values;
        }
        List<Object> shuffled = new ArrayList<>(values);
        Random rng = new Random(seed);
        Collections.shuffle(shuffled, rng);
        return shuffled.subList(0, k);
    }

    /// Generates evenly-spaced values from a numeric range.
    ///
    /// For numeric lists (all elements are `Number`), interpolates between
    /// the min and max values, preserving the original numeric type
    /// (Integer, Long, or Double).
    ///
    /// For non-numeric lists, falls back to evenly-spaced index sampling.
    ///
    /// @param values the full value list
    /// @param count  number of evenly-spaced points to generate
    /// @return the interpolated or index-sampled value list
    @SuppressWarnings("unchecked")
    public static List<?> generateLinspace(List<?> values, int count) {
        if (values.isEmpty()) {
            return values;
        }
        if (count == 1) {
            return List.of(values.getFirst());
        }

        // For numeric lists, interpolate between min and max even when
        // count exceeds the input list size. This supports the common
        // pattern of specifying only boundary values (e.g. [0, 100])
        // and requesting N interpolated points.
        boolean allNumeric = values.stream().allMatch(v -> v instanceof Number);
        if (allNumeric && values.size() >= 2) {
            return generateNumericLinspace((List<Number>) values, count);
        }

        // For non-numeric lists, can only sample from existing values
        if (count >= values.size()) {
            return values;
        }
        return sampleEvenly(values, count);
    }

    private static List<Object> generateNumericLinspace(List<Number> values, int count) {
        double min = values.stream().mapToDouble(Number::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Number::doubleValue).max().orElse(0);

        Number representative = values.getFirst();
        double step = (max - min) / (count - 1);

        List<Object> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double val = min + i * step;
            if (representative instanceof Integer) {
                result.add((int) Math.round(val));
            } else if (representative instanceof Long) {
                result.add(Math.round(val));
            } else {
                result.add(val);
            }
        }
        return result;
    }

    private static List<?> sampleEvenly(List<?> values, int count) {
        if (count >= values.size()) {
            return values;
        }
        List<Object> result = new ArrayList<>(count);
        double step = (double) (values.size() - 1) / (count - 1);
        for (int i = 0; i < count; i++) {
            int index = (int) Math.round(i * step);
            result.add(values.get(index));
        }
        return result;
    }
}
