package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.core.Domain;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for TestPlan contract.
 *
 * Validates that implementations correctly:
 * - Define parameter spaces
 * - Organize axes and elements
 * - Apply constraints
 * - Commit to execution plans
 * - Validate configurations
 */
public abstract class TestPlanTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testTestPlanHasName() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.name()).isNotNull();
    }

    @Test
    public void testTestPlanHasAxes() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.axes()).isNotNull();
    }

    @Test
    public void testTestPlanBuilderAddsElements() {
        Element element = getProvider().createElement("elem1");

        TestPlan plan = getProvider().createTestPlanBuilder()
            .withElement(element)
            .build();

        assertThat(plan.elements()).hasSize(1);
        assertThat(plan.elements().get(0).name()).isEqualTo("elem1");
    }

    @Test
    public void testTestPlanHasElements() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.elements()).isNotNull();
    }

    @Test
    public void testTestPlanHasRelationships() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.relationships()).isNotNull();
    }

    @Test
    public void testTestPlanHasOptimizationStrategy() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.optimizationStrategy()).isNotNull();
    }

    @Test
    public void testTestPlanValidation() {
        Element element = getProvider().createElement("elem");

        TestPlan plan = getProvider().createTestPlanBuilder()
            .withElement(element)
            .build();

        assertThat(plan.validate().isPassed()).isTrue();
    }

    @Test
    public void testTestPlanCommitCreatesExecutionPlan() {
        Element element = getProvider().createElement("elem");

        TestPlan plan = getProvider().createTestPlanBuilder()
            .withElement(element)
            .build();

        ExecutionPlan execPlan = plan.commit();

        assertThat(execPlan).isNotNull();
    }

    @Test
    public void testTestPlanIsNotCommittedInitially() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.isCommitted()).isFalse();
    }

    @Test
    public void testTestPlanIsCommittedAfterCommit() {
        Element element = getProvider().createElement("elem");

        TestPlan plan = getProvider().createTestPlanBuilder()
            .withElement(element)
            .build();

        plan.commit();

        assertThat(plan.isCommitted()).isTrue();
    }

    @Test
    public void testTestPlanCannotCommitTwice() {
        Element element = getProvider().createElement("elem");

        TestPlan plan = getProvider().createTestPlanBuilder()
            .withElement(element)
            .build();

        plan.commit();

        assertThatThrownBy(() -> plan.commit())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testTestPlanMetadata() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.metadata()).isNotNull();
    }
}
