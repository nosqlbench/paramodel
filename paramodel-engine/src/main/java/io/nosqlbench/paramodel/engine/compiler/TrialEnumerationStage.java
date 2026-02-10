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

        if (axes.isEmpty()) {
            context.addError(Compiler.ErrorSeverity.ERROR, "No axes defined in TestPlan", "TrialEnumeration", "Define at least one axis");
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
        // Build Cartesian product with sampling applied
        List<Map<String, Value<?>>> combinations = new ArrayList<>();
        combinations.add(new HashMap<>());

        for (Axis<?> axis : sortedAxes) {
            List<?> effectiveValues = getEffectiveValues(axis, config);
            combinations = expandAxis(combinations, axis, effectiveValues);
        }

        // Apply repetitions
        int maxRepetitions = 1;
        for (Axis<?> axis : sortedAxes) {
            int reps = config.repetitions().getOrDefault(axis.name(), 1);
            maxRepetitions = Math.max(maxRepetitions, reps);
        }

        List<Map<String, Value<?>>> finalCombinations;
        if (maxRepetitions > 1) {
            finalCombinations = applyRepetitions(combinations, maxRepetitions);
        } else {
            finalCombinations = combinations;
        }

        // Convert to Trial objects
        List<Trial> result = new ArrayList<>();
        int index = 0;
        for (Map<String, Value<?>> assignments : finalCombinations) {
            int repetitionIndex = 0;
            if (assignments.containsKey("__repetition__")) {
                Value<?> repVal = assignments.get("__repetition__");
                repetitionIndex = ((Number) repVal.value()).intValue();
                // Remove internal marker from assignments
                assignments = new HashMap<>(assignments);
                assignments.remove("__repetition__");
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
        List<?> allValues = axis.values();
        SamplingStrategy strategy = config.strategies().getOrDefault(axis.name(), SamplingStrategy.grid());

        return switch (strategy) {
            case SamplingStrategy.Grid() -> allValues;
            case SamplingStrategy.Random(int count, long seed) -> sampleRandom(allValues, count, seed);
            case SamplingStrategy.Linspace(int count) -> generateLinspace(allValues, count);
        };
    }

    private List<?> sampleRandom(List<?> values, int k, long seed) {
        if (k >= values.size()) {
            return values;
        }
        List<Object> shuffled = new ArrayList<>(values);
        Random rng = new Random(seed);
        Collections.shuffle(shuffled, rng);
        return shuffled.subList(0, k);
    }

    @SuppressWarnings("unchecked")
    private List<?> generateLinspace(List<?> values, int count) {
        if (values.isEmpty() || count >= values.size()) {
            return values;
        }
        if (count == 1) {
            return List.of(values.getFirst());
        }

        // Check if all values are numeric
        boolean allNumeric = values.stream().allMatch(v -> v instanceof Number);
        if (allNumeric && values.size() >= 2) {
            return generateNumericLinspace((List<Number>) values, count);
        }

        // Non-numeric: evenly-spaced indices
        return sampleEvenly(values, count);
    }

    private List<Object> generateNumericLinspace(List<Number> values, int count) {
        double min = values.stream().mapToDouble(Number::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Number::doubleValue).max().orElse(0);

        // Determine original type from first value
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

    private List<?> sampleEvenly(List<?> values, int count) {
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

    private List<Map<String, Value<?>>> expandAxis(
        List<Map<String, Value<?>>> combinations, Axis<?> axis, List<?> effectiveValues
    ) {
        List<Map<String, Value<?>>> newCombinations = new ArrayList<>();
        String paramName = axis.underlyingParameter().map(p -> p.name()).orElse(axis.name());

        for (Map<String, Value<?>> base : combinations) {
            for (Object val : effectiveValues) {
                Map<String, Value<?>> next = new HashMap<>(base);
                Value<Object> valueObj = new DefaultValue<>(
                    val,
                    paramName,
                    Instant.now(),
                    Optional.of("Axis enumeration")
                );
                next.put(paramName, valueObj);
                newCombinations.add(next);
            }
        }
        return newCombinations;
    }

    private List<Map<String, Value<?>>> applyRepetitions(
        List<Map<String, Value<?>>> combinations, int maxRepetitions
    ) {
        List<Map<String, Value<?>>> result = new ArrayList<>(combinations.size() * maxRepetitions);
        for (int rep = 0; rep < maxRepetitions; rep++) {
            for (Map<String, Value<?>> combo : combinations) {
                Map<String, Value<?>> copy = new HashMap<>(combo);
                copy.put("__repetition__", new DefaultValue<>(
                    rep, "__repetition__", Instant.now(), Optional.of("repetition_index")
                ));
                result.add(copy);
            }
        }
        return result;
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
