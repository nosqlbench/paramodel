package io.nosqlbench.paramodel.tck.sequence;

import io.nosqlbench.paramodel.sequence.Sequence;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for Sequence contract.
 *
 * Validates that implementations correctly:
 * - Store ordered collections of trials
 * - Provide iteration capabilities
 * - Validate sequences
 * - Support filtering and transformation
 */
public abstract class SequenceTCK {
    protected SequenceTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testSequenceStoresTrials() {
        Trial trial1 = getProvider().createTrial("t1");
        Trial trial2 = getProvider().createTrial("t2");

        Sequence sequence = getProvider().createSequence(List.of(trial1, trial2));

        assertThat(sequence.trials()).hasSize(2);
        assertThat(sequence.trials()).containsExactly(trial1, trial2);
    }

    @Test
    public void testSequencePreservesOrder() {
        Trial t1 = getProvider().createTrial("first");
        Trial t2 = getProvider().createTrial("second");
        Trial t3 = getProvider().createTrial("third");

        Sequence sequence = getProvider().createSequence(List.of(t1, t2, t3));

        List<Trial> trials = sequence.trials();
        assertThat(trials.get(0).id()).isEqualTo("first");
        assertThat(trials.get(1).id()).isEqualTo("second");
        assertThat(trials.get(2).id()).isEqualTo("third");
    }

    @Test
    public void testSequenceSize() {
        Trial t1 = getProvider().createTrial("t1");
        Trial t2 = getProvider().createTrial("t2");

        Sequence sequence = getProvider().createSequence(List.of(t1, t2));

        assertThat(sequence.size()).isEqualTo(2);
    }

    @Test
    public void testEmptySequence() {
        Sequence sequence = getProvider().createSequence(List.of());

        assertThat(sequence.trials()).isEmpty();
        assertThat(sequence.size()).isEqualTo(0);
    }

    @Test
    public void testSequenceValidation() {
        Trial trial = getProvider().createTrial("t1");
        Sequence sequence = getProvider().createSequence(List.of(trial));

        assertThat(sequence.validate()).isNotNull();
    }

    @Test
    public void testSequenceCreatedFromTrials() {
        Trial t1 = getProvider().createTrial("t1");
        Trial t2 = getProvider().createTrial("t2");

        Sequence sequence = getProvider().createSequence(List.of(t1, t2));

        assertThat(sequence.trials()).hasSize(2);
        assertThat(sequence.trials()).containsExactly(t1, t2);
    }

    @Test
    public void testSequenceFromMultipleTrials() {
        Trial t1 = getProvider().createTrial("t1");
        Trial t2 = getProvider().createTrial("t2");
        Trial t3 = getProvider().createTrial("t3");

        Sequence sequence = getProvider().createSequence(List.of(t1, t2, t3));

        assertThat(sequence.trials()).hasSize(3);
    }

    @Test
    public void testSequenceImmutability() {
        Trial trial = getProvider().createTrial("t1");
        Sequence sequence = getProvider().createSequence(List.of(trial));

        List<Trial> trials = sequence.trials();

        // Attempting to modify should throw (or be ineffective for unmodifiable list)
        assertThatThrownBy(() -> trials.add(getProvider().createTrial("t2")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testSequenceWithIdenticalTrials() {
        Trial t1 = getProvider().createTrial("same-id");
        Trial t2 = getProvider().createTrial("same-id");

        Sequence sequence = getProvider().createSequence(List.of(t1, t2));

        // Should still store both even with same ID
        assertThat(sequence.size()).isEqualTo(2);
    }

    @Test
    public void testSequenceIsEmpty() {
        Sequence empty = getProvider().createSequence(List.of());
        assertThat(empty.isEmpty()).isTrue();

        Trial t1 = getProvider().createTrial("t1");
        Sequence nonEmpty = getProvider().createSequence(List.of(t1));
        assertThat(nonEmpty.isEmpty()).isFalse();
    }

    @Test
    public void testSequenceIterator() {
        Trial t1 = getProvider().createTrial("t1");
        Trial t2 = getProvider().createTrial("t2");
        Trial t3 = getProvider().createTrial("t3");

        Sequence sequence = getProvider().createSequence(List.of(t1, t2, t3));

        List<Trial> fromIterator = new ArrayList<>();
        Iterator<Trial> iter = sequence.iterator();
        while (iter.hasNext()) {
            fromIterator.add(iter.next());
        }

        assertThat(fromIterator).containsExactlyElementsOf(sequence.trials());
    }
}
