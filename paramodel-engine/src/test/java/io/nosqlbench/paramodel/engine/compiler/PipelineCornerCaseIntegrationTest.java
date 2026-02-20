/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.mock.elements.MockHealthCheckSpec;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.OptimizationStrategy;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/// Full 8-stage pipeline integration tests covering corner cases in
/// optimization, deep dependency chains, degenerate plans, and
/// health check barriers.
class PipelineCornerCaseIntegrationTest {

    @Test
    @DisplayName("PRUNE_REDUNDANT optimization removes redundant teardown+deploy for constant config")
    void allPrunableOptimization() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();

        // Same config for all trials → the intermediate teardown+deploy
        // pair is redundant and should be pruned.
        var portAxis = MockAxis.of("port", 8080, 8080, 8080);

        TestPlan plan = MockTestPlan.builder()
            .name("all-prunable-test")
            .axis(portAxis)
            .element(server)
            .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
            .build();

        Compiler.CompilationResult result = compile(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();
        List<AtomicStep> steps = execPlan.steps();

        // With constant config, the pipeline generates 1 deploy at start,
        // 3 exec trials, and 1 final teardown. No intermediate
        // teardown+deploy pairs because config never changes.
        long deploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("server"))
            .count();
        assertThat(deploys).isEqualTo(1);

