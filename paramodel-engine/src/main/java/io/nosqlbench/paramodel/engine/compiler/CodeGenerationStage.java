package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import io.nosqlbench.paramodel.plan.TrialOrdering;

import java.util.List;
import java.util.UUID;

///
/// Stage 8: Code Generation
///
/// Assembles the final {@link io.nosqlbench.paramodel.plan.ExecutionPlan} from
/// compiled artifacts stored in the compilation context:
///
/// 1. Reads steps, barriers, and execution graph from context
/// 2. Constructs a {@link DefaultExecutionPlan}
/// 3. Stores the plan in context as {@code "executionPlan"}
///
public class CodeGenerationStage implements CompilationStage {
    public CodeGenerationStage() {}

    @Override
    public String name() {
        return "CodeGeneration";
    }

    @Override
    public void execute(CompilationContext context) {
        List<AtomicStep> steps = context.steps().orElse(List.of());
        List<Barrier> barriers = context.barriers().orElse(List.of());

        // Build execution graph if not already built (fallback)
        ExecutionGraph graph = context.get("executionGraph")
            .filter(ExecutionGraph.class::isInstance)
            .map(ExecutionGraph.class::cast)
            .orElseGet(() -> new DefaultExecutionGraph(steps));

        String planId = UUID.randomUUID().toString();
        String fingerprint = "compiled:" + context.testPlan().name() + ":" + steps.size();

        DefaultExecutionPlan plan = new DefaultExecutionPlan(
            planId,
            fingerprint,
            steps,
            barriers,
            graph,
            TrialOrdering.SEQUENTIAL
        );

        context.put("executionPlan", plan);
        context.recordMetric("code_generated", steps.size());
    }
}
