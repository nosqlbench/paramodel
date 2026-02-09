package io.nosqlbench.paramodel.observability;

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// # Observer
///
/// Provides unified observability for execution through events, metrics, logs, and traces.
/// The observer pattern allows decoupled monitoring of execution progress, performance,
/// and failures across the entire system.
///
/// ## Observer Architecture
///
/// The observer implements a publish-subscribe pattern:
///
/// ```
/// Observer Architecture:
///
/// Event Sources                Observer                Subscribers
///   │                            │                         │
///   ├─ Executor ────────────────→│                         │
///   ├─ Scheduler ───────────────→│                         │
///   ├─ Runtime ─────────────────→│────→ Console Logger    │
///   ├─ Compiler ────────────────→│────→ Metrics Collector │
///   └─ ArtifactCollector ───────→│────→ Progress UI       │
///                                 │────→ Alert System     │
///                                 │────→ Database Writer  │
///                                 └────→ Custom Handlers  │
///
/// Event Flow:
///   1. Event occurs (trial starts, step completes, etc.)
///   2. Source emits event to Observer
///   3. Observer routes to all registered subscribers
///   4. Subscribers process event independently
/// ```
///
/// ## Event Types
///
/// The observer handles multiple event categories:
///
/// ```
/// Event Taxonomy:
///
/// Lifecycle Events
///   ├─ ExecutionStarted
///   ├─ ExecutionCompleted
///   ├─ ExecutionFailed
///   ├─ ExecutionCancelled
///   └─ ExecutionPaused/Resumed
///
/// Progress Events
///   ├─ PhaseChanged (DEPLOYING → EXECUTING)
///   ├─ ProgressUpdated (75% complete)
///   ├─ TrialBatchCompleted
///   └─ CheckpointCreated
///
/// Step Events
///   ├─ StepStarted
///   ├─ StepCompleted
///   ├─ StepFailed
///   └─ StepRetried
///
/// Trial Events
///   ├─ TrialStarted
///   ├─ TrialCompleted
///   ├─ TrialFailed
///   └─ TrialSkipped
///
/// Resource Events
///   ├─ ElementDeployed
///   ├─ ElementReady
///   ├─ ElementUnhealthy
///   ├─ ElementTornDown
///   └─ ResourceExhausted
///
/// Error Events
///   ├─ CompilationError
///   ├─ DeploymentError
///   ├─ ExecutionError
///   └─ TimeoutError
/// ```
///
/// ## Event Structure
///
/// All events share common structure:
///
/// ```
/// Event Structure:
///
/// Event:
///   id: "evt_abc123"
///   timestamp: 2025-01-15T14:30:45.123Z
///   type: TRIAL_COMPLETED
///   source: "executor-1"
///   executionId: "exec_xyz789"
///   severity: INFO
///   payload:
///     trial_id: "trial_42"
///     status: "SUCCESS"
///     duration: "2m 35s"
///     metrics:
///       requests_processed: 10450
///       avg_latency_ms: 45.2
///   tags:
///     environment: "production"
///     region: "us-west-2"
/// ```
///
/// ## Metrics Collection
///
/// The observer aggregates metrics from events:
///
/// ```
/// Metrics Collection:
///
/// Counters (cumulative):
///   trials_started: 100
///   trials_completed: 95
///   trials_failed: 5
///   steps_executed: 312
///
/// Gauges (current value):
///   active_trials: 8
///   cpu_usage_percent: 67.5
///   memory_usage_gb: 24.3
///   queue_depth: 3
///
/// Histograms (distribution):
///   trial_duration_seconds:
///     p50: 45.2
///     p95: 120.8
///     p99: 250.1
///   step_duration_seconds:
///     p50: 2.1
///     p95: 15.3
///     p99: 45.7
///
/// Rates (per time unit):
///   trials_per_second: 0.67
///   errors_per_minute: 0.2
///   throughput_mb_per_second: 12.5
/// ```
///
/// ## Log Correlation
///
/// The observer correlates logs with execution context:
///
/// ```
/// Log Correlation:
///
/// Log Entry:
///   timestamp: 2025-01-15T14:30:45.123Z
///   level: ERROR
///   message: "Connection timeout to cache"
///   context:
///     execution_id: "exec_xyz789"
///     trial_id: "trial_42"
///     step_id: "execute_trial_42"
///     element: "cache_instance_50"
///     trace_id: "trace_abc123"
///     span_id: "span_def456"
///   tags:
///     cache_size: "256"
///     concurrency: "50"
///
/// Correlation Benefits:
///   - Trace logs back to specific trial/step
///   - Filter logs by execution context
///   - Aggregate errors by parameter values
///   - Debug failures with full context
/// ```
///
/// ## Alert System
///
/// The observer triggers alerts on critical events:
///
/// ```
/// Alert Rules:
///
/// Rule: High Error Rate
///   Condition: error_rate > 10% for 5 minutes
///   Severity: WARNING
///   Action: Send notification to #alerts channel
///
/// Rule: Resource Exhaustion
///   Condition: available_memory < 10%
///   Severity: CRITICAL
///   Action: Pause execution, alert on-call
///
/// Rule: Trial Timeout Spike
///   Condition: timeout_rate > 20% for 10 minutes
///   Severity: WARNING
///   Action: Log to incident tracker
///
/// Alert Example:
///   [CRITICAL] Resource Exhaustion
///   Execution: exec_xyz789
///   Message: Available memory below 10% (5.2 GB remaining)
///   Time: 2025-01-15T14:35:22Z
///   Action: Execution paused, awaiting manual intervention
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Basic Event Subscription
///
/// ```java
/// Observer observer = Observer.create();
///
/// // Subscribe to all trial events
/// observer.subscribe(TrialEvent.class, event -> {
///     System.out.printf("[%s] Trial %s: %s%n",
///         event.timestamp(),
///         event.trialId(),
///         event.type());
/// });
///
/// // Subscribe to errors
/// observer.subscribe(ErrorEvent.class, event -> {
///     System.err.printf("[ERROR] %s: %s%n",
///         event.source(),
///         event.message());
/// });
///
/// // Execute with observer
/// Executor executor = Executor.create();
/// executor.setObserver(observer);
/// executor.execute(plan);
/// ```
///
/// ### Example 2: Metrics Dashboard
///
/// ```java
/// Observer observer = Observer.create();
/// MetricsCollector metrics = observer.metricsCollector();
///
/// // Update dashboard every second
/// ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
/// scheduler.scheduleAtFixedRate(() -> {
///     MetricsSnapshot snapshot = metrics.snapshot();
///
///     System.out.printf("=== Execution Dashboard ===%n");
///     System.out.printf("Active Trials: %d%n",
///         snapshot.gauge("active_trials"));
///     System.out.printf("Completed: %d%n",
///         snapshot.counter("trials_completed"));
///     System.out.printf("Failed: %d%n",
///         snapshot.counter("trials_failed"));
///     System.out.printf("Success Rate: %.1f%%%n",
///         snapshot.rate("success_rate") * 100);
///     System.out.printf("Avg Duration: %.1fs%n",
///         snapshot.histogram("trial_duration_seconds").mean());
/// }, 0, 1, TimeUnit.SECONDS);
/// ```
///
/// ### Example 3: Progress Tracking
///
/// ```java
/// Observer observer = Observer.create();
///
/// observer.subscribe(ProgressEvent.class, event -> {
///     double progress = event.progress();
///     int completed = event.completedTrials();
///     int total = event.totalTrials();
///
///     // Update progress bar
///     int barWidth = 50;
///     int filled = (int) (progress * barWidth);
///     String bar = "█".repeat(filled) + "░".repeat(barWidth - filled);
///
///     System.out.printf("\r[%s] %d/%d trials (%.1f%%) ETA: %s",
///         bar,
///         completed,
///         total,
///         progress * 100,
///         event.estimatedTimeRemaining().orElse(Duration.ZERO));
/// });
/// ```
///
/// ### Example 4: Alert Configuration
///
/// ```java
/// Observer observer = Observer.create();
///
/// // Configure alert rules
/// AlertRule highErrorRate = AlertRule.builder()
///     .name("high_error_rate")
///     .condition(ctx -> ctx.metric("error_rate") > 0.10)
///     .duration(Duration.ofMinutes(5))
///     .severity(AlertSeverity.WARNING)
///     .action(alert -> sendSlackNotification(alert))
///     .build();
///
/// AlertRule resourceExhaustion = AlertRule.builder()
///     .name("resource_exhaustion")
///     .condition(ctx -> ctx.metric("available_memory_gb") < 10.0)
///     .severity(AlertSeverity.CRITICAL)
///     .action(alert -> {
///         pauseExecution();
///         pageOnCall(alert);
///     })
///     .build();
///
/// observer.addAlertRule(highErrorRate);
/// observer.addAlertRule(resourceExhaustion);
/// ```
///
/// ### Example 5: Distributed Tracing
///
/// ```java
/// Observer observer = Observer.create();
/// TracingCollector tracing = observer.tracingCollector();
///
/// // Subscribe to create spans
/// observer.subscribe(StepEvent.class, event -> {
///     if (event.type() == EventType.STEP_STARTED) {
///         Span span = tracing.startSpan(
///             event.stepId(),
///             event.executionId());
///         span.setTag("step_type", event.stepType());
///         span.setTag("trial_id", event.trialId());
///     } else if (event.type() == EventType.STEP_COMPLETED) {
///         Span span = tracing.getSpan(event.stepId());
///         span.setTag("duration_ms", event.duration().toMillis());
///         span.finish();
///     }
/// });
///
/// // Export traces to Jaeger
/// tracing.setExporter(JaegerExporter.create("localhost:14268"));
/// ```
///
/// ## Contract Requirements
///
/// ### Reliability
/// - Observer MUST NOT lose events
/// - Observer MUST NOT block event sources
/// - Observer MUST handle subscriber failures gracefully
///
/// ### Performance
/// - Observer SHOULD process events asynchronously
/// - Observer SHOULD buffer events under high load
/// - Observer SHOULD NOT impact execution performance
///
/// ### Consistency
/// - Observer MUST deliver events in order per source
/// - Observer MUST preserve event timestamps
/// - Observer MUST maintain event causality
///
/// @see io.nosqlbench.paramodel.execution.Executor
/// @see Event
/// @see MetricsCollector
///
public interface Observer {

