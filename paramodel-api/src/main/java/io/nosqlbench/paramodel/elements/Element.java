package io.nosqlbench.paramodel.elements;

import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ParameterView;
import io.nosqlbench.paramodel.parameters.Tagged;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
/// ├── resultParameters: List<Parameter<?>>
/// │   └── Optional deployment outputs this element can publish
/// │
/// ├── dependencies: List<Element>
/// │   └── Other elements this depends on (DAG)
/// │
/// ├── healthCheck: Optional<HealthCheckSpec>
/// │   └── Readiness verification strategy
/// │
/// ├── statusCheck: LiveStatusSummary
/// │   └── Current operational state + one-line evidence
/// │
/// ├── instancingScope: Optional<InstancingScope>
/// │   └── PER_TRIAL | PER_GROUP | PER_RUN
/// │
/// ├── trial lifecycle (via TrialLifecycleParticipant)
/// │   ├── onTrialStarting(TrialContext)
/// │   └── onTrialEnding(TrialContext)
/// │
/// └── state observation (via OperationalStateObservable)
///     └── observeState(listener) → StateObservation
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
/// ## Live Status Checks
///
/// Elements report their current state via {@link #statusCheck()}:
///
/// ```
/// Live Status Report:
/// ├── OperationalState  — lifecycle position (RUNNING, READY, FAILED, ...)
/// └── summary           — one-line evidence ("3/3 nodes active")
/// ```
///
/// This is distinct from health checks: a health check is a *specification*
/// for how to verify readiness; a status check is a *live query* that
/// returns the element's current operational state.
///
/// @see Parameter
/// @see RelationshipType
/// @see TrialLifecycleParticipant
/// @see OperationalStateObservable
/// @see io.nosqlbench.paramodel.plan.TestPlan
/// @since 0.1.0
///
public interface Element extends Tagged, TrialLifecycleParticipant, OperationalStateObservable {

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
    /// For elements with dynamic parameter views, this method returns the
    /// required/structural parameters. Consult {@link #parameterView()} for
    /// the full active parameter set, which may include dynamically resolved
    /// parameters that depend on required parameter values.
    ///
    /// @return unmodifiable list of parameter models, never null
    /// @see Parameter
    /// @see #parameterView()
    ///
    List<Parameter<?>> parameters();

    ///
    /// Returns a {@link ParameterView} that models the required/dynamic parameter split.
    ///
    /// For elements whose valid parameter set depends on the value of certain
    /// structural parameters, the view distinguishes between required parameters
    /// (always present) and dynamic parameters (resolved once required values
    /// are known).
    ///
    /// The default implementation returns a static view wrapping {@link #parameters()}.
    /// Elements with dynamic behavior should override this method.
    ///
    /// @return the parameter view for this element, never null
    /// @see ParameterView
    ///
    default ParameterView parameterView() {
        return ParameterView.of(parameters());
    }

    ///
    /// Returns optional result parameter models this element may publish after deployment.
    ///
    /// Result parameters model typed outputs produced by materializing this element
    /// (for example: endpoint URL, allocated port, generated credential ID, or
    /// runtime feature flags). They are distinct from {@link #parameters()}, which
    /// model the element's configurable input dimensions.
    ///
    /// Concrete implementations MAY override this method to provide result models.
    /// The default behavior returns an empty list.
    ///
    /// ## Contract
    ///
    /// - MUST return a non-null, unmodifiable list
    /// - Parameter names within the list MUST be unique
    /// - MAY return an empty list when the element publishes no typed outputs
    ///
    /// @return unmodifiable list of optional result parameter models, never null
    /// @see Parameter
    ///
    default List<Parameter<?>> resultParameters() {
        return List.of();
    }

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
    /// Returns the current live status of this element.
    ///
    /// Unlike {@link #healthCheck()}, which is a static specification for
    /// readiness detection, this method returns the element's actual
    /// operational state and a one-line summary at the moment of invocation.
    ///
    /// ## Contract
    ///
    /// - MUST return a non-null result
    /// - The summary MUST be a single human-readable line
    /// - Implementations SHOULD reflect actual live state when possible
    /// - When live state cannot be determined, return {@link OperationalState#UNKNOWN}
    ///
    /// @return current live status, never null
    ///
    LiveStatusSummary statusCheck();

