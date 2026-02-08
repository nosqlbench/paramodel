package io.nosqlbench.paramodel.execution;

import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// # ArtifactCollector
///
/// Captures, stores, and manages artifacts produced during trial execution, including
/// logs, metrics, screenshots, core dumps, and custom outputs. The artifact collector
/// provides organized storage with metadata for result analysis and debugging.
///
/// ## Artifact Types
///
/// The collector handles multiple artifact categories:
///
/// ```
/// Artifact Categories:
///
/// Logs
///   ├─ Application logs (stdout, stderr)
///   ├─ System logs (dmesg, syslog)
///   ├─ Service logs (nginx, postgres, etc.)
///   └─ Framework logs (test runner, profiler)
///
/// Metrics
///   ├─ Performance metrics (CPU, memory, latency)
///   ├─ Business metrics (throughput, errors)
///   ├─ System metrics (disk I/O, network)
///   └─ Custom metrics (application-specific)
///
/// Traces
///   ├─ Distributed traces (Jaeger, Zipkin)
///   ├─ Profiling data (CPU profiles, heap dumps)
///   ├─ Network traces (pcap files)
///   └─ Call graphs
///
/// Outputs
///   ├─ Test results (JUnit XML, TAP)
///   ├─ Reports (HTML, PDF, JSON)
///   ├─ Screenshots/recordings
///   └─ Generated data files
///
/// Diagnostics
///   ├─ Core dumps
///   ├─ Stack traces
///   ├─ Memory dumps
///   └─ Error logs
/// ```
///
/// ## Artifact Organization
///
/// Artifacts are organized hierarchically:
///
/// ```
/// Artifact Storage Structure:
///
/// execution_plan_abc123/
///   │
///   ├─ trial_001/
///   │   ├─ logs/
///   │   │   ├─ stdout.log
///   │   │   ├─ stderr.log
///   │   │   └─ app.log
///   │   ├─ metrics/
///   │   │   ├─ cpu.json
///   │   │   ├─ memory.json
///   │   │   └─ latency.json
///   │   ├─ traces/
///   │   │   └─ trace.json
///   │   └─ outputs/
///   │       ├─ results.xml
///   │       └─ report.html
///   │
///   ├─ trial_002/
///   │   └─ ... (similar structure)
///   │
///   ├─ elements/
///   │   ├─ database_instance_1/
///   │   │   ├─ logs/
///   │   │   │   └─ postgres.log
///   │   │   └─ diagnostics/
///   │   │       └─ slow_queries.log
///   │   └─ cache_instance_1/
///   │       └─ logs/
///   │           └─ redis.log
///   │
///   └─ execution/
///       ├─ execution.log
///       ├─ metrics_summary.json
///       └─ manifest.json (artifact index)
/// ```
///
/// ## Artifact Lifecycle
///
/// Artifacts progress through collection phases:
///
/// ```
/// Artifact Lifecycle:
///
/// COLLECTING
///   ├─ Stream logs in real-time
///   ├─ Buffer metrics
///   ├─ Capture screenshots on events
///   └─ Monitor for diagnostic triggers
///   ↓
/// FINALIZING
///   ├─ Flush buffers
///   ├─ Compress large files
///   ├─ Generate summaries
///   └─ Create manifest
///   ↓
/// STORING
///   ├─ Upload to persistent storage
///   ├─ Generate checksums
///   ├─ Create metadata
///   └─ Index for search
///   ↓
/// ARCHIVED
///   ├─ Available for retrieval
///   ├─ Indexed in catalog
///   ├─ Retention policy applied
///   └─ Can be purged after TTL
/// ```
///
/// ## Streaming Collection
///
/// The collector streams artifacts during execution:
///
/// ```
/// Streaming Collection:
///
/// Trial Execution:
///   t=0s:   Trial starts
///           → Open log streams (stdout, stderr)
///           → Start metrics collection (1s interval)
///
///   t=2s:   Application logs: "Starting load test..."
///           → Stream to stdout.log
///
///   t=5s:   Metrics collected: CPU 45%, Memory 2.1GB
///           → Buffer in memory
///
///   t=10s:  Error detected: "Connection timeout"
///           → Capture stack trace
///           → Take screenshot (if UI test)
///           → Trigger diagnostic collection
///
///   t=15s:  Metrics buffer full (10s worth)
///           → Flush to metrics/cpu.json
///
///   t=30s:  Trial completes
///           → Close log streams
///           → Flush final metrics
///           → Compress logs
///           → Generate manifest
/// ```
///
/// ## Conditional Collection
///
/// Artifacts can be collected conditionally:
///
/// ```
/// Conditional Collection Rules:
///
/// Always Collect:
///   - Basic logs (stdout, stderr)
///   - Trial result (status, duration)
///   - Summary metrics
///
/// On Error:
///   - Detailed logs (debug level)
///   - Full stack traces
///   - Core dumps (if crash)
///   - Memory dumps
///   - Element logs
///
/// On Performance Issue (latency > threshold):
///   - CPU profile
///   - Memory profile
///   - Network trace
///   - Detailed metrics
///
/// On Request (debug mode):
///   - Everything (full verbosity)
///   - Screenshots at intervals
///   - Video recording
///   - All element logs
/// ```
///
/// ## Retention and Cleanup
///
/// The collector applies retention policies:
///
/// ```
/// Retention Policies:
///
/// Policy: Default
///   Success artifacts: 7 days
///   Failed artifacts: 30 days
///   Summary data: 1 year
///   Compression: After 24 hours
///
/// Policy: Production
///   Success artifacts: 30 days
///   Failed artifacts: 90 days
///   Summary data: 5 years
///   Compression: Immediate
///
/// Policy: Development
///   Success artifacts: 1 day
///   Failed artifacts: 7 days
///   Summary data: 30 days
///   Compression: Never
///
/// Cleanup Process:
///   - Scan daily for expired artifacts
///   - Move to cold storage before deletion
///   - Preserve summary metadata
///   - Generate compliance reports
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Basic Artifact Collection
///
/// ```java
/// ArtifactCollector collector = ArtifactCollector.create();
///
/// // Start collection for trial
/// collector.startCollection(trial);
///
/// try {
///     // Execute trial
///     TrialResult result = executeTrial(trial);
///
///     // Collect logs
///     collector.collectLogs(trial, trialOutput);
///
///     // Collect metrics
///     collector.collectMetrics(trial, metricsData);
///
///     // Finalize
///     ArtifactCollection artifacts = collector.finalizeCollection(trial);
///
///     System.out.printf("Collected %d artifacts for %s%n",
///         artifacts.count(), trial.id());
/// } finally {
///     collector.stopCollection(trial);
/// }
/// ```
///
/// ### Example 2: Streaming Logs
///
/// ```java
/// ArtifactCollector collector = ArtifactCollector.create();
///
/// // Open log stream
/// LogStream logStream = collector.openLogStream(
///     trial,
///     ArtifactType.STDOUT);
///
/// // Stream logs during execution
/// Process process = startTrialProcess(trial);
/// BufferedReader reader = new BufferedReader(
///     new InputStreamReader(process.getInputStream()));
///
/// String line;
/// while ((line = reader.readLine()) != null) {
///     logStream.write(line);
///
///     // Also write to console
///     System.out.println(line);
/// }
///
/// // Close stream
/// logStream.close();
/// ```
///
/// ### Example 3: Conditional Collection
///
/// ```java
/// ArtifactCollector collector = ArtifactCollector.create();
///
/// CollectionPolicy policy = CollectionPolicy.builder()
///     .always(ArtifactType.STDOUT, ArtifactType.RESULT)
///     .onError(ArtifactType.STDERR, ArtifactType.STACK_TRACE,
///              ArtifactType.CORE_DUMP)
///     .onSlowExecution(ArtifactType.CPU_PROFILE,
///                      ArtifactType.MEMORY_PROFILE)
///     .build();
///
/// collector.setPolicy(trial, policy);
///
/// // Execute trial
/// TrialResult result = executeTrial(trial);
///
/// if (result.status() == TrialStatus.FAILED) {
///     // Error artifacts automatically collected per policy
///     List<Artifact> errorArtifacts = collector.artifacts(
///         trial,
///         artifact -> artifact.trigger() == CollectionTrigger.ON_ERROR);
///
///     System.out.printf("Collected %d error artifacts%n",
///         errorArtifacts.size());
/// }
/// ```
///
/// ### Example 4: Retrieving Artifacts
///
/// ```java
/// ArtifactCollector collector = ArtifactCollector.create();
///
/// // Retrieve all artifacts for trial
/// List<Artifact> artifacts = collector.artifacts(trial);
///
/// for (Artifact artifact : artifacts) {
///     System.out.printf("Artifact: %s (%s, %d bytes)%n",
///         artifact.name(),
///         artifact.type(),
///         artifact.size());
///
///     // Download specific artifact
///     if (artifact.type() == ArtifactType.STDOUT) {
///         InputStream content = collector.download(artifact);
///         // Process content
///     }
/// }
///
/// // Search artifacts
/// List<Artifact> logs = collector.search(
///     trial,
///     ArtifactQuery.builder()
///         .type(ArtifactType.LOG)
///         .containsText("ERROR")
///         .build());
/// ```
///
/// ### Example 5: Cleanup and Retention
///
/// ```java
/// ArtifactCollector collector = ArtifactCollector.create();
///
/// // Set retention policy
/// RetentionPolicy policy = RetentionPolicy.builder()
///     .successRetention(Duration.ofDays(7))
///     .failureRetention(Duration.ofDays(30))
///     .compressionDelay(Duration.ofHours(24))
///     .build();
///
/// collector.setRetentionPolicy(policy);
///
/// // Manually trigger cleanup
/// CleanupReport report = collector.cleanup();
///
/// System.out.printf("Cleanup completed:%n");
/// System.out.printf("  Artifacts deleted: %d%n", report.deletedCount());
/// System.out.printf("  Space freed: %.2f GB%n", report.spaceFreedGb());
/// System.out.printf("  Artifacts compressed: %d%n",
///     report.compressedCount());
/// ```
///
/// ## Contract Requirements
///
/// ### Reliability
/// - Collector MUST NOT lose artifacts during collection
/// - Collector MUST handle storage failures gracefully
/// - Collector MUST flush buffers before finalizing
///
/// ### Performance
/// - Collector SHOULD stream large artifacts (not buffer entirely)
/// - Collector SHOULD compress artifacts asynchronously
/// - Collector SHOULD NOT impact trial execution performance
///
/// ### Organization
/// - Collector MUST organize artifacts hierarchically
/// - Collector MUST generate artifact manifests
/// - Collector MUST support artifact search and retrieval
///
/// @see TrialResult
/// @see Executor
///
public interface ArtifactCollector {

