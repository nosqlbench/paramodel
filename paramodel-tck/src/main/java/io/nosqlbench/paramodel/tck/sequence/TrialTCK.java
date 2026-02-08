package io.nosqlbench.paramodel.tck.sequence;

import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for Trial contract.
 *
 * Validates that implementations correctly:
 * - Store parameter assignments
 * - Provide unique identifiers
 * - Compute fingerprints
 * - Track metadata
 */
public abstract class TrialTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testTrialHasId() {
        Trial trial = getProvider().createTrial("trial-123");

        assertThat(trial.id()).isEqualTo("trial-123");
    }

    @Test
    public void testTrialHasAssignments() {
        Trial trial = getProvider().createTrialBuilder()
            .id("trial-1")
            .build();

        assertThat(trial.assignments()).isNotNull();
    }

    @Test
    public void testTrialStoresAssignments() {
        Value<String> opValue = getProvider().createValue("read", "operation");
        Value<Integer> threadValue = getProvider().createValue(16, "threads");

        Trial trial = getProvider().createTrialBuilder()
            .id("trial-1")
            .assignment("operation", opValue)
            .assignment("threads", threadValue)
            .build();

        assertThat(trial.assignments()).hasSize(2);
        assertThat(trial.assignments()).containsKeys("operation", "threads");
        assertThat(trial.assignments().get("operation").value()).isEqualTo("read");
        assertThat(trial.assignments().get("threads").value()).isEqualTo(16);
    }

    @Test
    public void testTrialHasFingerprint() {
        Trial trial = getProvider().createTrial("trial-1");

        assertThat(trial.fingerprint()).isNotNull();
        assertThat(trial.fingerprint()).isNotEmpty();
    }

    @Test
    public void testTrialFingerprintStability() {
        Value<String> value = getProvider().createValue("test", "param");

        Trial trial1 = getProvider().createTrialBuilder()
            .id("trial-1")
            .assignment("param", value)
            .build();

        Trial trial2 = getProvider().createTrialBuilder()
            .id("trial-1")
            .assignment("param", value)
            .build();

        // Same assignments should produce consistent fingerprints
        assertThat(trial1.fingerprint()).isEqualTo(trial2.fingerprint());
    }

    @Test
    public void testTrialValidationWithConstraints() {
        Value<Integer> threads = getProvider().createValue(4, "threads");

        Trial trial = getProvider().createTrialBuilder()
            .id("trial-1")
            .assignment("threads", threads)
            .constraint(assignment -> {
                Integer t = (Integer) assignment.get("threads").value();
                return t > 0;
            })
            .build();

        assertThat(trial.validate().isValid()).isTrue();
    }

    @Test
    public void testTrialMetadata() {
        Trial trial = getProvider().createTrial("trial-1");

        assertThat(trial.metadata()).isNotNull();
    }

    @Test
    public void testEmptyTrialIsValid() {
        Trial trial = getProvider().createTrialBuilder()
            .id("empty-trial")
            .build();

        assertThat(trial.assignments()).isEmpty();
        assertThat(trial.id()).isEqualTo("empty-trial");
    }

    @Test
    public void testTrialWithMultipleConstraints() {
        Value<Integer> threads = getProvider().createValue(8, "threads");

        Trial trial = getProvider().createTrialBuilder()
            .id("trial-1")
            .assignment("threads", threads)
            .constraint(a -> ((Integer) a.get("threads").value()) > 0)
            .constraint(a -> ((Integer) a.get("threads").value()) <= 64)
            .build();

        assertThat(trial.validate().isValid()).isTrue();
    }
}
