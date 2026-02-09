package io.nosqlbench.paramodel.execution;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// # Runtime
///
/// Provides runtime services for executing trials, managing element instances, collecting
/// metrics, and coordinating resources. The runtime acts as the execution environment
/// that supports the {@link Executor} during trial execution.
///
/// ## Runtime Architecture
///
/// The runtime provides core services to the executor:
///
/// ```
/// Runtime Services:
///
/// Element Lifecycle Management
///   ├─ Deploy element instances
///   ├─ Monitor instance health
///   ├─ Manage instance connectivity
///   └─ Teardown instances
///
/// Trial Execution
///   ├─ Execute trial logic
///   ├─ Bind trials to elements
///   ├─ Capture trial outputs
///   └─ Handle trial failures
///
/// Resource Management
///   ├─ Allocate resources
///   ├─ Track resource usage
///   ├─ Enforce resource limits
///   └─ Release resources
///
/// Observability
///   ├─ Collect metrics
///   ├─ Stream logs
///   ├─ Capture artifacts
///   └─ Emit events
/// ```
///
/// ## Element Instance Lifecycle
///
/// The runtime manages element instances from creation to destruction:
///
/// ```
/// Instance Lifecycle:
///
/// PROVISIONING
///   ├─ Allocate infrastructure (VMs, containers, etc.)
///   ├─ Install dependencies
///   ├─ Configure instance
///   └─ Start services
///   ↓
/// STARTING
///   ├─ Wait for process startup
///   ├─ Establish network connectivity
///   ├─ Run initialization scripts
///   └─ Execute warmup procedures
///   ↓
/// HEALTH_CHECK
///   ├─ Verify service responsiveness
///   ├─ Check critical dependencies
///   ├─ Validate configuration
///   └─ Confirm readiness
///   ↓
/// READY
///   ├─ Accept trial connections
///   ├─ Process requests
///   ├─ Report metrics
///   └─ Remain available
///   ↓
/// STOPPING
///   ├─ Drain active connections
///   ├─ Complete pending operations
///   ├─ Flush buffers
///   └─ Prepare for teardown
///   ↓
/// TERMINATED
///   ├─ Collect final artifacts (logs, dumps, etc.)
///   ├─ Release resources
///   ├─ Clean up storage
///   └─ Report final metrics
/// ```
///
/// ## Trial Execution Context
///
/// Each trial executes within an isolated context:
///
/// ```
/// Trial Execution Context:
///
/// Trial: trial_42
///   Parameters:
///     cache_size: 256
///     concurrency: 50
///
///   Element Bindings:
///     database → db_instance_1 (endpoint: 10.0.1.5:5432)
///     cache → cache_instance_256 (endpoint: 10.0.2.8:6379)
///     app → app_instance_42 (endpoint: 10.0.3.12:8080)
///
///   Execution Environment:
///     workdir: /tmp/trials/trial_42
///     timeout: 5 minutes
///     retry_policy: exponential_backoff(3)
///
///   Resources:
///     cpu: 2.0 cores
///     memory: 4 GB
///     storage: 10 GB
///     network: 1 Gbps
///
///   Observability:
///     metrics_sink: prometheus:9090
///     log_sink: elasticsearch:9200
///     trace_sink: jaeger:14268
/// ```
///
/// ## Resource Isolation
///
/// The runtime provides isolation between concurrent trials:
///
/// ```
/// Resource Isolation:
///
/// Namespace: trial_42
///   Process Group: PID 12345
///   Network Namespace: net_42
///   Filesystem: /mnt/trial_42
///   Environment Variables:
///     TRIAL_ID=trial_42
///     TRIAL_WORKDIR=/mnt/trial_42
///     DB_HOST=10.0.1.5
///     CACHE_HOST=10.0.2.8
///
/// Namespace: trial_43
///   Process Group: PID 12389
///   Network Namespace: net_43
///   Filesystem: /mnt/trial_43
///   Environment Variables:
///     TRIAL_ID=trial_43
///     TRIAL_WORKDIR=/mnt/trial_43
///     DB_HOST=10.0.1.5
///     CACHE_HOST=10.0.2.9
///
/// Isolation Guarantees:
///   ✓ Trials cannot access each other's filesystems
///   ✓ Trials have independent network namespaces
///   ✓ Trials have isolated process trees
///   ✓ Trials have separate environment variables
/// ```
///
/// ## Metrics Collection
///
/// The runtime continuously collects metrics:
///
/// ```
/// Metrics Collection:
///
/// System Metrics (per instance):
///   cpu_usage: 2.3 cores
///   memory_usage: 3.8 GB
///   disk_read_bytes: 1.2 GB
///   disk_write_bytes: 450 MB
///   network_rx_bytes: 2.1 GB
///   network_tx_bytes: 780 MB
///
/// Application Metrics (per trial):
///   requests_processed: 10,450
///   requests_failed: 23
///   avg_response_time_ms: 45.2
///   p95_response_time_ms: 120.8
///   p99_response_time_ms: 250.1
///
/// Custom Metrics:
///   cache_hit_rate: 0.87
///   cache_evictions: 125
///   db_query_count: 8,230
///   db_slow_queries: 12
///
/// Collection Strategy:
///   - Poll every 1 second
///   - Buffer in memory
///   - Flush to storage every 10 seconds
///   - Aggregate on completion
/// ```
///
/// ## Error Recovery
///
/// The runtime handles various failure scenarios:
///
/// ```
/// Error Recovery Strategies:
///
/// Transient Element Failure:
///   cache_instance_50 becomes unresponsive
///   ↓
///   Health check fails
///   ↓
///   Retry health check (3 attempts)
///   ↓
///   Still unresponsive → Mark UNHEALTHY
///   ↓
///   Redeploy instance
///   ↓
///   Wait for new instance READY
///   ↓
///   Resume affected trials
///
/// Trial Timeout:
///   trial_42 exceeds 5 minute timeout
///   ↓
///   Send SIGTERM to trial process
///   ↓
///   Wait 30 seconds for graceful shutdown
///   ↓
///   Send SIGKILL if still running
///   ↓
///   Collect partial results
///   ↓
///   Mark trial as TIMEOUT
///   ↓
///   Apply retry policy
///
/// Resource Exhaustion:
///   System running low on memory
///   ↓
///   Stop accepting new trials
///   ↓
///   Wait for running trials to complete
///   ↓
///   Garbage collect artifacts
///   ↓
///   Resume execution when memory available
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Deploying an Element
///
/// ```java
/// Runtime runtime = Runtime.create();
///
/// Element element = ...; // "database" with max_connections and shared_buffers parameters
///
/// DeploymentRequest request = DeploymentRequest.builder()
///     .element(element)
///     .instanceId("db_instance_1")
///     .resources(Resources.of(4.0, 8.0, 100.0))
///     .build();
///
/// ElementInstance instance = runtime.deploy(request);
///
/// System.out.printf("Deployed %s at %s%n",
///     instance.instanceId(),
///     instance.endpoint());
///
/// // Wait for ready
/// runtime.awaitReady(instance, Duration.ofMinutes(5));
/// ```
///
/// ### Example 2: Executing a Trial
///
/// ```java
/// Runtime runtime = Runtime.create();
///
/// Trial trial = /* ... */;
/// Map<String, ElementInstance> bindings = Map.of(
///     "database", dbInstance,
///     "cache", cacheInstance
/// );
///
/// TrialExecutionRequest request = TrialExecutionRequest.builder()
///     .trial(trial)
///     .elementBindings(bindings)
///     .timeout(Duration.ofMinutes(5))
///     .resources(Resources.of(2.0, 4.0, 10.0))
///     .build();
///
/// TrialResult result = runtime.executeTrial(request);
///
/// System.out.printf("Trial %s: %s in %s%n",
///     result.trial().id(),
///     result.status(),
///     result.timing().duration());
/// ```
///
/// ### Example 3: Monitoring Instance Health
///
/// ```java
/// Runtime runtime = Runtime.create();
/// ElementInstance instance = /* ... */;
///
/// HealthStatus health = runtime.checkHealth(instance);
///
/// if (health.isHealthy()) {
///     System.out.printf("Instance %s is healthy%n", instance.instanceId());
/// } else {
///     System.err.printf("Instance %s is unhealthy: %s%n",
///         instance.instanceId(),
///         health.reason());
///
///     // Attempt recovery
///     runtime.restart(instance);
/// }
/// ```
///
/// ### Example 4: Collecting Metrics
///
/// ```java
/// Runtime runtime = Runtime.create();
/// ElementInstance instance = /* ... */;
///
/// // Start metrics collection
/// MetricsCollector collector = runtime.metricsCollector();
/// collector.start(instance);
///
/// // Execute trials...
///
/// // Retrieve metrics
/// MetricsSnapshot snapshot = collector.snapshot(instance);
///
/// System.out.printf("CPU usage: %.2f cores%n",
///     snapshot.metric("cpu_usage").asDouble());
/// System.out.printf("Memory usage: %.2f GB%n",
///     snapshot.metric("memory_usage_gb").asDouble());
/// System.out.printf("Network I/O: %.2f MB%n",
///     snapshot.metric("network_bytes_total").asDouble() / 1e6);
/// ```
///
/// ### Example 5: Resource Management
///
/// ```java
/// Runtime runtime = Runtime.create();
///
/// // Check available resources
/// ResourceAvailability available = runtime.availableResources();
///
/// System.out.printf("Available resources:%n");
/// System.out.printf("  CPU: %.1f cores%n", available.cpu());
/// System.out.printf("  Memory: %.1f GB%n", available.memoryGb());
/// System.out.printf("  Storage: %.1f GB%n", available.storageGb());
///
/// // Allocate resources for trial
/// ResourceAllocation allocation = runtime.allocateResources(
///     Resources.of(2.0, 4.0, 10.0));
///
/// try {
///     // Execute trial with allocated resources
///     executeTrial(allocation);
/// } finally {
///     // Release resources
///     runtime.releaseResources(allocation);
/// }
/// ```
///
/// ## Contract Requirements
///
/// ### Instance Management
/// - Runtime MUST ensure instance uniqueness (no duplicate instance IDs)
/// - Runtime MUST verify instance readiness before marking READY
/// - Runtime MUST clean up all resources on teardown
///
/// ### Isolation
/// - Runtime MUST isolate concurrent trials
/// - Runtime MUST prevent resource leaks across trials
/// - Runtime MUST enforce resource limits
///
/// ### Observability
/// - Runtime MUST collect metrics for all instances
/// - Runtime MUST capture logs and artifacts
/// - Runtime MUST provide real-time status
///
/// ### Fault Tolerance
/// - Runtime MUST handle instance failures gracefully
/// - Runtime MUST support retry on transient errors
/// - Runtime MUST preserve partial results on failure
///
/// @see Executor
/// @see ElementInstance
/// @see TrialResult
///
public interface Runtime {

