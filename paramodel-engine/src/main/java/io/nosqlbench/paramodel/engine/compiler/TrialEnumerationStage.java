package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.engine.sequence.DefaultTrial;
import io.nosqlbench.paramodel.engine.sequence.DefaultValue;
import io.nosqlbench.paramodel.parameters.SamplingStrategy;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.parameters.Value;

import java.time.Instant;
import java.util.*;

///
/// Stage 3: Trial Enumeration
///
/// Expands the parameter space into trials by computing the Cartesian product
/// of axis values, with support for:
///
/// - **Sampling strategies**: Grid (all values), Random (k samples), Linspace (n evenly-spaced)
/// - **Nesting order**: Controls which axis forms the outermost loop
/// - **Repetitions**: Duplicates each combination a specified number of times
///
/// Reads optional {@link SamplingConfig} from context. If absent, defaults to
/// Grid sampling for all axes in their natural order with no repetitions.
///
public class TrialEnumerationStage implements CompilationStage {
    public TrialEnumerationStage() {}

    @Override
    public String name() {
        return "TrialEnumeration";
    }

    @Override
    public void execute(CompilationContext context) {
        TestPlan plan = context.testPlan();
        List<Axis<?>> axes = plan.axes();

        // No axes = single trial with no varying parameters (degenerate Cartesian product).
        // This supports "one-shot" studies where all element parameters are fixed.
        if (axes.isEmpty()) {
            Trial.TrialMetadata metadata = new SimpleTrialMetadata(0, "default", "degenerate", 0);
            List<Trial> singleTrial = List.of(new DefaultTrial(
                UUID.randomUUID().toString(),
                Collections.emptyMap(),
                Collections.emptyList(),
                metadata
            ));
            context.setTrials(singleTrial);
            context.recordMetric("trials_enumerated", 1);
            return;
        }

        SamplingConfig config = context.get("samplingConfig")
            .map(SamplingConfig.class::cast)
            .orElse(SamplingConfig.defaults());

        // Sort axes by nesting level (outermost first)
        List<Axis<?>> sortedAxes = new ArrayList<>(axes);
        sortedAxes.sort(Comparator.comparingInt(a -> config.nesting().getOrDefault(a.name(), axes.indexOf(a))));

        List<Trial> trials = generateTrials(sortedAxes, config);
        context.setTrials(trials);
        context.recordMetric("trials_enumerated", trials.size());
    }

    private List<Trial> generateTrials(List<Axis<?>> sortedAxes, SamplingConfig config) {
        // Build raw Cartesian product using CartesianExpander
        List<Map<String, Object>> combinations = new ArrayList<>();
        combinations.add(new HashMap<>());

        for (Axis<?> axis : sortedAxes) {
            List<?> effectiveValues = getEffectiveValues(axis, config);
            String paramName = axis.underlyingParameter().map(p -> p.name()).orElse(axis.name());
            combinations = CartesianExpander.expandAxis(combinations, paramName, effectiveValues);
        }

        // Apply repetitions
        int maxRepetitions = 1;
        for (Axis<?> axis : sortedAxes) {
            int reps = config.repetitions().getOrDefault(axis.name(), 1);
            maxRepetitions = Math.max(maxRepetitions, reps);
        }

        combinations = CartesianExpander.applyRepetitions(combinations, maxRepetitions);

        // Convert raw combinations to Trial objects with Value<?> wrappers
        List<Trial> result = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> rawAssignments : combinations) {
            int repetitionIndex = 0;
            if (rawAssignments.containsKey(CartesianExpander.REPETITION_KEY)) {
                repetitionIndex = ((Number) rawAssignments.get(CartesianExpander.REPETITION_KEY)).intValue();
            }

            // Wrap raw values in DefaultValue, excluding the repetition marker
            Map<String, Value<?>> assignments = new HashMap<>();
            for (Map.Entry<String, Object> entry : rawAssignments.entrySet()) {
                if (!CartesianExpander.REPETITION_KEY.equals(entry.getKey())) {
                    assignments.put(entry.getKey(), new DefaultValue<>(
                        entry.getValue(),
                        entry.getKey(),
                        Instant.now(),
                        Optional.of("Axis enumeration")
                    ));
                }
            }

            String genMethod = maxRepetitions > 1 ? "cartesian_product_rep" + repetitionIndex : "cartesian_product";
            Trial.TrialMetadata metadata = new SimpleTrialMetadata(index++, "default", genMethod, 0);
            result.add(new DefaultTrial(
                UUID.randomUUID().toString(),
                assignments,
                Collections.emptyList(),
                metadata
            ));
        }
        return result;
    }

    private List<?> getEffectiveValues(Axis<?> axis, SamplingConfig config) {
        SamplingStrategy strategy = config.strategies()
            .getOrDefault(axis.name(), SamplingStrategy.grid());
        return AxisExpander.applyStrategy(axis.values(), strategy);
    }

    private static class SimpleTrialMetadata implements Trial.TrialMetadata {
        private final int index;
        private final String group;
        private final String genMethod;
        private final int priority;

        public SimpleTrialMetadata(int index, String group, String genMethod, int priority) {
            this.index = index;
            this.group = group;
            this.genMethod = genMethod;
            this.priority = priority;
        }

        @Override public Optional<Integer> sequenceIndex() { return Optional.of(index); }
        @Override public Optional<String> group() { return Optional.ofNullable(group); }
        @Override public Optional<String> generationMethod() { return Optional.ofNullable(genMethod); }
        @Override public Optional<Integer> priority() { return Optional.of(priority); }
    }
}
