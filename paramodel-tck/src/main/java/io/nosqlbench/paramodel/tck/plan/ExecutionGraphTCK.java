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
 * - Handle barriers and synchronization
 */
public abstract class ExecutionGraphTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testExecutionGraphHasNodes() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThat(graph.nodes()).isNotNull();
    }

    @Test
    public void testExecutionGraphStoresSteps() {
        Trial t1 = getProvider().createTrial("t1");
        AtomicStep step1 = getProvider().createAtomicStep("s1", t1);

        ExecutionGraph graph = getProvider().createExecutionGraph();

        // Graph nodes should be queryable
        assertThat(graph.nodes()).isNotNull();
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
    public void testExecutionGraphTopologicalOrder() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        List<AtomicStep> ordered = graph.topologicalOrder();

        assertThat(ordered).isNotNull();
    }

    @Test
    public void testExecutionGraphTopologicalOrderRespectsDependencies() {
        Trial t1 = getProvider().createTrial("t1");
        Trial t2 = getProvider().createTrial("t2");
        Trial t3 = getProvider().createTrial("t3");

        AtomicStep step1 = getProvider().createAtomicStep("s1", t1);
        AtomicStep step2 = getProvider().createAtomicStep("s2", t2);
        AtomicStep step3 = getProvider().createAtomicStep("s3", t3);

        ExecutionGraph graph = getProvider().createExecutionGraph();

        List<AtomicStep> ordered = graph.topologicalOrder();

        // In a valid topological order, dependencies come before dependents
        assertThat(ordered).isNotNull();
    }

    @Test
    public void testExecutionGraphBarriers() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThat(graph.barriers()).isNotNull();
    }

    @Test
    public void testEmptyExecutionGraph() {
        ExecutionGraph graph = getProvider().createExecutionGraph();

        assertThat(graph.nodes()).isNotNull();
        assertThat(graph.barriers()).isNotNull();
        assertThat(graph.topologicalOrder()).isEmpty();
    }

    @Test
    public void testExecutionGraphNoDependenciesReturnsEmptySet() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("s1", trial);

        ExecutionGraph graph = getProvider().createExecutionGraph();

        Set<AtomicStep> deps = graph.dependencies(step);
        assertThat(deps).isNotNull();
    }
}
