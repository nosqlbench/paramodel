package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class DependencyAnalysisStageTest {

    @Test
    @DisplayName("Topological sort respects dependencies")
    void topologicalSortRespectsDependencies() {
        // A -> B -> C
        AtomicStep a = deployStep("step_a", List.of());
        AtomicStep b = deployStep("step_b", List.of("step_a"));
        AtomicStep c = deployStep("step_c", List.of("step_b"));

        DefaultExecutionGraph graph = new DefaultExecutionGraph(List.of(a, b, c));

        List<AtomicStep> sorted = graph.topologicalSort();
        assertThat(sorted).hasSize(3);

        List<String> ids = sorted.stream().map(AtomicStep::id).toList();
        assertThat(ids.indexOf("step_a")).isLessThan(ids.indexOf("step_b"));
        assertThat(ids.indexOf("step_b")).isLessThan(ids.indexOf("step_c"));
    }

    @Test
    @DisplayName("Cycle detection identifies invalid graphs")
    void cycleDetection() {
        // A -> B -> C -> A (cycle!)
        // Note: we create steps that reference each other circularly
        AtomicStep a = deployStep("step_a", List.of("step_c"));
        AtomicStep b = deployStep("step_b", List.of("step_a"));
        AtomicStep c = deployStep("step_c", List.of("step_b"));

        DefaultExecutionGraph graph = new DefaultExecutionGraph(List.of(a, b, c));

        assertThat(graph.isAcyclic()).isFalse();
    }

    @Test
    @DisplayName("Acyclic graph is detected correctly")
    void acyclicGraphDetected() {
        AtomicStep a = deployStep("step_a", List.of());
        AtomicStep b = deployStep("step_b", List.of("step_a"));

        DefaultExecutionGraph graph = new DefaultExecutionGraph(List.of(a, b));

        assertThat(graph.isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Parallel waves group independent steps")
    void parallelWavesGroupIndependentSteps() {
        // Wave 0: A, B (no deps)
        // Wave 1: C (depends on A), D (depends on B)
        // Wave 2: E (depends on C and D)
        AtomicStep a = deployStep("a", List.of());
        AtomicStep b = deployStep("b", List.of());
        AtomicStep c = deployStep("c", List.of("a"));
        AtomicStep d = deployStep("d", List.of("b"));
        AtomicStep e = deployStep("e", List.of("c", "d"));

        DefaultExecutionGraph graph = new DefaultExecutionGraph(List.of(a, b, c, d, e));

        Map<Integer, List<AtomicStep>> waves = graph.parallelWaves();
        assertThat(waves).hasSize(3);
        assertThat(waves.get(0)).extracting(AtomicStep::id).containsExactlyInAnyOrder("a", "b");
        assertThat(waves.get(1)).extracting(AtomicStep::id).containsExactlyInAnyOrder("c", "d");
        assertThat(waves.get(2)).extracting(AtomicStep::id).containsExactly("e");
    }

    @Test
    @DisplayName("Maximum parallelism is size of largest wave")
    void maximumParallelismIsLargestWave() {
        AtomicStep a = deployStep("a", List.of());
        AtomicStep b = deployStep("b", List.of());
        AtomicStep c = deployStep("c", List.of());
        AtomicStep d = deployStep("d", List.of("a", "b", "c"));

        DefaultExecutionGraph graph = new DefaultExecutionGraph(List.of(a, b, c, d));

        assertThat(graph.maximumParallelism()).isEqualTo(3);
    }

    @Test
    @DisplayName("Statistics report correct counts")
    void statisticsReportCorrectCounts() {
        AtomicStep a = deployStep("a", List.of());
        AtomicStep b = deployStep("b", List.of("a"));
        AtomicStep c = deployStep("c", List.of("a"));

        DefaultExecutionGraph graph = new DefaultExecutionGraph(List.of(a, b, c));

        ExecutionGraph.GraphStatistics stats = graph.statistics();
        assertThat(stats.nodeCount()).isEqualTo(3);
        assertThat(stats.edgeCount()).isEqualTo(2); // a->b, a->c
        assertThat(stats.maxFanOut()).isEqualTo(2); // a fans out to b and c
    }

    @Test
    @DisplayName("Can execute concurrently identifies independent steps")
    void canExecuteConcurrentlyIdentifiesIndependentSteps() {
        AtomicStep a = deployStep("a", List.of());
        AtomicStep b = deployStep("b", List.of());
        AtomicStep c = deployStep("c", List.of("a"));

        DefaultExecutionGraph graph = new DefaultExecutionGraph(List.of(a, b, c));

        assertThat(graph.canExecuteConcurrently(a, b)).isTrue();
        assertThat(graph.canExecuteConcurrently(a, c)).isFalse(); // c depends on a
        assertThat(graph.canExecuteConcurrently(b, c)).isTrue();
    }

    private AtomicStep deployStep(String id, List<String> deps) {
        return new AtomicStep.DeployElement(
            id, "elem_" + id, 0, Map.of(), List.of(), deps,
            Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(), Map.of()
        );
    }
}
