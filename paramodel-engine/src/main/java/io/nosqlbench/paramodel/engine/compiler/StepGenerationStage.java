package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.plan.AtomicStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stage 5: Step Generation
 *
 * Converts instantiated trials into atomic execution steps:
 * - Create AtomicStep for each trial
 * - Add execution context (resources, priorities)
 * - Compute step identifiers
 * - Track step metadata
 */
public class StepGenerationStage implements CompilationStage {

    @Override
    public String name() {
        return "StepGeneration";
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompilationContext execute(CompilationContext context) {
        // Get instantiated trials from previous stage
        Optional<List<InstantiationStage.InstantiatedTrial>> trialsOpt =
            context.getArtifact("instantiated_trials", List.class);

        if (trialsOpt.isEmpty()) {
            return context.withError("Instantiated trials not found");
        }

        List<InstantiationStage.InstantiatedTrial> trials =
            (List<InstantiationStage.InstantiatedTrial>) (List<?>) trialsOpt.get();

        // Generate atomic steps
        List<AtomicStepSpec> steps = new ArrayList<>();

        for (InstantiationStage.InstantiatedTrial trial : trials) {
            AtomicStepSpec step = new AtomicStepSpec(
                trial.id(),
                trial.assignments()
            );
            steps.add(step);
        }

        return context.withArtifact("atomic_steps", steps);
    }

    /**
     * Specification for an atomic step.
     */
    public static class AtomicStepSpec {
        private final String id;
        private final java.util.Map<String, io.nosqlbench.paramodel.core.Value<?>> assignments;

        public AtomicStepSpec(String id,
                             java.util.Map<String, io.nosqlbench.paramodel.core.Value<?>> assignments) {
            this.id = id;
            this.assignments = java.util.Map.copyOf(assignments);
        }

        public String id() {
            return id;
        }

        public java.util.Map<String, io.nosqlbench.paramodel.core.Value<?>> assignments() {
            return assignments;
        }
    }
}
