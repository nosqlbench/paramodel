/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nosqlbench.paramodel.tck.api;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.compilation.Compiler.*;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.AtomicStep.*;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import io.nosqlbench.paramodel.plan.ExecutionGraph.*;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.ExecutionPlan.*;
import io.nosqlbench.paramodel.plan.ExecutionPlanMetadata;
import io.nosqlbench.paramodel.plan.ExecutionPlanMetadata.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for concrete inner types across plan, execution graph,
/// compilation, and metadata: records, enums, exceptions, and stubs.
///
class PlanRecordsAndMetadataTest {

    // ── ExecutionPlan.ResourceRequirements record ──────────────────────

    @Test
    void planResourceRequirementsAccessors() {
        var rr = new ExecutionPlan.ResourceRequirements(
            8.0, 4096L, 100L, 1.5, Map.of("gpu", 2));
        assertThat(rr.peakCpu()).isEqualTo(8.0);
        assertThat(rr.peakMemoryMb()).isEqualTo(4096L);
        assertThat(rr.peakStorageGb()).isEqualTo(100L);
        assertThat(rr.peakNetworkGbps()).isEqualTo(1.5);
        assertThat(rr.customResources()).containsEntry("gpu", 2);
    }

    @Test
    void planResourceRequirementsEquality() {
        var rr1 = new ExecutionPlan.ResourceRequirements(1.0, 1024L, 10L, 0.5, Map.of());
        var rr2 = new ExecutionPlan.ResourceRequirements(1.0, 1024L, 10L, 0.5, Map.of());
        assertThat(rr1).isEqualTo(rr2);
        assertThat(rr1.hashCode()).isEqualTo(rr2.hashCode());
    }

    // ── ExecutionPlan.CheckpointStrategy record ───────────────────────

    @Test
    void checkpointStrategyAccessors() {
        var cs = new CheckpointStrategy(
            Duration.ofMinutes(10), true, false, 5);
        assertThat(cs.interval()).isEqualTo(Duration.ofMinutes(10));
        assertThat(cs.checkpointOnBarriers()).isTrue();
        assertThat(cs.checkpointOnErrors()).isFalse();
        assertThat(cs.maxCheckpoints()).isEqualTo(5);
    }

    @Test
    void checkpointStrategyEquality() {
        var cs1 = new CheckpointStrategy(Duration.ofMinutes(5), true, true, 3);
        var cs2 = new CheckpointStrategy(Duration.ofMinutes(5), true, true, 3);
        assertThat(cs1).isEqualTo(cs2);
    }

    // ── ExecutionPlan.ExecutionException ───────────────────────────────

    @Test
    void executionExceptionMessageOnly() {
        var ex = new ExecutionPlan.ExecutionException("plan failed");
        assertThat(ex.getMessage()).isEqualTo("plan failed");
        assertThat(ex.failedStepId()).isEmpty();
        assertThat(ex).isInstanceOf(Exception.class)
            .isNotInstanceOf(RuntimeException.class);
    }