    ///
    /// Creates a runtime with default configuration.
    ///
    /// @return Runtime instance
    ///
    static Runtime create() {
        throw new UnsupportedOperationException(
            "Runtime.create() requires a concrete implementation");
    }

    ///
    /// Creates a runtime with specified configuration.
    ///
    /// @param config Runtime configuration
    /// @return Runtime instance
    ///
    static Runtime create(RuntimeConfig config) {
        throw new UnsupportedOperationException(
            "Runtime.create(config) requires a concrete implementation");
    }

    ///
    /// Deploys an element instance.
    ///
    /// @param request Deployment request
    /// @return Deployed element instance
    /// @throws DeploymentException if deployment fails
    ///
    ElementInstance deploy(DeploymentRequest request) throws DeploymentException;

    ///
    /// Waits for an instance to become ready.
    ///
    /// @param instance Element instance
    /// @param timeout Maximum time to wait
    /// @throws TimeoutException if instance not ready within timeout
    /// @throws InterruptedException if interrupted while waiting
    ///
    void awaitReady(ElementInstance instance, Duration timeout)
        throws TimeoutException, InterruptedException;

    ///
    /// Checks the health of an element instance.
    ///
    /// @param instance Element instance
    /// @return Health status
    ///
    HealthStatus checkHealth(ElementInstance instance);

