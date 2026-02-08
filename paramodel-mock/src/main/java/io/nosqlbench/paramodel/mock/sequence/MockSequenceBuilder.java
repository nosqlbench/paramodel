package io.nosqlbench.paramodel.mock.sequence;

import io.nosqlbench.paramodel.core.metadata.SequenceMetadata;
import io.nosqlbench.paramodel.sequence.Sequence;
import io.nosqlbench.paramodel.sequence.SequenceBuilder;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple builder for constructing sequences.
 */
public class MockSequenceBuilder implements SequenceBuilder {
    private final List<Trial> trials = new ArrayList<>();
    private SequenceMetadata metadata;

    @Override
    public SequenceBuilder addTrial(Trial trial) {
        this.trials.add(trial);
        return this;
    }

    @Override
    public SequenceBuilder addTrials(Iterable<Trial> trials) {
        trials.forEach(this.trials::add);
        return this;
    }

    @Override
    public SequenceBuilder metadata(SequenceMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    @Override
    public Sequence build() {
        if (metadata == null) {
            return new MockSequence(trials);
        }
        return new MockSequence(trials, metadata);
    }

    @Override
    public SequenceBuilder clear() {
        trials.clear();
        metadata = null;
        return this;
    }

    public static MockSequenceBuilder create() {
        return new MockSequenceBuilder();
    }
}
