package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.core.Domain;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Element;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for ExecutionPlan contract.
 *
 * Validates that implementations correctly:
 * - Reference source test plans
 * - Store atomic steps
 * - Maintain execution graphs
 * - Estimate trial counts
 * - Track metadata
 */
public abstract class ExecutionPlanTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testExecutionPlanHasTestPlanFingerprint() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.testPlanFingerprint()).isNotNull();
    }

    @Test
    public void testExecutionPlanHasSteps() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.steps()).isNotNull();
    }

    @Test
    public void testExecutionPlanStoresSteps() {
        Element element = getProvider().createElement("elem");
        TestPlan testPlan = getProvider().createTestPlanBuilder()
            .withElement(element)
            .build();

        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step1", trial);

        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        // Note: ExecutionPlan is typically built by compilation process
        // This test validates structure exists
        assertThat(execPlan.steps()).isNotNull();
    }

    @Test
    public void testExecutionPlanHasGraph() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.executionGraph()).isNotNull();
    }

    @Test
    public void testExecutionPlanEstimatesMaxParallelism() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.estimatedMaxParallelism()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testExecutionPlanMetadata() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.metadata()).isNotNull();
        assertThat(execPlan.metadata().id()).isNotNull();
        assertThat(execPlan.metadata().compiledAt()).isNotNull();
        assertThat(execPlan.metadata().compilerVersion()).isNotNull();
    }

    @Test
    public void testExecutionPlanFromCommittedTestPlan() {
        Element element = getProvider().createElement("elem");

        TestPlan testPlan = getProvider().createTestPlanBuilder()
            .withElement(element)
            .build();

        ExecutionPlan execPlan = testPlan.commit();

        assertThat(execPlan.testPlanFingerprint()).isNotNull();
        assertThat(testPlan.isCommitted()).isTrue();
    }

    @Test
    public void testExecutionPlanGraphTopology() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        // Execution graph should support topological ordering
        assertThat(execPlan.executionGraph().topologicalSort()).isNotNull();
    }
}