    @Test
    void executionExceptionWithCause() {
        var cause = new RuntimeException("disk full");
        var ex = new ExecutionPlan.ExecutionException("plan failed", cause);
        assertThat(ex.getMessage()).isEqualTo("plan failed");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.failedStepId()).isEmpty();
    }

    @Test
    void executionExceptionWithStepId() {
        var cause = new RuntimeException("OOM");
        var ex = new ExecutionPlan.ExecutionException(
            "step crashed", "step-42", cause);
        assertThat(ex.getMessage()).isEqualTo("step crashed");
        assertThat(ex.failedStepId()).hasValue("step-42");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    // ── ExecutionPlanMetadata.OptimizationLevel enum ──────────────────

    @Test
    void metadataOptimizationLevelValues() {
        assertThat(ExecutionPlanMetadata.OptimizationLevel.values())
            .containsExactlyInAnyOrder(
                ExecutionPlanMetadata.OptimizationLevel.NONE,
                ExecutionPlanMetadata.OptimizationLevel.BASIC,
                ExecutionPlanMetadata.OptimizationLevel.STANDARD,
                ExecutionPlanMetadata.OptimizationLevel.AGGRESSIVE);
    }

    // ── ExecutionPlanMetadata.ExecutionStatus enum ─────────────────────

    @Test
    void metadataExecutionStatusValues() {
        assertThat(ExecutionPlanMetadata.ExecutionStatus.values())
            .containsExactlyInAnyOrder(
                ExecutionPlanMetadata.ExecutionStatus.RUNNING,
                ExecutionPlanMetadata.ExecutionStatus.COMPLETED,
                ExecutionPlanMetadata.ExecutionStatus.FAILED,
                ExecutionPlanMetadata.ExecutionStatus.CANCELLED);
    }

    // ── ExecutionPlanMetadata.ResourceProfile record ───────────────────

    @Test
    void resourceProfileAccessors() {
        var rp = new ResourceProfile(32.0, 18.5, 128.0, 64.0, 500.0, 10.0);
        assertThat(rp.peakCpu()).isEqualTo(32.0);
        assertThat(rp.averageCpu()).isEqualTo(18.5);
        assertThat(rp.peakMemoryGb()).isEqualTo(128.0);
        assertThat(rp.averageMemoryGb()).isEqualTo(64.0);
        assertThat(rp.peakStorageGb()).isEqualTo(500.0);
        assertThat(rp.peakNetworkGbps()).isEqualTo(10.0);
    }

    @Test
    void resourceProfileEquality() {
        var rp1 = new ResourceProfile(1.0, 0.5, 2.0, 1.0, 10.0, 0.5);
        var rp2 = new ResourceProfile(1.0, 0.5, 2.0, 1.0, 10.0, 0.5);
        assertThat(rp1).isEqualTo(rp2);
        assertThat(rp1.hashCode()).isEqualTo(rp2.hashCode());
    }

    // ── ExecutionPlanMetadata.PerformanceMetrics record ────────────────

    @Test
    void performanceMetricsAccessors() {
        var gc = new GraphComplexity(100, 250, 5.0, 12, 18);
        var pm = new PerformanceMetrics(
            24, 12.4, Duration.ofHours(4), Duration.ofHours(96),
            21.4, 0.89, gc);

        assertThat(pm.maximumParallelism()).isEqualTo(24);
        assertThat(pm.averageParallelism()).isEqualTo(12.4);
        assertThat(pm.criticalPathDuration()).isEqualTo(Duration.ofHours(4));
        assertThat(pm.totalDuration()).isEqualTo(Duration.ofHours(96));
        assertThat(pm.speedup()).isEqualTo(21.4);
        assertThat(pm.efficiency()).isEqualTo(0.89);
        assertThat(pm.graphComplexity()).isEqualTo(gc);
    }

    // ── ExecutionPlanMetadata.GraphComplexity record ───────────────────

    @Test
    void graphComplexityAccessors() {
        var gc = new GraphComplexity(312, 847, 5.4, 12, 18);
        assertThat(gc.nodeCount()).isEqualTo(312);
        assertThat(gc.edgeCount()).isEqualTo(847);
        assertThat(gc.averageDegree()).isEqualTo(5.4);
        assertThat(gc.maxDepth()).isEqualTo(12);
        assertThat(gc.diameter()).isEqualTo(18);
    }

    @Test
    void graphComplexityEquality() {
        var gc1 = new GraphComplexity(10, 20, 4.0, 5, 8);
        var gc2 = new GraphComplexity(10, 20, 4.0, 5, 8);
        assertThat(gc1).isEqualTo(gc2);
    }

    // ── ExecutionPlanMetadata.Optimization record ─────────────────────

    @Test
    void metadataOptimizationApplied() {
        var opt = new ExecutionPlanMetadata.Optimization(
            "barrier coalescing", true, "6 barriers removed", Optional.empty());
        assertThat(opt.name()).isEqualTo("barrier coalescing");
        assertThat(opt.applied()).isTrue();
        assertThat(opt.savings()).isEqualTo("6 barriers removed");
        assertThat(opt.skipReason()).isEmpty();
    }

    @Test
    void metadataOptimizationSkipped() {
        var opt = new ExecutionPlanMetadata.Optimization(
            "speculative execution", false, "", Optional.of("cost constraints"));
        assertThat(opt.applied()).isFalse();
        assertThat(opt.skipReason()).hasValue("cost constraints");
    }

    // ── ExecutionGraph.Edge record ─────────────────────────────────────

    @Test
    void edgeAccessors() {
        var source = createMinimalStep("step-1");
        var target = createMinimalStep("step-2");
        var edge = new Edge(source, target, Duration.ofSeconds(30));

        assertThat(edge.source()).isSameAs(source);
        assertThat(edge.target()).isSameAs(target);
        assertThat(edge.weight()).isEqualTo(Duration.ofSeconds(30));
    }

    // ── ExecutionGraph.ResourceLimits record ───────────────────────────

    @Test
    void resourceLimitsAccessors() {
        var rl = new ResourceLimits(8.0, 16.0, 100.0, 5.0);
        assertThat(rl.maxCpu()).isEqualTo(8.0);
        assertThat(rl.maxMemoryGb()).isEqualTo(16.0);
        assertThat(rl.maxStorageGb()).isEqualTo(100.0);
        assertThat(rl.maxNetworkGbps()).isEqualTo(5.0);
    }

    @Test
    void resourceLimitsEquality() {
        var rl1 = new ResourceLimits(4.0, 8.0, 50.0, 1.0);
        var rl2 = new ResourceLimits(4.0, 8.0, 50.0, 1.0);
        assertThat(rl1).isEqualTo(rl2);
        assertThat(rl1.hashCode()).isEqualTo(rl2.hashCode());
    }

    @Test
    void resourceLimitsBuilderThrows() {
        assertThatThrownBy(ResourceLimits::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ExecutionGraph.ScheduledStep record ────────────────────────────

    @Test
    void scheduledStepAccessors() {
        var step = createMinimalStep("step-1");
        var ss = new ScheduledStep(step, Duration.ofSeconds(10), Duration.ofSeconds(40));
        assertThat(ss.step()).isSameAs(step);
        assertThat(ss.startTime()).isEqualTo(Duration.ofSeconds(10));
        assertThat(ss.endTime()).isEqualTo(Duration.ofSeconds(40));
    }

    // ── ExecutionGraph.ResourceUsagePoint record ──────────────────────

    @Test
    void resourceUsagePointAccessors() {
        var p = new ResourceUsagePoint(Duration.ofSeconds(30), 4.0, 8.0, 20.0);
        assertThat(p.time()).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.cpu()).isEqualTo(4.0);
        assertThat(p.memoryGb()).isEqualTo(8.0);
        assertThat(p.storageGb()).isEqualTo(20.0);
    }

    @Test
    void resourceUsagePointEquality() {
        var p1 = new ResourceUsagePoint(Duration.ZERO, 1.0, 2.0, 3.0);
        var p2 = new ResourceUsagePoint(Duration.ZERO, 1.0, 2.0, 3.0);
        assertThat(p1).isEqualTo(p2);
    }

    // ── ExecutionGraph.GraphStatistics record ──────────────────────────

    @Test
    void graphStatisticsAccessors() {
        var gs = new ExecutionGraph.GraphStatistics(
            100, 250, 8, 5, 3, 5.0,
            Duration.ofMinutes(30), Duration.ofHours(4), 12, 6.5);
        assertThat(gs.nodeCount()).isEqualTo(100);
        assertThat(gs.edgeCount()).isEqualTo(250);
        assertThat(gs.maxDepth()).isEqualTo(8);
        assertThat(gs.maxFanOut()).isEqualTo(5);
        assertThat(gs.maxFanIn()).isEqualTo(3);
        assertThat(gs.averageDegree()).isEqualTo(5.0);
        assertThat(gs.criticalPathDuration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(gs.totalDuration()).isEqualTo(Duration.ofHours(4));
        assertThat(gs.maximumParallelism()).isEqualTo(12);
        assertThat(gs.averageParallelism()).isEqualTo(6.5);
    }

    @Test
    void graphStatisticsEquality() {
        var gs1 = new ExecutionGraph.GraphStatistics(
            10, 20, 3, 2, 2, 4.0,
            Duration.ofSeconds(60), Duration.ofMinutes(5), 4, 2.5);
        var gs2 = new ExecutionGraph.GraphStatistics(
            10, 20, 3, 2, 2, 4.0,
            Duration.ofSeconds(60), Duration.ofMinutes(5), 4, 2.5);
        assertThat(gs1).isEqualTo(gs2);
    }

    // ── Compiler.CompilationStrategy enum ─────────────────────────────

    @Test
    void compilationStrategyValues() {
        assertThat(CompilationStrategy.values()).containsExactlyInAnyOrder(
            CompilationStrategy.FAST_COMPILE,
            CompilationStrategy.BALANCED,
            CompilationStrategy.OPTIMIZE_EXECUTION);
    }

    // ── Compiler.OptimizationLevel enum ───────────────────────────────

    @Test
    void compilerOptimizationLevelValues() {
        assertThat(Compiler.OptimizationLevel.values()).containsExactlyInAnyOrder(
            Compiler.OptimizationLevel.NONE,
            Compiler.OptimizationLevel.BASIC,
            Compiler.OptimizationLevel.STANDARD,
            Compiler.OptimizationLevel.AGGRESSIVE);
    }

    // ── Compiler.ErrorSeverity enum ───────────────────────────────────

    @Test
    void errorSeverityValues() {
        assertThat(ErrorSeverity.values()).containsExactlyInAnyOrder(
            ErrorSeverity.ERROR,
            ErrorSeverity.WARNING,
            ErrorSeverity.INFO);
    }

    // ── Compiler.Optimization record ──────────────────────────────────

    @Test
    void compilerOptimizationApplied() {
        var opt = new Compiler.Optimization(
            "step fusion", "fuse compatible steps",
            true, Optional.of("7 steps eliminated"));
        assertThat(opt.name()).isEqualTo("step fusion");
        assertThat(opt.description()).isEqualTo("fuse compatible steps");
        assertThat(opt.applied()).isTrue();
        assertThat(opt.savings()).hasValue("7 steps eliminated");
    }

    @Test
    void compilerOptimizationNotApplied() {
        var opt = new Compiler.Optimization(
            "speculative", "speculative execution",
            false, Optional.empty());
        assertThat(opt.applied()).isFalse();
        assertThat(opt.savings()).isEmpty();
    }

    // ── Compiler.CompilationStatistics record ─────────────────────────

    @Test
    void compilationStatisticsAccessors() {
        var stats = new CompilationStatistics(
            1000, 3000, 89, 12,
            Duration.ofMillis(120), Duration.ofSeconds(2),
            Duration.ofSeconds(3), Duration.ofMillis(340));
        assertThat(stats.trialsGenerated()).isEqualTo(1000);
        assertThat(stats.stepsGenerated()).isEqualTo(3000);
        assertThat(stats.barriersGenerated()).isEqualTo(89);
        assertThat(stats.optimizationsApplied()).isEqualTo(12);
        assertThat(stats.validationTime()).isEqualTo(Duration.ofMillis(120));
        assertThat(stats.enumerationTime()).isEqualTo(Duration.ofSeconds(2));
        assertThat(stats.optimizationTime()).isEqualTo(Duration.ofSeconds(3));
        assertThat(stats.codeGenTime()).isEqualTo(Duration.ofMillis(340));
    }

    @Test
    void compilationStatisticsEquality() {
        var s1 = new CompilationStatistics(
            10, 30, 5, 2,
            Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        var s2 = new CompilationStatistics(
            10, 30, 5, 2,
            Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        assertThat(s1).isEqualTo(s2);
    }

    // ── Compiler stubs ────────────────────────────────────────────────

    @Test
    void compilerOptionsBuilderThrows() {
        assertThatThrownBy(CompilerOptions::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── CompilationContext.ElementInstance record ──────────────────────

    @Test
    void compilationContextElementInstanceAccessors() {
        var inst = new CompilationContext.ElementInstance(
            "inst-1", null, List.of(), "global", Set.of("dep-1"));
        assertThat(inst.instanceId()).isEqualTo("inst-1");
        assertThat(inst.element()).isNull();
        assertThat(inst.trials()).isEmpty();
        assertThat(inst.scopeDescription()).isEqualTo("global");
        assertThat(inst.dependsOn()).containsExactly("dep-1");
    }

    @Test
    void compilationContextElementInstanceEquality() {
        var i1 = new CompilationContext.ElementInstance(
            "a", null, List.of(), "scope", Set.of());
        var i2 = new CompilationContext.ElementInstance(
            "a", null, List.of(), "scope", Set.of());
        assertThat(i1).isEqualTo(i2);
    }

    // ── AtomicStep.ElementInstance record ──────────────────────────────

    @Test
    void atomicStepElementInstanceAccessors() {
        var inst = new AtomicStep.ElementInstance(
            "inst-1", "db", "localhost:5432",
            Map.of("port", 5432), AtomicStep.InstanceStatus.HEALTHY);
        assertThat(inst.instanceId()).isEqualTo("inst-1");
        assertThat(inst.elementId()).isEqualTo("db");
        assertThat(inst.endpoint()).isEqualTo("localhost:5432");
        assertThat(inst.configuration()).containsEntry("port", 5432);
        assertThat(inst.status()).isEqualTo(AtomicStep.InstanceStatus.HEALTHY);
    }

    @Test
    void atomicStepInstanceStatusValues() {
        assertThat(AtomicStep.InstanceStatus.values()).containsExactlyInAnyOrder(
            AtomicStep.InstanceStatus.DEPLOYING,
            AtomicStep.InstanceStatus.HEALTHY,
            AtomicStep.InstanceStatus.UNHEALTHY,
            AtomicStep.InstanceStatus.TEARDOWN);
    }

    // ── helper ────────────────────────────────────────────────────────

    private static AtomicStep createMinimalStep(String id) {
        return new AtomicStep.DeployElement(
            id, "element", 0, Map.of(), List.of(), List.of(),
            Optional.empty(),
            AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of());
    }
}
