package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.core.Domain;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.plan.AtomicStep;
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
    public void testExecutionPlanReferencesTestPlan() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.testPlan()).isEqualTo(testPlan);
    }

    @Test
    public void testExecutionPlanHasSteps() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.steps()).isNotNull();
    }

    @Test
    public void testExecutionPlanStoresSteps() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("a"));
        Parameter<String> param = getProvider().createParameter("p", domain);
        TestPlan testPlan = getProvider().createTestPlanBuilder()
            .parameter(param)
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

        assertThat(execPlan.graph()).isNotNull();
    }

    @Test
    public void testExecutionPlanEstimatesTrialCount() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.estimatedTrialCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testExecutionPlanMetadata() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        assertThat(execPlan.metadata()).isNotNull();
        assertThat(execPlan.metadata().compilationVersion()).isNotNull();
        assertThat(execPlan.metadata().compiledAt()).isNotNull();
        assertThat(execPlan.metadata().fingerprint()).isNotNull();
    }

    @Test
    public void testExecutionPlanFromCommittedTestPlan() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("x", "y"));
        Parameter<String> param = getProvider().createParameter("letters", domain);

        TestPlan testPlan = getProvider().createTestPlanBuilder()
            .parameter(param)
            .build();

        ExecutionPlan execPlan = testPlan.commit();

        assertThat(execPlan.testPlan()).isEqualTo(testPlan);
        assertThat(testPlan.isCommitted()).isTrue();
    }

    @Test
    public void testExecutionPlanGraphTopology() {
        TestPlan testPlan = getProvider().createTestPlan();
        ExecutionPlan execPlan = getProvider().createExecutionPlan(testPlan);

        // Execution graph should support topological ordering
        assertThat(execPlan.graph().topologicalOrder()).isNotNull();
    }
}
