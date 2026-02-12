package io.nosqlbench.paramodel.mock.persistence;

import io.nosqlbench.paramodel.persistence.ResultStore;
import io.nosqlbench.paramodel.sequence.TrialResult;
import io.nosqlbench.paramodel.sequence.TrialStatus;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

///
/// In-memory result store for testing.
///
/// Stores trial results in a map keyed by trial ID.
/// Supports basic querying by status and time range.
///
/// @see ResultStore
/// @since 0.1.0
///
public class MockResultStore implements ResultStore {
    private final Map<String, TrialResult> results = new LinkedHashMap<>();

    /// Creates a new empty result store.
    public MockResultStore() {}

    @Override
    public void save(TrialResult result) {
        Objects.requireNonNull(result, "result must not be null");
        results.put(result.trial().id(), result);
    }

    @Override
    public Optional<TrialResult> get(String trialId) {
        return Optional.ofNullable(results.get(trialId));
    }

    @Override
    public List<TrialResult> query(Query query) {
        return streamAll(query).toList();
    }

    @Override
    public Stream<TrialResult> stream(Query query) {
        return streamAll(query);
    }

    @Override
    public long count(Query query) {
        return streamAll(query).count();
    }

    @Override
    public void delete(String trialId) {
        results.remove(trialId);
    }

    private Stream<TrialResult> streamAll(Query query) {
        if (query instanceof MockQuery mq) {
            Stream<TrialResult> stream = results.values().stream();
            if (mq.status != null) {
                stream = stream.filter(r -> r.status() == mq.status);
            }
            if (mq.after != null) {
                stream = stream.filter(r -> r.timing().startedAt().isAfter(mq.after));
            }
            if (mq.before != null) {
                stream = stream.filter(r -> r.timing().startedAt().isBefore(mq.before));
            }
            if (mq.offset > 0) {
                stream = stream.skip(mq.offset);
            }
            if (mq.limit > 0) {
                stream = stream.limit(mq.limit);
            }
            return stream;
        }
        return results.values().stream();
    }

    ///
    /// Creates a query builder for this store.
    ///
    /// @return a new query builder
    ///
    public static MockQuery.Builder queryBuilder() {
        return new MockQuery.Builder();
    }

    ///
    /// Simple query implementation for testing.
    ///
    public static class MockQuery implements Query {
        final TrialStatus status;
        final Map<String, Object> parameters;
        final Instant after;
        final Instant before;
        final int limit;
        final int offset;

        MockQuery(TrialStatus status, Map<String, Object> parameters,
                  Instant after, Instant before, int limit, int offset) {
            this.status = status;
            this.parameters = parameters;
            this.after = after;
            this.before = before;
            this.limit = limit;
            this.offset = offset;
        }

        ///
        /// Builder for MockQuery.
        ///
        public static class Builder implements Query.Builder {
            /// Creates a new query builder.
            public Builder() {}

            private TrialStatus status;
            private final Map<String, Object> parameters = new HashMap<>();
            private Instant after;
            private Instant before;
            private int limit;
            private int offset;

            @Override
            public Query.Builder status(TrialStatus status) {
                this.status = status;
                return this;
            }

            @Override
            public Query.Builder parameter(String name, Object value) {
                this.parameters.put(name, value);
                return this;
            }

            @Override
            public Query.Builder after(Instant after) {
                this.after = after;
                return this;
            }

            @Override
            public Query.Builder before(Instant before) {
                this.before = before;
                return this;
            }

            @Override
            public Query.Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            @Override
            public Query.Builder offset(int offset) {
                this.offset = offset;
                return this;
            }

            @Override
            public Query build() {
                return new MockQuery(status, parameters, after, before, limit, offset);
            }
        }
    }
}