    ///
    /// Returns the element's base configuration — fixed parameter values
    /// that do not vary across trials.
    ///
    /// Configuration entries serve as default bindings for the element.
    /// When an axis targets this element and varies a parameter that also
    /// appears in the configuration map, the axis value overrides the
    /// configuration value for that trial.
    ///
    /// @return unmodifiable configuration map, never null
    ///
    default Map<String, Object> configuration() {
        return Map.of();
    }

    ///
    /// Returns export definitions that this element publishes for
    /// downstream elements to reference.
    ///
    /// @return unmodifiable exports map, never null
    ///
    default Map<String, String> exports() {
        return Map.of();
    }

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

    // -----------------------------------------------------------------------
    // State observation (default implementation)
    // -----------------------------------------------------------------------

    ///
    /// Registers a listener for operational state transitions.
    ///
    /// The default implementation immediately delivers a synthetic transition
    /// from {@link OperationalState#UNKNOWN} to the element's current state
    /// (as reported by {@link #statusCheck()}) and returns a no-op observation
    /// handle. This satisfies the registration-as-catchup contract: the
    /// observer immediately learns the element's current state.
    ///
    /// Concrete implementations that support real-time state transitions
    /// (e.g. elements backed by live infrastructure) SHOULD override this
    /// method to maintain a listener registry and deliver genuine transitions
    /// as they occur.
    ///
    /// @param listener the listener to receive state transitions, must not be null
    /// @return a handle for cancelling the observation, never null
    /// @throws NullPointerException if listener is null
    ///
    @Override
    default StateObservation observeState(StateTransitionListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        LiveStatusSummary current = statusCheck();
        listener.onStateTransition(new StateTransition(
            OperationalState.UNKNOWN,
            current.state(),
            current.summary(),
            Instant.now()
        ));
        return () -> {};
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

    /// Operational lifecycle state of an element at runtime.
    ///
    /// Progresses through: INACTIVE -> PROVISIONING -> STARTING -> HEALTH_CHECK ->
    /// READY -> RUNNING -> STOPPING -> STOPPED -> TERMINATED.
    /// FAILED and UNKNOWN are non-sequential states.
    enum OperationalState {
        /// Not yet started or provisioned.
        INACTIVE,
        /// Infrastructure being allocated (e.g. VM creation).
        PROVISIONING,
        /// Process starting up.
        STARTING,
        /// Verifying readiness via health check.
        HEALTH_CHECK,
        /// Ready for use but not yet active in a trial.
        READY,
        /// Actively in use by one or more trials.
        RUNNING,
        /// Graceful shutdown in progress.
        STOPPING,
        /// Stopped normally, resources still allocated.
        STOPPED,
        /// Error state — element cannot operate.
        FAILED,
        /// Fully torn down, all resources released.
        TERMINATED,
        /// Status cannot be determined.
        UNKNOWN
    }

    /// Live status report from an element, combining operational state with a
    /// human-readable summary line.
    ///
    /// The summary provides prima-facie evidence of the element's live state
    /// and should be a single line suitable for display in dashboards and logs.
    ///
    /// Examples:
    /// - "3/3 nodes active, all heartbeats OK"
    /// - "Container cassandra:4.1 running on port 9042"
    /// - "Benchmark 45% complete, 12m remaining"
    /// - "Stopped normally after 847 requests"
    record LiveStatusSummary(OperationalState state, String summary) {
        /// Creates a summary in the UNKNOWN state.
        public static LiveStatusSummary unknown(String summary) {
            return new LiveStatusSummary(OperationalState.UNKNOWN, summary);
        }

        /// Creates a summary indicating the element is inactive.
        public static LiveStatusSummary inactive() {
            return new LiveStatusSummary(OperationalState.INACTIVE, "Not started");
        }
    }
}