    ///
    /// Restarts an element instance.
    ///
    /// @param instance Element instance
    /// @throws DeploymentException if restart fails
    ///
    void restart(ElementInstance instance) throws DeploymentException;

    ///
    /// Tears down an element instance.
    ///
    /// @param instance Element instance
    /// @param collectArtifacts Whether to collect artifacts before teardown
    ///
    void teardown(ElementInstance instance, boolean collectArtifacts);

    ///
    /// Executes a trial.
    ///
    /// @param request Trial execution request
    /// @return Trial result
    /// @throws TrialExecutionException if trial execution fails
    ///
    TrialResult executeTrial(TrialExecutionRequest request)
        throws TrialExecutionException;

    ///
    /// Returns available resources.
    ///
    /// @return Resource availability
    ///
    ResourceAvailability availableResources();

    ///
    /// Allocates resources.
    ///
    /// @param resources Resources to allocate
    /// @return Resource allocation
    /// @throws InsufficientResourcesException if resources not available
    ///
    ResourceAllocation allocateResources(Resources resources)
        throws InsufficientResourcesException;

    ///
    /// Releases allocated resources.
    ///
    /// @param allocation Resource allocation to release
    ///
    void releaseResources(ResourceAllocation allocation);

    ///
    /// Returns the metrics collector.
    ///
    /// @return Metrics collector
    ///
    MetricsCollector metricsCollector();

