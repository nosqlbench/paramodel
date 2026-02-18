package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FullPipelineIntegrationTest {

    @Test
    @DisplayName("End-to-end: TestPlan with axes + elements -> compile -> verify plan")
    void endToEndCompilation() {
        // Setup: DB (global) + App (per-trial, depends on DB)
        Element db = MockElement.of("db");
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element app = MockElement.builder("app")
            .parameter(portParam)
            .dependency(db)
            .build();

        var portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("e2e-test")
            .axis(portAxis)
            .element(db)
            .element(app)
            .build();

        // Compile via DefaultCompiler
        DefaultCompiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        Compiler.CompilationResult result = compiler.compile(plan);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.executionPlan()).isPresent();

        ExecutionPlan execPlan = result.executionPlan().get();

        // Verify step count: should have deploy, execute, barrier, teardown steps
        assertThat(execPlan.steps()).isNotEmpty();

        // Verify trial count: 2 trials (port 8080, 8081)
        long trialSteps = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TrialStep)
            .count();
        assertThat(trialSteps).isEqualTo(2);

        // No ELEMENT_SCOPE_END barriers (removed from planner). No
        // ELEMENT_READY or TRIAL_BATCH barriers since neither element
        // has a health check.
        assertThat(execPlan.barriers()).isEmpty();

        // Verify execution graph
        assertThat(execPlan.executionGraph()).isNotNull();
        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Multi-element with dependencies: correct step ordering")
    void multiElementWithDependencies() {
        Element infra = MockElement.of("infra");
        Element db = MockElement.builder("db")
            .dependency(infra)
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            .build();

        var axis = MockAxis.of("mode", "test");

        TestPlan plan = MockTestPlan.builder()
            .name("multi-dep-test")
            .axis(axis)
            .element(infra)
            .element(db)
            .element(app)
            .build();

        DefaultCompiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        Compiler.CompilationResult result = compiler.compile(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // Verify deploy order: infra before db before app
        List<AtomicStep> steps = execPlan.steps();
        List<AtomicStep.DeployElement> deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        assertThat(deploys).hasSizeGreaterThanOrEqualTo(3);

        // infra should be first deploy
        assertThat(deploys.getFirst().elementId()).isEqualTo("infra");
    }

    @Test
    @DisplayName("Compilation result contains non-empty compiled plan from pipeline")
    void compiledPlanFromPipeline() {
        Element svc = MockElement.of("svc");
        var axis = MockAxis.of("threads", 1, 2, 4, 8);

        TestPlan plan = MockTestPlan.builder()
            .name("pipeline-plan-test")
            .axis(axis)
            .element(svc)
            .build();

        DefaultCompiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .build();

        Compiler.CompilationResult result = compiler.compile(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // The plan should come from the pipeline (DefaultExecutionPlan), not MockExecutionPlan
        assertThat(execPlan).isInstanceOf(DefaultExecutionPlan.class);
        assertThat(execPlan.steps()).isNotEmpty();

        // 4 trials => 4 execute trial steps
        long execTrials = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TrialStep)
            .count();
        assertThat(execTrials).isEqualTo(4);
    }
}