    ///
    /// Creates an artifact collector with default configuration.
    ///
    /// @return Artifact collector instance
    ///
    static ArtifactCollector create() {
        throw new UnsupportedOperationException(
            "ArtifactCollector.create() requires a concrete implementation");
    }

    ///
    /// Starts artifact collection for a trial.
    ///
    /// @param trial Trial to collect artifacts for
    ///
    void startCollection(Trial trial);

    ///
    /// Stops artifact collection for a trial.
    ///
    /// @param trial Trial to stop collecting for
    ///
    void stopCollection(Trial trial);

    ///
    /// Collects logs for a trial.
    ///
    /// @param trial Trial
    /// @param logs Log content
    /// @param type Log type
    ///
    void collectLogs(Trial trial, String logs, ArtifactType type);

    ///
    /// Collects metrics for a trial.
    ///
    /// @param trial Trial
    /// @param metrics Metrics data
    ///
    void collectMetrics(Trial trial, Map<String, Object> metrics);

    ///
    /// Collects a custom artifact.
    ///
    /// @param trial Trial
    /// @param name Artifact name
    /// @param content Artifact content
    /// @param type Artifact type
    ///
    void collectArtifact(Trial trial, String name, InputStream content, ArtifactType type);

    ///
    /// Opens a log stream for streaming collection.
    ///
    /// @param trial Trial
    /// @param type Log type
    /// @return Log stream
    ///
    LogStream openLogStream(Trial trial, ArtifactType type);

