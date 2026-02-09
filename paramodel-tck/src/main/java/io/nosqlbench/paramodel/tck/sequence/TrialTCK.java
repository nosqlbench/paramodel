package io.nosqlbench.paramodel.tck.sequence;

import io.nosqlbench.paramodel.parameters.Value;
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
 * - Track metadata
 * - Validate constraints
 */
public abstract class TrialTCK {
    protected TrialTCK() {}

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
    public void testTrialIdIsStable() {
        Trial trial = getProvider().createTrial("trial-1");

        assertThat(trial.id()).isNotNull();
        assertThat(trial.id()).isNotEmpty();
    }

    @Test
    public void testTrialIdConsistency() {
        Value<String> value = getProvider().createValue("test", "param");

        Trial trial1 = getProvider().createTrialBuilder()
            .id("trial-1")
            .assignment("param", value)
            .build();

        Trial trial2 = getProvider().createTrialBuilder()
            .id("trial-1")
            .assignment("param", value)
            .build();

        // Same id should produce same id
        assertThat(trial1.id()).isEqualTo(trial2.id());
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

        assertThat(trial.validate().isPassed()).isTrue();
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

        assertThat(trial.validate().isPassed()).isTrue();
    }
}
