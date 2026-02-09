package io.nosqlbench.paramodel.plan;

import java.util.Map;
import java.util.Optional;

///
/// An instantiable/deployable resource required for trial execution.
///
/// ## Concept
///
/// An {@code Element} represents any resource that must be provisioned, started,
/// monitored, and stopped during study execution. Elements have lifecycles and
/// can have dependencies on other elements.
///
/// ## Element Categories
///
/// ```
/// Element Types:
/// │
/// ├── Services
/// │   └── API servers, databases, message queues, etc.
/// │
/// ├── Environments
/// │   └── Containers, VMs, sandboxes
/// │
/// ├── Caches
/// │   └── In-memory caches, distributed caches
/// │
/// ├── Datasets
/// │   └── Training data, reference data, test data
/// │
/// └── Tools
///     └── Profilers, monitors, loggers
/// ```
///
/// ## Element Lifecycle
///
/// ```
/// Lifecycle States:
///
///   NOT_STARTED
///       ↓
///   STARTING (provision, configure)
///       ↓
///   READY (health check passed)
///       ↓
///   RUNNING (in use by trials)
///       ↓
///   STOPPING (graceful shutdown)
///       ↓
///   STOPPED
///       ↓
///   TEARDOWN (cleanup resources)
///       ↓
///   TERMINATED
/// ```
///
/// ## Element Structure
///
/// ```
/// Element
/// ├── name: String
/// │   └── Unique identifier
/// │
/// ├── type: ElementType
/// │   └── SERVICE | ENVIRONMENT | CACHE | DATASET | TOOL
/// │
/// ├── configuration: Map<String, Object>
/// │   └── Type-specific config (endpoints, resources, etc.)
/// │
/// ├── dependencies: List<Element>
/// │   └── Other elements this depends on
/// │
/// └── healthCheck: Optional<HealthCheckSpec>
///     └── How to verify element is ready
/// ```
///
/// ## Dependency Examples
///
/// ```
/// Element Dependency Chain:
///
/// LoadBalancer
///     ↓ depends on
/// AppServer (N instances)
///     ↓ depends on
/// Database
///     ↓ depends on
/// StorageVolume
/// ```
///
/// ## Relationship Types
///
/// Elements relate via {@link RelationshipType}:
///
/// ```
/// Database ←MUTUALLY_EXCLUSIVE→ AppServer
///   (Only one trial uses database at a time)
///
/// Cache ←SHARED→ AppServer
///   (All trials share cache instance)
///
/// Container ←INSTANCED_PER→ TestRunner
///   (Each trial gets its own container)
/// ```
///
/// ## Usage Example: Service Element
///
/// ```java
/// Element database = Element.service("postgres")
///     .withConfiguration(Map.of(
///         "host", "localhost",
///         "port", 5432,
///         "database", "testdb",
///         "max_connections", 100
///     ))
///     .withHealthCheck(HealthCheckSpec.tcp("localhost", 5432)
///         .withTimeout(Duration.ofSeconds(30))
///         .withRetries(3)
///     )
///     .build();
/// ```
///
/// ## Usage Example: Container Element
///
/// ```java
/// Element container = Element.environment("docker-container")
///     .withConfiguration(Map.of(
///         "image", "ubuntu:22.04",
///         "memory_mb", 2048,
///         "cpu_cores", 2,
///         "network_mode", "bridge"
///     ))
///     .withHealthCheck(HealthCheckSpec.command("curl http://localhost:8080/health"))
///     .build();
/// ```
///
/// ## Usage Example: Cache Element
///
/// ```java
/// Element cache = Element.cache("redis-cache")
///     .withConfiguration(Map.of(
///         "host", "cache.example.com",
///         "port", 6379,
///         "max_memory_mb", 4096,
///         "eviction_policy", "lru"
///     ))
///     .withHealthCheck(HealthCheckSpec.tcp("cache.example.com", 6379))
///     .build();
/// ```
///
/// ## Usage Example: Dataset Element
///
/// ```java
/// Element dataset = Element.dataset("training-data")
///     .withConfiguration(Map.of(
///         "path", "s3://bucket/datasets/train.parquet",
///         "size_gb", 10.5,
///         "format", "parquet",
///         "partitions", 100
///     ))
///     .build();
/// ```
///
/// ## Usage Example: Dependencies
///
/// ```java
/// Element storage = Element.environment("storage-volume");
/// Element database = Element.service("postgres")
///     .dependsOn(storage)
///     .build();
///
/// Element appServer = Element.service("app-server")
///     .dependsOn(database)
///     .build();
///
/// Element loadBalancer = Element.service("load-balancer")
///     .dependsOn(appServer)
///     .build();
///
/// // Lifecycle order: storage → database → appServer → loadBalancer
/// ```
///
/// ## Resource Specification
///
/// Elements can specify resource requirements:
///
/// ```java
/// Element mlModel = Element.service("ml-model")
///     .withResourceRequirements(
///         ResourceSpec.builder()
///             .cpu(4.0)           // 4 CPU cores
///             .memoryMb(8192)     // 8GB RAM
///             .gpus(1)            // 1 GPU
///             .diskMb(50000)      // 50GB disk
///             .build()
///     )
///     .build();
/// ```
///
/// ## Health Checks
///
/// Elements define how to verify they're ready:
///
/// ```
/// HealthCheck Types:
/// │
/// ├── TCP Connection
/// │   └── Check if port is open
/// │
/// ├── HTTP GET
/// │   └── Check endpoint returns 200
/// │
/// ├── Command Execution
/// │   └── Run shell command, check exit code
/// │
/// └── Custom Predicate
///     └── User-defined readiness check
/// ```
///
/// ## Lifecycle Management
///
/// ResourceOrchestrator manages element lifecycles:
///
/// ```java
/// // Execution plan includes element lifecycle
/// ExecutionPlan plan = testPlan.commit();
///
/// // Orchestrator starts elements before trials
/// ResourceOrchestrator orchestrator = ...;
/// orchestrator.startElement(database);
/// orchestrator.waitUntilReady(database);  // Polls health check
///
/// // Trials execute
/// for (Trial trial : plan.trials()) {
///     executor.execute(trial);
/// }
///
/// // Orchestrator stops elements after trials
/// orchestrator.stopElement(database);
/// orchestrator.teardownElement(database);
/// ```
///
/// ## Instancing Scopes
///
/// For {@link RelationshipType#INSTANCED_PER}, specify scope:
///
/// ```java
/// Element container = Element.environment("container")
///     .withInstancingScope(InstancingScope.PER_TRIAL)
///     .build();
/// // Each trial gets its own container instance
///
/// Element tempDb = Element.database("temp-db")
///     .withInstancingScope(InstancingScope.PER_GROUP)
///     .build();
/// // Trials in same group share database
/// ```
///
/// ## Configuration Validation
///
/// Elements validate their configuration:
///
/// ```java
/// Element element = Element.service("api")
///     .withConfiguration(Map.of(
///         "endpoint", "https://api.example.com",
///         "timeout_ms", 5000,
///         "retries", 3
///     ))
///     .build();
///
/// // Builder validates required fields present
/// // Execution plan validation checks config is valid
/// ```
///
/// @see TestPlan
/// @see RelationshipType
/// @since 0.1.0
///
public interface Element {