    ///
    /// Creates an observer with default configuration.
    ///
    /// @return Observer instance
    ///
    static Observer create() {
        throw new UnsupportedOperationException(
            "Observer.create() requires a concrete implementation");
    }

    ///
    /// Subscribes to events of a specific type.
    ///
    /// @param eventType Event type to subscribe to
    /// @param handler Event handler
    /// @param <T> Event type
    /// @return Subscription handle
    ///
    <T extends Event> Subscription subscribe(Class<T> eventType, EventHandler<T> handler);

    ///
    /// Subscribes to all events.
    ///
    /// @param handler Event handler
    /// @return Subscription handle
    ///
    Subscription subscribeAll(EventHandler<Event> handler);

    ///
    /// Emits an event.
    ///
    /// @param event Event to emit
    ///
    void emit(Event event);

    ///
    /// Returns the metrics collector.
    ///
    /// @return Metrics collector
    ///
    MetricsCollector metricsCollector();

    ///
    /// Returns the tracing collector.
    ///
    /// @return Tracing collector
    ///
    TracingCollector tracingCollector();

    ///
    /// Adds an alert rule.
    ///
    /// @param rule Alert rule
    ///
    void addAlertRule(AlertRule rule);

    ///
    /// Returns all active alerts.
    ///
    /// @return Active alerts
    ///
    List<Alert> activeAlerts();

