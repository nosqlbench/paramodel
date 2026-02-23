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
package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.plan.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DefaultLiveExecutionGraphTest {

    // --- helpers ---

    /// Creates a simple barrier step with the given ID and dependencies.
    private static AtomicStep step(String id, String... deps) {
        return new AtomicStep.BarrierSync(
            id, id, List.of(deps),
            Optional.of(Duration.ofSeconds(1)),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }

    /// Creates a trial step with the given ID, trial ID, and dependencies.
    private static AtomicStep trialStep(String id, String trialId, String... deps) {
        return new AtomicStep.TrialStep(
            id, trialId, Map.of(),
            List.of(deps),
            Optional.of(Duration.ofSeconds(10)),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }

    private static LiveExecutionGraph live(List<AtomicStep> steps, ExecutionState state) {
        ExecutionGraph graph = new DefaultExecutionGraph(steps);
        return DefaultLiveExecutionGraph.create(graph, state);
    }

    // --- linear graph tests ---

    @Test
    @DisplayName("Empty state: roots READY, downstream BLOCKED")
    void emptyStateRootsReadyDownstreamBlocked() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "B");

        LiveExecutionGraph live = live(List.of(a, b, c), ExecutionState.empty());

        assertThat(live.stepStatus(a)).isEqualTo(StepStatus.READY);
        assertThat(live.stepStatus(b)).isEqualTo(StepStatus.BLOCKED);
        assertThat(live.stepStatus(c)).isEqualTo(StepStatus.BLOCKED);
        assertThat(live.frontier()).containsExactly(a);
        assertThat(live.frontierSize()).isEqualTo(1);
        assertThat(live.isComplete()).isFalse();
    }

    @Test
    @DisplayName("Linear graph: A completed, B READY, C BLOCKED")
    void linearPartiallyCompleted() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "B");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c), state);

        assertThat(live.stepStatus(a)).isEqualTo(StepStatus.COMPLETED);
        assertThat(live.stepStatus(b)).isEqualTo(StepStatus.READY);
        assertThat(live.stepStatus(c)).isEqualTo(StepStatus.BLOCKED);
        assertThat(live.frontier()).containsExactly(b);
    }

    @Test
    @DisplayName("Linear graph: A completed, B in-progress, C BLOCKED")
    void linearWithInProgress() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "B");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of(), Set.of(), Set.of("B"),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c), state);

        assertThat(live.stepStatus(b)).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(live.stepStatus(c)).isEqualTo(StepStatus.BLOCKED);
        assertThat(live.activeSteps()).containsExactly(b);
        assertThat(live.frontier()).isEmpty();
    }

    @Test
    @DisplayName("All steps completed: isComplete true")
    void allCompleted() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "B");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A", "B", "C"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c), state);

        assertThat(live.isComplete()).isTrue();
        assertThat(live.frontier()).isEmpty();
        assertThat(live.progress().completionPercentage()).isEqualTo(100.0);
        assertThat(live.hasFailures()).isFalse();
    }

    // --- fan-out / fan-in graph tests ---

    @Test
    @DisplayName("Fan-out: A completed, B and C become READY")
    void fanOutFrontierDetection() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "A");
        AtomicStep d = step("D", "B", "C");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c, d), state);

        assertThat(live.stepStatus(b)).isEqualTo(StepStatus.READY);
        assertThat(live.stepStatus(c)).isEqualTo(StepStatus.READY);
        assertThat(live.stepStatus(d)).isEqualTo(StepStatus.BLOCKED);
        assertThat(live.frontier()).containsExactlyInAnyOrder(b, c);
        assertThat(live.frontierSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("Fan-in: D becomes READY when both B and C complete")
    void fanInReady() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "A");
        AtomicStep d = step("D", "B", "C");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A", "B", "C"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c, d), state);

        assertThat(live.stepStatus(d)).isEqualTo(StepStatus.READY);
        assertThat(live.frontier()).containsExactly(d);
    }

    // --- UNREACHABLE propagation ---

    @Test
    @DisplayName("UNREACHABLE propagates when dependency fails")
    void unreachablePropagation() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "B");

        ExecutionState state = new ImmutableExecutionState(
            Set.of(), Set.of("A"), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c), state);

        assertThat(live.stepStatus(a)).isEqualTo(StepStatus.FAILED);
        assertThat(live.stepStatus(b)).isEqualTo(StepStatus.UNREACHABLE);
        assertThat(live.stepStatus(c)).isEqualTo(StepStatus.UNREACHABLE);
        assertThat(live.hasFailures()).isTrue();
        assertThat(live.hasUnreachableSteps()).isTrue();
        assertThat(live.frontier()).isEmpty();
    }

    @Test
    @DisplayName("Fan-out: one branch fails, only its dependents are unreachable")
    void fanOutPartialFailure() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "A");
        AtomicStep d = step("D", "B", "C");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of("B"), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c, d), state);

        assertThat(live.stepStatus(b)).isEqualTo(StepStatus.FAILED);
        assertThat(live.stepStatus(c)).isEqualTo(StepStatus.READY);
        // D depends on both B and C; B failed so D is unreachable
        assertThat(live.stepStatus(d)).isEqualTo(StepStatus.UNREACHABLE);
    }

    @Test
    @DisplayName("SKIPPED dependency also makes downstream UNREACHABLE")
    void skippedCausesUnreachable() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");

        ExecutionState state = new ImmutableExecutionState(
            Set.of(), Set.of(), Set.of("A"), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b), state);

        assertThat(live.stepStatus(a)).isEqualTo(StepStatus.SKIPPED);
        assertThat(live.stepStatus(b)).isEqualTo(StepStatus.UNREACHABLE);
    }

    // --- edge status tests ---

    @Test
    @DisplayName("Edge statuses reflect step statuses")
    void edgeStatusDerivation() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "B");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of(), Set.of(), Set.of("B"),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c), state);

        List<LiveExecutionGraph.LiveEdge> liveEdges = live.liveEdges();
        assertThat(liveEdges).hasSize(2);

        // Edge A→B: A completed, B in-progress → ACTIVE
        LiveExecutionGraph.LiveEdge abEdge = liveEdges.stream()
            .filter(e -> e.edge().source().id().equals("A") && e.edge().target().id().equals("B"))
            .findFirst().orElseThrow();
        assertThat(abEdge.status()).isEqualTo(EdgeStatus.ACTIVE);
        assertThat(abEdge.sourceStatus()).isEqualTo(StepStatus.COMPLETED);
        assertThat(abEdge.targetStatus()).isEqualTo(StepStatus.IN_PROGRESS);

        // Edge B→C: B in-progress, C blocked → PENDING
        LiveExecutionGraph.LiveEdge bcEdge = liveEdges.stream()
            .filter(e -> e.edge().source().id().equals("B") && e.edge().target().id().equals("C"))
            .findFirst().orElseThrow();
        assertThat(bcEdge.status()).isEqualTo(EdgeStatus.PENDING);
    }

    @Test
    @DisplayName("Edge SATISFIED when source completed but target not in-progress")
    void edgeSatisfied() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b), state);

        ExecutionGraph.Edge edge = live.graph().edges().getFirst();
        assertThat(live.edgeStatus(edge)).isEqualTo(EdgeStatus.SATISFIED);
    }

    @Test
    @DisplayName("Edge FAILED when source failed")
    void edgeFailed() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");

        ExecutionState state = new ImmutableExecutionState(
            Set.of(), Set.of("A"), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b), state);

        ExecutionGraph.Edge edge = live.graph().edges().getFirst();
        assertThat(live.edgeStatus(edge)).isEqualTo(EdgeStatus.FAILED);
        assertThat(live.edgesByStatus(EdgeStatus.FAILED)).containsExactly(edge);
    }

    // --- progress metrics ---

    @Test
    @DisplayName("Progress counts are accurate")
    void progressMetrics() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "A");
        AtomicStep d = step("D", "B");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of("B"), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c, d), state);
        LiveExecutionGraph.Progress progress = live.progress();

        assertThat(progress.completed()).isEqualTo(1); // A
        assertThat(progress.failed()).isEqualTo(1); // B
        assertThat(progress.ready()).isEqualTo(1); // C
        assertThat(progress.unreachable()).isEqualTo(1); // D
        assertThat(progress.totalSteps()).isEqualTo(4);
        assertThat(progress.successRate()).isEqualTo(0.5); // 1 completed / 2 resolved
    }

    @Test
    @DisplayName("Progress completion percentage includes all terminal statuses")
    void completionPercentage() {
        AtomicStep a = step("A");
        AtomicStep b = step("B");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of("B"), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b), state);

        assertThat(live.progress().completionPercentage()).isEqualTo(100.0);
        assertThat(live.isComplete()).isTrue();
    }

    // --- trial aggregation ---

    @Test
    @DisplayName("Trial step status and trial ID aggregation")
    void trialAggregation() {
        AtomicStep deploy = step("deploy");
        AtomicStep t1 = trialStep("trial-1", "t1", "deploy");
        AtomicStep t2 = trialStep("trial-2", "t2", "deploy");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy"), Set.of(), Set.of(), Set.of(),
            Set.of("t1"), Set.of("t2"), Map.of()
        );

        LiveExecutionGraph live = live(List.of(deploy, t1, t2), state);

        assertThat(live.progress().totalTrials()).isEqualTo(2);
        assertThat(live.completedTrialIds()).containsExactly("t1");
        assertThat(live.activeTrialIds()).containsExactly("t2");
    }

    // --- step status lookup by ID ---

    @Test
    @DisplayName("stepStatus by ID works correctly")
    void stepStatusById() {
        AtomicStep a = step("A");

        LiveExecutionGraph live = live(List.of(a), ExecutionState.empty());

        assertThat(live.stepStatus("A")).isEqualTo(StepStatus.READY);
    }

    @Test
    @DisplayName("stepStatus by ID throws for unknown step")
    void stepStatusByIdUnknown() {
        LiveExecutionGraph live = live(List.of(step("A")), ExecutionState.empty());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> live.stepStatus("Z"))
            .withMessageContaining("Z");
    }

    // --- bulk queries ---

    @Test
    @DisplayName("stepsByStatus returns correct groupings")
    void stepsByStatus() {
        AtomicStep a = step("A");
        AtomicStep b = step("B");
        AtomicStep c = step("C", "A");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("A"), Set.of("B"), Set.of(), Set.of(),
            Set.of(), Set.of(), Map.of()
        );

        LiveExecutionGraph live = live(List.of(a, b, c), state);

        assertThat(live.stepsByStatus(StepStatus.COMPLETED)).containsExactly(a);
        assertThat(live.stepsByStatus(StepStatus.FAILED)).containsExactly(b);
        assertThat(live.stepsByStatus(StepStatus.READY)).containsExactly(c);
        assertThat(live.stepsByStatus(StepStatus.BLOCKED)).isEmpty();
    }

    @Test
    @DisplayName("allStepStatuses returns map of every step")
    void allStepStatuses() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");

        LiveExecutionGraph live = live(List.of(a, b), ExecutionState.empty());

        Map<AtomicStep, StepStatus> all = live.allStepStatuses();
        assertThat(all).hasSize(2);
        assertThat(all.get(a)).isEqualTo(StepStatus.READY);
        assertThat(all.get(b)).isEqualTo(StepStatus.BLOCKED);
    }
}
