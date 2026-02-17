package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.SamplingStrategy;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.TestPlan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

///
/// Stage 2: Normalization
///
/// Canonicalizes TestPlan representation and derives axis binding sets for
/// all elements.
///
/// ## Binding Derivation
///
/// Binding sets are derived purely from parameter-axis overlap and dependency
/// propagation. Elements do not declare their own binding sets.
///
/// Inference rules:
///
/// 1. If any axis targets this element → bound to those axes
/// 2. If this element depends on a bound element → propagate axes
/// 3. Otherwise → run-scoped (depth 0)
///
public class NormalizationStage implements CompilationStage {
    public NormalizationStage() {}

    @Override
    public String name() {
        return "Normalization";
    }

    @Override
    public void execute(CompilationContext context) {
        TestPlan plan = context.testPlan();

        // Store normalized plan as artifact
        context.put("normalized_plan", plan);

        // Extract axis-level tags (repetitions, nesting, sampling) into SamplingConfig
        // so TrialEnumerationStage can use them
        buildSamplingConfig(plan, context);

        // Derive axis binding sets for all elements
        deriveAxisBindings(plan, context);
    }

    /// Reads axis tags and builds a {@link SamplingConfig} for the
    /// {@link TrialEnumerationStage}.
    ///
    /// Tags read: {@code "repetitions"}, {@code "nesting"}, {@code "sampling_type"},
    /// {@code "sampling_count"}, {@code "sampling_seed"}.
    private void buildSamplingConfig(TestPlan plan, CompilationContext context) {
        // Don't overwrite if already set (e.g. by adopter code or tests)
        if (context.get("samplingConfig").isPresent()) {
            return;
        }

        Map<String, SamplingStrategy> strategies = new HashMap<>();
        Map<String, Integer> nesting = new HashMap<>();
        Map<String, Integer> repetitions = new HashMap<>();

        for (Axis<?> axis : plan.axes()) {
            String name = axis.name();
            Map<String, String> tags = axis.tags();

            String repsStr = tags.get("repetitions");
            if (repsStr != null) {
                try {
                    int reps = Integer.parseInt(repsStr);
                    if (reps > 1) {
                        repetitions.put(name, reps);
                    }
                } catch (NumberFormatException ignored) {}
            }

            String nestStr = tags.get("nesting");
            if (nestStr != null) {
                try {
                    nesting.put(name, Integer.parseInt(nestStr));
                } catch (NumberFormatException ignored) {}
            }

            String samplingType = tags.get("sampling_type");
            if (samplingType != null) {
                int count = 0;
                String countStr = tags.get("sampling_count");
                if (countStr != null) {
                    try { count = Integer.parseInt(countStr); } catch (NumberFormatException ignored) {}
                }
                SamplingStrategy strategy = switch (samplingType) {
                    case "linspace" -> SamplingStrategy.linspace(count);
                    case "random" -> {
                        long seed = 0;
                        String seedStr = tags.get("sampling_seed");
                        if (seedStr != null) {
                            try { seed = Long.parseLong(seedStr); } catch (NumberFormatException ignored) {}
                        }
                        yield SamplingStrategy.random(count, seed);
                    }
                    default -> SamplingStrategy.grid();
                };
                strategies.put(name, strategy);
            }
        }

        context.put("samplingConfig", new SamplingConfig(strategies, nesting, repetitions));
    }

    /// Derives axis binding sets for all elements from parameter-axis overlap
    /// and dependency propagation.
    ///
    /// The algorithm computes which axes bind to each element:
    ///
    /// 1. **Direct binding pass** — for each element, compute which axes directly
    ///    target it (via `targetElement` or parameter name matching).
    ///
    /// 2. **Propagation pass** — fixed-point iteration: if A depends on B and B
    ///    is bound to axis X, A is also bound to X.
    ///
    /// 3. **Store bindings** — store all computed bindings in context for
    ///    downstream stages.
    private void deriveAxisBindings(TestPlan plan, CompilationContext context) {
        // First pass: compute direct axis bindings
        Map<String, Set<String>> directAxes = new HashMap<>();
        for (Axis<?> axis : plan.axes()) {
            axis.targetElement().ifPresent(target ->
                directAxes.computeIfAbsent(target, k -> new HashSet<>()).add(axis.name()));
        }

        // Also check parameter name matching for axis binding
        for (Element element : plan.elements()) {
            for (Axis<?> axis : plan.axes()) {
                if (axis.targetElement().isEmpty()) {
                    // Check if axis name matches a parameter
                    for (var param : element.parameters()) {
                        if (axis.name().equals(param.name()) ||
                            axis.name().equals(element.name() + "." + param.name())) {
                            directAxes.computeIfAbsent(element.name(), k -> new HashSet<>())
                                .add(axis.name());
                        }
                    }
                }
            }
        }

        // Build effective bindings per element from direct axes
        Map<String, Set<String>> effectiveAxes = new HashMap<>();
        for (Element element : plan.elements()) {
            effectiveAxes.put(element.name(),
                new HashSet<>(directAxes.getOrDefault(element.name(), Set.of())));
        }

        // Second pass: propagation — if A depends on B, A's effective axes
        // include B's effective axes.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Element element : plan.elements()) {
                Set<String> currentAxes = effectiveAxes.get(element.name());
                if (currentAxes == null) continue;

                for (Element.Dependency dep : element.dependencies()) {
                    Set<String> depAxes = effectiveAxes.get(dep.target().name());
                    if (depAxes != null && currentAxes.addAll(depAxes)) {
                        changed = true;
                    }
                }
            }
        }

        // Third pass: compute final bindings and store in context
        Map<String, AxisBindingSet> effectiveBindings = new HashMap<>();
        for (Element element : plan.elements()) {
            Set<String> axes = effectiveAxes.getOrDefault(element.name(), Set.of());
            AxisBindingSet binding = axes.isEmpty()
                ? AxisBindingSet.runScoped()
                : AxisBindingSet.of(axes);
            effectiveBindings.put(element.name(), binding);
        }

        // Store all effective bindings in context for downstream stages
        context.put(EFFECTIVE_BINDINGS_KEY, effectiveBindings);
    }

    /// Context key for the effective axis bindings map.
    public static final String EFFECTIVE_BINDINGS_KEY = "effectiveAxisBindings";

    /// Resolves the effective axis binding set for an element.
    ///
    /// Checks the context's effective bindings map (populated by
    /// NormalizationStage), defaulting to run-scoped if not found.
    ///
    /// @param context the compilation context
    /// @param element the element to resolve bindings for
    /// @return the effective axis binding set
    @SuppressWarnings("unchecked")
    public static AxisBindingSet resolveBinding(CompilationContext context, Element element) {
        return context.get(EFFECTIVE_BINDINGS_KEY)
            .filter(v -> v instanceof Map)
            .map(v -> ((Map<String, AxisBindingSet>) v).get(element.name()))
            .orElse(AxisBindingSet.runScoped());
    }
}
