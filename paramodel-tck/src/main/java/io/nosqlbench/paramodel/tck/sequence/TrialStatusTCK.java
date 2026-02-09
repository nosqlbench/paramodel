package io.nosqlbench.paramodel.tck.sequence;

import io.nosqlbench.paramodel.sequence.TrialStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for TrialStatus enum behavior.
///
/// Validates that the enum's behavior methods correctly identify terminal
/// states, success conditions, and failure conditions.
///
/// @see TrialStatus
/// @since 0.1.0
///
public abstract class TrialStatusTCK {
    protected TrialStatusTCK() {}

    @Test
    public void testCompletedIsTerminal() {
        assertThat(TrialStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    public void testPendingIsNotTerminal() {
        assertThat(TrialStatus.PENDING.isTerminal()).isFalse();
        assertThat(TrialStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    @Test
    public void testCompletedIsSuccess() {
        assertThat(TrialStatus.COMPLETED.isSuccess()).isTrue();
        assertThat(TrialStatus.FAILED.isSuccess()).isFalse();
        assertThat(TrialStatus.SKIPPED.isSuccess()).isFalse();
    }

    @Test
    public void testFailedIsFailure() {
        assertThat(TrialStatus.FAILED.isFailure()).isTrue();
        assertThat(TrialStatus.COMPLETED.isFailure()).isFalse();
        assertThat(TrialStatus.SKIPPED.isFailure()).isFalse();
    }

    @Test
    public void testSkippedIsTerminal() {
        assertThat(TrialStatus.SKIPPED.isTerminal()).isTrue();
    }

    @Test
    public void testCancelledIsTerminal() {
        assertThat(TrialStatus.CANCELLED.isTerminal()).isTrue();
    }
}
