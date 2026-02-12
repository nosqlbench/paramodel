package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.OptimizationStrategy;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class PruneRedundantPassTest {

    @Test
    @DisplayName("Adjacent trials with same config are pruned")
    void adjacentTrialsWithSameConfigArePruned() {
        // Directly inject steps that represent a redundant deploy/teardown pattern:
        // deploy_server_t0 (port=8080) -> exec_trial_0 -> barrier_0
        // -> teardown_server (reason=parameter_change, trial_index=1)
        // -> deploy_server_t1 (port=8080, same config!) -> exec_trial_1 -> barrier_1
        // -> teardown_final
        // The PruneRedundantPass should remove the intermediate teardown+deploy pair.
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var server = MockElement.builder("server")
            .parameter(portParam)
            .build();
        var portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("prune-test")
            .axis(portAxis)
            .element(server)
            .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
            .build();

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());

        // Manually construct steps with redundant deploy pair (same config)
        Map<String, Object> sameConfig = Map.of("port", 8080);
        List<AtomicStep> injectedSteps = new ArrayList<>();

        injectedSteps.add(new AtomicStep.DeployElement(
            "deploy_server_t0", "server", 0, sameConfig, List.of(), List.of(),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("scope", "PER_TRIAL", "trial_index", 0)
        ));
        injectedSteps.add(new AtomicStep.ExecuteTrial(
            "exec_trial_0", "trial_0", Map.of("server", "inst_0"), List.of("deploy_server_t0"),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("trial_index", 0)
        ));
        injectedSteps.add(new AtomicStep.TeardownElement(
            "teardown_server_t1", "server", 0, false, List.of("exec_trial_0"),
            Optional.empty(), AtomicStep.ResourceRequirements.none(),
            Optional.empty(), Map.of("reason", "parameter_change", "trial_index", 1)
        ));
        injectedSteps.add(new AtomicStep.DeployElement(
            "deploy_server_t1", "server", 1, sameConfig, List.of(), List.of("teardown_server_t1"),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("scope", "PER_TRIAL", "trial_index", 1)
        ));
        injectedSteps.add(new AtomicStep.ExecuteTrial(
            "exec_trial_1", "trial_1", Map.of("server", "inst_1"), List.of("deploy_server_t1"),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("trial_index", 1)
        ));
        injectedSteps.add(new AtomicStep.TeardownElement(
            "teardown_final_server", "server", 1, true, List.of("exec_trial_1"),
            Optional.empty(), AtomicStep.ResourceRequirements.none(),
            Optional.empty(), Map.of("phase", "cleanup")
        ));

        context.setSteps(injectedSteps);

        int stepsBefore = context.steps().get().size();
        assertThat(stepsBefore).isEqualTo(6);

        new OptimizationStage().execute(context);

        // The intermediate teardown + deploy pair should be pruned (2 steps removed)
        int stepsAfter = context.steps().get().size();
        assertThat(stepsAfter).isEqualTo(4);
    }

    @Test
    @DisplayName("Distinct configs are not pruned")
    void distinctConfigsAreNotPruned() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        // Two different values — no pruning possible
        var portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("no-prune-test")
            .axis(portAxis)
            .element(server)
            .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
            .build();

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());

        // Inject steps with different configs
        List<AtomicStep> injectedSteps = new ArrayList<>();
        injectedSteps.add(new AtomicStep.DeployElement(
            "deploy_server_t0", "server", 0, Map.of("port", 8080), List.of(), List.of(),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("scope", "PER_TRIAL", "trial_index", 0)
        ));
        injectedSteps.add(new AtomicStep.ExecuteTrial(
            "exec_trial_0", "trial_0", Map.of("server", "inst_0"), List.of("deploy_server_t0"),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("trial_index", 0)
        ));
        injectedSteps.add(new AtomicStep.TeardownElement(
            "teardown_server_t1", "server", 0, false, List.of("exec_trial_0"),
            Optional.empty(), AtomicStep.ResourceRequirements.none(),
            Optional.empty(), Map.of("reason", "parameter_change", "trial_index", 1)
        ));
        injectedSteps.add(new AtomicStep.DeployElement(
            "deploy_server_t1", "server", 1, Map.of("port", 8081), List.of(), List.of("teardown_server_t1"),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("scope", "PER_TRIAL", "trial_index", 1)
        ));
        injectedSteps.add(new AtomicStep.ExecuteTrial(
            "exec_trial_1", "trial_1", Map.of("server", "inst_1"), List.of("deploy_server_t1"),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("trial_index", 1)
        ));
        injectedSteps.add(new AtomicStep.TeardownElement(
            "teardown_final_server", "server", 1, true, List.of("exec_trial_1"),
            Optional.empty(), AtomicStep.ResourceRequirements.none(),
            Optional.empty(), Map.of("phase", "cleanup")
        ));

        context.setSteps(injectedSteps);

        int stepsBefore = context.steps().get().size();

        new OptimizationStage().execute(context);

        int stepsAfter = context.steps().get().size();
        assertThat(stepsAfter).isEqualTo(stepsBefore); // no pruning — configs differ
    }

    @Test
    @DisplayName("NONE optimization strategy skips all passes")
    void noneStrategySkipsAllPasses() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        var portAxis = MockAxis.of("port", 8080, 8080);

        TestPlan plan = MockTestPlan.builder()
            .name("none-strategy-test")
            .axis(portAxis)
            .element(server)
            .optimizationStrategy(OptimizationStrategy.NONE)
            .build();

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());

        // Inject identical-config steps that would be prunable
        Map<String, Object> sameConfig = Map.of("port", 8080);
        List<AtomicStep> injectedSteps = new ArrayList<>();
        injectedSteps.add(new AtomicStep.DeployElement(
            "deploy_server_t0", "server", 0, sameConfig, List.of(), List.of(),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("scope", "PER_TRIAL", "trial_index", 0)
        ));
        injectedSteps.add(new AtomicStep.TeardownElement(
            "teardown_server_t1", "server", 0, false, List.of("deploy_server_t0"),
            Optional.empty(), AtomicStep.ResourceRequirements.none(),
            Optional.empty(), Map.of("reason", "parameter_change", "trial_index", 1)
        ));
        injectedSteps.add(new AtomicStep.DeployElement(
            "deploy_server_t1", "server", 1, sameConfig, List.of(), List.of("teardown_server_t1"),
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of("scope", "PER_TRIAL", "trial_index", 1)
        ));

        context.setSteps(injectedSteps);

        int stepsBefore = context.steps().get().size();

        new OptimizationStage().execute(context);

        int stepsAfter = context.steps().get().size();
        assertThat(stepsAfter).isEqualTo(stepsBefore); // NONE strategy — no optimization applied
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