    ///
    /// Event.
    ///
    interface Event {
        String id();
        Instant timestamp();
        EventType type();
        String source();
        String executionId();
        EventSeverity severity();
        Map<String, Object> payload();
        Map<String, String> tags();
    }

    ///
    /// Event type.
    ///
    enum EventType {
        // Lifecycle
        EXECUTION_STARTED,
        EXECUTION_COMPLETED,
        EXECUTION_FAILED,
        EXECUTION_CANCELLED,
        EXECUTION_PAUSED,
        EXECUTION_RESUMED,

        // Progress
        PHASE_CHANGED,
        PROGRESS_UPDATED,
        TRIAL_BATCH_COMPLETED,
        CHECKPOINT_CREATED,

        // Steps
        STEP_STARTED,
        STEP_COMPLETED,
        STEP_FAILED,
        STEP_RETRIED,

        // Trials
        TRIAL_STARTED,
        TRIAL_COMPLETED,
        TRIAL_FAILED,
        TRIAL_SKIPPED,

        // Resources
        ELEMENT_DEPLOYED,
        ELEMENT_READY,
        ELEMENT_UNHEALTHY,
        ELEMENT_TORN_DOWN,
        RESOURCE_EXHAUSTED,

        // Errors
        COMPILATION_ERROR,
        DEPLOYMENT_ERROR,
        EXECUTION_ERROR,
        TIMEOUT_ERROR
    }

