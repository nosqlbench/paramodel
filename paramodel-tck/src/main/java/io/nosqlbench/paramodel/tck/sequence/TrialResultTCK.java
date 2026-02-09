package io.nosqlbench.paramodel.tck.sequence;

import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.sequence.TrialResult;
import io.nosqlbench.paramodel.sequence.TrialStatus;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for TrialResult contract.
///
/// Validates that implementations correctly:
/// - Link back to the executed trial
/// - Report status
/// - Provide metrics, artifacts, timing, and provenance
/// - Distinguish success from failure results
///
/// @see TrialResult
/// @since 0.1.0
///
public abstract class TrialResultTCK {
    protected TrialResultTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testTrialResultHasTrial() {
        Trial trial = getProvider().createTrial("t1");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);

        assertThat(result.trial()).isNotNull();
        assertThat(result.trial().id()).isEqualTo("t1");
    }

    @Test
    public void testTrialResultHasStatus() {
        Trial trial = getProvider().createTrial("t2");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);

        assertThat(result.status()).isNotNull();
        assertThat(result.status()).isEqualTo(TrialStatus.COMPLETED);
    }

    @Test
    public void testTrialResultHasMetrics() {
        Trial trial = getProvider().createTrial("t3");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);

        assertThat(result.metrics()).isNotNull();
    }

    @Test
    public void testTrialResultMetricLookup() {
        Trial trial = getProvider().createTrial("t4");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);

        // metric() for a nonexistent key should return empty
        assertThat(result.metric("nonexistent")).isEmpty();
    }

    @Test
    public void testTrialResultHasArtifacts() {
        Trial trial = getProvider().createTrial("t5");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);

        assertThat(result.artifacts()).isNotNull();
    }

    @Test
    public void testTrialResultHasTiming() {
        Trial trial = getProvider().createTrial("t6");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);

        assertThat(result.timing()).isNotNull();
        assertThat(result.timing().startedAt()).isNotNull();
        assertThat(result.timing().completedAt()).isNotNull();
    }

    @Test
    public void testTrialResultHasProvenance() {
        Trial trial = getProvider().createTrial("t7");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);

        assertThat(result.provenance()).isNotNull();
        assertThat(result.provenance().configurationFingerprint()).isNotNull();
        assertThat(result.provenance().configurationFingerprint()).isNotEmpty();
    }

    @Test
    public void testTrialResultSuccessHasNoError() {
        Trial trial = getProvider().createTrial("t8");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);

        assertThat(result.error()).isEmpty();
    }

    @Test
    public void testTrialResultFailureHasError() {
        Trial trial = getProvider().createTrial("t9");
        TrialResult result = getProvider().createFailedTrialResult(trial, "Connection refused");

        assertThat(result.status()).isEqualTo(TrialStatus.FAILED);
        assertThat(result.error()).isPresent();
        assertThat(result.error().get().message()).isEqualTo("Connection refused");
    }
}
