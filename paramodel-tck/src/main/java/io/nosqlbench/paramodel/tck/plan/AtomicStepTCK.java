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
 * - Provide unique identifiers
 * - Have a step type
 * - Support descriptions
 * - Track dependencies
 * - Provide metadata
 */
public abstract class AtomicStepTCK {
    protected AtomicStepTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testAtomicStepHasId() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        assertThat(step.id()).isEqualTo("step-1");
    }

    @Test
    public void testAtomicStepHasType() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        assertThat(step.type()).isNotNull();
    }

    @Test
    public void testAtomicStepHasDescription() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        assertThat(step.description()).isNotNull();
    }

    @Test
    public void testAtomicStepHasMetadata() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        assertThat(step.metadata()).isNotNull();
        assertThat(step.metadata()).isInstanceOf(java.util.Map.class);
    }

    @Test
    public void testAtomicStepWithDifferentIds() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step1 = getProvider().createAtomicStep("step-1", trial);
        AtomicStep step2 = getProvider().createAtomicStep("step-2", trial);

        assertThat(step1.id()).isNotEqualTo(step2.id());
    }

    @Test
    public void testAtomicStepHasDependencies() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        assertThat(step.dependencies()).isNotNull();
    }

    @Test
    public void testAtomicStepMetadataImmutability() {
        Trial trial = getProvider().createTrial("t1");
        AtomicStep step = getProvider().createAtomicStep("step-1", trial);

        // Metadata should be unmodifiable
        assertThatThrownBy(() -> step.metadata().put("key", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
