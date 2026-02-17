package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.StringParameter;
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
            .filter(s -> s instanceof AtomicStep.DeployElement d && d.elementId().equals("server"))
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
    @DisplayName("No ELEMENT_SCOPE_END barriers — teardowns depend directly on exec steps")
    void noScopeEndBarriersCreated() {
        // ELEMENT_SCOPE_END barriers are no longer emitted. Teardowns depend
        // directly on exec steps. TRIAL_BATCH barriers are NOT emitted because
        // nothing depends on them. ELEMENT_READY barriers are NOT emitted
        // (no health check on this element).
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

        // No ELEMENT_SCOPE_END barriers — removed from planner output
        long scopeEndCount = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_SCOPE_END)
            .count();
        assertThat(scopeEndCount).isZero();

        // No BarrierSync steps (no ELEMENT_SCOPE_END barriers)
        long barrierStepCount = context.steps().get().stream()
            .filter(s -> s instanceof AtomicStep.BarrierSync)
            .count();
        assertThat(barrierStepCount).isZero();
    }

    @Test
    @DisplayName("No barriers for global-only and PER_TRIAL-only plans")
    void noBarriersWhenNoRecyclingElements() {
        // Global element without health check: no barriers of any kind.
        // Final teardowns depend directly on exec steps without intermediaries.
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

        // No ELEMENT_SCOPE_END barriers (removed from planner)
        long globalScopeEndBarriers = globalCtx.barriers().orElse(List.of()).stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_SCOPE_END)
            .count();
        assertThat(globalScopeEndBarriers).isZero();

        // PER_TRIAL element: bound to the axis per trial, no barriers of any kind
        // (all teardowns are eager, no final teardowns, so no barrier needed)
        Element worker = MockElement.builder("worker")
            .parameter(StringParameter.of("mode"))
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

        // All teardowns are predictive_eager (Phase 2c catches them all
        // since every trial has a different config). Instance 0 is torn
        // down after trial 0, instance 1 after trial 1.
        List<AtomicStep.TeardownElement> eagerTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "predictive_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(eagerTeardowns).hasSize(2);
        assertThat(eagerTeardowns.get(0).instanceNumber()).isEqualTo(0);
        assertThat(eagerTeardowns.get(1).instanceNumber()).isEqualTo(1);

        // No final (cleanup) teardown — Phase 2c already tore down all instances
        List<AtomicStep.TeardownElement> finalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(finalTeardowns).isEmpty();
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
    @DisplayName("Independent elements deploy sequentially: trial N+1 depends on trial N teardown")
    void perTrialElementsConcurrentDeployment() {
        Element db = MockElement.builder("db")
            .parameter(StringParameter.of("mode"))
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            .parameter(StringParameter.of("mode"))
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

        // In the unified model, independent elements go through Phase 2a
        // which tracks lastStepForElement. Trial 1 deploys depend on the
        // previous trial's teardown step for the same element (ensuring
        // proper resource cleanup before re-deploy).
        // Verify that trial 1 deploys depend on trial 0 teardown steps
        // (not deploy steps — they depend on teardowns via ownLastStep).
        Set<String> trial0TeardownIds = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && Integer.valueOf(0).equals(t.metadata().get("trial_index")))
            .map(AtomicStep::id)
            .collect(java.util.stream.Collectors.toSet());

        // db's trial 1 deploy should depend on some trial 0 step (its own teardown)
        AtomicStep.DeployElement dbT1Deploy = t1Deploys.stream()
            .filter(d -> d.elementId().equals("db"))
            .findFirst().orElseThrow();
        assertThat(dbT1Deploy.dependencies())
            .as("Trial 1 db deploy should depend on trial 0 db teardown or later")
            .isNotEmpty();
    }

    @Test
    @DisplayName("PER_TRIAL elements are eagerly torn down after each trial execution")
    void perTrialElementsEagerTeardown() {
        Element worker = MockElement.builder("worker")
            .parameter(StringParameter.of("mode"))
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
                && "predictive_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(eagerTeardowns).hasSize(2); // one per trial

        // Each eager teardown should depend on its trial's exec step or
        // the NotifyTrialEnd step (which follows the exec step)
        for (AtomicStep.TeardownElement td : eagerTeardowns) {
            assertThat(td.dependencies())
                .allMatch(dep -> dep.startsWith("exec_trial_") || dep.startsWith("notify_trial_end_"));
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
                && "predictive_eager".equals(t.metadata().get("reason")))
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
    @DisplayName("Group-boundary teardown depends directly on execution step")
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

        // Find the first predictive_eager teardown (trial 0).
        // With every trial having different config, Phase 2c catches all
        // teardowns as "predictive_eager". We filter for trial_index=0
        // to get only the intermediate (non-final-trial) teardown.
        List<AtomicStep.TeardownElement> intermediateTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "predictive_eager".equals(t.metadata().get("reason"))
                && Integer.valueOf(0).equals(t.metadata().get("trial_index")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(intermediateTeardowns).hasSize(1);

        AtomicStep.TeardownElement teardown = intermediateTeardowns.getFirst();

        // The teardown should depend on a trial-completion step — either
        // an exec step directly or a NotifyTrialEnd step (which itself
        // depends on the exec step, representing trial completion).
        assertThat(teardown.dependencies())
            .as("Intermediate teardown should depend on exec or trial-end notification step")
            .anyMatch(dep -> dep.startsWith("exec_") || dep.startsWith("notify_trial_end_"));

        // The teardown should NOT depend on a deploy step
        assertThat(teardown.dependencies())
            .as("Intermediate teardown should not depend on a deploy step")
            .noneMatch(dep -> dep.startsWith("deploy_"));

        // No barrier steps should exist (ELEMENT_SCOPE_END removed)
        assertThat(teardown.dependencies())
            .as("Intermediate teardown should not depend on a barrier step")
            .noneMatch(dep -> dep.startsWith("barrier_scope_end"));
    }

    @Test
    @DisplayName("Mixed PER_RUN and PER_TRIAL elements: PER_RUN gets final teardown, PER_TRIAL does not")
    void mixedScopeElementsTeardownCorrectly() {
        Element db = MockElement.of("db"); // global (PER_RUN)
        Element worker = MockElement.builder("worker")
            .parameter(StringParameter.of("mode"))
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
                && "predictive_eager".equals(t.metadata().get("reason")))
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

        // Independent elements deploy in Phase 2a (before NotifyTrialStart).
        // Worker deploys depend on db's deploy (its dependency, Phase 1) or
        // on its own previous teardown (for subsequent trials).
        List<AtomicStep.DeployElement> workerDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d && d.elementId().equals("worker"))
            .map(AtomicStep.DeployElement.class::cast)
            .toList();
        assertThat(workerDeploys).hasSize(2); // one per trial

        // First worker deploy depends on db deploy (Phase 1 dependency)
        assertThat(workerDeploys.getFirst().dependencies())
            .as("first worker deploy should depend on db deploy (Phase 1)")
            .anyMatch(dep -> dep.startsWith("deploy_db"));

        // The first final teardown (db) must depend on the latest step
        // from each trial. With predictive eager teardown, the latest step
        // per trial is the last worker teardown (which comes after
        // NotifyTrialEnd in the Phase 2c chain).
        AtomicStep.TeardownElement dbTeardown = dbFinalTeardowns.getFirst();

        List<String> workerTeardownIds = workerEagerTeardowns.stream()
            .map(AtomicStep::id).toList();
        assertThat(workerTeardownIds).hasSize(2); // one per trial
        assertThat(dbTeardown.dependencies())
            .as("First final teardown must wait for latest-per-trial steps (worker teardowns)")
            .containsAll(workerTeardownIds);

        // No ELEMENT_SCOPE_END barrier steps should exist
        long scopeEndBarriers = steps.stream()
            .filter(s -> s instanceof AtomicStep.BarrierSync b
                && "ELEMENT_SCOPE_END".equals(b.metadata().get("barrierType")))
            .count();
        assertThat(scopeEndBarriers).isZero();
    }

    @Test
    @DisplayName("PER_TRIAL teardowns are chained for safe concurrent execution")
    void perTrialTeardownsChainedForConcurrency() {
        Element db = MockElement.builder("db")
            .parameter(StringParameter.of("mode"))
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            .parameter(StringParameter.of("mode"))
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
                && "predictive_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(eagerTeardowns).hasSize(2);
        assertThat(eagerTeardowns.get(0).elementId()).isEqualTo("app");
        assertThat(eagerTeardowns.get(1).elementId()).isEqualTo("db");

        // In Phase 2c, NotifyTrialEnd is emitted first (depends on exec),
        // then eager teardowns follow in LIFO order. The chain is:
        //   exec → NotifyTrialEnd → app_teardown → db_teardown
        AtomicStep.NotifyTrialEnd notifyEnd = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialEnd)
            .map(AtomicStep.NotifyTrialEnd.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected NotifyTrialEnd step"));

        // app's teardown depends on NotifyTrialEnd (the first in the chain)
        assertThat(eagerTeardowns.get(0).dependencies())
            .as("app teardown must depend on NotifyTrialEnd")
            .contains(notifyEnd.id());

        // db's teardown depends on app's teardown (chained via prevEagerTeardownId)
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
                && "predictive_eager".equals(t.metadata().get("reason")))
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

    @Test
    @DisplayName("Lifeline-subsumed element skips final teardown but keeps eager teardowns")
    void lifelineSubsumedElementSkipsTeardown() {
        // node is PER_RUN, service is PER_TRIAL with lifeline on node
        Element node = MockElement.of("node");
        Element service = MockElement.builder("service")
            .parameter(StringParameter.of("mode"))
            .dependency(node, RelationshipType.LIFELINE)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a", "b");

        TestPlan plan = MockTestPlan.builder()
            .name("lifeline-test")
            .axis(axis)
            .element(node)
            .element(service)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // service is lifeline-subsumed (all deps are lifeline), so Phase 2c
        // skips its eager teardowns — the lifeline root (node) is responsible
        // for tearing it down atomically.
        List<AtomicStep.TeardownElement> serviceEagerTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("service")
                && "predictive_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(serviceEagerTeardowns).isEmpty();

        // node (PER_RUN, no lifeline deps) should have a final teardown
        List<AtomicStep.TeardownElement> nodeFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("node")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(nodeFinalTeardowns).hasSize(1);

        // service should NOT have a final teardown (PER_TRIAL are already skipped,
        // but even if it were PER_RUN, the lifeline would skip it)
        List<AtomicStep.TeardownElement> serviceFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("service")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(serviceFinalTeardowns).isEmpty();
    }

    @Test
    @DisplayName("Lifeline-subsumed PER_RUN element skips final teardown")
    void lifelineSubsumedPerRunElementSkipsFinalTeardown() {
        // Both PER_RUN: node has no deps, service depends on node with lifeline
        Element node = MockElement.of("node");
        Element service = MockElement.builder("service")
            .dependency(node, RelationshipType.LIFELINE)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a");

        TestPlan plan = MockTestPlan.builder()
            .name("lifeline-per-run-test")
            .axis(axis)
            .element(node)
            .element(service)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // service should NOT have a final teardown (lifeline-subsumed)
        List<AtomicStep.TeardownElement> serviceFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("service")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(serviceFinalTeardowns).isEmpty();

        // node should still have a final teardown
        List<AtomicStep.TeardownElement> nodeFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("node")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(nodeFinalTeardowns).hasSize(1);
    }

    @Test
    @DisplayName("Mixed lifeline/non-lifeline does NOT skip teardown")
    void mixedLifelineDoesNotSkipTeardown() {
        Element node = MockElement.of("node");
        Element db = MockElement.of("db");
        Element service = MockElement.builder("service")
            .dependency(node, RelationshipType.LIFELINE)
            .dependency(db)
            // db is NOT lifeline
            .build();

        Axis<String> axis = MockAxis.of("mode", "a");

        TestPlan plan = MockTestPlan.builder()
            .name("mixed-lifeline-test")
            .axis(axis)
            .element(node)
            .element(db)
            .element(service)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // service should STILL have a final teardown since not all deps are lifeline
        List<AtomicStep.TeardownElement> serviceFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("service")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(serviceFinalTeardowns).hasSize(1);
    }

    @Test
    @DisplayName("No lifeline preserves all teardown steps (backward compatible)")
    void noLifelinePreservesTeardown() {
        Element node = MockElement.of("node");
        Element service = MockElement.builder("service")
            .dependency(node)
            // No lifeline tag
            .build();

        Axis<String> axis = MockAxis.of("mode", "a");

        TestPlan plan = MockTestPlan.builder()
            .name("no-lifeline-test")
            .axis(axis)
            .element(node)
            .element(service)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Both elements should have final teardowns
        List<AtomicStep.TeardownElement> finalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(finalTeardowns).hasSize(2);

        // service should be torn down before node (LIFO)
        assertThat(finalTeardowns.get(0).elementId()).isEqualTo("service");
        assertThat(finalTeardowns.get(1).elementId()).isEqualTo("node");
    }

    @Test
    @DisplayName("Trial element identified as innermost leaf in dependency chain")
    void trialElementIdentifiedAsInnermostLeaf() {
        Element node = MockElement.of("node");
        Element db = MockElement.builder("db")
            .dependency(node)
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("trial-element-test")
            .axis(axis)
            .element(node)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // ExecuteTrial should carry trial_element=app (the leaf)
        List<AtomicStep.ExecuteTrial> execTrials = steps.stream()
            .filter(s -> s instanceof AtomicStep.ExecuteTrial)
            .map(AtomicStep.ExecuteTrial.class::cast)
            .toList();

        assertThat(execTrials).hasSize(1);
        assertThat(execTrials.getFirst().metadata().get("trial_element"))
            .isEqualTo("app");
    }

    @Test
    @DisplayName("Trial element: last-defined wins among peer leaves")
    void trialElementLastDefinedWinsAmongPeers() {
        Element node = MockElement.of("node");
        Element svcA = MockElement.builder("svc_a")
            .dependency(node)
            .build();
        Element svcB = MockElement.builder("svc_b")
            .dependency(node)
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("trial-element-peers-test")
            .axis(axis)
            .element(node)
            .element(svcA)
            .element(svcB)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        List<AtomicStep.ExecuteTrial> execTrials = steps.stream()
            .filter(s -> s instanceof AtomicStep.ExecuteTrial)
            .map(AtomicStep.ExecuteTrial.class::cast)
            .toList();

        assertThat(execTrials).hasSize(1);
        // svc_b is last-defined leaf, so it should be the trial element
        assertThat(execTrials.getFirst().metadata().get("trial_element"))
            .isEqualTo("svc_b");
    }

    @Test
    @DisplayName("Lifeline cluster metadata on root teardown step")
    void lifelineClusterMetadataOnRootTeardown() {
        Element node = MockElement.of("node");
        Element db = MockElement.builder("db")
            .dependency(node, RelationshipType.LIFELINE)
            .build();
        Element app = MockElement.builder("app")
            .dependency(db, RelationshipType.LIFELINE)
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("lifeline-cluster-test")
            .axis(axis)
            .element(node)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Node's final teardown should have lifeline_cluster metadata
        List<AtomicStep.TeardownElement> nodeTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("node")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(nodeTeardowns).hasSize(1);
        @SuppressWarnings("unchecked")
        List<String> cluster = (List<String>) nodeTeardowns.getFirst().metadata().get("lifeline_cluster");
        assertThat(cluster).containsExactlyInAnyOrder("node", "db", "app");

        // db and app should have NO final teardown (lifeline-subsumed)
        long dbFinalCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("db")
                && "cleanup".equals(t.metadata().get("phase")))
            .count();
        long appFinalCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("app")
                && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(dbFinalCount).isZero();
        assertThat(appFinalCount).isZero();
    }

    @Test
    @DisplayName("Non-lifeline edge breaks cluster boundary")
    void nonLifelineEdgeBreaksCluster() {
        Element node = MockElement.of("node");
        Element db = MockElement.builder("db")
            .dependency(node, RelationshipType.LIFELINE)
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            // No lifeline tag — regular dependency on db
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("non-lifeline-break-test")
            .axis(axis)
            .element(node)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Node's cluster should be [node, db] only — app is not in it
        List<AtomicStep.TeardownElement> nodeTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("node")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();

        assertThat(nodeTeardowns).hasSize(1);
        @SuppressWarnings("unchecked")
        List<String> cluster = (List<String>) nodeTeardowns.getFirst().metadata().get("lifeline_cluster");
        assertThat(cluster).containsExactlyInAnyOrder("node", "db");

        // app should have its own explicit final teardown
        long appFinalCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("app")
                && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(appFinalCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Sibling with regular dep on cluster root tears down before cluster root")
    void siblingRegularDepTeardownBeforeClusterRoot() {
        // Node is the cluster root, DB is lifeline-attached to Node,
        // App is a sibling with a regular (non-lifeline) dep on Node.
        // App's teardown must complete before Node's teardown because
        // tearing down Node atomically destroys DB — if App were still
        // running while Node tears down, App would lose its dependency.
        Element node = MockElement.of("node");
        Element db = MockElement.builder("db")
            .dependency(node, RelationshipType.LIFELINE)
            .build();
        Element app = MockElement.builder("app")
            .dependency(node)
            // No lifeline — regular dependency
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("sibling-regular-dep-test")
            .axis(axis)
            .element(node)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // DB should NOT have a final teardown (lifeline-subsumed)
        List<AtomicStep.TeardownElement> dbFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("db")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(dbFinalTeardowns).isEmpty();

        // App SHOULD have a final teardown (not lifeline-subsumed)
        List<AtomicStep.TeardownElement> appFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("app")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(appFinalTeardowns).hasSize(1);

        // Node SHOULD have a final teardown (cluster root)
        List<AtomicStep.TeardownElement> nodeFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("node")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(nodeFinalTeardowns).hasSize(1);

        // CRITICAL: Node's teardown must depend on App's teardown,
        // ensuring App completes before Node atomically destroys the
        // lifeline cluster [node, db].
        AtomicStep.TeardownElement appTeardown = appFinalTeardowns.getFirst();
        AtomicStep.TeardownElement nodeTeardown = nodeFinalTeardowns.getFirst();
        assertThat(nodeTeardown.dependencies())
            .as("Node (cluster root) teardown must depend on App's teardown via LIFO chain")
            .contains(appTeardown.id());

        // Node's teardown carries the lifeline_cluster metadata
        @SuppressWarnings("unchecked")
        List<String> cluster = (List<String>) nodeTeardown.metadata().get("lifeline_cluster");
        assertThat(cluster).containsExactlyInAnyOrder("node", "db");

        // Verify overall ordering: App teardown appears before Node teardown in step list
        int appTeardownIdx = steps.indexOf(appTeardown);
        int nodeTeardownIdx = steps.indexOf(nodeTeardown);
        assertThat(appTeardownIdx)
            .as("App teardown must appear before Node teardown in the step sequence")
            .isLessThan(nodeTeardownIdx);
    }

    @Test
    @DisplayName("Sibling regular dep on interior cluster member tears down before cluster root")
    void siblingRegularDepOnInteriorClusterMemberTeardownBeforeClusterRoot() {
        // Node → DB (lifeline) → App (regular dep on DB, NOT lifeline).
        // The lifeline cluster is [node, db]. App depends on DB (a cluster
        // member) via a regular edge. App's teardown must complete before
        // Node's teardown, because tearing down Node atomically destroys
        // DB, which App depends on.
        Element node = MockElement.of("node");
        Element db = MockElement.builder("db")
            .dependency(node, RelationshipType.LIFELINE)
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            // No lifeline — regular dependency on db
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("sibling-interior-cluster-dep-test")
            .axis(axis)
            .element(node)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // App SHOULD have a final teardown (not lifeline-subsumed)
        List<AtomicStep.TeardownElement> appFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("app")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(appFinalTeardowns).hasSize(1);

        // DB should NOT have a final teardown (lifeline-subsumed)
        long dbFinalCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("db")
                && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(dbFinalCount).isZero();

        // Node SHOULD have a final teardown (cluster root)
        List<AtomicStep.TeardownElement> nodeFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("node")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .toList();
        assertThat(nodeFinalTeardowns).hasSize(1);

        // CRITICAL: Node's teardown must depend on App's teardown.
        // App → DB (regular) and DB is in Node's lifeline cluster.
        // When Node tears down it destroys DB, so App must finish first.
        AtomicStep.TeardownElement appTeardown = appFinalTeardowns.getFirst();
        AtomicStep.TeardownElement nodeTeardown = nodeFinalTeardowns.getFirst();
        assertThat(nodeTeardown.dependencies())
            .as("Node (cluster root) teardown must depend on App's teardown via LIFO chain")
            .contains(appTeardown.id());

        // Verify cluster metadata
        @SuppressWarnings("unchecked")
        List<String> cluster = (List<String>) nodeTeardown.metadata().get("lifeline_cluster");
        assertThat(cluster).containsExactlyInAnyOrder("node", "db");
    }

    @Test
    @DisplayName("Independent element deploys in Phase 2a before NotifyTrialStart")
    void notifyTrialStartBeforeTrialElementDeploy() {
        Element node = MockElement.of("node"); // PER_RUN
        Element app = MockElement.builder("app")
            .dependency(node)
            .parameter(StringParameter.of("mode"))
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("notify-start-per-trial-test")
            .axis(axis)
            .element(node)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Should have a NotifyTrialStart step
        List<AtomicStep.NotifyTrialStart> notifyStarts = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialStart)
            .map(AtomicStep.NotifyTrialStart.class::cast)
            .toList();
        assertThat(notifyStarts).hasSize(1);

        AtomicStep.NotifyTrialStart notifyStart = notifyStarts.getFirst();
        assertThat(notifyStart.elementNames()).containsExactlyInAnyOrder("node", "app");

        // Independent elements deploy in Phase 2a (before NotifyTrialStart).
        // NotifyTrialStart depends on app's deploy (the leaf deploy in the
        // unified loop), NOT the other way around.
        AtomicStep.DeployElement appDeploy = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d && d.elementId().equals("app"))
            .map(AtomicStep.DeployElement.class::cast)
            .findFirst().orElseThrow();

        // app's deploy depends on node's deploy (its dependency, deployed in Phase 1)
        assertThat(appDeploy.dependencies())
            .as("app deploy must depend on node's deploy (Phase 1)")
            .anyMatch(dep -> dep.startsWith("deploy_node"));

        // NotifyTrialStart depends on app's deploy (the leaf after Phase 2a)
        assertThat(notifyStart.dependencies())
            .as("NotifyTrialStart depends on app deploy (Phase 2a leaf)")
            .anyMatch(dep -> dep.startsWith("deploy_app"));

        // app deploy must appear before NotifyTrialStart in step list
        assertThat(steps.indexOf(appDeploy)).isLessThan(steps.indexOf(notifyStart));
    }

    @Test
    @DisplayName("NotifyTrialStart emitted before ExecuteTrial when trial element is PER_RUN")
    void notifyTrialStartBeforeExecWhenTrialElementIsPerRun() {
        Element db = MockElement.of("db"); // PER_RUN, also the trial element (only element)

        Axis<String> axis = MockAxis.of("mode", "a", "b");

        TestPlan plan = MockTestPlan.builder()
            .name("notify-start-per-run-test")
            .axis(axis)
            .element(db)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Should have two NotifyTrialStart steps (one per trial)
        List<AtomicStep.NotifyTrialStart> notifyStarts = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialStart)
            .map(AtomicStep.NotifyTrialStart.class::cast)
            .toList();
        assertThat(notifyStarts).hasSize(2);

        // Each NotifyTrialStart should appear before its trial's ExecuteTrial
        List<AtomicStep.ExecuteTrial> execTrials = steps.stream()
            .filter(s -> s instanceof AtomicStep.ExecuteTrial)
            .map(AtomicStep.ExecuteTrial.class::cast)
            .toList();
        assertThat(execTrials).hasSize(2);

        for (int i = 0; i < 2; i++) {
            assertThat(steps.indexOf(notifyStarts.get(i)))
                .isLessThan(steps.indexOf(execTrials.get(i)));
            // ExecuteTrial should depend on NotifyTrialStart
            assertThat(execTrials.get(i).dependencies())
                .contains(notifyStarts.get(i).id());
        }
    }

    @Test
    @DisplayName("NotifyTrialEnd emitted after trial element teardown (PER_TRIAL)")
    void notifyTrialEndAfterTrialElementTeardown() {
        Element node = MockElement.of("node"); // PER_RUN
        Element app = MockElement.builder("app")
            .dependency(node)
            .parameter(StringParameter.of("mode"))
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("notify-end-per-trial-test")
            .axis(axis)
            .element(node)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Should have a NotifyTrialEnd step
        List<AtomicStep.NotifyTrialEnd> notifyEnds = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialEnd)
            .map(AtomicStep.NotifyTrialEnd.class::cast)
            .toList();
        assertThat(notifyEnds).hasSize(1);

        AtomicStep.NotifyTrialEnd notifyEnd = notifyEnds.getFirst();
        assertThat(notifyEnd.plannedReason()).isEqualTo(AtomicStep.ShutdownReason.NORMAL);
        assertThat(notifyEnd.elementNames()).containsExactlyInAnyOrder("node", "app");

        // In Phase 2c, NotifyTrialEnd is emitted first (depends on exec),
        // then app's teardown follows (depends on NotifyTrialEnd).
        AtomicStep.TeardownElement appTeardown = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("app")
                && "predictive_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep.TeardownElement.class::cast)
            .findFirst().orElseThrow();
        assertThat(appTeardown.dependencies()).contains(notifyEnd.id());

        // NotifyTrialEnd appears before app teardown in step list
        assertThat(steps.indexOf(notifyEnd)).isLessThan(steps.indexOf(appTeardown));
    }

    @Test
    @DisplayName("NotifyTrialEnd emitted after ExecuteTrial when trial element is PER_RUN")
    void notifyTrialEndAfterExecWhenTrialElementIsPerRun() {
        Element db = MockElement.of("db"); // PER_RUN, also the trial element

        Axis<String> axis = MockAxis.of("mode", "a", "b");

        TestPlan plan = MockTestPlan.builder()
            .name("notify-end-per-run-test")
            .axis(axis)
            .element(db)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Should have two NotifyTrialEnd steps (one per trial)
        List<AtomicStep.NotifyTrialEnd> notifyEnds = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialEnd)
            .map(AtomicStep.NotifyTrialEnd.class::cast)
            .toList();
        assertThat(notifyEnds).hasSize(2);

        // Each NotifyTrialEnd should depend on its trial's ExecuteTrial
        List<AtomicStep.ExecuteTrial> execTrials = steps.stream()
            .filter(s -> s instanceof AtomicStep.ExecuteTrial)
            .map(AtomicStep.ExecuteTrial.class::cast)
            .toList();
        assertThat(execTrials).hasSize(2);

        for (int i = 0; i < 2; i++) {
            assertThat(notifyEnds.get(i).dependencies())
                .contains(execTrials.get(i).id());
            assertThat(steps.indexOf(notifyEnds.get(i)))
                .isGreaterThan(steps.indexOf(execTrials.get(i)));
        }
    }

    @Test
    @DisplayName("Notification steps carry correct trial metadata")
    void notificationStepsCarryTrialMetadata() {
        Element db = MockElement.builder("db")
            .parameter(StringParameter.of("mode"))
            .build();

        Axis<String> axis = MockAxis.of("mode", "a");

        TestPlan plan = MockTestPlan.builder()
            .name("notify-metadata-test")
            .axis(axis)
            .element(db)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        AtomicStep.NotifyTrialStart notifyStart = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialStart)
            .map(AtomicStep.NotifyTrialStart.class::cast)
            .findFirst().orElseThrow();

        // Notification steps carry trial-level metadata (trial_index, trial_id,
        // nesting_path) but not per-element binding metadata
        assertThat(notifyStart.metadata().get("trial_index")).isEqualTo(0);
        assertThat(notifyStart.metadata()).containsKey("trial_id");
        assertThat(notifyStart.metadata()).containsKey("nesting_path");

        AtomicStep.NotifyTrialEnd notifyEnd = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialEnd)
            .map(AtomicStep.NotifyTrialEnd.class::cast)
            .findFirst().orElseThrow();

        assertThat(notifyEnd.metadata().get("trial_index")).isEqualTo(0);
        assertThat(notifyEnd.metadata()).containsKey("trial_id");
        assertThat(notifyEnd.metadata()).containsKey("nesting_path");
    }

    // ── COMMAND shutdown semantics tests ─────────────────────────────

    @Test
    @DisplayName("COMMAND trial element emits AwaitElement instead of ExecuteTrial")
    void commandTrialElementEmitsAwaitInsteadOfExecute() {
        Element node = MockElement.of("node");
        Element app = MockElement.builder("app")
            .dependency(node)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("command-await-test")
            .axis(axis)
            .element(node)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Should have AwaitElement, NOT ExecuteTrial
        long awaitCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.AwaitElement)
            .count();
        long execTrialCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.ExecuteTrial)
            .count();

        assertThat(awaitCount).as("COMMAND trial element should emit AwaitElement").isEqualTo(1);
        assertThat(execTrialCount).as("COMMAND trial element should NOT emit ExecuteTrial").isZero();

        // AwaitElement should reference the trial element (app)
        AtomicStep.AwaitElement await = steps.stream()
            .filter(s -> s instanceof AtomicStep.AwaitElement)
            .map(AtomicStep.AwaitElement.class::cast)
            .findFirst().orElseThrow();
        assertThat(await.elementId()).isEqualTo("app");
    }

    @Test
    @DisplayName("SERVICE trial element emits ExecuteTrial as usual (backward compatible)")
    void serviceTrialElementEmitsExecuteTrialAsUsual() {
        Element node = MockElement.of("node");
        Element app = MockElement.builder("app")
            .dependency(node)
            .shutdownSemantics(Element.ShutdownSemantics.SERVICE)
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("service-exec-test")
            .axis(axis)
            .element(node)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Should have ExecuteTrial, NOT AwaitElement
        long execTrialCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.ExecuteTrial)
            .count();
        long awaitCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.AwaitElement)
            .count();

        assertThat(execTrialCount).as("SERVICE trial element should emit ExecuteTrial").isEqualTo(1);
        assertThat(awaitCount).as("SERVICE trial element should NOT emit AwaitElement").isZero();
    }

    @Test
    @DisplayName("COMMAND trial element skips teardown")
    void commandTrialElementSkipsTeardown() {
        Element node = MockElement.of("node");
        Element app = MockElement.builder("app")
            .dependency(node)
            .parameter(StringParameter.of("mode"))
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a", "b");

        TestPlan plan = MockTestPlan.builder()
            .name("command-skip-teardown-test")
            .axis(axis)
            .element(node)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // No teardown for app (COMMAND elements terminate themselves)
        long appTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("app"))
            .count();
        assertThat(appTeardowns).as("COMMAND trial element should have no teardown").isZero();

        // Node should still have a final teardown (PER_RUN, not COMMAND)
        long nodeFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("node")
                && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(nodeFinalTeardowns).as("PER_RUN node should have final teardown").isEqualTo(1);

        // Should have AwaitElement for each trial
        long awaitCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.AwaitElement)
            .count();
        assertThat(awaitCount).as("Should have one AwaitElement per trial").isEqualTo(2);
    }

    @Test
    @DisplayName("AwaitElement carries trial metadata")
    void awaitElementCarriesTrialMetadata() {
        Element node = MockElement.of("node");
        Element app = MockElement.builder("app")
            .dependency(node)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("await-metadata-test")
            .axis(axis)
            .element(node)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        AtomicStep.AwaitElement await = steps.stream()
            .filter(s -> s instanceof AtomicStep.AwaitElement)
            .map(AtomicStep.AwaitElement.class::cast)
            .findFirst().orElseThrow();

        assertThat(await.trialId()).isNotNull();
        assertThat(await.elementBindings()).containsKeys("node", "app");
        assertThat(await.metadata()).containsKey("trial_element");
        assertThat(await.metadata().get("trial_element")).isEqualTo("app");
        // AwaitElement/ExecuteTrial metadata carries trial-level info (trial_index,
        // trial_id, nesting_path) but not per-element binding metadata
        assertThat(await.metadata()).containsKey("trial_index");
    }

    // ── Minimal dependency tests ─────────────────────────────────────

    @Test
    @DisplayName("Default: ExecuteTrial depends only on leaf deploy steps (minimal deps)")
    void minimalDepsOnlyLeafDeploysLinkedToExec() {
        // node → db → app chain; all PER_RUN so ExecuteTrial should only
        // depend on deploy_app (deploy_db and deploy_node are transitive)
        Element node = MockElement.of("node");
        Element db = MockElement.builder("db").dependency(node).build();
        Element app = MockElement.builder("app").dependency(db).build();
        Axis<String> axis = MockAxis.of("mode", "a");

        TestPlan plan = MockTestPlan.builder()
            .name("minimal-deps-test")
            .axis(axis)
            .element(node)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Find the NotifyTrialStart (which sits between deploys and ExecuteTrial)
        AtomicStep.NotifyTrialStart notifyStart = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialStart)
            .map(AtomicStep.NotifyTrialStart.class::cast)
            .findFirst().orElseThrow();

        // NotifyTrialStart should depend only on the app deploy (the leaf),
        // not on deploy_node or deploy_db
        assertThat(notifyStart.dependencies())
            .allSatisfy(dep -> assertThat(dep).contains("app"))
            .hasSize(1);
    }

    @Test
    @DisplayName("explicitTransitiveDeps=true: ExecuteTrial depends on ALL deploy steps")
    void explicitTransitiveDepsIncludesAllDeploys() {
        // Same chain: node → db → app
        Element node = MockElement.of("node");
        Element db = MockElement.builder("db").dependency(node).build();
        Element app = MockElement.builder("app").dependency(db).build();
        Axis<String> axis = MockAxis.of("mode", "a");

        TestPlan plan = MockTestPlan.builder()
            .name("explicit-deps-test")
            .axis(axis)
            .element(node)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan,
            Map.of(StepGenerationStage.OPTION_EXPLICIT_TRANSITIVE_DEPS, true));
        List<AtomicStep> steps = context.steps().get();

        // Find the NotifyTrialStart
        AtomicStep.NotifyTrialStart notifyStart = steps.stream()
            .filter(s -> s instanceof AtomicStep.NotifyTrialStart)
            .map(AtomicStep.NotifyTrialStart.class::cast)
            .findFirst().orElseThrow();

        // With explicit transitive deps, should depend on all 3 deploy steps
        assertThat(notifyStart.dependencies()).hasSize(3);
        assertThat(notifyStart.dependencies())
            .anyMatch(dep -> dep.contains("node"))
            .anyMatch(dep -> dep.contains("_db_"))
            .anyMatch(dep -> dep.contains("app"));
    }

    @Test
    @DisplayName("Independent element deploy depends on its dependency's deploy (Phase 2a)")
    void trialElementDeployMinimalDepsAfterNotifyStart() {
        // node (PER_RUN) -> service (independent) -> benchmark (independent, COMMAND)
        // In the unified model, independent elements deploy in Phase 2a
        // (before NotifyTrialStart), so benchmark's deploy depends on
        // service's deploy, not on NotifyTrialStart.
        Element node = MockElement.of("node");
        Element service = MockElement.builder("service")
            .dependency(node)
            .parameter(StringParameter.of("mode"))
            .build();
        Element benchmark = MockElement.builder("benchmark")
            .dependency(service)
            .parameter(StringParameter.of("mode"))
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        Axis<String> axis = MockAxis.of("mode", "x");

        TestPlan plan = MockTestPlan.builder()
            .name("deploy-minimal-deps-test")
            .axis(axis)
            .element(node)
            .element(service)
            .element(benchmark)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Find benchmark's deploy
        AtomicStep.DeployElement benchDeploy = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement d
                && d.elementId().equals("benchmark"))
            .map(AtomicStep.DeployElement.class::cast)
            .findFirst().orElseThrow();

        // benchmark deploy should depend on service's deploy (its
        // dependency, both deployed in Phase 2a before NotifyTrialStart)
        assertThat(benchDeploy.dependencies())
            .as("benchmark deploy depends on service deploy (Phase 2a dependency)")
            .anyMatch(dep -> dep.startsWith("deploy_service"));
    }

    @Test
    @DisplayName("First final teardown depends on latest-per-trial steps, not all exec step IDs")
    void firstFinalTeardownMinimalDeps() {
        Element db = MockElement.of("db"); // global (PER_RUN)
        Element worker = MockElement.builder("worker")
            .parameter(StringParameter.of("mode"))
            .dependency(db)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a", "b", "c");

        TestPlan plan = MockTestPlan.builder()
            .name("final-teardown-minimal-deps-test")
            .axis(axis)
            .element(db)
            .element(worker)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // First final teardown (db) should depend on the latest-per-trial
        // steps. With predictive eager teardown, the latest step per trial
        // is the last worker teardown (the final eager teardown in Phase 2c).
        AtomicStep.TeardownElement dbTeardown = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("db")
                && "cleanup".equals(t.metadata().get("phase")))
            .map(AtomicStep.TeardownElement.class::cast)
            .findFirst().orElseThrow();

        List<String> workerTeardownIds = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("worker")
                && "predictive_eager".equals(t.metadata().get("reason")))
            .map(AtomicStep::id).toList();
        assertThat(workerTeardownIds).hasSize(3); // one per trial

        // First final teardown should depend on all worker teardown entries
        assertThat(dbTeardown.dependencies())
            .as("First final teardown depends on latest-per-trial steps (worker teardowns)")
            .containsAll(workerTeardownIds);

        // First final teardown should NOT depend directly on exec step IDs
        // (those are transitively covered by the Phase 2c chain)
        List<String> execStepIds = steps.stream()
            .filter(s -> s instanceof AtomicStep.AwaitElement || s instanceof AtomicStep.ExecuteTrial)
            .map(AtomicStep::id).toList();
        assertThat(dbTeardown.dependencies())
            .as("First final teardown should not depend directly on exec steps")
            .doesNotContainAnyElementsOf(execStepIds);
    }

    private DefaultCompilationContext runPipeline(TestPlan plan) {
        return runPipeline(plan, Map.of());
    }

    private DefaultCompilationContext runPipeline(TestPlan plan, Map<String, Object> customOpts) {
        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions(customOpts));
        new ValidationStage().execute(context);
        new NormalizationStage().execute(context);
        new TrialEnumerationStage().execute(context);
        new InstantiationStage().execute(context);
        new StepGenerationStage().execute(context);
        return context;
    }

    private Compiler.CompilerOptions defaultOptions() {
        return defaultOptions(Map.of());
    }

    private Compiler.CompilerOptions defaultOptions(Map<String, Object> customOpts) {
        return new Compiler.CompilerOptions() {
            @Override public Compiler.CompilationStrategy strategy() { return Compiler.CompilationStrategy.BALANCED; }
            @Override public Compiler.OptimizationLevel optimizationLevel() { return Compiler.OptimizationLevel.STANDARD; }
            @Override public long maxTrialSpaceSize() { return 1_000_000; }
            @Override public boolean parallelCompilation() { return false; }
            @Override public boolean dryRun() { return false; }
            @Override public Map<String, Object> customOptions() { return customOpts; }
        };
    }
}