    ///
    /// Returns the runtime configuration.
    ///
    /// @return Runtime configuration
    ///
    RuntimeConfig config();

    ///
    /// Element instance.
    ///
    interface ElementInstance {
        String instanceId();
        Element element();
        String endpoint();
        InstanceState state();
        Map<String, Object> configuration();
        Instant deployedAt();
        Optional<Instant> readyAt();
    }

    ///
    /// Instance state.
    ///
    enum InstanceState {
        PROVISIONING,
        STARTING,
        HEALTH_CHECK,
        READY,
        UNHEALTHY,
        STOPPING,
        TERMINATED
    }

    ///
    /// Deployment request.
    ///
    interface DeploymentRequest {
        Element element();
        String instanceId();
        Resources resources();
        Map<String, Object> configuration();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "DeploymentRequest.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder element(Element element);
            Builder instanceId(String instanceId);
            Builder resources(Resources resources);
            Builder configuration(Map<String, Object> config);
            Builder config(String key, Object value);
            DeploymentRequest build();
        }
    }

    ///
    /// Trial execution request.
    ///
    interface TrialExecutionRequest {
        Trial trial();
        Map<String, ElementInstance> elementBindings();
        Duration timeout();
        Resources resources();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "TrialExecutionRequest.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder trial(Trial trial);
            Builder elementBindings(Map<String, ElementInstance> bindings);
            Builder elementBinding(String name, ElementInstance instance);
            Builder timeout(Duration timeout);
            Builder resources(Resources resources);
            TrialExecutionRequest build();
        }
    }

    ///
    /// Resource specification.
    ///
    record Resources(double cpu, double memoryGb, double storageGb) {
        public static Resources of(double cpu, double memoryGb, double storageGb) {
            return new Resources(cpu, memoryGb, storageGb);
        }
    }

    ///
    /// Resource availability.
    ///
    record ResourceAvailability(
        double cpu,
        double memoryGb,
        double storageGb,
        double networkGbps
    ) {}

    ///
    /// Resource allocation.
    ///
    interface ResourceAllocation {
        String allocationId();
        Resources resources();
        Instant allocatedAt();
        void release();
    }

    ///
    /// Health status.
    ///
    interface HealthStatus {
        boolean isHealthy();
        Optional<String> reason();
        Map<String, Object> details();
    }

    ///
    /// Metrics collector.
    ///
    interface MetricsCollector {
        void start(ElementInstance instance);
        void stop(ElementInstance instance);
        MetricsSnapshot snapshot(ElementInstance instance);
        List<MetricsSnapshot> history(ElementInstance instance);
    }

    ///
    /// Metrics snapshot.
    ///
    interface MetricsSnapshot {
        Instant timestamp();
        Map<String, MetricValue> metrics();
        MetricValue metric(String name);
    }

    ///
    /// Metric value.
    ///
    interface MetricValue {
        double asDouble();
        long asLong();
        String asString();
        boolean asBoolean();
    }

    ///
    /// Runtime configuration.
    ///
    interface RuntimeConfig {
        Duration defaultHealthCheckTimeout();
        Duration defaultDeploymentTimeout();
        Duration defaultTrialTimeout();
        Map<String, Object> customConfig();
    }

    ///
    /// Exception thrown when deployment fails.
    ///
    class DeploymentException extends Exception {
        public DeploymentException(String message) {
            super(message);
        }

        public DeploymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    ///
    /// Exception thrown when trial execution fails.
    ///
    class TrialExecutionException extends Exception {
        public TrialExecutionException(String message) {
            super(message);
        }

        public TrialExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    ///
    /// Exception thrown when resources are insufficient.
    ///
    class InsufficientResourcesException extends Exception {
        public InsufficientResourcesException(String message) {
            super(message);
        }
    }

    ///
    /// Exception thrown when operation times out.
    ///
    class TimeoutException extends Exception {
        public TimeoutException(String message) {
            super(message);
        }
    }
}
