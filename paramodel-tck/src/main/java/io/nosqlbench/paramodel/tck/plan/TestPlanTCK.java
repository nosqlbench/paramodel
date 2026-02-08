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
    public void testTestPlanHasParameters() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.parameters()).isNotNull();
    }

    @Test
    public void testTestPlanBuilderAddsParameters() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("a", "b"));
        Parameter<String> param = getProvider().createParameter("param1", domain);

        TestPlan plan = getProvider().createTestPlanBuilder()
            .parameter(param)
            .build();

        assertThat(plan.parameters()).containsKey("param1");
        assertThat(plan.parameters().get("param1")).isEqualTo(param);
    }

    @Test
    public void testTestPlanHasAxes() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.axes()).isNotNull();
    }

    @Test
    public void testTestPlanBuilderAddsAxes() {
        Element element = getProvider().createElement("param1");
        Axis axis = getProvider().createAxis("axis1", List.of(element));

        TestPlan plan = getProvider().createTestPlanBuilder()
            .axis(axis)
            .build();

        assertThat(plan.axes()).hasSize(1);
        assertThat(plan.axes().get(0).name()).isEqualTo("axis1");
    }

    @Test
    public void testTestPlanHasConstraints() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.constraints()).isNotNull();
    }

    @Test
    public void testTestPlanHasOptimizationStrategy() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.optimizationStrategy()).isNotNull();
    }

    @Test
    public void testTestPlanValidation() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3));
        Parameter<Integer> param = getProvider().createParameter("numbers", domain);

        TestPlan plan = getProvider().createTestPlanBuilder()
            .parameter(param)
            .build();

        assertThat(plan.validate().isValid()).isTrue();
    }

    @Test
    public void testTestPlanCommitCreatesExecutionPlan() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("x", "y"));
        Parameter<String> param = getProvider().createParameter("letters", domain);

        TestPlan plan = getProvider().createTestPlanBuilder()
            .parameter(param)
            .build();

        ExecutionPlan execPlan = plan.commit();

        assertThat(execPlan).isNotNull();
        assertThat(execPlan.testPlan()).isEqualTo(plan);
    }

    @Test
    public void testTestPlanIsNotCommittedInitially() {
        TestPlan plan = getProvider().createTestPlan();

        assertThat(plan.isCommitted()).isFalse();
    }

    @Test
    public void testTestPlanIsCommittedAfterCommit() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("a"));
        Parameter<String> param = getProvider().createParameter("p", domain);

        TestPlan plan = getProvider().createTestPlanBuilder()
            .parameter(param)
            .build();

        plan.commit();

        assertThat(plan.isCommitted()).isTrue();
    }

    @Test
    public void testTestPlanCannotCommitTwice() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("a"));
        Parameter<String> param = getProvider().createParameter("p", domain);

        TestPlan plan = getProvider().createTestPlanBuilder()
            .parameter(param)
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