    ///
    /// Returns the unique name of this element within the study.
    ///
    /// ## Contract
    ///
    /// - MUST be non-null, non-empty
    /// - MUST be unique within TestPlan
    /// - SHOULD be human-readable
    ///
    /// ## Example Names
    ///
    /// ```
    /// "postgres-db"
    /// "redis-cache"
    /// "ml-model-server"
    /// "docker-container"
    /// "training-dataset"
    /// ```
    ///
    /// @return element name, never null or empty
    ///
    String name();

    ///
    /// Returns the type category of this element.
    ///
    /// ## Element Types
    ///
    /// ```
    /// SERVICE      - Long-running server/daemon
    /// ENVIRONMENT  - Execution environment (container, VM)
    /// CACHE        - Caching layer (Redis, Memcached)
    /// DATASET      - Data resource (S3 object, database)
    /// TOOL         - Utility (profiler, monitor, logger)
    /// ```
    ///
    /// Type affects:
    /// - Lifecycle management strategy
    /// - Health check defaults
    /// - Resource allocation
    ///
    /// @return element type, never null
    ///
    ElementType type();

    ///
    /// Returns type-specific configuration for this element.
    ///
    /// ## Configuration Schema
    ///
    /// Schema depends on element type:
    ///
    /// ```
    /// SERVICE:
    ///   - endpoint, port, protocol
    ///   - timeouts, retries
    ///   - authentication
    ///
    /// ENVIRONMENT:
    ///   - image, memory, cpu
    ///   - volumes, network
    ///   - environment variables
    ///
    /// CACHE:
    ///   - host, port
    ///   - max_memory, eviction_policy
    ///   - connection_pool_size
    ///
    /// DATASET:
    ///   - path, format, size
    ///   - partitions, schema
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Map<String, Object> config = element.configuration();
    /// String endpoint = (String) config.get("endpoint");
    /// Integer timeout = (Integer) config.get("timeout_ms");
    /// ```
    ///
    /// @return immutable configuration map, never null
    ///
    Map<String, Object> configuration();

