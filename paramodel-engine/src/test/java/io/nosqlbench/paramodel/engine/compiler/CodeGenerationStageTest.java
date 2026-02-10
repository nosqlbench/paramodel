package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CodeGenerationStageTest {

    @Test
    @DisplayName("Plan structure matches compiled artifacts")
    void planStructureMatchesCompiledArtifacts() {
        var svc = MockElement.of("svc");
        var axis = MockAxis.of("mode", "fast", "slow");

        TestPlan plan = MockTestPlan.builder()
            .name("codegen-test")
            .axis(axis)
            .element(svc)
            .build();

        DefaultCompilationContext context = runFullPipeline(plan);

        // Retrieve assembled execution plan
        var planOpt = context.get("executionPlan");
        assertThat(planOpt).isPresent();

        ExecutionPlan execPlan = (ExecutionPlan) planOpt.get();

        assertThat(execPlan.id()).isNotNull();
        assertThat(execPlan.testPlanFingerprint()).contains("codegen-test");
        assertThat(execPlan.steps()).isEqualTo(context.steps().get());
        assertThat(execPlan.barriers()).isEqualTo(context.barriers().get());
        assertThat(execPlan.executionGraph()).isNotNull();
        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Execution graph is present and consistent")
    void executionGraphIsPresentAndConsistent() {
        var svc = MockElement.of("svc");
        var axis = MockAxis.of("op", "r", "w", "rw");

        TestPlan plan = MockTestPlan.builder()
            .name("graph-test")
            .axis(axis)
            .element(svc)
            .build();

        DefaultCompilationContext context = runFullPipeline(plan);
        ExecutionPlan execPlan = (ExecutionPlan) context.get("executionPlan").get();

        ExecutionGraph graph = execPlan.executionGraph();
        assertThat(graph.steps()).hasSize(execPlan.steps().size());
        assertThat(graph.isAcyclic()).isTrue();
        assertThat(graph.maximumParallelism()).isGreaterThanOrEqualTo(1);
    }

    private DefaultCompilationContext runFullPipeline(TestPlan plan) {
        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());
        new ValidationStage().execute(context);
        new NormalizationStage().execute(context);
        new TrialEnumerationStage().execute(context);
        new InstantiationStage().execute(context);
        new StepGenerationStage().execute(context);
        new DependencyAnalysisStage().execute(context);
        new OptimizationStage().execute(context);
        new CodeGenerationStage().execute(context);
        return context;
    }

    private Compiler.CompilerOptions defaultOptions() {
        return new Compiler.CompilerOptions() {
            @Override public Compiler.CompilationStrategy strategy() { return Compiler.CompilationStrategy.BALANCED; }
            @Override public Compiler.OptimizationLevel optimizationLevel() { return Compiler.OptimizationLevel.STANDARD; }
            @Override public long maxTrialSpaceSize() { return 1_000_000; }
            @Override public boolean parallelCompilation() { return false; }
            @Override public boolean dryRun() { return false; }
            @Override public Map<String, Object> customOptions() { return Map.of(); }
        };
    }
}