    ///
    /// Finalizes artifact collection for a trial.
    ///
    /// @param trial Trial
    /// @return Artifact collection summary
    ///
    ArtifactCollection finalizeCollection(Trial trial);

    ///
    /// Sets the collection policy for a trial.
    ///
    /// @param trial Trial
    /// @param policy Collection policy
    ///
    void setPolicy(Trial trial, CollectionPolicy policy);

    ///
    /// Returns all artifacts for a trial.
    ///
    /// @param trial Trial
    /// @return Artifacts
    ///
    List<Artifact> artifacts(Trial trial);

    ///
    /// Searches artifacts matching query.
    ///
    /// @param trial Trial
    /// @param query Search query
    /// @return Matching artifacts
    ///
    List<Artifact> search(Trial trial, ArtifactQuery query);

    ///
    /// Downloads an artifact.
    ///
    /// @param artifact Artifact to download
    /// @return Artifact content stream
    ///
    InputStream download(Artifact artifact);

    ///
    /// Sets the retention policy.
    ///
    /// @param policy Retention policy
    ///
    void setRetentionPolicy(RetentionPolicy policy);

    ///
    /// Triggers cleanup of expired artifacts.
    ///
    /// @return Cleanup report
    ///
    CleanupReport cleanup();

    ///
    /// Artifact type.
    ///
    enum ArtifactType {
        STDOUT,
        STDERR,
        LOG,
        METRIC,
        TRACE,
        PROFILE,
        SCREENSHOT,
        VIDEO,
        REPORT,
        RESULT,
        STACK_TRACE,
        CORE_DUMP,
        MEMORY_DUMP,
        NETWORK_TRACE,
        CPU_PROFILE,
        MEMORY_PROFILE,
        CUSTOM
    }

