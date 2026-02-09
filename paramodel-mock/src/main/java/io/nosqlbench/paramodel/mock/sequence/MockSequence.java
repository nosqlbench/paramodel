package io.nosqlbench.paramodel.mock.sequence;

import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.mock.parameters.MockValidationResult;
import io.nosqlbench.paramodel.sequence.Sequence;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

/**
 * Simple sequence implementation.
 */
public class MockSequence implements Sequence {
    private final List<Trial> trials;

    public MockSequence(List<Trial> trials) {
        this.trials = new ArrayList<>(trials);
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
    public Iterator<Trial> iterator() {
        return trials.iterator();
    }

    public static MockSequence of(Trial... trials) {
        return new MockSequence(Arrays.asList(trials));
    }

    public static MockSequence of(List<Trial> trials) {
        return new MockSequence(trials);
    }
}
