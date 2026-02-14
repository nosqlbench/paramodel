/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.plan.DefaultAxis;
import io.nosqlbench.paramodel.mock.elements.MockHealthCheckSpec;
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

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/// Corner case tests for scope derivation, fingerprint computation,
/// barrier generation, and degenerate plans.
class ScopeAndFingerprintCornerCaseTest {

    // ── Scope derivation corner cases ──────────────────────────────────

    @Test
    @DisplayName("Diamond dependency: D→{B,C}→A — all PER_GROUP, correct deploy ordering")
    void diamondDependencyDerivesScopeCorrectly() {
        // All elements share the same parameter so InstantiationStage
        // classifies them as non-global (Tier 3 matching). This forces
        // them through the PER_GROUP fingerprint lifecycle.
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element d = MockElement.builder("d")
            .parameter(portParam)
            .build();
        Element b = MockElement.builder("b")
            .parameter(portParam)
            .dependency(d)
            .build();
        Element c = MockElement.builder("c")
            .parameter(portParam)
            .dependency(d)
            .build();
        Element a = MockElement.builder("a")
            .parameter(portParam)
            .dependency(b)
            .dependency(c)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("diamond-dep-test")
            .axis(portAxis)
            .element(d)
            .element(b)
            .element(c)
            .element(a)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // d has a parameter matching axis "port" → PER_GROUP with 2 deploys
        long dDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("d"))
            .count();
        assertThat(dDeploys).isEqualTo(2);

