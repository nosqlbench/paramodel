package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for AtomicStep contract.
 *
 * Validates that implementations correctly:
 * - Store trial references
 * - Provide unique identifiers
 * - Track execution context
 * - Support metadata
 */
public abstract class AtomicStepTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testAtomicStepHasId() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        assertThat(step.id()).isEqualTo("step-1");
    }

    @Test
    public void testAtomicStepHasTrial() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        assertThat(step.trial()).isEqualTo(trial);
    }

    @Test
    public void testAtomicStepHasExecutionContext() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        assertThat(step.executionContext()).isNotNull();
    }

    @Test
    public void testAtomicStepExecutionContextIsMap() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        // Execution context should be accessible as a map
        assertThat(step.executionContext()).isInstanceOf(java.util.Map.class);
    }

    @Test
    public void testAtomicStepWithDifferentIds() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step1 = getProvider().createAtomicStep("step-1", trial);
        AtomicStep step2 = getProvider().createAtomicStep("step-2", trial);

        assertThat(step1.id()).isNotEqualTo(step2.id());
    }

    @Test
    public void testAtomicStepWithSameTrial() {
        Trial trial = getProvider().createTrial("shared-trial");
        AtomicStep step1 = getProvider().createAtomicStep("step-1", trial);
        AtomicStep step2 = getProvider().createAtomicStep("step-2", trial);

        assertThat(step1.trial()).isEqualTo(step2.trial());
    }

    @Test
    public void testAtomicStepImmutability() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        // Execution context should be unmodifiable or defensive copy
        assertThatThrownBy(() -> step.executionContext().put("key", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
