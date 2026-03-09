package io.nosqlbench.paramodel.elements;

import io.nosqlbench.paramodel.attributes.AttributeSupport;
import io.nosqlbench.paramodel.attributes.Labeled;
import io.nosqlbench.paramodel.attributes.Tagged;
import io.nosqlbench.paramodel.attributes.Traits;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ParameterView;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

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
/// trait supplied via {@link #traits()}.
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
/// ├── dependencies: List<Dependency>
/// │   └── Typed dependency edges (target + RelationshipType)
/// │
/// ├── healthCheck: Optional<HealthCheckSpec>
/// │   └── Readiness verification strategy
/// │
/// ├── statusCheck: LiveStatusSummary
/// │   └── Current operational state + one-line evidence
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
/// Elements form a directed acyclic graph (DAG) through typed dependencies.
/// Each dependency edge is a {@link Dependency} record carrying the target
/// element and a {@link RelationshipType} that describes how the dependent
/// relates to its upstream:
///
/// ```
/// Element
/// └── dependencies: List<Dependency>
///     └── Dependency(target: Element, type: RelationshipType)
/// ```
///
/// Ordering rules:
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
/// ## Relationship Types (on the dependency edge)
///
/// | Type | Meaning |
/// |------|---------|
/// | SHARED | Target can be concurrently used by multiple dependents (default) |
/// | EXCLUSIVE | During dependent's lifetime, no other dependent of target can be active |
/// | DEDICATED | Target gets a dedicated instance for the dependent |
/// | LIFELINE | Target's teardown subsumes the dependent's teardown |
///
/// When all of an element's dependencies are LIFELINE, the element's
/// final teardown step is omitted — the upstream teardowns implicitly
/// destroy it.
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
/// List<Dependency> deps = db.dependencies();
/// // [Dependency(storage, SHARED)]
/// ```
///
/// ## Element Lifecycle Derivation
///
/// An element's lifecycle is derived by the compilation pipeline from
/// parameter-axis overlap. Elements do not declare their own lifecycle:
///
/// ```
/// Group level 0 (no axes target this element's parameters):
///   Global — deploys once at start, tears down once at end.
///
/// Group level K (K axes target this element's parameters):
///   Axis-bound — persists for contiguous trial blocks where all K
///   bound axis values are constant. Redeployed at group boundaries
///   when the configuration fingerprint changes.
/// ```
///
/// The fingerprint includes recursive dependency fingerprints, so if
/// an upstream element's configuration changes, downstream elements
/// are also redeployed.
///
/// ## Health Checks
///
/// Elements define readiness verification timing. The host system owns
/// the health check mechanism (protocol, endpoint, acceptance criteria).
/// Paramodel only needs timing parameters for coordination:
///
/// ```
/// HealthCheckSpec:
/// ├── timeout        — maximum time to wait for readiness
/// ├── maxRetries     — number of retry attempts
/// └── retryInterval  — pause between retries
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
public interface Element extends Labeled, Traits, Tagged, TrialLifecycleParticipant, OperationalStateObservable {

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
    /// Returns an unmodifiable map of labels describing this element's
    /// immutable structural properties.
    ///
    /// The default implementation returns a map with a single {@code "name"}
    /// entry derived from {@link #name()}. Implementations SHOULD override
    /// this to include a {@code "type"} key whose value is defined by the
    /// adopting system's type taxonomy (e.g. {@code "service"},
    /// {@code "environment"}, {@code "cache"}).
    ///
    /// @return unmodifiable label map, never null
    ///
    default Map<String, String> labels() {
        return Map.of("name", name());
    }

    ///
    /// Returns an unmodifiable map of traits for this element.
    ///
    /// Traits describe type-relational capabilities with plug-and-socket
    /// semantics. The paramodel engine does not consume any trait keys on
    /// elements — this tier exists as an adopter extension point.
    ///
    /// The default implementation returns an empty map.
    ///
    /// @return unmodifiable trait map, never null
    ///
    default Map<String, String> traits() {
        return Map.of();
    }

    ///
    /// Returns an unmodifiable map of tags for this element.
    ///
    /// Tags are user-mutable categorization properties and
    /// adopter-specific metadata. The paramodel engine does not consume
    /// any tag keys on elements — this tier exists as an adopter
    /// extension point.
    ///
    /// The default implementation returns an empty map.
    ///
    /// @return unmodifiable tag map, never null
    ///
    default Map<String, String> tags() {
        return Map.of();
    }

    ///
    /// Returns a combined view of all attributes across labels, traits, and tags.
    ///
    /// The default implementation merges all three tiers via
    /// {@link AttributeSupport#combine}.
    ///
    /// @return unmodifiable combined attribute map, never null
    ///
    default Map<String, String> attributes() {
        return AttributeSupport.combine(labels(), traits(), tags());
    }

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
    /// A directed dependency edge from this element to an upstream target element.
    ///
    /// Each dependency carries a {@link RelationshipType} that describes how
    /// the dependent relates to its upstream target.
    ///
    /// @param target the upstream element this element depends on
    /// @param type   how this element relates to the dependency
    ///
    record Dependency(Element target, RelationshipType type) {
        public Dependency {
            java.util.Objects.requireNonNull(target, "target");
            java.util.Objects.requireNonNull(type, "type");
        }