    ///
    /// Returns elements this element depends on.
    ///
    /// ## Dependency Semantics
    ///
    /// If A depends on B:
    /// - B must start before A
    /// - B must be ready before A starts
    /// - A must stop before B stops
    ///
    /// ## Lifecycle Ordering
    ///
    /// ```
    /// Dependencies form a DAG (Directed Acyclic Graph):
    ///
    /// A → B → D
    /// ↓   ↓
    /// C   E
    ///
    /// Start order: D, E, B, C, A
    /// Stop order:  A, C, B, E, D (reverse)
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Element storage = ...;
    /// Element db = Element.service("db").dependsOn(storage).build();
    /// Element app = Element.service("app").dependsOn(db).build();
    ///
    /// List<Element> deps = app.dependencies();
    /// // [db]
    /// // (Transitive: db → storage)
    /// ```
    ///
    /// @return immutable list of dependencies, never null (may be empty)
    ///
    java.util.List<Element> dependencies();

    ///
    /// Returns the health check specification for this element.
    ///
    /// ## Health Check Purpose
    ///
    /// Determines when element is ready for use:
    /// ```
    /// START → [Health Check Loop] → READY
    ///                ↓
    ///         Retry if not healthy
    /// ```
    ///
    /// ## Health Check Types
    ///
    /// ```
    /// TCP:     HealthCheckSpec.tcp(host, port)
    /// HTTP:    HealthCheckSpec.http(url, expectedStatus)
    /// Command: HealthCheckSpec.command(shellCommand)
    /// Custom:  HealthCheckSpec.custom(predicate)
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Optional<HealthCheckSpec> hc = element.healthCheck();
    /// hc.ifPresent(check -> {
    ///     Duration timeout = check.timeout();
    ///     int retries = check.maxRetries();
    ///     System.out.printf("Health check: %s (timeout=%s, retries=%d)%n",
    ///         check.type(), timeout, retries);
    /// });
    /// ```
    ///
    /// @return health check spec if defined, empty otherwise
    ///
    Optional<HealthCheckSpec> healthCheck();

    ///
    /// Returns the instancing scope for this element if INSTANCED_PER.
    ///
    /// ## Instancing Scope
    ///
    /// For {@link RelationshipType#INSTANCED_PER} relationships:
    ///
    /// ```
    /// PER_TRIAL:
    ///   - New instance for each trial
    ///   - Lifecycle: start before trial, stop after trial
    ///
    /// PER_GROUP:
    ///   - New instance for each group of trials
    ///   - Lifecycle: start before group, stop after group
    ///
    /// PER_RUN:
    ///   - New instance for each run
    ///   - Lifecycle: start before run, stop after run
    /// ```
    ///
    /// ## Example
    ///
    /// ```java
    /// Element container = Element.environment("container")
    ///     .withInstancingScope(InstancingScope.PER_TRIAL)
    ///     .build();
    ///
    /// Optional<InstancingScope> scope = container.instancingScope();
    /// // Optional.of(InstancingScope.PER_TRIAL)
    /// ```
    ///
    /// @return instancing scope if this element is instanced per scope
    ///
    Optional<InstancingScope> instancingScope();

    ///
    /// Element type categories.
    ///
    enum ElementType {
        /// Long-running service/daemon (database, API server, message queue)
        SERVICE,

        /// Execution environment (container, VM, sandbox)
        ENVIRONMENT,

        /// Caching layer (Redis, Memcached, in-memory cache)
        CACHE,

        /// Data resource (S3 object, database table, file)
        DATASET,

        /// Utility tool (profiler, monitor, logger)
        TOOL
    }

    ///
    /// Instancing scope for INSTANCED_PER relationships.
    ///
    enum InstancingScope {
        /// One instance per trial
        PER_TRIAL,

        /// One instance per group of trials
        PER_GROUP,

        /// One instance per run
        PER_RUN
    }

    ///
    /// Health check specification for determining element readiness.
    ///
    interface HealthCheckSpec {
        /// Type of health check (TCP, HTTP, COMMAND, CUSTOM)
        String type();

        /// Maximum time to wait for health check to pass
        java.time.Duration timeout();

        /// Maximum number of retry attempts
        int maxRetries();

        /// Interval between retry attempts
        java.time.Duration retryInterval();
    }
}
