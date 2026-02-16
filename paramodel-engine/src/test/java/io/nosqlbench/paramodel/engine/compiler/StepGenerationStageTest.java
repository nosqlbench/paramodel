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

import java.util.*;
import java.util.stream.Collectors;

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
    @DisplayName("PER_GROUP element deploys at group boundary when config changes")
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

        // PER_GROUP element with changing config: deploy for trial 0, teardown+deploy for trial 1
        long deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d && "PER_GROUP".equals(d.metadata().get("scope")))
            .count();
        assertThat(deploys).isEqualTo(2); // one per group since config differs
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
    @DisplayName("ELEMENT_SCOPE_END barriers for PER_GROUP elements at group boundaries and final teardown")
    void barrierStepsCreated() {
        // ELEMENT_SCOPE_END barriers are emitted at group boundaries and
        // before final teardown — teardowns depend on them for synchronization.
        // TRIAL_BATCH barriers are NOT emitted because nothing depends on them.
        // ELEMENT_READY barriers are NOT emitted (no health check on this element).
        var portParam = IntegerParameter.range("port", 8080, 8082);
        Element svc = MockElement.builder("svc")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081, 8082);

        TestPlan plan = MockTestPlan.builder()
            .name("barrier-test")
            .axis(portAxis)
            .element(svc)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<Barrier> barriers = context.barriers().get();

        // No TRIAL_BATCH barriers — nothing depends on them
        long trialBatchCount = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.TRIAL_BATCH)
            .count();
        assertThat(trialBatchCount).isZero();

        // ELEMENT_SCOPE_END barriers at group boundaries + final teardown
        long scopeEndCount = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_SCOPE_END)
            .count();
        assertThat(scopeEndCount).isGreaterThan(0);

        // Barrier sync steps exist (ELEMENT_SCOPE_END)
        long barrierStepCount = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.BarrierSync)
            .count();
        assertThat(barrierStepCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Barrier behavior for global-only and PER_TRIAL-only plans")
    void noBarriersWhenNoRecyclingElements() {
        // Global element without health check: ELEMENT_SCOPE_END barrier at
        // final teardown, but no TRIAL_BATCH or ELEMENT_READY barriers.
        Element db = MockElement.of("db");
        Axis<String> axis = MockAxis.of("mode", "a", "b");

        TestPlan globalPlan = MockTestPlan.builder()
            .name("global-no-barrier-test")
            .axis(axis)
            .element(db)
            .build();

        DefaultCompilationContext globalCtx = runPipeline(globalPlan);
        long globalTrialBatchBarriers = globalCtx.barriers().orElse(List.of()).stream()
            .filter(b -> b.type() == Barrier.BarrierType.TRIAL_BATCH)
            .count();
        assertThat(globalTrialBatchBarriers).isZero();

        // Global plan should have a final ELEMENT_SCOPE_END barrier
        long globalScopeEndBarriers = globalCtx.barriers().orElse(List.of()).stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_SCOPE_END)
            .count();
        assertThat(globalScopeEndBarriers).isGreaterThan(0);

        // PER_TRIAL element: independent per trial, no barriers of any kind
        // (all teardowns are eager, no final teardowns, so no barrier needed)
        Element worker = MockElement.builder("worker")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();

        TestPlan perTrialPlan = MockTestPlan.builder()
            .name("per-trial-no-barrier-test")
            .axis(axis)
            .element(worker)
            .build();

        DefaultCompilationContext perTrialCtx = runPipeline(perTrialPlan);
        long perTrialBarriers = perTrialCtx.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.BarrierSync)
            .count();
        assertThat(perTrialBarriers).isZero();
    }

    @Test
    @DisplayName("Teardown in reverse topological order with chained dependencies")
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

        // db's teardown must explicitly depend on app's teardown so a
        // concurrent executor cannot tear down db while app is still
        // shutting down
        assertThat(finalTeardowns.get(1).dependencies())
            .as("db teardown must depend on app teardown for safe concurrent execution")
            .contains(finalTeardowns.get(0).id());
    }

    @Test
    @DisplayName("Instance numbers assigned sequentially for per-trial deploys")
    void testInstanceNumbersAssignedSequentially() {
        var portParam = IntegerParameter.range("port", 8080, 8082);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081, 8082);

        TestPlan plan = MockTestPlan.builder()
            .name("instance-seq-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep.DeployElement> deploys = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        assertThat(deploys).hasSize(3);
        assertThat(deploys.get(0).instanceNumber()).isEqualTo(0);
        assertThat(deploys.get(1).instanceNumber()).isEqualTo(1);
        assertThat(deploys.get(2).instanceNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Instance numbers never reused after teardown and re-deploy")
    void testInstanceNumbersNeverReused() {
        var portParam = IntegerParameter.range("port", 8080, 8082);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081, 8080);

        TestPlan plan = MockTestPlan.builder()
            .name("instance-no-reuse-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep.DeployElement> deploys = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        // Even if config goes back to 8080, instance number still increments
        for (int i = 0; i < deploys.size(); i++) {
            assertThat(deploys.get(i).instanceNumber()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("Teardown carries matching instance number from its deploy")
    void testTeardownCarriesMatchingInstanceNumber() {
        var portParam = IntegerParameter.range("port", 8080, 8082);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("teardown-match-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep> steps = context.steps().get();

        // The intermediate teardown (group_boundary) should carry instance 0
        // matching the deploy it is tearing down
        List<AtomicStep.TeardownElement> intermediateTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "group_boundary".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(intermediateTeardowns).hasSize(1);
        assertThat(intermediateTeardowns.getFirst().instanceNumber()).isEqualTo(0);

        // The final teardown should carry instance 1 (the last deployed instance)
        List<AtomicStep.TeardownElement> finalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(finalTeardowns).hasSize(1);
        assertThat(finalTeardowns.getFirst().instanceNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Global (PER_RUN) element gets instance number 0")
    void testGlobalElementGetsInstanceZero() {
        Element db = MockElement.of("db");
        Axis<String> axis = MockAxis.of("mode", "read", "write");

        TestPlan plan = MockTestPlan.builder()
            .name("global-instance-test")
            .axis(axis)
            .element(db)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep> steps = context.steps().get();

        AtomicStep.DeployElement deploy = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(AtomicStep.DeployElement.class::cast)
            .findFirst()
            .orElseThrow();

        assertThat(deploy.instanceNumber()).isEqualTo(0);

        AtomicStep.TeardownElement teardown = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .findFirst()
            .orElseThrow();

        assertThat(teardown.instanceNumber()).isEqualTo(0);
    }

    @Test
    @DisplayName("Multiple elements have separate instance counters")
    void testMultipleElementsSeparateCounters() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var memParam = IntegerParameter.range("mem", 512, 1024);

        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();
        Element cache = MockElement.builder("cache")
            .parameter(memParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);
        Axis<Integer> memAxis = MockAxis.of("mem", 512, 1024);

        TestPlan plan = MockTestPlan.builder()
            .name("separate-counters-test")
            .axis(portAxis)
            .axis(memAxis)
            .element(server)
            .element(cache)
            .build();

        DefaultCompilationContext context = runPipeline(plan);

        List<AtomicStep.DeployElement> serverDeploys = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d && d.elementId().equals("server"))
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        List<AtomicStep.DeployElement> cacheDeploys = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d && d.elementId().equals("cache"))
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        // Each element's instance numbers start at 0 independently
        if (!serverDeploys.isEmpty()) {
            assertThat(serverDeploys.getFirst().instanceNumber()).isEqualTo(0);
        }
        if (!cacheDeploys.isEmpty()) {
            assertThat(cacheDeploys.getFirst().instanceNumber()).isEqualTo(0);
        }

        // Instance numbers are sequential within each element
        for (int i = 0; i < serverDeploys.size(); i++) {
            assertThat(serverDeploys.get(i).instanceNumber()).isEqualTo(i);
        }
        for (int i = 0; i < cacheDeploys.size(); i++) {
            assertThat(cacheDeploys.get(i).instanceNumber()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("PER_TRIAL elements deploy concurrently across trials with no cross-trial dependencies")
    void perTrialElementsConcurrentDeployment() {
        Element db = MockElement.builder("db")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a", "b", "c");

        TestPlan plan = MockTestPlan.builder()
            .name("concurrent-per-trial-test")
            .axis(axis)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Collect deploy steps per trial
        List<AtomicStep.DeployElement> t0Deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d
                && Integer.valueOf(0).equals(d.metadata().get("trial_index")))
            .map(AtomicStep.DeployElement.class::cast)
            .toList();
        List<AtomicStep.DeployElement> t1Deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d
                && Integer.valueOf(1).equals(d.metadata().get("trial_index")))
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        assertThat(t0Deploys).hasSize(2);
        assertThat(t1Deploys).hasSize(2);

        // Trial 1's deploys should NOT depend on any step from trial 0.
        // Collect all step IDs from trial 0 (deploys, exec, teardowns, barriers)
        Set<String> trial0StepIds = steps.stream()
            .filter(s -> {
                Map<String, Object> meta = s.metadata();
                return Integer.valueOf(0).equals(meta.get("trial_index"));
            })
            .map(AtomicStep::id)
            .collect(java.util.stream.Collectors.toSet());

        for (AtomicStep.DeployElement deploy : t1Deploys) {
            for (String dep : deploy.dependencies()) {
                assertThat(trial0StepIds)
                    .as("Trial 1 deploy %s should not depend on trial 0 step %s", deploy.id(), dep)
                    .doesNotContain(dep);
            }
        }
    }

    @Test
    @DisplayName("PER_TRIAL elements are eagerly torn down after each trial execution")
    void perTrialElementsEagerTeardown() {
        Element worker = MockElement.builder("worker")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a", "b");

        TestPlan plan = MockTestPlan.builder()
            .name("eager-teardown-test")
            .axis(axis)
            .element(worker)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Each trial should have its own deploy, exec, and teardown
        List<AtomicStep.TeardownElement> eagerTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "per_trial_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(eagerTeardowns).hasSize(2); // one per trial

        // Each eager teardown should depend on its trial's exec step
        for (AtomicStep.TeardownElement td : eagerTeardowns) {
            assertThat(td.dependencies()).allMatch(dep -> dep.startsWith("exec_trial_"));
        }

        // No final teardown for PER_TRIAL elements
        List<AtomicStep.TeardownElement> finalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(finalTeardowns).isEmpty();

        // No barriers for fully-PER_TRIAL plans (they would be dangling leaves)
        long barrierCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.BarrierSync)
            .count();
        assertThat(barrierCount).isZero();
    }

    @Test
    @DisplayName("Intermediate config-change teardowns happen in LIFO (reverse topo) order")
    void intermediateTeardownInReverseTopoOrder() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var memParam = IntegerParameter.range("mem", 512, 1024);

        Element db = MockElement.builder("db")
            .parameter(portParam)
            .build();
        Element app = MockElement.builder("app")
            .parameter(memParam)
            .dependency(db)
            .build();

        // Both parameters change between trials, requiring recycling of both elements
        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);
        Axis<Integer> memAxis = MockAxis.of("mem", 512, 1024);

        TestPlan plan = MockTestPlan.builder()
            .name("lifo-intermediate-teardown-test")
            .axis(portAxis)
            .axis(memAxis)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Find intermediate teardowns for the second trial (first group boundary).
        // Topo order is db → app, so LIFO teardown order should be app → db
        List<AtomicStep.TeardownElement> intermediateTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "group_boundary".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        // There should be intermediate teardowns (at least for one trial transition)
        assertThat(intermediateTeardowns).isNotEmpty();

        // Within each trial transition, app should be torn down before db (LIFO)
        // Group by trial_index and verify order
        Map<Object, List<AtomicStep.TeardownElement>> byTrial = new LinkedHashMap<>();
        for (AtomicStep.TeardownElement td : intermediateTeardowns) {
            byTrial.computeIfAbsent(td.metadata().get("trial_index"), k -> new ArrayList<>()).add(td);
        }

        for (var entry : byTrial.entrySet()) {
            List<AtomicStep.TeardownElement> trialTeardowns = entry.getValue();
            if (trialTeardowns.size() >= 2) {
                // Find indices of app and db teardowns
                int appIdx = -1, dbIdx = -1;
                for (int i = 0; i < trialTeardowns.size(); i++) {
                    if (trialTeardowns.get(i).elementId().equals("app")) appIdx = i;
                    if (trialTeardowns.get(i).elementId().equals("db")) dbIdx = i;
                }
                if (appIdx >= 0 && dbIdx >= 0) {
                    assertThat(appIdx)
                        .as("app should be torn down before db (LIFO) in trial %s", entry.getKey())
                        .isLessThan(dbIdx);
                }
            }
        }
    }

    @Test
    @DisplayName("Group-boundary teardown depends on ELEMENT_SCOPE_END barrier")
    void intermediateTeardownDependsOnExecStep() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("teardown-deps-exec-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Find the intermediate teardown (group_boundary)
        List<AtomicStep.TeardownElement> intermediateTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "group_boundary".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(intermediateTeardowns).hasSize(1);

        AtomicStep.TeardownElement teardown = intermediateTeardowns.getFirst();

        // The teardown should depend on the ELEMENT_SCOPE_END barrier, which
        // in turn depends on the exec step — the barrier synchronizes the
        // transition from execution to teardown.
        assertThat(teardown.dependencies())
            .as("Intermediate teardown should depend on a scope-end barrier")
            .anyMatch(dep -> dep.startsWith("barrier_scope_end_step_"));

        // The teardown should NOT depend on a deploy step
        assertThat(teardown.dependencies())
            .as("Intermediate teardown should not depend on a deploy step")
            .noneMatch(dep -> dep.startsWith("deploy_"));
    }

    @Test
    @DisplayName("Mixed PER_RUN and PER_TRIAL elements: PER_RUN gets final teardown, PER_TRIAL does not")
    void mixedScopeElementsTeardownCorrectly() {
        Element db = MockElement.of("db"); // global (PER_RUN)
        Element worker = MockElement.builder("worker")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .dependency(db)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a", "b");

        TestPlan plan = MockTestPlan.builder()
            .name("mixed-scope-test")
            .axis(axis)
            .element(db)
            .element(worker)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // db (global) should have exactly one final teardown
        List<AtomicStep.TeardownElement> dbFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("db")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(dbFinalTeardowns).hasSize(1);

        // worker (PER_TRIAL) should have eager teardowns, no final teardown
        List<AtomicStep.TeardownElement> workerEagerTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("worker")
                && "per_trial_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(workerEagerTeardowns).hasSize(2); // one per trial

        List<AtomicStep.TeardownElement> workerFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("worker")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(workerFinalTeardowns).isEmpty();

        // worker deploys should depend on db deploy (PER_RUN element)
        List<AtomicStep.DeployElement> workerDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d && d.elementId().equals("worker"))
            .map(AtomicStep.DeployElement.class::cast)
            .toList();
        assertThat(workerDeploys).hasSize(2); // one per trial

        String dbDeployId = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d && d.elementId().equals("db"))
            .map(AtomicStep::id)
            .findFirst()
            .orElseThrow();

        for (AtomicStep.DeployElement workerDeploy : workerDeploys) {
            assertThat(workerDeploy.dependencies()).contains(dbDeployId);
        }
    }

    @Test
    @DisplayName("PER_TRIAL teardowns are chained for safe concurrent execution")
    void perTrialTeardownsChainedForConcurrency() {
        Element db = MockElement.builder("db")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a");

        TestPlan plan = MockTestPlan.builder()
            .name("per-trial-teardown-chain-test")
            .axis(axis)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Eager teardowns: LIFO order = app first, then db
        List<AtomicStep.TeardownElement> eagerTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "per_trial_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(eagerTeardowns).hasSize(2);
        assertThat(eagerTeardowns.get(0).elementId()).isEqualTo("app");
        assertThat(eagerTeardowns.get(1).elementId()).isEqualTo("db");

        // db's teardown must depend on app's teardown so a concurrent
        // executor cannot tear down db while app is still shutting down
        assertThat(eagerTeardowns.get(1).dependencies())
            .as("db teardown must depend on app teardown for safe concurrent execution")
            .contains(eagerTeardowns.get(0).id());
    }

    @Test
    @DisplayName("PER_GROUP boundary teardowns are chained for safe concurrent execution")
    void perGroupBoundaryTeardownsChainedForConcurrency() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var memParam = IntegerParameter.range("mem", 512, 1024);

        Element db = MockElement.builder("db")
            .parameter(portParam)
            .build();
        Element app = MockElement.builder("app")
            .parameter(memParam)
            .dependency(db)
            .build();

        // Both params change between trials, so both elements need group boundary teardown
        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);
        Axis<Integer> memAxis = MockAxis.of("mem", 512, 1024);

        TestPlan plan = MockTestPlan.builder()
            .name("per-group-teardown-chain-test")
            .axis(portAxis)
            .axis(memAxis)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Group boundary teardowns: LIFO order = app first, then db
        List<AtomicStep.TeardownElement> boundaryTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "group_boundary".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        // Should have at least one set of boundary teardowns (both elements at first boundary)
        assertThat(boundaryTeardowns.size()).isGreaterThanOrEqualTo(2);

        // Within each boundary group, check that db's teardown depends on app's teardown
        Map<Object, List<AtomicStep.TeardownElement>> byTrial = new LinkedHashMap<>();
        for (AtomicStep.TeardownElement td : boundaryTeardowns) {
            byTrial.computeIfAbsent(td.metadata().get("trial_index"), k -> new ArrayList<>()).add(td);
        }

        for (var entry : byTrial.entrySet()) {
            List<AtomicStep.TeardownElement> group = entry.getValue();
            if (group.size() >= 2) {
                AtomicStep.TeardownElement appTd = group.stream()
                    .filter(t -> t.elementId().equals("app")).findFirst().orElse(null);
                AtomicStep.TeardownElement dbTd = group.stream()
                    .filter(t -> t.elementId().equals("db")).findFirst().orElse(null);

                if (appTd != null && dbTd != null) {
                    assertThat(dbTd.dependencies())
                        .as("db boundary teardown must depend on app boundary teardown at trial %s", entry.getKey())
                        .contains(appTd.id());
                }
            }
        }
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