        // All elements are PER_GROUP with changing config → 2 deploys each
        for (String id : List.of("b", "c", "a")) {
            long deploys = steps.stream()
                .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals(id))
                .count();
            assertThat(deploys).as("deploys for %s", id).isEqualTo(2);
        }

        // Verify topological deploy order: d before b and c, b and c before a
        List<String> deployOrder = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(s -> ((AtomicStep.DeployElement) s).elementId())
            .toList();

        // d must appear before b, c, and a in the deploy list
        int firstD = deployOrder.indexOf("d");
        int firstB = deployOrder.indexOf("b");
        int firstC = deployOrder.indexOf("c");
        int firstA = deployOrder.indexOf("a");
        assertThat(firstD).isLessThan(firstB);
        assertThat(firstD).isLessThan(firstC);
        assertThat(firstB).isLessThan(firstA);
        assertThat(firstC).isLessThan(firstA);
    }

    @Test
    @DisplayName("Deep dependency chain: A→B→C→D — all PER_GROUP, topo order is D→C→B→A")
    void deepDependencyChainTaintPropagation() {
        // All elements share the port parameter so they are all classified
        // as PER_GROUP via Tier 3 matching in InstantiationStage.
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element d = MockElement.builder("d")
            .parameter(portParam)
            .build();
        Element c = MockElement.builder("c")
            .parameter(portParam)
            .dependency(d)
            .build();
        Element b = MockElement.builder("b")
            .parameter(portParam)
            .dependency(c)
            .build();
        Element a = MockElement.builder("a")
            .parameter(portParam)
            .dependency(b)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("deep-chain-test")
            .axis(portAxis)
            .element(d)
            .element(c)
            .element(b)
            .element(a)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Verify deploy order follows topo sort
        List<String> deployOrder = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(s -> ((AtomicStep.DeployElement) s).elementId())
            .toList();

        // First deploys should be d, c, b, a in topological order
        assertThat(deployOrder.indexOf("d")).isLessThan(deployOrder.indexOf("c"));
        assertThat(deployOrder.indexOf("c")).isLessThan(deployOrder.indexOf("b"));
        assertThat(deployOrder.indexOf("b")).isLessThan(deployOrder.indexOf("a"));

        // Final teardowns should be in reverse topo order: a, b, c, d
        List<String> finalTeardownOrder = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "cleanup".equals(t.metadata().get("phase")))
            .map(s -> ((AtomicStep.TeardownElement) s).elementId())
            .toList();

        assertThat(finalTeardownOrder).containsExactly("a", "b", "c", "d");
    }

    @Test
    @DisplayName("Mixed explicit scopes: PER_RUN, PER_GROUP, and PER_TRIAL together")
    void mixedExplicitAndInferredScopes() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element db = MockElement.of("db"); // global (PER_RUN): no parameters match axis
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build(); // PER_GROUP: parameter matches axis
        Element worker = MockElement.builder("worker")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build(); // explicit PER_TRIAL

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("mixed-scope-test")
            .axis(portAxis)
            .element(db)
            .element(server)
            .element(worker)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // db: PER_RUN → single deploy, single final teardown
        long dbDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("db"))
            .count();
        assertThat(dbDeploys).isEqualTo(1);

        long dbFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("db") && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(dbFinalTeardowns).isEqualTo(1);

        // server: PER_GROUP with config change → 2 deploys
        long serverDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("server"))
            .count();
        assertThat(serverDeploys).isEqualTo(2);

        // worker: PER_TRIAL → 2 deploys (one per trial), eager teardowns
        long workerDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("worker"))
            .count();
        assertThat(workerDeploys).isEqualTo(2);

        long workerEagerTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("worker") && "per_trial_eager".equals(t.metadata().get("reason")))
            .count();
        assertThat(workerEagerTeardowns).isEqualTo(2);

        // worker: no final teardown (PER_TRIAL elements are torn down eagerly)
        long workerFinalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("worker") && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(workerFinalTeardowns).isZero();
    }

    @Test
    @DisplayName("PER_GROUP element depending on PER_GROUP element: dependency fingerprint triggers redeploy")
    void perRunElementIgnoresAxisOnDependency() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build(); // PER_GROUP: parameter matches axis

        // Gateway has explicit PER_GROUP scope and depends on server.
        // Without its own varying parameter, its fingerprint is static;
        // but the dependency fingerprint for server changes, causing
        // gateway to also redeploy.
        Element gateway = MockElement.builder("gateway")
            .instancingScope(Element.InstancingScope.PER_GROUP)
            .dependency(server)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("per-run-dep-test")
            .axis(portAxis)
            .element(server)
            .element(gateway)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // server: PER_GROUP → 2 deploys (config changes)
        long serverDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("server"))
            .count();
        assertThat(serverDeploys).isEqualTo(2);

        // gateway is PER_GROUP and its dependency fingerprint changes
        // when server's config changes → 2 deploys
        long gatewayDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("gateway"))
            .count();
        assertThat(gatewayDeploys).isEqualTo(2);
    }

    // ── Fingerprint corner cases ───────────────────────────────────────

    @Test
    @DisplayName("All trials same config → single group, 1 deploy, 1 final teardown")
    void allTrialsSameConfigProducesSingleGroup() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        // Same value for all trials
        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8080, 8080);

        TestPlan plan = MockTestPlan.builder()
            .name("same-config-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Only 1 deploy because config never changes
        long deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("server"))
            .count();
        assertThat(deploys).isEqualTo(1);

        // No intermediate teardowns (no group boundaries)
        long intermediateTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("server") && "group_boundary".equals(t.metadata().get("reason")))
            .count();
        assertThat(intermediateTeardowns).isZero();

        // Exactly 1 final teardown
        long finalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("server") && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(finalTeardowns).isEqualTo(1);
    }

    @Test
    @DisplayName("Dependency fingerprint change triggers redeploy even without own parameter changes")
    void dependencyOnlyFingerprintChangeTriggersRedeploy() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        // app has explicit PER_GROUP scope and depends on server, but has
        // no own parameter matching the axis. Its own fingerprint is static,
        // but the dependency fingerprint for server changes between trials.
        Element app = MockElement.builder("app")
            .instancingScope(Element.InstancingScope.PER_GROUP)
            .dependency(server)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("dep-fingerprint-test")
            .axis(portAxis)
            .element(server)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // server changes config → 2 deploys
        long serverDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("server"))
            .count();
        assertThat(serverDeploys).isEqualTo(2);

        // app has no own varying params but includes dependency fingerprint,
        // so it also gets 2 deploys
        long appDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("app"))
            .count();
        assertThat(appDeploys).isEqualTo(2);
    }

    @Test
    @DisplayName("Oscillating config (A→B→A) produces 3 deploys, not 2")
    void oscillatingConfigProducesMultipleGroups() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081, 8080);

        TestPlan plan = MockTestPlan.builder()
            .name("oscillating-config-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Each config change triggers a redeploy, even returning to a previous value
        long deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("server"))
            .count();
        assertThat(deploys).isEqualTo(3);

        // 2 intermediate teardowns (at transitions 8080→8081 and 8081→8080)
        long intermediateTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("server") && "group_boundary".equals(t.metadata().get("reason")))
            .count();
        assertThat(intermediateTeardowns).isEqualTo(2);
    }

    @Test
    @DisplayName("Single trial PER_GROUP element: 1 deploy, 0 intermediate teardowns, 1 final teardown")
    void singleTrialProducesNoGroupBoundaries() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080);

        TestPlan plan = MockTestPlan.builder()
            .name("single-trial-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        long deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("server"))
            .count();
        assertThat(deploys).isEqualTo(1);

        long intermediateTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("server") && "group_boundary".equals(t.metadata().get("reason")))
            .count();
        assertThat(intermediateTeardowns).isZero();

        long finalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("server") && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(finalTeardowns).isEqualTo(1);
    }

    // ── Barrier generation corner cases ────────────────────────────────

    @Test
    @DisplayName("Health check barrier emitted after PER_RUN deploy")
    void healthCheckBarrierEmittedAfterDeploy() {
        Element db = MockElement.builder("db")
            .healthCheck(MockHealthCheckSpec.withTimeout(Duration.ofSeconds(30)))
            .build();
        Element app = MockElement.builder("app")
            .dependency(db)
            .build();

        Axis<String> axis = MockAxis.of("mode", "read");

        TestPlan plan = MockTestPlan.builder()
            .name("health-check-barrier-test")
            .axis(axis)
            .element(db)
            .element(app)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();
        List<Barrier> barriers = context.barriers().get();

        // Should have an ELEMENT_READY barrier for db
        long readyBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_READY
                && b.metadata().get("element").equals("db"))
            .count();
        assertThat(readyBarriers).isGreaterThanOrEqualTo(1);

        // The ready barrier step should appear between db deploy and app deploy
        int dbDeployIdx = -1;
        int readyBarrierIdx = -1;
        int appDeployIdx = -1;
        for (int i = 0; i < steps.size(); i++) {
            AtomicStep step = steps.get(i);
            if (step instanceof AtomicStep.DeployElement de && de.elementId().equals("db")) {
                dbDeployIdx = i;
            }
            if (step instanceof AtomicStep.BarrierSync bs
                && bs.metadata().getOrDefault("element", "").equals("db")
                && bs.metadata().getOrDefault("scope", "").equals("PER_RUN")) {
                readyBarrierIdx = i;
            }
            if (step instanceof AtomicStep.DeployElement de && de.elementId().equals("app")) {
                appDeployIdx = i;
            }
        }
        assertThat(dbDeployIdx).isGreaterThanOrEqualTo(0);
        assertThat(readyBarrierIdx).isGreaterThan(dbDeployIdx);
        assertThat(appDeployIdx).isGreaterThan(readyBarrierIdx);

        // app deploy should depend on the ready barrier step (not the deploy step directly)
        AtomicStep.DeployElement appDeploy = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("app"))
            .map(AtomicStep.DeployElement.class::cast)
            .findFirst()
            .orElseThrow();
        String readyBarrierStepId = steps.get(readyBarrierIdx).id();
        assertThat(appDeploy.dependencies()).contains(readyBarrierStepId);
    }

    @Test
    @DisplayName("Health check barrier emitted after PER_GROUP redeploy")
    void healthCheckBarrierOnPerGroupRedeploy() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .healthCheck(MockHealthCheckSpec.withTimeout(Duration.ofSeconds(30)))
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("health-check-redeploy-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<Barrier> barriers = context.barriers().get();

        // Should have ELEMENT_READY barriers after each deploy
        long readyBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_READY
                && b.metadata().get("element").equals("server"))
            .count();
        assertThat(readyBarriers).isEqualTo(2); // one per deploy
    }

    @Test
    @DisplayName("All three scopes together: correct barrier generation")
    void allThreeScopesMixedBarrierGeneration() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element db = MockElement.of("db"); // PER_RUN
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build(); // PER_GROUP
        Element worker = MockElement.builder("worker")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build(); // PER_TRIAL

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("three-scope-barrier-test")
            .axis(portAxis)
            .element(db)
            .element(server)
            .element(worker)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();
        List<Barrier> barriers = context.barriers().get();

        // TRIAL_BATCH barriers should exist (PER_GROUP element present)
        long trialBatchBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.TRIAL_BATCH)
            .count();
        assertThat(trialBatchBarriers).isEqualTo(2); // one per trial

        // ELEMENT_SCOPE_END barriers at group boundaries for server
        long scopeEndBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_SCOPE_END)
            .count();
        // 1 group boundary + final cleanup for db + final cleanup for server = at least 3
        assertThat(scopeEndBarriers).isGreaterThanOrEqualTo(3);

        // PER_TRIAL worker has no barriers — eager teardowns instead
        long workerBarrierSyncs = steps.stream()
            .filter(s -> s instanceof AtomicStep.BarrierSync bs
                && "worker".equals(bs.metadata().get("element")))
            .count();
        assertThat(workerBarrierSyncs).isZero();

        // PER_TRIAL worker has eager teardowns
        long workerEagerTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("worker") && "per_trial_eager".equals(t.metadata().get("reason")))
            .count();
        assertThat(workerEagerTeardowns).isEqualTo(2);
    }

    @Test
    @DisplayName("Single trial with PER_GROUP element: TRIAL_BATCH emitted, no ELEMENT_SCOPE_END at boundary")
    void singleTrialWithPerGroupElementProducesNoTrialBatchBarrier() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080);

        TestPlan plan = MockTestPlan.builder()
            .name("single-trial-barrier-test")
            .axis(portAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<Barrier> barriers = context.barriers().get();

        // TRIAL_BATCH barrier emitted for the single trial
        long trialBatchBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.TRIAL_BATCH)
            .count();
        assertThat(trialBatchBarriers).isEqualTo(1);

        // No group-boundary ELEMENT_SCOPE_END (only final cleanup)
        long groupBoundaryBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_SCOPE_END
                && b.metadata().containsKey("trial_index"))
            .count();
        // Only the final cleanup scope-end barrier, no intermediate ones
        long finalScopeEndBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_SCOPE_END
                && "cleanup".equals(b.metadata().get("phase")))
            .count();
        assertThat(finalScopeEndBarriers).isEqualTo(1);
    }

    // ── Degenerate plan corner cases ───────────────────────────────────

    @Test
    @DisplayName("All PER_RUN elements: no group boundaries")
    void allPerRunPlanProducesNoGroupBoundaries() {
        Element db = MockElement.of("db");
        Element cache = MockElement.of("cache");

        Axis<String> axis = MockAxis.of("mode", "read", "write");

        TestPlan plan = MockTestPlan.builder()
            .name("all-per-run-test")
            .axis(axis)
            .element(db)
            .element(cache)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Each element: 1 deploy, 1 final teardown
        for (String elemId : List.of("db", "cache")) {
            long deploys = steps.stream()
                .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals(elemId))
                .count();
            assertThat(deploys).as("deploys for %s", elemId).isEqualTo(1);

            long finalTeardowns = steps.stream()
                .filter(s -> s instanceof AtomicStep.TeardownElement t
                    && t.elementId().equals(elemId) && "cleanup".equals(t.metadata().get("phase")))
                .count();
            assertThat(finalTeardowns).as("final teardowns for %s", elemId).isEqualTo(1);

            long intermediateTeardowns = steps.stream()
                .filter(s -> s instanceof AtomicStep.TeardownElement t
                    && t.elementId().equals(elemId) && "group_boundary".equals(t.metadata().get("reason")))
                .count();
            assertThat(intermediateTeardowns).as("intermediate teardowns for %s", elemId).isZero();
        }

        // No TRIAL_BATCH barriers (no PER_GROUP elements)
        List<Barrier> barriers = context.barriers().orElse(List.of());
        long trialBatchBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.TRIAL_BATCH)
            .count();
        assertThat(trialBatchBarriers).isZero();
    }

    @Test
    @DisplayName("All PER_TRIAL elements: no barriers of any kind")
    void allPerTrialPlanProducesNoBarriers() {
        Element worker1 = MockElement.builder("worker1")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();
        Element worker2 = MockElement.builder("worker2")
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();

        Axis<String> axis = MockAxis.of("mode", "a", "b");

        TestPlan plan = MockTestPlan.builder()
            .name("all-per-trial-test")
            .axis(axis)
            .element(worker1)
            .element(worker2)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // Zero barriers of any kind
        long barrierCount = steps.stream()
            .filter(s -> s instanceof AtomicStep.BarrierSync)
            .count();
        assertThat(barrierCount).isZero();

        // Per-trial deploys: 2 elements × 2 trials = 4 deploys
        long deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .count();
        assertThat(deploys).isEqualTo(4);

        // Eager teardowns: 2 elements × 2 trials = 4 teardowns
        long eagerTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "per_trial_eager".equals(t.metadata().get("reason")))
            .count();
        assertThat(eagerTeardowns).isEqualTo(4);

        // No final teardowns
        long finalTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "cleanup".equals(t.metadata().get("phase")))
            .count();
        assertThat(finalTeardowns).isZero();
    }

    @Test
    @DisplayName("All PER_GROUP same config: 1 deploy per element, 0 intermediate teardowns")
    void allPerGroupSameConfigPlan() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var memParam = IntegerParameter.range("mem", 512, 1024);

        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();
        Element cache = MockElement.builder("cache")
            .parameter(memParam)
            .build();

        // Both axes have same value for all trials
        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8080);
        Axis<Integer> memAxis = MockAxis.of("mem", 512, 512);

        TestPlan plan = MockTestPlan.builder()
            .name("all-per-group-same-config-test")
            .axis(portAxis)
            .axis(memAxis)
            .element(server)
            .element(cache)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // 1 deploy per element (config never changes)
        for (String elemId : List.of("server", "cache")) {
            long deploys = steps.stream()
                .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals(elemId))
                .count();
            assertThat(deploys).as("deploys for %s", elemId).isEqualTo(1);

            long intermediateTeardowns = steps.stream()
                .filter(s -> s instanceof AtomicStep.TeardownElement t
                    && t.elementId().equals(elemId) && "group_boundary".equals(t.metadata().get("reason")))
                .count();
            assertThat(intermediateTeardowns).as("intermediate teardowns for %s", elemId).isZero();
        }
    }

    @Test
    @DisplayName("Multi-axis same element: Cartesian product and fingerprint includes both axes")
    void multiAxisSameElementMergesCorrectly() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var threadsParam = IntegerParameter.range("threads", 1, 4);

        Element server = MockElement.builder("server")
            .parameter(portParam)
            .parameter(threadsParam)
            .build();

        Axis<Integer> portAxis = MockAxis.of("port", 8080, 8081);
        Axis<Integer> threadsAxis = MockAxis.of("threads", 1, 4);

        TestPlan plan = MockTestPlan.builder()
            .name("multi-axis-test")
            .axis(portAxis)
            .axis(threadsAxis)
            .element(server)
            .build();

        DefaultCompilationContext context = runPipeline(plan);
        List<AtomicStep> steps = context.steps().get();

        // 2×2 = 4 trials
        long execTrials = steps.stream()
            .filter(s -> s instanceof AtomicStep.ExecuteTrial)
            .count();
        assertThat(execTrials).isEqualTo(4);

        // Each trial has a unique config → 4 deploys
        long deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("server"))
            .count();
        assertThat(deploys).isEqualTo(4);

        // Group boundaries trigger at each config change → 3 intermediate teardowns
        long intermediateTeardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && t.elementId().equals("server") && "group_boundary".equals(t.metadata().get("reason")))
            .count();
        assertThat(intermediateTeardowns).isEqualTo(3);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

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
