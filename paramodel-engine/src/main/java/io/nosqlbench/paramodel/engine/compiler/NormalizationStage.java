package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.plan.TestPlan;

/**
 * Stage 2: Normalization
 *
 * Canonicalizes TestPlan representation:
 * - Sort parameters alphabetically
 * - Merge duplicate constraints
 * - Expand axis shorthand notation
 * - Flatten nested structures
 */
public class NormalizationStage implements CompilationStage {

    @Override
    public String name() {
        return "Normalization";
    }

    @Override
    public CompilationContext execute(CompilationContext context) {
        TestPlan plan = context.testPlan();

        // For now, normalization is a no-op
        // In a full implementation, this would canonicalize the plan structure
        // Store normalized plan as artifact
        return context.withArtifact("normalized_plan", plan);
    }
}
