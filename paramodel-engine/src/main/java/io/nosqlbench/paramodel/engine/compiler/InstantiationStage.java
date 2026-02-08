package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.plan.Element;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

/**
 * Stage 4: Instantiation
 *
 * Creates concrete values from trial specifications:
 * - Generate values from domains
 * - Apply fixed values from elements
 * - Create Trial instances
 * - Apply constraints and filter invalid trials
 */
public class InstantiationStage implements CompilationStage {

    @Override
    public String name() {
        return "Instantiation";
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompilationContext execute(CompilationContext context) {
        TestPlan plan = context.testPlan();

        // Get trial specs from previous stage
        Optional<List<TrialEnumerationStage.TrialSpec>> specsOpt =
            context.getArtifact("trial_specs", List.class);

        if (specsOpt.isEmpty()) {
            return context.withError("Trial specifications not found");
        }

        List<TrialEnumerationStage.TrialSpec> specs =
            (List<TrialEnumerationStage.TrialSpec>) (List<?>) specsOpt.get();

        // Instantiate trials
        List<InstantiatedTrial> trials = new ArrayList<>();
        Random rng = new Random(42); // Deterministic for now

        for (int i = 0; i < specs.size(); i++) {
            TrialEnumerationStage.TrialSpec spec = specs.get(i);
            InstantiatedTrial trial = instantiate(spec, plan, rng, "trial-" + i);
            trials.add(trial);
        }

        return context.withArtifact("instantiated_trials", trials);
    }

    private InstantiatedTrial instantiate(TrialEnumerationStage.TrialSpec spec,
                                         TestPlan plan,
                                         Random rng,
                                         String trialId) {
        Map<String, Value<?>> assignments = new HashMap<>();

        for (Map.Entry<String, Element> entry : spec.elements().entrySet()) {
            String paramName = entry.getKey();
            Element element = entry.getValue();
            Parameter<?> parameter = plan.parameters().get(paramName);

            Value<?> value;
            if (element.fixedValue().isPresent()) {
                value = element.fixedValue().get();
            } else {
                // Generate value based on sampling strategy
                value = generateValue(parameter, element.samplingStrategy(), rng);
            }

            assignments.put(paramName, value);
        }

        return new InstantiatedTrial(trialId, assignments);
    }

    private <T> Value<T> generateValue(Parameter<T> parameter,
                                       Element.SamplingStrategy strategy,
                                       Random rng) {
        // For now, just generate a random value
        // Full implementation would handle different strategies
        T value = parameter.generate(rng);
        return new SimpleValue<>(value, parameter.name());
    }

    /**
     * Instantiated trial with concrete assignments.
     */
    public static class InstantiatedTrial {
        private final String id;
        private final Map<String, Value<?>> assignments;

        public InstantiatedTrial(String id, Map<String, Value<?>> assignments) {
            this.id = id;
            this.assignments = Map.copyOf(assignments);
        }

        public String id() {
            return id;
        }

        public Map<String, Value<?>> assignments() {
            return assignments;
        }
    }

    /**
     * Simple Value implementation for instantiation.
     */
    private static class SimpleValue<T> implements Value<T> {
        private final T value;
        private final String parameterName;
        private final java.time.Instant generatedAt;

        public SimpleValue(T value, String parameterName) {
            this.value = value;
            this.parameterName = parameterName;
            this.generatedAt = java.time.Instant.now();
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        public String parameterName() {
            return parameterName;
        }

        @Override
        public java.time.Instant generatedAt() {
            return generatedAt;
        }

        @Override
        public String fingerprint() {
            return UUID.randomUUID().toString();
        }

        @Override
        public Optional<String> generatorMetadata() {
            return Optional.empty();
        }
    }
}
