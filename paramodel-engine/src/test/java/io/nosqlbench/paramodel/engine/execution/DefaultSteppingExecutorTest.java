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
package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.engine.compiler.DefaultExecutionGraph;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.Executor.SteppingHandle;
import io.nosqlbench.paramodel.execution.Executor.StepOutcome;
import io.nosqlbench.paramodel.plan.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DefaultSteppingExecutorTest {

    private static AtomicStep step(String id, String... deps) {
        return new AtomicStep.BarrierSync(
            id, id, List.of(deps),
            Optional.of(Duration.ofSeconds(1)),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }

    private static ExecutionPlan planWith(List<AtomicStep> steps) {
        return new StubExecutionPlan(steps);
    }

    private static Executor createExecutor() {
        return DefaultExecutor.builder().build();
    }

    @Test
    @DisplayName("Step through linear graph A → B → C via advance(1) + awaitNextOutcome")
    void stepThroughLinearGraph() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "B");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a, b, c)));

        assertThat(handle.frontier()).extracting(AtomicStep::id).containsExactly("A");
        assertThat(handle.isComplete()).isFalse();

        handle.advance(1);
        Optional<StepOutcome> outcome1 = handle.awaitNextOutcome(Duration.ofSeconds(5));
        assertThat(outcome1).isPresent();
        assertThat(outcome1.get().step().id()).isEqualTo("A");
        assertThat(outcome1.get().resultStatus()).isEqualTo(StepStatus.FAILED);

        // A failed → B and C become UNREACHABLE → session is complete
        assertThat(handle.liveGraph().stepStatus("B")).isEqualTo(StepStatus.UNREACHABLE);
        assertThat(handle.liveGraph().stepStatus("C")).isEqualTo(StepStatus.UNREACHABLE);
        assertThat(handle.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Fan-out: initially only root is in frontier")
    void fanOutInitialFrontier() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");
        AtomicStep c = step("C", "A");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a, b, c)));

        assertThat(handle.frontier()).extracting(AtomicStep::id).containsExactly("A");
        handle.cancel();
    }

    @Test
    @DisplayName("Live graph updates after advance(1) + awaitNextOutcome")
    void liveGraphUpdatesAfterAdvance() {
        AtomicStep a = step("A");
        AtomicStep b = step("B", "A");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a, b)));

        LiveExecutionGraph before = handle.liveGraph();
        assertThat(before.stepStatus("A")).isEqualTo(StepStatus.READY);
        assertThat(before.stepStatus("B")).isEqualTo(StepStatus.BLOCKED);

        handle.advance(1);
        Optional<StepOutcome> outcome = handle.awaitNextOutcome(Duration.ofSeconds(5));
        assertThat(outcome).isPresent();
        LiveExecutionGraph after = outcome.get().updatedGraph();
        assertThat(after.stepStatus("A")).isEqualTo(StepStatus.FAILED);
        assertThat(after.stepStatus("B")).isEqualTo(StepStatus.UNREACHABLE);
    }

    @Test
    @DisplayName("Cancel prevents further execution")
    void cancelMidExecution() {
        AtomicStep a = step("A");
        AtomicStep b = step("B");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a, b)));

        handle.cancel();

        // After cancel, awaitNextOutcome should return empty
        Optional<StepOutcome> outcome = handle.awaitNextOutcome(Duration.ofMillis(200));
        assertThat(outcome).isEmpty();
    }

    @Test
    @DisplayName("currentState reflects execution progress")
    void currentStateReflectsProgress() {
        AtomicStep a = step("A");
        AtomicStep b = step("B");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a, b)));

        assertThat(handle.currentState().completedStepIds()).isEmpty();
        assertThat(handle.currentState().failedStepIds()).isEmpty();

        handle.advance(1);
        handle.awaitNextOutcome(Duration.ofSeconds(5));

        // A failed (execute() throws)
        assertThat(handle.currentState().failedStepIds()).contains("A");
        handle.cancel();
    }

    @Test
    @DisplayName("Elapsed time is non-negative")
    void elapsedTimeNonNegative() {
        AtomicStep a = step("A");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a)));

        handle.advance(1);
        Optional<StepOutcome> outcome = handle.awaitNextOutcome(Duration.ofSeconds(5));
        assertThat(outcome).isPresent();
        assertThat(outcome.get().elapsed()).isGreaterThanOrEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Multiple independent roots are all in the frontier")
    void multipleRootsInFrontier() {
        AtomicStep a = step("A");
        AtomicStep b = step("B");
        AtomicStep c = step("C");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a, b, c)));

        assertThat(handle.frontier()).extracting(AtomicStep::id)
            .containsExactlyInAnyOrder("A", "B", "C");
        handle.cancel();
    }

    @Test
    @DisplayName("advance(1) auto-selects first frontier step")
    void advanceAutoSelects() {
        AtomicStep a = step("A");
        AtomicStep b = step("B");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a, b)));

        handle.advance(1);
        Optional<StepOutcome> outcome = handle.awaitNextOutcome(Duration.ofSeconds(5));
        assertThat(outcome).isPresent();
        assertThat(outcome.get().step().id()).isIn("A", "B");
        handle.cancel();
    }

    @Test
    @DisplayName("awaitNextOutcome returns empty after session completes")
    void awaitNextOutcomeEmptyAfterComplete() {
        AtomicStep a = step("A");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a)));

        handle.advance(1);
        handle.awaitNextOutcome(Duration.ofSeconds(5)); // consume A's outcome

        // Session is complete, next await should return empty
        Optional<StepOutcome> outcome = handle.awaitNextOutcome(Duration.ofMillis(200));
        assertThat(outcome).isEmpty();
    }

    @Test
    @DisplayName("hasFailures reflects failed steps in live graph")
    void hasFailuresInLiveGraph() {
        AtomicStep a = step("A");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a)));

        assertThat(handle.liveGraph().hasFailures()).isFalse();

        handle.advance(1);
        handle.awaitNextOutcome(Duration.ofSeconds(5));

        assertThat(handle.liveGraph().hasFailures()).isTrue();
    }

    @Test
    @DisplayName("Throttled mode: advance(0) initially, then advance(1) repeatedly")
    void throttledMode() {
        AtomicStep a = step("A");
        AtomicStep b = step("B");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a, b)));

        // No permits initially — nothing should execute
        Optional<StepOutcome> nothing = handle.awaitNextOutcome(Duration.ofMillis(200));
        assertThat(nothing).isEmpty();
        assertThat(handle.isComplete()).isFalse();

        // Release one permit at a time
        handle.advance(1);
        Optional<StepOutcome> first = handle.awaitNextOutcome(Duration.ofSeconds(5));
        assertThat(first).isPresent();

        handle.advance(1);
        Optional<StepOutcome> second = handle.awaitNextOutcome(Duration.ofSeconds(5));
        assertThat(second).isPresent();

        assertThat(handle.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Unthrottled mode: all steps execute to completion")
    void unthrottledMode() throws InterruptedException {
        AtomicStep a = step("A");
        AtomicStep b = step("B");
        AtomicStep c = step("C");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(
            planWith(List.of(a, b, c)), Integer.MAX_VALUE);

        boolean completed = handle.awaitCompletion(Duration.ofSeconds(5));
        assertThat(completed).isTrue();
        assertThat(handle.isComplete()).isTrue();
    }

    @Test
    @DisplayName("awaitCompletion with timeout returns false when not complete")
    void awaitCompletionTimeout() throws InterruptedException {
        AtomicStep a = step("A");

        Executor executor = createExecutor();
        SteppingHandle handle = executor.executeStepping(planWith(List.of(a)));

        // No permits released — should time out
        boolean completed = handle.awaitCompletion(Duration.ofMillis(200));
        assertThat(completed).isFalse();
        handle.cancel();
    }

    /// Minimal stub ExecutionPlan for stepping executor tests.
    private static class StubExecutionPlan implements ExecutionPlan {
        private final List<AtomicStep> steps;
        private final ExecutionGraph graph;

        StubExecutionPlan(List<AtomicStep> steps) {
            this.steps = List.copyOf(steps);
            this.graph = new DefaultExecutionGraph(steps);
        }

        @Override public String id() { return "test-plan"; }
        @Override public String testPlanFingerprint() { return "test-fp"; }
        @Override public List<AtomicStep> steps() { return steps; }
        @Override public List<String> trialElements() { return List.of(); }
        @Override public List<Barrier> barriers() { return List.of(); }
        @Override public ExecutionGraph executionGraph() { return graph; }
        @Override public TrialOrdering trialOrdering() { throw new UnsupportedOperationException(); }
        @Override public Optional<Duration> estimatedDuration() { return Optional.empty(); }
        @Override public int estimatedMaxParallelism() { return 1; }
        @Override public ResourceRequirements resourceRequirements() {
            return new ResourceRequirements(0, 0, 0, 0, Map.of());
        }
        @Override public Optional<CheckpointStrategy> checkpointStrategy() { return Optional.empty(); }
        @Override public Optional<Checkpoint> latestCheckpoint() { return Optional.empty(); }
        @Override public List<Checkpoint> checkpoints() { return List.of(); }
        @Override public ExecutionResults execute() { throw new UnsupportedOperationException(); }
        @Override public ExecutionResults execute(ExecutionObserver o) { throw new UnsupportedOperationException(); }
        @Override public ExecutionResults executeWithCheckpoints(Duration d) { throw new UnsupportedOperationException(); }
        @Override public ExecutionPlan resumeFrom(Checkpoint c) { throw new UnsupportedOperationException(); }
        @Override public ExecutionPlan withMaxConcurrency(int m) { throw new UnsupportedOperationException(); }
        @Override public ElementInstanceGraph elementInstanceGraph() { throw new UnsupportedOperationException(); }
        @Override public ExecutionPlanMetadata metadata() { throw new UnsupportedOperationException(); }
    }
}
