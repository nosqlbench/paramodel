package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class StepGenerationStageTest {

    @Test
    @DisplayName("Global element produces single deploy + teardown pair")
    void globalElementSingleDeployAndTeardown() {
        Element db = MockElement.of("db");
        Axis<String> axis = MockAxis.of("mode", "read", "write");

        TestPlan plan = MockTestPlan.builder()
            .name("global-test")
            .axis(axis)
            .element(db)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep> steps = context.steps().get();

        // Should have: 1 global deploy + 2 ExecuteTrials + 2 BarrierSyncs + 1 final teardown
        long deploys = steps.stream().filter(s -> s instanceof AtomicStep.DeployElement).count();
        long teardowns = steps.stream().filter(s -> s instanceof AtomicStep.TeardownElement).count();
        long execTrials = steps.stream().filter(s -> s instanceof AtomicStep.ExecuteTrial).count();

        assertThat(deploys).isEqualTo(1); // single global deploy
        assertThat(teardowns).isEqualTo(1); // single final teardown
        assertThat(execTrials).isEqualTo(2); // one per trial
    }

    @Test
    @DisplayName("Per-trial element deploys per trial when config changes")
    void perTrialElementDeploysOnConfigChange() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("per-trial-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep> steps = context.steps().get();

        // Per-trial element with changing config: deploy for trial 0, teardown+deploy for trial 1
        long deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d && "PER_TRIAL".equals(d.metadata().get("scope")))
            .count();
        assertThat(deploys).isEqualTo(2); // one per trial since config differs
    }

    @Test
    @DisplayName("Dependencies produce correct step ordering")
    void dependenciesProduceCorrectOrdering() {
        Element db = MockElement.of("db");
        Element app = MockElement.builder("app")
            .dependency(db)
            .build();

        Axis<String> axis = MockAxis.of("mode", "fast");

        TestPlan plan = MockTestPlan.builder()
            .name("dep-order-test")
            .axis(axis)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep> steps = context.steps().get();

        // Find deploy steps
        List<AtomicStep.DeployElement> deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        // DB deploy should come before APP deploy
        int dbDeployIdx = -1, appDeployIdx = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i) instanceof AtomicStep.DeployElement d) {
                if (d.elementId().equals("db")) dbDeployIdx = i;
                if (d.elementId().equals("app")) appDeployIdx = i;
            }
        }
        assertThat(dbDeployIdx).isLessThan(appDeployIdx);
    }

    @Test
    @DisplayName("ExecuteTrial binds correct element instances")
    void executeTrialBindsCorrectInstances() {
        Element db = MockElement.of("db");
        Element cache = MockElement.of("cache");

        Axis<String> axis = MockAxis.of("op", "read");

        TestPlan plan = MockTestPlan.builder()
            .name("binding-test")
            .axis(axis)
            .element(db)
            .element(cache)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep.ExecuteTrial> execTrials = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.ExecuteTrial)
            .map(AtomicStep.ExecuteTrial.class::cast)
            .toList();

        assertThat(execTrials).hasSize(1);
        assertThat(execTrials.getFirst().elementBindings()).containsKeys("db", "cache");
    }

    @Test
    @DisplayName("Barrier steps created at trial boundaries")
    void barrierStepsCreated() {
        Element svc = MockElement.of("svc");
        Axis<String> axis = MockAxis.of("mode", "a", "b", "c");

        TestPlan plan = MockTestPlan.builder()
            .name("barrier-test")
            .axis(axis)
            .element(svc)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep.BarrierSync> barrierSteps = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.BarrierSync)
            .map(AtomicStep.BarrierSync.class::cast)
            .toList();

        assertThat(barrierSteps).hasSize(3); // one per trial

        List<Barrier> barriers = context.barriers().get();
        assertThat(barriers).hasSize(3);
    }

    @Test
    @DisplayName("Teardown in reverse topological order")
    void teardownInReverseTopoOrder() {
        Element db = MockElement.of("db");
        Element app = MockElement.builder("app")
            .dependency(db)
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("reverse-teardown-test")
            .axis(axis)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        // Get final teardown steps (phase=cleanup)
        List<AtomicStep.TeardownElement> finalTeardowns = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(finalTeardowns).hasSize(2);
        // app should be torn down before db (reverse topo)
        assertThat(finalTeardowns.get(0).elementId()).isEqualTo("app");
        assertThat(finalTeardowns.get(1).elementId()).isEqualTo("db");
    }

    private DefaultCompilationContext runPipeline(TestPlan plan) {
        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());
        new ValidationStage().execute(context);
        new NormalizationStage().execute(context);
        new TrialEnumerationStage().execute(context);
        new InstantiationStage().execute(context);
        new StepGenerationStage().execute(context);
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
