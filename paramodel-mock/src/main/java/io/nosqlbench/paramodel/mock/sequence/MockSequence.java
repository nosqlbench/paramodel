package io.nosqlbench.paramodel.mock.sequence;

import io.nosqlbench.paramodel.core.ValidationResult;
import io.nosqlbench.paramodel.core.metadata.SequenceMetadata;
import io.nosqlbench.paramodel.mock.core.MockValidationResult;
import io.nosqlbench.paramodel.sequence.Sequence;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

/**
 * Simple sequence implementation.
 */
public class MockSequence implements Sequence {
    private final List<Trial> trials;
    private final SequenceMetadata metadata;

    public MockSequence(List<Trial> trials) {
        this(trials, new MockSequenceMetadata());
    }

    public MockSequence(List<Trial> trials, SequenceMetadata metadata) {
        this.trials = new ArrayList<>(trials);
        this.metadata = metadata;
    }

    @Override
    public List<Trial> trials() {
        return Collections.unmodifiableList(trials);
    }

    @Override
    public int size() {
        return trials.size();
    }

    @Override
    public boolean isEmpty() {
        return trials.isEmpty();
    }

    @Override
    public ValidationResult validate() {
        for (Trial trial : trials) {
            ValidationResult result = trial.validate();
            if (result.isFailed()) {
                return result;
            }
        }
        return MockValidationResult.passed();
    }

    @Override
    public SequenceMetadata metadata() {
        return metadata;
    }

    @Override
    public Iterator<Trial> iterator() {
        return trials.iterator();
    }

    public static MockSequence of(Trial... trials) {
        return new MockSequence(Arrays.asList(trials));
    }

    public static MockSequence of(List<Trial> trials) {
        return new MockSequence(trials);
    }

    private static class MockSequenceMetadata implements SequenceMetadata {
        @Override
        public java.time.Instant generatedAt() {
            return java.time.Instant.now();
        }

        @Override
        public Optional<String> generatedBy() {
            return Optional.of("mock-sequence");
        }

        @Override
        public String orderingStrategy() {
            return "user-defined";
        }

        @Override
        public int totalTrials() {
            return 0;
        }

        @Override
        public Optional<java.time.Duration> estimatedDuration() {
            return Optional.empty();
        }

        @Override
        public Map<String, String> tags() {
            return Map.of();
        }

        @Override
        public ValidationStatus validationStatus() {
            return ValidationStatus.NOT_VALIDATED;
        }
    }
}
