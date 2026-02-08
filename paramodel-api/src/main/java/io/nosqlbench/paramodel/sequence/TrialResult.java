package io.nosqlbench.paramodel.sequence;

import io.nosqlbench.paramodel.persistence.ArtifactStore;
import io.nosqlbench.paramodel.persistence.ResultStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// The outcome of executing a trial, including status, metrics, artifacts, and provenance.
///
/// ## Concept
///
/// A {@code TrialResult} captures everything that happened during trial execution:
/// - **Status**: Did it succeed, fail, or get skipped?
/// - **Metrics**: Structured dependent variables (accuracy, latency, etc.)
/// - **Artifacts**: Unstructured outputs (logs, models, datasets)
/// - **Timing**: When it started, ended, how long it took
/// - **Provenance**: Links back to exact trial configuration
/// - **Error**: Failure details if applicable
///
/// ## Structure
///
/// ```
/// TrialResult
/// ├── trial: Trial
/// │   └── The executed trial (parameter assignments)
/// │
/// ├── status: TrialStatus
/// │   └── COMPLETED | FAILED | SKIPPED | CANCELLED
/// │
/// ├── metrics: Map<String, Object>
/// │   └── Structured dependent variables
/// │
/// ├── artifacts: List<ArtifactReference>
/// │   └── Links to unstructured outputs
/// │
/// ├── timing: ExecutionTiming
/// │   ├── startedAt: Instant
/// │   ├── completedAt: Instant
/// │   └── duration: Duration
/// │
/// ├── provenance: ProvenanceInfo
/// │   └── Fingerprint linking to exact configuration
/// │
/// ├── error: Optional<ErrorInfo>
/// │   └── Details if status is FAILED
/// │
/// └── attemptNumber: int
///     └── Retry count (1 for first attempt)
/// ```
///
/// ## Result Flow
///
/// ```
/// Trial Execution:
///   TrialExecutor.execute(trial)
///         ↓
///   Trial runs with parameters
///         ↓
///   Metrics collected
///   Artifacts generated
///   Timing recorded
///         ↓
///   TrialResult created
///         ↓
///   ResultStore.save(result)
///         ↓
///   Persisted with provenance
/// ```
///
/// ## Metrics vs Artifacts
///
/// ```
/// Metrics (Structured):
///   - Numeric/boolean values
///   - Queryable, aggregatable
///   - Stored in database columns
///   - Example: accuracy=0.95, latency_ms=127
///
/// Artifacts (Unstructured):
///   - Binary files, logs, models
///   - Stored separately (S3, filesystem)
///   - Referenced by URI/path
///   - Example: model.pkl, logs.tar.gz, dataset.csv
/// ```
///
/// ## Usage Example: Success Case
///
/// ```java
/// Trial trial = ...;
/// TrialResult result = executor.execute(trial);
///
/// if (result.status().isSuccess()) {
///     // Extract metrics
///     Map<String, Object> metrics = result.metrics();
///     double accuracy = (Double) metrics.get("accuracy");
///     int latency = (Integer) metrics.get("latency_ms");
///
///     System.out.printf("Accuracy: %.2f%%, Latency: %dms%n",
///         accuracy * 100, latency);
///
///     // Access artifacts
///     for (ArtifactReference artifact : result.artifacts()) {
///         System.out.println("Artifact: " + artifact.name() +
///                          " at " + artifact.uri());
///     }
///
///     // Check timing
///     Duration duration = result.timing().duration();
///     System.out.println("Execution took: " + duration);
/// }
/// ```
///
/// ## Usage Example: Failure Case
///
/// ```java
/// TrialResult result = executor.execute(trial);
///
/// if (result.status().isFailure()) {
///     ErrorInfo error = result.error().orElseThrow();
///
///     System.err.println("Trial failed: " + error.message());
///     System.err.println("Error type: " + error.type());
///
///     error.stackTrace().ifPresent(trace ->
///         System.err.println("Stack trace:\n" + trace)
///     );
///
///     // Check if retryable
///     if (error.isRetryable() && result.attemptNumber() < maxRetries) {
///         System.out.println("Retrying trial (attempt " +
///                          (result.attemptNumber() + 1) + ")");
///         result = executor.retry(trial, result.attemptNumber() + 1);
///     }
/// }
/// ```
///
/// ## Usage Example: Metrics Analysis
///
/// ```java
/// // Collect results from sequence
/// List<TrialResult> results = new ArrayList<>();
/// for (Trial trial : sequence) {
///     TrialResult result = executor.execute(trial);
///     results.add(result);
/// }
///
/// // Analyze metrics
/// double avgAccuracy = results.stream()
///     .filter(r -> r.status().isSuccess())
///     .mapToDouble(r -> (Double) r.metrics().get("accuracy"))
///     .average()
///     .orElse(0.0);
///
/// System.out.printf("Average accuracy: %.2f%%\n", avgAccuracy * 100);
///
/// // Find best trial
/// TrialResult best = results.stream()
///     .filter(r -> r.status().isSuccess())
///     .max((r1, r2) -> Double.compare(
///         (Double) r1.metrics().get("accuracy"),
///         (Double) r2.metrics().get("accuracy")
///     ))
///     .orElseThrow();
///
/// System.out.println("Best trial: " + best.trial().id());
/// ```
///
/// ## Provenance and Reproducibility
///
/// Every result links back to exact configuration:
///
/// ```java
/// TrialResult result = ...;
/// ProvenanceInfo prov = result.provenance();
///
/// // Fingerprint uniquely identifies configuration
/// String fingerprint = prov.configurationFingerprint();
///
/// // Can reproduce exact same trial
/// Trial originalTrial = result.trial();
/// TrialResult reproduction = executor.execute(originalTrial);
///
/// // Should produce same fingerprint
/// assert reproduction.provenance()
///     .configurationFingerprint()
///     .equals(fingerprint);
/// ```
///
/// ## Relationship to Simplica
///
/// Paramodel TrialResult is the basic outcome. Simplica enhances it with:
///
/// ```
/// Paramodel TrialResult:
///   - Status, metrics, artifacts
///   - Basic timing
///   - Simple provenance
///
/// Simplica TrialResult:
///   - Everything from Paramodel
///   - Element lifecycle events
///   - Resource usage telemetry
///   - Barrier wait times
///   - Retry history
///   - Full provenance envelope with plan version
/// ```
///
/// @see Trial
/// @see TrialStatus
/// @see com.paramodel.api.execution.TrialExecutor
/// @see ResultStore
/// @since 0.1.0
///
public interface TrialResult {

