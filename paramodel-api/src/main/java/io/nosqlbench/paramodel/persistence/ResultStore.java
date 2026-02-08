package io.nosqlbench.paramodel.persistence;

import io.nosqlbench.paramodel.sequence.TrialResult;
import io.nosqlbench.paramodel.sequence.TrialStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

///
/// # ResultStore
///
/// Persists and retrieves trial results with efficient querying and aggregation.
/// The result store provides durable storage for execution outcomes with support
/// for filtering, pagination, and analytics.
///
/// ## Storage Model
///
/// ```
/// Result Storage:
///
/// execution_plan_abc123/
///   ├─ trials/
///   │   ├─ trial_001.json
///   │   ├─ trial_002.json
///   │   └─ trial_N.json
///   ├─ index/
///   │   ├─ by_status.idx
///   │   ├─ by_parameter.idx
///   │   └─ by_timestamp.idx
///   └─ summary.json
/// ```
///
/// ## Query Capabilities
///
/// ```
/// Query Operations:
///
/// By Status:
///   - All successful trials
///   - All failed trials
///   - All timeout trials
///
/// By Parameter:
///   - Trials with cache_size=256
///   - Trials with concurrency>50
///
/// By Time Range:
///   - Trials executed today
///   - Trials before checkpoint
///
/// By Performance:
///   - Trials with duration > 5 minutes
///   - Trials with error rate > 10%
///
/// Aggregations:
///   - Average duration by parameter
///   - Success rate by configuration
///   - P95 latency across all trials
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Store and Retrieve
///
/// ```java
/// ResultStore store = ResultStore.create();
///
/// // Store result
/// store.save(trialResult);
///
/// // Retrieve by ID
/// Optional<TrialResult> result = store.get("trial_42");
/// ```
///
/// ### Example 2: Query by Status
///
/// ```java
/// List<TrialResult> failures = store.query(
///     Query.builder()
///         .status(TrialStatus.FAILED)
///         .build()
/// );
/// ```
///
/// ### Example 3: Aggregate Results
///
/// ```java
/// Map<String, Double> avgDuration = store.aggregate(
///     Aggregation.average("duration"),
///     Aggregation.groupBy("cache_size")
/// );
/// ```
///
public interface ResultStore {

    static ResultStore create() {
        throw new UnsupportedOperationException(
            "ResultStore.create() requires a concrete implementation");
    }

    void save(TrialResult result);

    Optional<TrialResult> get(String trialId);

    List<TrialResult> query(Query query);

    Stream<TrialResult> stream(Query query);

    long count(Query query);

    void delete(String trialId);

    interface Query {
        static Builder builder() {
            throw new UnsupportedOperationException(
                "Query.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder status(TrialStatus status);
            Builder parameter(String name, Object value);
            Builder after(Instant after);
            Builder before(Instant before);
            Builder limit(int limit);
            Builder offset(int offset);
            Query build();
        }
    }
}
