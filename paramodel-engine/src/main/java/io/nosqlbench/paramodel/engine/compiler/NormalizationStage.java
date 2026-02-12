package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.plan.DefaultElement;
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
/// Canonicalizes TestPlan representation and derives instancing scopes for
/// elements that support it.
///
/// ## Scope Derivation
///
/// For {@link DefaultElement} instances without an explicit scope, the stage
/// reads an optional {@code "instancing_hint"} tag:
///
/// - {@code "per_run"}   → {@link Element.InstancingScope#PER_RUN}
/// - {@code "per_trial"} → {@link Element.InstancingScope#PER_TRIAL}
/// - absent              → infer from axis targeting and dependency graph
///
/// Inference rules (when no hint is present):
///
/// 1. If any axis targets this element → PER_TRIAL
/// 2. If this element depends on a PER_TRIAL element → PER_TRIAL (taint propagation)
/// 3. Otherwise → PER_RUN
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

        // Derive instancing scopes for DefaultElement instances
        deriveScopes(plan);
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

    /// Derives instancing scopes for elements that don't have explicit scopes.
    ///
    /// The algorithm is purely generic — it reads an optional "instancing_hint"
    /// tag on each element and infers scope from axis targeting and dependency
    /// graphs when no hint is present.
    private void deriveScopes(TestPlan plan) {
        // First pass: set scopes from hints and axis targeting
        Set<String> variedElements = new HashSet<>();
        for (Axis<?> axis : plan.axes()) {
            axis.targetElement().ifPresent(variedElements::add);
        }

        for (Element element : plan.elements()) {
            if (element.instancingScope().isEmpty() && element instanceof DefaultElement de) {
                String hint = element.tags().getOrDefault("instancing_hint", "");
                Element.InstancingScope scope = switch (hint) {
                    case "per_run" -> Element.InstancingScope.PER_RUN;
                    case "per_trial" -> Element.InstancingScope.PER_TRIAL;
                    default -> inferScope(element, variedElements, plan);
                };
                if (scope != null) {
                    de.setInstancingScope(scope);
                }
            }
        }

        // Second pass: taint propagation — if any dependency is PER_TRIAL,
        // then this element must also be PER_TRIAL
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Element element : plan.elements()) {
                if (element instanceof DefaultElement de
                        && element.instancingScope()
                            .map(s -> s != Element.InstancingScope.PER_TRIAL)
                            .orElse(false)) {
                    for (Element dep : element.dependencies()) {
                        if (dep.instancingScope()
                                .map(s -> s == Element.InstancingScope.PER_TRIAL)
                                .orElse(false)) {
                            de.setInstancingScope(Element.InstancingScope.PER_TRIAL);
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }
    }

    /// Infers scope from axis targeting when no hint is present.
    ///
    /// @return PER_TRIAL if any axis targets this element, PER_RUN otherwise
    private Element.InstancingScope inferScope(
            Element element,
            Set<String> variedElements,
            TestPlan plan) {
        if (variedElements.contains(element.name())) {
            return Element.InstancingScope.PER_TRIAL;
        }
        return Element.InstancingScope.PER_RUN;
    }
}