    ///
    /// Returns the trial that was executed to produce this result.
    ///
    /// ## Use Cases
    ///
    /// - Link results back to parameter assignments
    /// - Understand which configuration produced results
    /// - Reproduce trial execution
    ///
    /// @return the executed trial, never null
    ///
    Trial trial();

    ///
    /// Returns the execution status of this trial.
    ///
    /// ## Status Semantics
    ///
    /// ```
    /// COMPLETED  - Success, metrics available
    /// FAILED     - Error occurred, check error()
    /// SKIPPED    - Not executed, check skipReason()
    /// CANCELLED  - Interrupted before completion
    /// ```
    ///
    /// @return execution status, never null
    /// @see TrialStatus
    ///
    TrialStatus status();

    ///
    /// Returns structured metrics (dependent variables) produced by this trial.
    ///
    /// ## Metric Structure
    ///
    /// ```
    /// Map<String, Object>
    ///   ↓
    /// metricName → metricValue
    ///
    /// Example:
    ///   {
    ///     "accuracy": 0.942,
    ///     "precision": 0.88,
    ///     "recall": 0.91,
    ///     "latency_ms": 127,
    ///     "throughput_qps": 850,
    ///     "memory_mb": 2048,
    ///     "converged": true
    ///   }
    /// ```
    ///
    /// ## Common Metric Types
    ///
    /// - **Performance**: latency, throughput, qps
    /// - **Quality**: accuracy, precision, recall, f1_score
    /// - **Resources**: memory, cpu, disk, network
    /// - **Business**: cost, revenue, conversion_rate
    ///
    /// ## Contract
    ///
    /// - MUST return non-null, unmodifiable map
    /// - MAY be empty if no metrics collected
    /// - Values SHOULD be JSON-serializable types
    ///   (Number, String, Boolean, List, Map)
    ///
    /// @return immutable metrics map, never null
    ///
    Map<String, Object> metrics();