        /// Creates a SHARED dependency (the default).
        ///
        /// @param target the upstream element
        /// @return a new SHARED dependency edge
        public static Dependency shared(Element target) {
            return new Dependency(target, RelationshipType.SHARED);
        }
    }

    ///
    /// Returns the typed dependency edges for this element.
    ///
    /// Each dependency is a {@link Dependency} record carrying the target
    /// element and a {@link RelationshipType}.
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
    List<Dependency> dependencies();

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
    /// Shutdown behavior for this element.
    ///
    /// - **SERVICE**: Long-running process that requires explicit shutdown.
    ///   The scheduler issues a stop signal and waits for graceful termination.
    ///   This is the default when no semantics are specified.
    ///
    /// - **COMMAND**: Self-terminating process that runs to completion.
    ///   The scheduler awaits natural completion instead of issuing a shutdown.
    ///   The trial element's step becomes {@code AwaitElement} instead of
    ///   {@code TrialStep} + {@code TeardownElement}.
    enum ShutdownSemantics {
        /// Long-running; requires explicit stop signal. Default.
        SERVICE,
        /// Self-terminating; scheduler awaits natural completion.
        COMMAND
    }

    /// Returns the shutdown semantics for this element.
    ///
    /// SERVICE elements require explicit teardown. COMMAND elements are
    /// self-terminating and are awaited rather than stopped.
    ///
    /// @return shutdown semantics, defaults to SERVICE when not specified
    default ShutdownSemantics shutdownSemantics() {
        return ShutdownSemantics.SERVICE;
    }

    /// Returns the explicit trial-element override for this element.
    ///
    /// - **empty** (default): auto-detect — the compiler uses scope-aware
    ///   leaf-node heuristic (most-dependent among trial-scoped elements).
    /// - **true**: force this element to be a trial element regardless of
    ///   dependency position.
    /// - **false**: force this element to NOT be a trial element even if
    ///   it would be auto-detected.
    ///
    /// @return explicit override, or empty for auto-detection
    default Optional<Boolean> trialElement() {
        return Optional.empty();
    }

    /// Returns the maximum concurrency limit for parallel deployments of
    /// this element, or empty if unlimited.
    ///
    /// When present, the execution engine limits the number of concurrent
    /// active instances of this element to the specified value.
    ///
    /// @return the max concurrency limit, or empty if unlimited
    default OptionalInt maxConcurrency() {
        return OptionalInt.empty();
    }

    ///
    /// Health check specification for determining element readiness.
    ///
    /// The host system owns the health check mechanism (protocol, endpoint,
    /// acceptance criteria). Paramodel only needs timing parameters for
    /// coordination — specifically, how long to wait and how often to retry.
    ///
    /// An element whose {@link #healthCheck()} returns a spec transitions
    /// through {@link OperationalState#HEALTH_CHECK} before reaching
    /// {@link OperationalState#READY}. An element without a health check
    /// spec transitions directly to {@code READY} after starting.
    ///
    interface HealthCheckSpec {
        /// Maximum time to wait for health check to pass.
        java.time.Duration timeout();

        /// Maximum number of retry attempts.
        int maxRetries();

        /// Interval between retry attempts.
        java.time.Duration retryInterval();
    }

    /// Operational lifecycle state of an element at runtime.
    ///
    /// Normal progression: INACTIVE → PROVISIONING → STARTING → HEALTH_CHECK →
    /// READY → RUNNING → STOPPING → STOPPED → TERMINATED.
    ///
    /// - **INACTIVE** stands for "not yet started" — the element has not been
    ///   provisioned or deployed.
    /// - **PROVISIONING** stands for "starting" — infrastructure is being
    ///   allocated (e.g. VM creation, container start).
    /// - **READY** is reached as a side-effect of successful health checks
    ///   (when the element has a {@link HealthCheckSpec} defined), or
    ///   entered directly after STARTING when no health checks are defined.
    /// - **FAILED** and **UNKNOWN** are non-sequential states that can be
    ///   entered from any other state.
    ///
    enum OperationalState {
        /// Not yet started or provisioned.
        INACTIVE,
        /// Infrastructure being allocated (e.g. VM creation, container start).
        PROVISIONING,
        /// Process starting up.
        STARTING,
        /// Verifying readiness via health check.
        HEALTH_CHECK,
        /// Ready for use but not yet active in a trial. Reached as a
        /// side-effect of successful health checks, or directly after
        /// STARTING when the element defines no health checks.
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
