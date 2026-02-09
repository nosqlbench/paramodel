package io.nosqlbench.paramodel.mock.sequence;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.sequence.Sequence;
import io.nosqlbench.paramodel.sequence.SequenceBuilder;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple builder for constructing sequences.
 *
 * This mock implementation collects parameters and constraints but delegates
 * actual trial generation to simple strategies. For testing purposes, trials
 * can be added directly via {@link #addTrial(Trial)}.
 */
public class MockSequenceBuilder implements SequenceBuilder {
    public MockSequenceBuilder() {}

    private final List<Parameter<?>> parameters = new ArrayList<>();
    private final List<Constraint<Map<String, Value<?>>>> constraints = new ArrayList<>();
    private final List<Trial> trials = new ArrayList<>();
    private String strategy = "manual";

    @Override
    public SequenceBuilder withParameter(Parameter<?> parameter) {
        this.parameters.add(parameter);
        return this;
    }

    @Override
    public SequenceBuilder withParameters(Parameter<?>... parameters) {
        for (Parameter<?> p : parameters) {
            this.parameters.add(p);
        }
        return this;
    }

    @Override
    public SequenceBuilder constraint(Constraint<Map<String, Value<?>>> constraint) {
        this.constraints.add(constraint);
        return this;
    }

    @Override
    public SequenceBuilder generateExhaustive() {
        this.strategy = "exhaustive";
        return this;
    }

    @Override
    public SequenceBuilder generateRandom(int count) {
        this.strategy = "random(" + count + ")";
        return this;
    }

    @Override
    public SequenceBuilder generateFromSeed(long seed) {
        this.strategy = "seeded(" + seed + ")";
        return this;
    }

    @Override
    public SequenceBuilder generateEdgeFirst() {
        this.strategy = "edge-first";
        return this;
    }

    @Override
    public SequenceBuilder generatePairwise() {
        this.strategy = "pairwise";
        return this;
    }

    @Override
    public SequenceBuilder generateBoundary() {
        this.strategy = "boundary";
        return this;
    }

    @Override
    public Sequence build() {
        return new MockSequence(trials);
    }

    /// Adds a pre-built trial directly (for testing convenience).
    public MockSequenceBuilder addTrial(Trial trial) {
        this.trials.add(trial);
        return this;
    }

    /// Adds multiple pre-built trials (for testing convenience).
    public MockSequenceBuilder addTrials(Iterable<Trial> trials) {
        trials.forEach(this.trials::add);
        return this;
    }

    public static MockSequenceBuilder create() {
        return new MockSequenceBuilder();
    }
}