    ///
    /// Returns a specific metric value by name.
    ///
    /// ## Type Safety
    ///
    /// Caller must cast to expected type:
    /// ```java
    /// Optional<Object> opt = result.metric("accuracy");
    /// Double accuracy = (Double) opt.orElse(0.0);
    /// ```
    ///
    /// @param name metric name
    /// @return metric value if present
    ///
    default Optional<Object> metric(String name) {
        return Optional.ofNullable(metrics().get(name));
    }

    ///
    /// Returns references to unstructured artifacts produced by this trial.
    ///
    /// ## Artifact Types
    ///
    /// ```
    /// Logs:       trial.log, stdout.txt, stderr.txt
    /// Models:     model.pkl, weights.h5, checkpoint.pt
    /// Data:       predictions.csv, samples.parquet
    /// Plots:      loss_curve.png, confusion_matrix.pdf
    /// Config:     effective_config.json
    /// ```
    ///
    /// ## Artifact Storage
    ///
    /// Artifacts are NOT stored inline in the result.
    /// Instead, references (URIs) point to external storage:
    ///
    /// ```
    /// ArtifactReference {
    ///   name: "model.pkl"
    ///   uri: "s3://bucket/trials/trial-123/model.pkl"
    ///   contentType: "application/octet-stream"
    ///   sizeBytes: 1048576
    /// }
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return non-null, unmodifiable list
    /// - MAY be empty if no artifacts produced
    /// - URIs MUST be resolvable by ArtifactStore
    ///
    /// @return immutable artifact list, never null
    /// @see ArtifactReference
    /// @see ArtifactStore
    ///
    List<ArtifactReference> artifacts();

    ///
    /// Returns timing information for this trial's execution.
    ///
    /// ## Timing Structure
    ///
    /// ```
    /// ExecutionTiming {
    ///   startedAt:    2026-02-08T10:30:00Z
    ///   completedAt:  2026-02-08T10:35:23Z
    ///   duration:     PT5M23S (5 minutes 23 seconds)
    /// }
    /// ```
    ///
    /// ## Use Cases
    ///
    /// - Performance analysis
    /// - Cost estimation (duration * rate)
    /// - Timeout detection
    /// - Progress tracking
    ///
    /// @return execution timing, never null
    /// @see ExecutionTiming
    ///
    ExecutionTiming timing();

    ///
    /// Returns provenance information linking this result to its exact configuration.
    ///
    /// ## Provenance Contents
    ///
    /// - Configuration fingerprint (SHA-256)
    /// - Study/sequence identifiers
    /// - Plan version (Simplica)
    /// - Execution environment
    ///
    /// ## Use Cases
    ///
    /// - Reproducibility verification
    /// - Result deduplication
    /// - Audit trails
    /// - Lineage tracking
    ///
    /// @return provenance information, never null
    /// @see ProvenanceInfo
    /// @see com.paramodel.api.versioning.ProvenanceService
    ///
    ProvenanceInfo provenance();

    ///
    /// Returns error details if this trial failed.
    ///
    /// ## Error Information
    ///
    /// ```
    /// ErrorInfo {
    ///   type: "TimeoutException"
    ///   message: "Trial exceeded 5 minute timeout"
    ///   stackTrace: Optional<String>
    ///   isRetryable: true
    ///   errorCode: Optional<String>
    /// }
    /// ```
    ///
    /// ## Availability
    ///
    /// ```
    /// status == FAILED    → error().isPresent() = true
    /// status != FAILED    → error().isEmpty() = true
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// if (result.status().isFailure()) {
    ///     ErrorInfo error = result.error().orElseThrow();
    ///     System.err.println("Failed: " + error.message());
    ///
    ///     if (error.isRetryable()) {
    ///         // Retry logic
    ///     } else {
    ///         // Permanent failure
    ///     }
    /// }
    /// ```
    ///
    /// @return error details if failed, empty otherwise
    ///
    Optional<ErrorInfo> error();