        long teardowns = steps.stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t && t.elementId().equals("server"))
            .count();
        assertThat(teardowns).isEqualTo(1); // only final teardown

        long execTrials = steps.stream()
            .filter(s -> s instanceof AtomicStep.TrialStep)
            .count();
        assertThat(execTrials).isEqualTo(3);
    }

    @Test
    @DisplayName("Mixed prunable and non-prunable: only constant-config element pruned")
    void mixedPrunableAndNonPrunable() {
        var portParam = IntegerParameter.range("port", 8080, 8081);
        var memParam = IntegerParameter.range("mem", 512, 1024);

        // server has constant config → prunable
        Element server = MockElement.builder("server")
            .parameter(portParam)
            .build();
        // cache has varying config → not prunable
        Element cache = MockElement.builder("cache")
            .parameter(memParam)
            .build();

        var portAxis = MockAxis.of("port", 8080, 8080);
        var memAxis = MockAxis.of("mem", 512, 1024);

        TestPlan plan = MockTestPlan.builder()
            .name("mixed-prune-test")
            .axis(portAxis)
            .axis(memAxis)
            .element(server)
            .element(cache)
            .optimizationStrategy(OptimizationStrategy.PRUNE_REDUNDANT)
            .build();

        Compiler.CompilationResult result = compile(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();
        List<AtomicStep> steps = execPlan.steps();

        // 2×2 = 4 trials, 2 independent leaf elements → 2 TrialSteps per trial = 8
        long execTrials = steps.stream()
            .filter(s -> s instanceof AtomicStep.TrialStep)
            .count();
        assertThat(execTrials).isEqualTo(8);

        // cache has varying config: deploys depend on actual fingerprint changes
        // At minimum cache has more than 1 deploy (config changes)
        long cacheDeploys = steps.stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("cache"))
            .count();
        assertThat(cacheDeploys).isGreaterThan(1);
    }

    @Test
    @DisplayName("5-element dependency chain compiles successfully with correct ordering")
    void deepDependencyChainCompiles() {
        Element e = MockElement.of("e");
        Element d = MockElement.builder("d")
            .dependency(e)
            .build();
        Element c = MockElement.builder("c")
            .dependency(d)
            .build();
        Element b = MockElement.builder("b")
            .dependency(c)
            .build();
        Element a = MockElement.builder("a")
            .dependency(b)
            .build();

        var axis = MockAxis.of("mode", "test");

        TestPlan plan = MockTestPlan.builder()
            .name("deep-chain-compile-test")
            .axis(axis)
            .element(e)
            .element(d)
            .element(c)
            .element(b)
            .element(a)
            .build();

        Compiler.CompilationResult result = compile(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // All elements are PER_RUN (no parameters matching axes),
        // deployed once each in topo order
        List<AtomicStep.DeployElement> deploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        assertThat(deploys).hasSize(5);
        assertThat(deploys.get(0).elementId()).isEqualTo("e");

        // Execution graph should be acyclic
        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Plan with elements but no axes: single trial, all PER_RUN deploys")
    void emptyPlanSingleTrial() {
        Element db = MockElement.of("db");
        Element cache = MockElement.of("cache");

        // An axis with a single value produces exactly 1 trial
        var axis = MockAxis.of("mode", "default");

        TestPlan plan = MockTestPlan.builder()
            .name("single-trial-test")
            .axis(axis)
            .element(db)
            .element(cache)
            .build();

        Compiler.CompilationResult result = compile(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // db and cache are independent leaf elements → 2 TrialSteps per trial
        long execTrials = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TrialStep)
            .count();
        assertThat(execTrials).isEqualTo(2);

        // Each element: 1 deploy (PER_RUN), 1 final teardown
        long deploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .count();
        assertThat(deploys).isEqualTo(2);

        // No group boundary teardowns
        long groupBoundaryTeardowns = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement t
                && "group_boundary".equals(t.metadata().get("reason")))
            .count();
        assertThat(groupBoundaryTeardowns).isZero();
    }

    @Test
    @DisplayName("Health check barriers appear in full pipeline end-to-end")
    void healthCheckBarriersEndToEnd() {
        Element db = MockElement.builder("db")
            .healthCheck(MockHealthCheckSpec.withTimeout(Duration.ofSeconds(30)))
            .build();

        var portParam = IntegerParameter.range("port", 8080, 8081);
        Element app = MockElement.builder("app")
            .parameter(portParam)
            .dependency(db)
            .build();

        var portAxis = MockAxis.of("port", 8080, 8081);

        TestPlan plan = MockTestPlan.builder()
            .name("health-check-e2e-test")
            .axis(portAxis)
            .element(db)
            .element(app)
            .build();

        Compiler.CompilationResult result = compile(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // Barriers should include ELEMENT_READY for db
        List<Barrier> barriers = execPlan.barriers();
        long readyBarriers = barriers.stream()
            .filter(b -> b.type() == Barrier.BarrierType.ELEMENT_READY)
            .count();
        assertThat(readyBarriers).isGreaterThanOrEqualTo(1);

        // The execution graph should be acyclic
        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Plan with no elements produces compilation warning and minimal plan")
    void noElementsProducesCompilationWarning() {
        var axis = MockAxis.of("mode", "test");

        TestPlan plan = MockTestPlan.builder()
            .name("no-elements-test")
            .axis(axis)
            .build();

        Compiler.CompilationResult result = compile(plan);

        // Compilation succeeds but with a warning about missing elements
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings())
            .anyMatch(w -> w.message().contains("no elements"));

        // The execution plan should have no deploy steps
        ExecutionPlan execPlan = result.executionPlan().get();
        long deploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .count();
        assertThat(deploys).isZero();
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private Compiler.CompilationResult compile(TestPlan plan) {
        DefaultCompiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .options(new Compiler.CompilerOptions() {
                @Override public Compiler.CompilationStrategy strategy() { return Compiler.CompilationStrategy.BALANCED; }
                @Override public Compiler.OptimizationLevel optimizationLevel() { return Compiler.OptimizationLevel.STANDARD; }
                @Override public long maxTrialSpaceSize() { return 1_000_000; }
                @Override public boolean parallelCompilation() { return false; }
                @Override public boolean dryRun() { return false; }
                @Override public Map<String, Object> customOptions() {
                    return Map.of(StepGenerationStage.OPTION_STRATEGY, "fingerprint");
                }
            })
            .build();
        return compiler.compile(plan);
    }
}
