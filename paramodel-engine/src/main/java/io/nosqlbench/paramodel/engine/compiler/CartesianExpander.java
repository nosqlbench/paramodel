package io.nosqlbench.paramodel.engine.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Utility class that expands parameter combinations via Cartesian product.
///
/// Extracts the shared expansion logic used by both paramodel's
/// `TrialEnumerationStage` and hyperplane's `TrialComposer`, eliminating
/// code duplication between the two projects.
///
/// All methods are stateless and operate on raw `Map<String, Object>` so
/// they are independent of any specific value-wrapper type.
public final class CartesianExpander {

    /// Sentinel key used to mark the repetition index in combination maps.
    public static final String REPETITION_KEY = "__repetition__";

    private CartesianExpander() {}

    /// Expands a single parameter across all existing combinations
    /// (one step of the Cartesian product).
    ///
    /// For each existing combination and each value in `values`, a new
    /// combination is created containing all entries from the original plus
    /// the new `parameterName → value` mapping.
    ///
    /// @param combinations  the current list of partial combinations
    /// @param parameterName the parameter name to add
    /// @param values        the values to cross with existing combinations
    /// @return a new list of combinations with the parameter expanded
    public static List<Map<String, Object>> expandAxis(
            List<Map<String, Object>> combinations,
            String parameterName,
            List<?> values) {

        List<Map<String, Object>> expanded = new ArrayList<>();
        for (Map<String, Object> base : combinations) {
            for (Object value : values) {
                Map<String, Object> next = new LinkedHashMap<>(base);
                next.put(parameterName, value);
                expanded.add(next);
            }
        }
        return expanded;
    }

    /// Duplicates each combination for each repetition, adding a
    /// [REPETITION_KEY] marker with the repetition index.
    ///
    /// If `maxRepetitions` is 1, the input list is returned unchanged
    /// (no repetition markers are added).
    ///
    /// @param combinations   the combinations to repeat
    /// @param maxRepetitions number of repetitions (must be >= 1)
    /// @return a list with `combinations.size() * maxRepetitions` entries,
    ///         or the original list when `maxRepetitions == 1`
    public static List<Map<String, Object>> applyRepetitions(
            List<Map<String, Object>> combinations,
            int maxRepetitions) {

        if (maxRepetitions <= 1) {
            return combinations;
        }

        List<Map<String, Object>> result = new ArrayList<>(combinations.size() * maxRepetitions);
        for (Map<String, Object> combo : combinations) {
            for (int rep = 0; rep < maxRepetitions; rep++) {
                Map<String, Object> copy = new LinkedHashMap<>(combo);
                copy.put(REPETITION_KEY, rep);
                result.add(copy);
            }
        }
        return result;
    }
}