    ///
    /// Event severity.
    ///
    enum EventSeverity {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    ///
    /// Trial event.
    ///
    interface TrialEvent extends Event {
        String trialId();
        Optional<TrialResult> result();
    }

    ///
    /// Step event.
    ///
    interface StepEvent extends Event {
        String stepId();
        Optional<AtomicStep> step();
        Optional<Duration> duration();
    }

    ///
    /// Progress event.
    ///
    interface ProgressEvent extends Event {
        double progress();
        int completedTrials();
        int totalTrials();
        Optional<Duration> estimatedTimeRemaining();
    }

    ///
    /// Error event.
    ///
    interface ErrorEvent extends Event {
        String message();
        Optional<Throwable> error();
        Optional<String> suggestion();
    }

    ///
    /// Event handler.
    ///
    @FunctionalInterface
    interface EventHandler<T extends Event> {
        void handle(T event);
    }

    ///
    /// Subscription handle.
    ///
    interface Subscription {
        void unsubscribe();
        boolean isActive();
    }

    ///
    /// Metrics collector.
    ///
    interface MetricsCollector {
        void incrementCounter(String name);
        void setGauge(String name, double value);
        void recordHistogram(String name, double value);
        MetricsSnapshot snapshot();
    }

    ///
    /// Metrics snapshot.
    ///
    interface MetricsSnapshot {
        long counter(String name);
        double gauge(String name);
        Histogram histogram(String name);
        double rate(String name);
        Instant timestamp();
    }

    ///
    /// Histogram statistics.
    ///
    interface Histogram {
        double mean();
        double p50();
        double p95();
        double p99();
        double min();
        double max();
    }

    ///
    /// Tracing collector.
    ///
    interface TracingCollector {
        Span startSpan(String name, String traceId);
        Optional<Span> getSpan(String spanId);
        void setExporter(TraceExporter exporter);
    }

    ///
    /// Trace span.
    ///
    interface Span {
        String spanId();
        String traceId();
        void setTag(String key, Object value);
        void log(String message);
        void finish();
    }

    ///
    /// Trace exporter.
    ///
    interface TraceExporter {
        void export(List<Span> spans);
    }

    ///
    /// Alert rule.
    ///
    interface AlertRule {
        String name();
        AlertCondition condition();
        Duration duration();
        AlertSeverity severity();
        AlertAction action();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "AlertRule.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder name(String name);
            Builder condition(AlertCondition condition);
            Builder duration(Duration duration);
            Builder severity(AlertSeverity severity);
            Builder action(AlertAction action);
            AlertRule build();
        }
    }

    ///
    /// Alert condition.
    ///
    @FunctionalInterface
    interface AlertCondition {
        boolean test(AlertContext context);
    }

    ///
    /// Alert context.
    ///
    interface AlertContext {
        double metric(String name);
        Optional<Event> latestEvent(EventType type);
    }

    ///
    /// Alert action.
    ///
    @FunctionalInterface
    interface AlertAction {
        void execute(Alert alert);
    }

    ///
    /// Alert.
    ///
    interface Alert {
        String id();
        String ruleName();
        AlertSeverity severity();
        String message();
        Instant triggeredAt();
        Map<String, Object> context();
    }

    ///
    /// Alert severity.
    ///
    enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}