    ///
    /// Returns the reason this trial was skipped, if applicable.
    ///
    /// ## Skip Reasons
    ///
    /// ```
    /// "Constraint violation: age < 0"
    /// "Dependency failed: parent trial failed"
    /// "User directive: explicitly skipped"
    /// "Conditional skip: skip_if(platform == windows) triggered"
    /// ```
    ///
    /// ## Availability
    ///
    /// ```
    /// status == SKIPPED   → skipReason().isPresent() = true
    /// status != SKIPPED   → skipReason().isEmpty() = true
    /// ```
    ///
    /// @return skip reason if skipped, empty otherwise
    ///
    Optional<String> skipReason();

    ///
    /// Returns the attempt number for this execution (1-based).
    ///
    /// ## Retry Semantics
    ///
    /// ```
    /// First attempt:     attemptNumber() = 1
    /// First retry:       attemptNumber() = 2
    /// Second retry:      attemptNumber() = 3
    /// ...
    /// ```
    ///
    /// ## Use Cases
    ///
    /// - Track retry count
    /// - Enforce max retry limits
    /// - Analyze failure patterns
    ///
    /// ## Contract
    ///
    /// - MUST be ≥ 1
    /// - MUST increment on each retry
    ///
    /// @return attempt number, always ≥ 1
    ///
    int attemptNumber();

    ///
    /// Reference to an unstructured artifact produced by a trial.
    ///
    /// ## Structure
    ///
    /// ```
    /// ArtifactReference
    /// ├── name: String           - Human-readable name
    /// ├── uri: String            - Resolvable location
    /// ├── contentType: String    - MIME type
    /// └── sizeBytes: long        - File size
    /// ```
    ///
    interface ArtifactReference {
        /// Artifact name (e.g., "model.pkl", "logs.tar.gz")
        String name();

        /// URI where artifact is stored (e.g., "s3://bucket/path/file")
        String uri();

        /// MIME type (e.g., "application/octet-stream", "text/plain")
        String contentType();

        /// Size in bytes, or -1 if unknown
        long sizeBytes();
    }

    ///
    /// Timing information for trial execution.
    ///
    /// ## Time Points
    ///
    /// ```
    /// Timeline:
    ///   startedAt                    completedAt
    ///      ↓                              ↓
    ///   ───●──────────────────────────────●───
    ///      └──────────┬──────────┘
    ///              duration
    /// ```
    ///
    interface ExecutionTiming {
        /// When trial execution started
        Instant startedAt();

        /// When trial execution finished (success or failure)
        Instant completedAt();

        /// Total execution duration
        default Duration duration() {
            return Duration.between(startedAt(), completedAt());
        }
    }

    ///
    /// Provenance information for reproducibility and traceability.
    ///
    /// ## Provenance Fields
    ///
    /// ```
    /// ProvenanceInfo
    /// ├── configurationFingerprint: String
    /// │   └── SHA-256 of exact trial configuration
    /// │
    /// ├── sequenceId: Optional<String>
    /// │   └── Sequence that generated this trial
    /// │
    /// ├── executionPlanVersion: Optional<String>
    /// │   └── Simplica execution plan version
    /// │
    /// └── executionEnvironment: Map<String, String>
    ///     └── hostname, os, java_version, etc.
    /// ```
    ///
    interface ProvenanceInfo {
        /// SHA-256 fingerprint of trial configuration
        String configurationFingerprint();

        /// Sequence identifier if part of a sequence
        Optional<String> sequenceId();

        /// Execution plan version (Simplica)
        Optional<String> executionPlanVersion();

        /// Execution environment details
        Map<String, String> executionEnvironment();
    }

    ///
    /// Error information for failed trials.
    ///
    /// ## Error Categories
    ///
    /// ```
    /// ErrorType           Retryable?    Example
    /// ───────────────────────────────────────────────────────────
    /// TimeoutException    Yes           Exceeded 5min timeout
    /// OutOfMemoryError    Maybe         JVM heap exhausted
    /// NetworkException    Yes           Connection refused
    /// ValidationError     No            Invalid result
    /// ConfigurationError  No            Bad parameter value
    /// ```
    ///
    interface ErrorInfo {
        /// Error type/class name
        String type();

        /// Human-readable error message
        String message();

        /// Stack trace if available
        Optional<String> stackTrace();

        /// Whether this error is transient and retryable
        boolean isRetryable();

        /// Optional error code for categorization
        Optional<String> errorCode();
    }
}