    ///
    /// Artifact.
    ///
    interface Artifact {
        String id();
        String name();
        ArtifactType type();
        long size();
        Instant collectedAt();
        String trialId();
        Optional<String> contentType();
        Map<String, String> metadata();
    }

    ///
    /// Log stream for streaming collection.
    ///
    interface LogStream {
        void write(String line);
        void write(byte[] data);
        void flush();
        void close();
    }

    ///
    /// Artifact collection summary.
    ///
    interface ArtifactCollection {
        String trialId();
        int count();
        long totalSizeBytes();
        List<Artifact> artifacts();
        Instant collectedAt();
    }

    ///
    /// Collection policy.
    ///
    interface CollectionPolicy {
        List<ArtifactType> always();
        List<ArtifactType> onError();
        List<ArtifactType> onSlowExecution();
        Map<String, List<ArtifactType>> custom();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "CollectionPolicy.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder always(ArtifactType... types);
            Builder onError(ArtifactType... types);
            Builder onSlowExecution(ArtifactType... types);
            Builder custom(String trigger, ArtifactType... types);
            CollectionPolicy build();
        }
    }

    ///
    /// Artifact query.
    ///
    interface ArtifactQuery {
        Optional<ArtifactType> type();
        Optional<String> namePattern();
        Optional<String> containsText();
        Optional<Instant> after();
        Optional<Instant> before();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "ArtifactQuery.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder type(ArtifactType type);
            Builder namePattern(String pattern);
            Builder containsText(String text);
            Builder after(Instant after);
            Builder before(Instant before);
            ArtifactQuery build();
        }
    }

    ///
    /// Retention policy.
    ///
    interface RetentionPolicy {
        java.time.Duration successRetention();
        java.time.Duration failureRetention();
        java.time.Duration compressionDelay();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "RetentionPolicy.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder successRetention(java.time.Duration duration);
            Builder failureRetention(java.time.Duration duration);
            Builder compressionDelay(java.time.Duration duration);
            RetentionPolicy build();
        }
    }

    ///
    /// Cleanup report.
    ///
    interface CleanupReport {
        int deletedCount();
        int compressedCount();
        long spaceFreedBytes();
        double spaceFreedGb();
        java.time.Duration duration();
    }
}
