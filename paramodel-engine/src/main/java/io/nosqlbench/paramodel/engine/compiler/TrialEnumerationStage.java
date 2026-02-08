package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.Element;
import io.nosqlbench.paramodel.plan.TestPlan;

import java.util.*;

/**
 * Stage 3: Trial Enumeration
 *
 * Expands parameter space into trial specifications:
 * - Compute Cartesian products
 * - Apply sampling strategies (boundary, random, exhaustive)
 * - Generate trial blueprints
 * - Estimate trial counts
 */
public class TrialEnumerationStage implements CompilationStage {

    @Override
    public String name() {
        return "TrialEnumeration";
    }

    @Override
    public CompilationContext execute(CompilationContext context) {
        TestPlan plan = context.testPlan();

        // Compute trial specifications
        List<TrialSpec> trialSpecs = enumerateTrials(plan);

        // Store for next stage
        return context.withArtifact("trial_specs", trialSpecs);
    }

    private List<TrialSpec> enumerateTrials(TestPlan plan) {
        List<TrialSpec> specs = new ArrayList<>();

        if (plan.axes().isEmpty()) {
            // No axes defined - create single empty trial
            specs.add(new TrialSpec(Map.of()));
            return specs;
        }

        // Build Cartesian product of all axes
        List<Map<String, Element>> combinations = computeCartesianProduct(plan.axes());

        for (Map<String, Element> combination : combinations) {
            specs.add(new TrialSpec(combination));
        }

        return specs;
    }

    private List<Map<String, Element>> computeCartesianProduct(List<Axis> axes) {
        if (axes.isEmpty()) {
            return List.of(Map.of());
        }

        List<Map<String, Element>> result = new ArrayList<>();
        result.add(new HashMap<>());

        for (Axis axis : axes) {
            List<Map<String, Element>> newResult = new ArrayList<>();

            for (Map<String, Element> partial : result) {
                for (Element element : axis.elements()) {
                    Map<String, Element> extended = new HashMap<>(partial);
                    extended.put(element.parameterName(), element);
                    newResult.add(extended);
                }
            }

            result = newResult;
        }

        return result;
    }

    /**
     * Specification for a trial to be instantiated.
     */
    public static class TrialSpec {
        private final Map<String, Element> elements;

        public TrialSpec(Map<String, Element> elements) {
            this.elements = Map.copyOf(elements);
        }

        public Map<String, Element> elements() {
            return elements;
        }
    }
}
