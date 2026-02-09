package io.nosqlbench.paramodel.elements;

import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.Tagged;

import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// A resource that participates in study execution, defined by its parameters.
///
/// ## Concept
///
/// An {@code Element} represents any resource that must be provisioned, started,
/// monitored, and stopped during study execution. Elements are made usable
/// because they carry a set of {@link Parameter} definitions that describe
/// their configurable dimensions.
///
/// Element **types** are not predetermined by the paramodel API. The concrete
/// type taxonomy (e.g. service, environment, cache, dataset, tool) is defined
/// by the adopting system. The paramodel API treats element type as an opaque
/// tag supplied via {@link #tags()}.
///
/// ## Element Maturity Levels
///
/// An element progresses through three maturity levels:
///
/// ```
/// Element Model (this interface)
///   │  Has parameter models — defines what can be configured
///   │  e.g. Parameter<Integer>("port", range(1024, 65535))
///   │       Parameter<String>("host", discrete("localhost", "0.0.0.0"))
///   │
///   ▼
/// Element Instance
///   │  Has bound parameter values — specific configuration chosen
///   │  e.g. port=5432, host="localhost"
///   │
///   ▼
/// Materialized Element
///      Has real-world configuration values — live resource handle
///      e.g. JDBC connection string, PID, health endpoint URL
/// ```
///
/// This interface represents the **model** level: it declares the parameter
/// space that defines the element, without binding specific values. Instances
/// and materialized elements are produced during compilation and execution,
/// respectively.
///
/// ## Element Lifecycle
///
/// ```
/// Lifecycle States:
///
///   NOT_STARTED
///       │
///   STARTING (provision, configure)
///       │
///   READY (health check passed)
///       │
///   RUNNING (in use by trials)
///       │
///   STOPPING (graceful shutdown)
///       │
///   STOPPED
///       │
///   TEARDOWN (cleanup resources)
///       │
///   TERMINATED
/// ```
///
/// ## Element Structure
///
/// ```
/// Element
/// ├── name: String
/// │   └── Unique identifier within a study
/// │
/// ├── parameters: List<Parameter<?>>
/// │   └── Configurable dimensions of this element
/// │
/// ├── dependencies: List<Element>
/// │   └── Other elements this depends on (DAG)
/// │
/// ├── healthCheck: Optional<HealthCheckSpec>
/// │   └── Readiness verification strategy
/// │
/// └── instancingScope: Optional<InstancingScope>
///     └── PER_TRIAL | PER_GROUP | PER_RUN
/// ```
///
/// ## Dependency Semantics
///
/// Elements form a directed acyclic graph (DAG) through dependencies:
///
/// ```
/// If A depends on B:
///   - B must start before A
///   - B must be ready (health check passed) before A starts
///   - A must stop before B stops
///
/// Example dependency chain:
///
///   LoadBalancer
///       depends on
///   AppServer (N instances)
///       depends on
///   Database
///       depends on
///   StorageVolume
///
/// Start order: StorageVolume → Database → AppServer → LoadBalancer
/// Stop order:  LoadBalancer → AppServer → Database → StorageVolume
/// ```
///
/// ## Relationship Types
///
/// Elements relate via {@link RelationshipType}:
///
/// ```
/// Database <──MUTUALLY_EXCLUSIVE──> AppServer
///   (Only one trial uses database at a time)
///
/// Cache <──SHARED──> AppServer
///   (All trials share cache instance)
///
/// Container <──INSTANCED_PER──> TestRunner
///   (Each trial gets its own container)
/// ```
///
/// ## Usage Examples
///
/// ```java
/// // Element model with parameters describing a database resource
/// Element database = ...;  // name="postgres", parameters=[port, host, maxConn]
///
/// // Query the parameter space
/// List<Parameter<?>> params = database.parameters();
/// // [Parameter<Integer>("port"), Parameter<String>("host"), ...]
///
/// // Dependencies
/// Element storage = ...;
/// Element db = ...; // depends on storage
/// List<Element> deps = db.dependencies();
/// // [storage]
/// ```
///
/// ## Instancing Scopes
///
/// For {@link RelationshipType#INSTANCED_PER} relationships, specify scope:
///
/// ```
/// PER_TRIAL:
///   New instance for each trial
///   Lifecycle: start before trial, stop after trial
///
/// PER_GROUP:
///   New instance for each group of trials
///   Lifecycle: start before group, stop after group
///
/// PER_RUN:
///   New instance for each run
///   Lifecycle: start before run, stop after run
/// ```
///
/// ## Health Checks
///
/// Elements define how to verify readiness:
///
/// ```
/// HealthCheck Types:
/// ├── TCP Connection  — check if port is open
/// ├── HTTP GET        — check endpoint returns 200
/// ├── Command         — run shell command, check exit code
/// └── Custom          — user-defined readiness predicate
/// ```
///
/// @see Parameter
/// @see RelationshipType
/// @see io.nosqlbench.paramodel.plan.TestPlan
/// @since 0.1.0
///
public interface Element extends Tagged {

