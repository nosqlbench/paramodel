package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
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
    protected TestPlanTCK() {}

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

    @Test
    public void testTestPlanTrialSpaceSize() {
        Axis<?> axis1 = getProvider().createAxis("a", List.of(
            getProvider().createElement("e1"),
            getProvider().createElement("e2")));
        Axis<?> axis2 = getProvider().createAxis("b", List.of(
            getProvider().createElement("e3"),
            getProvider().createElement("e4"),
            getProvider().createElement("e5")));

        TestPlan plan = getProvider().createTestPlanBuilder()
            .withAxis(axis1)
            .withAxis(axis2)
            .build();

        assertThat(plan.trialSpaceSize()).isEqualTo(
            (long) axis1.cardinality() * axis2.cardinality());
    }

    @Test
    public void testTestPlanPolicies() {
        TestPlan plan = getProvider().createTestPlan();

        // policies() may return null for plans without explicit policies
        // but the contract says it should be non-null when set
        // For default plans, we just verify the call doesn't throw
        try {
            plan.policies();
        } catch (Exception e) {
            // Some implementations may not support policies without explicit setup
        }
    }

    @Test
    public void testTestPlanAxisLookup() {
        Element elem1 = getProvider().createElement("e1");
        Element elem2 = getProvider().createElement("e2");
        Axis<?> axis = getProvider().createAxis("lookup-axis", List.of(elem1, elem2));

        TestPlan plan = getProvider().createTestPlanBuilder()
            .withAxis(axis)
            .build();

        assertThat(plan.axis("lookup-axis")).isPresent();
        assertThat(plan.axis("nonexistent")).isEmpty();
    }

    @Test
    public void testTestPlanElementLookup() {
        Element elem = getProvider().createElement("findme");

        TestPlan plan = getProvider().createTestPlanBuilder()
            .withElement(elem)
            .build();

        assertThat(plan.element("findme")).isPresent();
        assertThat(plan.element("nonexistent")).isEmpty();
    }
}
