package io.nosqlbench.paramodel.observability;

import java.util.List;
import java.util.Map;

///
/// # MetricsExporter
///
/// Exports metrics to external monitoring systems (Prometheus, Grafana, CloudWatch, etc.).
/// The exporter provides pluggable adapters for different metrics backends while maintaining
/// a consistent internal representation.
///
/// ## Export Formats
///
/// ```
/// Supported Export Formats:
///
/// Prometheus
///   Format: OpenMetrics text format
///   Protocol: HTTP pull (scrape endpoint)
///   Features: Labels, histograms, summaries
///
/// Grafana
///   Format: JSON
///   Protocol: HTTP push (API)
///   Features: Annotations, dashboards
///
/// CloudWatch
///   Format: CloudWatch Metrics API
///   Protocol: AWS SDK
///   Features: Dimensions, alarms
///
/// StatsD
///   Format: StatsD protocol
///   Protocol: UDP
///   Features: Counters, gauges, timers
///
/// InfluxDB
///   Format: Line protocol
///   Protocol: HTTP
///   Features: Tags, fields, timestamps
/// ```
///
/// ## Prometheus Export
///
/// ```
/// Prometheus Format:
///
/// # HELP paramodel_trials_total Total number of trials executed
/// # TYPE paramodel_trials_total counter
/// paramodel_trials_total{status="success",execution="exec_123"} 95
/// paramodel_trials_total{status="failed",execution="exec_123"} 5
///
/// # HELP paramodel_trial_duration_seconds Trial execution duration
/// # TYPE paramodel_trial_duration_seconds histogram
/// paramodel_trial_duration_seconds_bucket{le="10"} 20
/// paramodel_trial_duration_seconds_bucket{le="30"} 60
/// paramodel_trial_duration_seconds_bucket{le="60"} 85
/// paramodel_trial_duration_seconds_bucket{le="+Inf"} 100
/// paramodel_trial_duration_seconds_sum 4520.5
/// paramodel_trial_duration_seconds_count 100
///
/// # HELP paramodel_active_trials Current number of active trials
/// # TYPE paramodel_active_trials gauge
/// paramodel_active_trials{execution="exec_123"} 8
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Prometheus Exporter
///
/// ```java
/// MetricsExporter exporter = PrometheusExporter.create(8080);
///
/// Observer observer = Observer.create();
/// MetricsCollector collector = observer.metricsCollector();
///
/// // Export metrics every 10 seconds
/// ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
/// scheduler.scheduleAtFixedRate(() -> {
///     MetricsSnapshot snapshot = collector.snapshot();
///     exporter.export(snapshot);
/// }, 0, 10, TimeUnit.SECONDS);
///
/// // Prometheus scrapes http://localhost:8080/metrics
/// ```
///
/// ### Example 2: CloudWatch Exporter
///
/// ```java
/// MetricsExporter exporter = CloudWatchExporter.builder()
///     .namespace("Paramodel/Execution")
///     .region("us-west-2")
///     .build();
///
/// observer.subscribe(TrialEvent.class, event -> {
///     if (event.type() == EventType.TRIAL_COMPLETED) {
///         exporter.exportCounter("TrialsCompleted", 1,
///             Map.of("status", event.result().status().toString()));
///     }
/// });
/// ```
///
/// ### Example 3: Custom Exporter
///
/// ```java
/// MetricsExporter customExporter = new MetricsExporter() {
///     @Override
///     public void export(MetricsSnapshot snapshot) {
///         // Custom export logic
///         sendToInternalSystem(snapshot);
///     }
/// };
/// ```
///
public interface MetricsExporter {

    ///
    /// Exports a metrics snapshot.
    ///
    /// @param snapshot Metrics snapshot to export
    ///
    void export(Observer.MetricsSnapshot snapshot);

    ///
    /// Exports a counter metric.
    ///
    /// @param name Metric name
    /// @param value Counter value
    /// @param labels Metric labels
    ///
    void exportCounter(String name, long value, Map<String, String> labels);

    ///
    /// Exports a gauge metric.
    ///
    /// @param name Metric name
    /// @param value Gauge value
    /// @param labels Metric labels
    ///
    void exportGauge(String name, double value, Map<String, String> labels);

    ///
    /// Exports a histogram metric.
    ///
    /// @param name Metric name
    /// @param values Histogram values
    /// @param labels Metric labels
    ///
    void exportHistogram(String name, List<Double> values, Map<String, String> labels);

    ///
    /// Flushes buffered metrics.
    ///
    void flush();

    ///
    /// Closes the exporter and releases resources.
    ///
    void close();
}