    ///
    /// Returns the unique name of this element within the study.
    ///
    /// ## Contract
    ///
    /// - MUST be non-null, non-empty
    /// - MUST be unique within a {@link io.nosqlbench.paramodel.plan.TestPlan}
    /// - SHOULD be human-readable
    ///
    /// @return element name, never null or empty
    ///
    String name();

    ///
    /// Returns an unmodifiable map of tags describing this element.
    ///
    /// The map MUST contain at minimum a {@code "name"} entry whose value
    /// equals {@link #name()}. It SHOULD contain a {@code "type"} entry
    /// whose value is defined by the adopting system's type taxonomy
    /// (e.g. {@code "service"}, {@code "environment"}, {@code "cache"}).
    ///
    /// @return unmodifiable tag map, never null
    ///
    Map<String, String> tags();

    ///
    /// Returns the parameter models that define this element's configurable dimensions.
    ///
    /// Each parameter represents one dimension of the element's configuration space.
    /// At the **model** level, parameters declare what *can* be configured (domains,
    /// constraints, value generation). At the **instance** level, each parameter is
    /// bound to a specific value. At the **materialized** level, bound values are
    /// resolved into real-world configuration.
    ///
    /// ## Maturity Level Mapping
    ///
    /// ```
    /// Model:        parameters() → List<Parameter<?>>   (value space)
    /// Instance:     bindings()   → Map<String, Value<?>> (chosen values)
    /// Materialized: config()     → Map<String, Object>   (live values)
    /// ```
    ///
    /// ## Contract
    ///
    /// - MUST return a non-null, unmodifiable list
    /// - Parameter names within the list MUST be unique
    /// - Parameters MUST be immutable after element creation
    /// - MAY return an empty list for elements with no configurable dimensions
    ///
    /// ## Example
    ///
    /// ```java
    /// Element database = ...;
    /// List<Parameter<?>> params = database.parameters();
    /// // [Parameter<Integer>("port"), Parameter<String>("host"),
    /// //  Parameter<Integer>("max_connections")]
    ///
    /// for (Parameter<?> p : params) {
    ///     System.out.printf("  %s: domain=%s%n", p.name(), p.domain());
    /// }
    /// ```
    ///
    /// @return unmodifiable list of parameter models, never null
    /// @see Parameter
    ///
    List<Parameter<?>> parameters();

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
    /// │   │
    /// C   E
    ///
    /// Start order: D, E, B, C, A
    /// Stop order:  A, C, B, E, D (reverse)
    /// ```
    ///
    /// @return unmodifiable list of dependencies, never null (may be empty)
    ///
    List<Element> dependencies();

    ///
    /// Returns the health check specification for this element.
    ///
    /// Health checks determine when an element has transitioned from STARTING
    /// to READY and is available for use by trials.
    ///
    /// ## Health Check Loop
    ///
    /// ```
    /// START → [Health Check Loop] → READY
    ///               │
    ///         Retry if not healthy
    /// ```
    ///
    /// @return health check spec if defined, empty otherwise
    ///
    Optional<HealthCheckSpec> healthCheck();

    ///
    /// Returns the instancing scope for this element.
    ///
    /// For {@link RelationshipType#INSTANCED_PER} relationships, the scope
    /// determines how frequently new instances are created:
    ///
    /// ```
    /// PER_TRIAL: new instance for each trial
    /// PER_GROUP: new instance for each group of trials
    /// PER_RUN:   new instance for each run
    /// ```
    ///
    /// @return instancing scope if this element is instanced per scope
    ///
    Optional<InstancingScope> instancingScope();

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
