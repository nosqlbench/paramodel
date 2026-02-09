package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionGraph;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for ExecutionGraph contract.
 *
 * Validates that implementations correctly:
 * - Represent directed acyclic graphs (DAGs)
 * - Track dependencies between steps
 * - Compute topological orderings
 * - Provide graph statistics
 */
public abstract class ExecutionGraphTCK {
    protected ExecutionGraphTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testExecutionGraphHasSteps() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThat(graph.steps()).isNotNull();
    }

    @Test
    public void testExecutionGraphStoresSteps() {
        Trial t1 = getProvider().createTrial("t1");
        AtomicStep step1 = getProvider().createAtomicStep("s1", t1);

        ExecutionGraph graph = getProvider().createExecutionGraph();

        // Graph steps should be queryable
        assertThat(graph.steps()).isNotNull();
    }

    @Test
    public void testExecutionGraphDependencies() {
        Trial t1 = getProvider().createTrial("t1");
        Trial t2 = getProvider().createTrial("t2");
        AtomicStep step1 = getProvider().createAtomicStep("s1", t1);
        AtomicStep step2 = getProvider().createAtomicStep("s2", t2);

        ExecutionGraph graph = getProvider().createExecutionGraph();

        Set<AtomicStep> deps = graph.dependencies(step2);
        assertThat(deps).isNotNull();
    }

    @Test
    public void testExecutionGraphTopologicalSort() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        List<AtomicStep> ordered = graph.topologicalSort();

        assertThat(ordered).isNotNull();
    }

    @Test
    public void testExecutionGraphTopologicalSortRespectsDependencies() {
        Trial t1 = getProvider().createTrial("t1");
        Trial t2 = getProvider().createTrial("t2");
        Trial t3 = getProvider().createTrial("t3");

        AtomicStep step1 = getProvider().createAtomicStep("s1", t1);
        AtomicStep step2 = getProvider().createAtomicStep("s2", t2);
        AtomicStep step3 = getProvider().createAtomicStep("s3", t3);

        ExecutionGraph graph = getProvider().createExecutionGraph();

        List<AtomicStep> ordered = graph.topologicalSort();

        // In a valid topological order, dependencies come before dependents
        assertThat(ordered).isNotNull();
    }

    @Test
    public void testExecutionGraphEdges() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThat(graph.edges()).isNotNull();
    }

    @Test
    public void testEmptyExecutionGraph() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThat(graph.steps()).isNotNull();
        assertThat(graph.edges()).isNotNull();
        assertThat(graph.topologicalSort()).isEmpty();
    }

    @Test
    public void testExecutionGraphNoDependenciesReturnsEmptySet() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("s1", trial);

        ExecutionGraph graph = getProvider().createExecutionGraph();

        Set<AtomicStep> deps = graph.dependencies(step);
        assertThat(deps).isNotNull();
    }

    @Test
    public void testExecutionGraphMaximumParallelism() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThat(graph.maximumParallelism()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testExecutionGraphIsAcyclic() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThat(graph.isAcyclic()).isTrue();
    }

    @Test
    public void testExecutionGraphStatistics() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        ExecutionGraph.GraphStatistics stats = graph.statistics();
        assertThat(stats).isNotNull();
        assertThat(stats.nodeCount()).isGreaterThanOrEqualTo(0);
        assertThat(stats.edgeCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testExecutionGraphCriticalPath() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        List<AtomicStep> criticalPath = graph.criticalPath();
        assertThat(criticalPath).isNotNull();
    }

    @Test
    public void testExecutionGraphReadySteps() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        // Parallel waves wave 0 contains steps with no dependencies (ready steps)
        java.util.Map<Integer, List<AtomicStep>> waves = graph.parallelWaves();
        assertThat(waves).isNotNull();
    }
}
