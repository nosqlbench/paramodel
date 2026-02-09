package io.nosqlbench.paramodel.tck.persistence;

import io.nosqlbench.paramodel.persistence.ResultStore;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.sequence.TrialResult;
import io.nosqlbench.paramodel.sequence.TrialStatus;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link ResultStore} implementations.
///
/// Validates save, get, query, count, stream, and delete operations
/// for trial result persistence.
///
/// @since 0.1.0
///
public abstract class ResultStoreTCK {

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private ResultStore store;

    @BeforeEach
    void setUp() {
        store = getProvider().createResultStore();
    }

    @Test
    void testSaveAndGetResult() {
        Trial trial = getProvider().createTrial("trial-1");
        TrialResult result = getProvider().createTrialResult(trial, TrialStatus.COMPLETED);
        store.save(result);

        Optional<TrialResult> retrieved = store.get("trial-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().trial().id()).isEqualTo("trial-1");
        assertThat(retrieved.get().status()).isEqualTo(TrialStatus.COMPLETED);
    }

    @Test
    void testQueryByStatus() {
        Trial trial1 = getProvider().createTrial("trial-q1");
        Trial trial2 = getProvider().createTrial("trial-q2");
        Trial trial3 = getProvider().createTrial("trial-q3");

        store.save(getProvider().createTrialResult(trial1, TrialStatus.COMPLETED));
        store.save(getProvider().createTrialResult(trial2, TrialStatus.FAILED));
        store.save(getProvider().createTrialResult(trial3, TrialStatus.COMPLETED));

        ResultStore.Query query = getProvider().createResultQuery(TrialStatus.COMPLETED);
        List<TrialResult> completed = store.query(query);
        assertThat(completed).hasSize(2);
        assertThat(completed).allMatch(r -> r.status() == TrialStatus.COMPLETED);
    }

    @Test
    void testCount() {
        Trial trial1 = getProvider().createTrial("trial-c1");
        Trial trial2 = getProvider().createTrial("trial-c2");

        store.save(getProvider().createTrialResult(trial1, TrialStatus.COMPLETED));
        store.save(getProvider().createTrialResult(trial2, TrialStatus.COMPLETED));

        ResultStore.Query allQuery = getProvider().createResultQuery(null);
        long count = store.count(allQuery);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testDeleteResult() {
        Trial trial = getProvider().createTrial("trial-del");
        store.save(getProvider().createTrialResult(trial, TrialStatus.COMPLETED));

        assertThat(store.get("trial-del")).isPresent();

        store.delete("trial-del");
        assertThat(store.get("trial-del")).isEmpty();
    }

    @Test
    void testStream() {
        Trial trial1 = getProvider().createTrial("trial-s1");
        Trial trial2 = getProvider().createTrial("trial-s2");
        Trial trial3 = getProvider().createTrial("trial-s3");

        store.save(getProvider().createTrialResult(trial1, TrialStatus.COMPLETED));
        store.save(getProvider().createTrialResult(trial2, TrialStatus.FAILED));
        store.save(getProvider().createTrialResult(trial3, TrialStatus.COMPLETED));

        ResultStore.Query allQuery = getProvider().createResultQuery(null);
        try (Stream<TrialResult> stream = store.stream(allQuery)) {
            long count = stream.count();
            assertThat(count).isEqualTo(3);
        }
    }
}
