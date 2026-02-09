package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for Barrier contract.
///
/// Validates that implementations correctly:
/// - Have non-null id, type, and description
/// - Track dependencies and dependent steps
/// - Support timeout configuration
/// - Start in PENDING state
/// - Provide unmodifiable metadata
/// - Report isSatisfied as false initially
///
/// @see Barrier
/// @since 0.1.0
///
public abstract class BarrierTCK {
    protected BarrierTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testBarrierHasId() {
        Barrier barrier = getProvider().createBarrier("db-ready");

        assertThat(barrier.id()).isNotNull();
        assertThat(barrier.id()).isEqualTo("db-ready");
    }

    @Test
    public void testBarrierHasType() {
        Barrier barrier = getProvider().createBarrier("barrier-1");

        assertThat(barrier.type()).isNotNull();
    }

    @Test
    public void testBarrierHasDescription() {
        Barrier barrier = getProvider().createBarrier("barrier-2");

        assertThat(barrier.description()).isNotNull();
        assertThat(barrier.description()).isNotEmpty();
    }

    @Test
    public void testBarrierHasDependencies() {
        Barrier barrier = getProvider().createBarrier("barrier-3");

        assertThat(barrier.dependencies()).isNotNull();
    }

    @Test
    public void testBarrierHasDependentSteps() {
        Barrier barrier = getProvider().createBarrier("barrier-4");

        assertThat(barrier.dependentSteps()).isNotNull();
    }

    @Test
    public void testBarrierTimeout() {
        Barrier barrier = getProvider().createBarrier("barrier-5");

        // timeout() should return non-null Optional
        assertThat(barrier.timeout()).isNotNull();
    }

    @Test
    public void testBarrierTimeoutAction() {
        Barrier barrier = getProvider().createBarrier("barrier-6");

        assertThat(barrier.timeoutAction()).isNotNull();
    }

    @Test
    public void testBarrierState() {
        Barrier barrier = getProvider().createBarrier("barrier-7");

        assertThat(barrier.state()).isEqualTo(Barrier.BarrierState.PENDING);
    }

    @Test
    public void testBarrierMetadata() {
        Barrier barrier = getProvider().createBarrier("barrier-8");

        assertThat(barrier.metadata()).isNotNull();
        // Metadata should be unmodifiable
        assertThatThrownBy(() -> barrier.metadata().put("key", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testBarrierIsSatisfiedInitiallyFalse() {
        Barrier barrier = getProvider().createBarrier("barrier-9");

        assertThat(barrier.isSatisfied()).isFalse();
        assertThat(barrier.isFailed()).isFalse();
        assertThat(barrier.isTimedOut()).isFalse();
    }
}
