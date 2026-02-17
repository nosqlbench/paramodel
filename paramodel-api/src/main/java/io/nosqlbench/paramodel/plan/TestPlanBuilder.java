package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

///
/// # TestPlanBuilder
///
/// Fluent API for constructing {@link TestPlan} instances with validation and
/// incremental assembly. The builder pattern ensures that test plans are constructed
/// in a consistent, validated manner before commitment to execution.
///
/// ## Builder Pattern Philosophy
///
/// ```
/// Construction Flow:
///
///   create()
///      │
///      ├─→ name()           ─┐
///      ├─→ withAxis()        │
///      ├─→ withElement()     ├─→ [Accumulation Phase]
///      ├─→ policies()       ─┘   (Mutable State)
///      │
///      └─→ build()
///            │
///            └─→ TestPlan (Immutable)
///                   │
///                   └─→ commit() → ExecutionPlan
/// ```
///
/// ## Axis Ordering Strategy
///
/// Axis order determines trial enumeration sequence. The builder maintains insertion
/// order by default, but provides explicit ordering controls:
///
/// ```
/// Ordering Impact on Trial Space:
///
/// Given axes: [A={1,2}, B={x,y}]
///
/// Order [A, B]:                Order [B, A]:
///   (1,x) (1,y)                  (x,1) (x,2)
///   (2,x) (2,y)                  (y,1) (y,2)
///
/// Edge-first with [A, B]:      Edge-first with [B, A]:
///   (1,x) (1,y)                  (x,1) (y,1)
///   (2,x) (2,y)                  (x,2) (y,2)
///   (1,x) (2,x)                  (x,1) (x,2)
///   (1,y) (2,y)                  (y,1) (y,2)
/// ```
///
/// Axis ordering affects:
/// - Trial enumeration sequence
/// - Progressive disclosure patterns
/// - Caching locality
/// - Incremental refinement strategies
///
/// ## Relationship Graph Construction
///
/// Element relationships define concurrency constraints and resource sharing:
///
/// ```
/// Relationship Matrix:
///
///           DB    Cache  API
///     DB    --    SHARED SHARED
///     Cache --    --     SHARED
///     API   --    --     --
///
/// Compilation yields:
///   - DB: Single shared instance across all trials
///   - Cache: SHARED with DB and API
///   - API: Lifecycle determined by fingerprint-based grouping (parameter-axis overlap)
///
/// Concurrency Graph:
///   Trials using same (DB, Cache) → Can run concurrently
///   Trials using different Cache → Must use different API
/// ```
///
/// ## Validation Strategy
///
/// The builder performs incremental validation during construction and comprehensive
/// validation at build time:
///
/// ```
/// Validation Phases:
///
/// 1. Incremental (During withAxis/withElement):
///    - Name uniqueness
///    - Type compatibility
///    - Null checks
///
/// 2. Build-time (At build()):
///    - Relationship graph acyclicity
///    - Element dependency satisfaction
///    - Axis cardinality sanity
///    - Policy consistency
///    - Trial space size overflow
///
/// 3. Commit-time (At TestPlan.commit()):
///    - Full compilation feasibility
///    - Resource availability
///    - Execution plan generation
/// ```
///
/// ## Metadata and Provenance
///
/// Builders capture construction metadata automatically:
///
/// ```
/// Provenance Chain:
///
/// Builder Creation
///   ├─ Timestamp
///   ├─ Builder version
///   └─ Construction environment
///        │
///        ├─→ Each modification
///        │     ├─ Operation (withAxis, relationship, etc.)
///        │     ├─ Timestamp
///        │     └─ Modification hash
///        │
///        └─→ Build completion
///              ├─ Final validation results
///              ├─ Trial space size
///              └─ Plan fingerprint
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Simple Load Test Study
///
/// ```java
/// TestPlan plan = TestPlanBuilder.create()
///     .name("cache-load-test")
///     .withAxis(Axis.of("cache_size_mb", List.of(128, 256, 512, 1024)))
///     .withAxis(Axis.of("concurrency", List.of(10, 50, 100)))
///     .withElement(cacheElement)   // "cache" with maxmemory parameter
///     .withElement(loadElement)    // "load" with threads parameter
///     // Relationship types are on Element dependency edges
///     .policies(ExecutionPolicies.builder()
///         .trialTimeout(Duration.ofMinutes(5))
///         .trialRetryPolicy(RetryPolicy.exponentialBackoff(3))
///         .build())
///     .build();
///
/// // Trial space: 4 cache_size × 3 concurrency = 12 trials
/// // All trials share one cache instance per configuration
/// ```
///
/// ### Example 2: Microservices Compatibility Matrix
///
/// ```java
/// TestPlan plan = TestPlanBuilder.create()
///     .name("service-compatibility")
///     .withAxis(Axis.of("auth_version", List.of("v1.2", "v1.3", "v2.0")))
///     .withAxis(Axis.of("api_version", List.of("v3.1", "v3.2")))
///     .withAxis(Axis.of("db_version", List.of("pg14", "pg15")))
///     .withElement(databaseElement)     // "database"
///     .withElement(authElement)         // "auth-service"
///     .withElement(apiGatewayElement)   // "api-gateway"
///     // Relationship types are on Element dependency edges
///     .policies(ExecutionPolicies.defaultPolicies())
///     .build();
///
/// // Trial space: 3 × 2 × 2 = 12 trials
/// // Database: 1 shared instance per (db_version)
/// // Auth: 1 shared instance per (auth_version, db_version)
/// // API: Fresh instance per (api_version, auth_version, db_version)
/// ```
///
/// ### Example 3: Progressive Axis Refinement
///
/// ```java
/// TestPlanBuilder builder = TestPlanBuilder.create()
///     .name("ml-hyperparameter-search");
///
/// // Start with coarse grid
/// builder.withAxis(Axis.of("learning_rate",
///     List.of(0.001, 0.01, 0.1)));
/// builder.withAxis(Axis.of("batch_size",
///     List.of(32, 128, 512)));
///
/// // Build and execute initial coarse search
/// TestPlan coarsePlan = builder.build();
/// ExecutionPlan coarseExecution = coarsePlan.commit();
/// // ... execute and analyze ...
///
/// // Refine based on results - rebuild with finer grid
/// TestPlan refinedPlan = TestPlanBuilder.create()
///     .name("ml-hyperparameter-search-refined")
///     .withAxis(Axis.of("learning_rate",
///         List.of(0.005, 0.008, 0.01, 0.012, 0.015))) // Narrow around 0.01
///     .withAxis(Axis.of("batch_size",
///         List.of(96, 128, 160))) // Narrow around 128
///     .basedOn(coarsePlan) // Copy elements, relationships, policies
///     .build();
/// ```
///
/// ### Example 4: Conditional Element Dependencies
///
/// ```java
/// TestPlan plan = TestPlanBuilder.create()
///     .name("feature-flag-testing")
///     .withAxis(Axis.of("feature_x_enabled", List.of(true, false)))
///     .withAxis(Axis.of("feature_y_enabled", List.of(true, false)))
///     .withElement(appElement)        // "app" with feature flag parameters
///     .withElement(analyticsElement)  // "analytics" (conditional deployment)
///     .withElement(mlElement)         // "ml-service" (conditional deployment)
///     // Relationship types are on Element dependency edges
///     .policies(ExecutionPolicies.builder()
///         .partialRunBehavior(PartialRunBehavior.SKIP_TRIAL)
///         .build())
///     .build();
///
/// // Compiler optimizes: Only deploy analytics when feature_x_enabled=true
/// // Only deploy ml-service when feature_y_enabled=true
/// ```
///
/// ## Contract Requirements
///
/// ### Immutability and Thread Safety
/// - Builder instances MUST be mutable and NOT thread-safe
/// - `build()` MUST produce immutable TestPlan instances
/// - Repeated calls to `build()` MUST produce equivalent TestPlan instances
///
/// ### Validation Behavior
/// - Invalid operations (duplicate names, cyclic dependencies) MUST throw IllegalArgumentException
/// - Validation failures at `build()` MUST throw IllegalStateException with comprehensive messages
/// - Validation SHOULD be incremental where possible to provide early feedback
///
/// ### Metadata Preservation
/// - Construction metadata MUST be captured automatically
/// - `basedOn(TestPlan)` MUST preserve provenance chain from source plan
/// - Plan fingerprints MUST be deterministic and cryptographically stable
///
/// ### Ordering Guarantees
/// - Axes MUST maintain insertion order unless explicitly reordered
/// - Elements MUST maintain insertion order
/// - Relationship order MUST NOT affect semantics (commutative)
///
/// @see TestPlan
/// @see ExecutionPlan
/// @see Axis
/// @see Element
/// @see ExecutionPolicies
///
public interface TestPlanBuilder {

    ///
    /// Creates a new TestPlanBuilder instance.
    ///
    /// @return Fresh builder with no axes, elements, or relationships
    ///
    static TestPlanBuilder create() {
        throw new UnsupportedOperationException(
            "TestPlanBuilder.create() requires a concrete implementation");
    }

    ///
    /// Sets the name of the test plan being constructed.
    ///
    /// Names MUST be unique within a study repository and SHOULD follow
    /// kebab-case conventions for consistency.
    ///
    /// @param name The test plan name (must be non-null, non-empty)
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if name is null or empty
    ///
    TestPlanBuilder name(String name);

    ///
    /// Adds an axis to the test plan.
    ///
    /// Axes are maintained in insertion order, which determines trial
    /// enumeration sequence. This affects progressive disclosure and
    /// caching strategies.
    ///
    /// @param axis The axis to add (must be non-null)
    /// @param <T> The value type of the axis
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if axis is null or name duplicates existing axis
    ///
    <T> TestPlanBuilder withAxis(Axis<T> axis);

    ///
    /// Creates and adds an axis from a parameter.
    ///
    /// The axis will sample boundary values and representative interior values
    /// from the parameter's domain according to the specified strategy.
    ///
    /// @param parameter The parameter to convert to an axis
    /// @param sampleSize Number of interior values to sample (in addition to boundaries)
    /// @param <T> The value type of the parameter
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if parameter is null or sampleSize is negative
    ///
    <T> TestPlanBuilder withAxisFromParameter(Parameter<T> parameter, int sampleSize);

    ///
    /// Adds multiple axes at once.
    ///
    /// Axes are added in iteration order.
    ///
    /// @param axes The axes to add (must be non-null)
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if any axis is null or duplicates existing names
    ///
    TestPlanBuilder withAxes(List<Axis<?>> axes);

    ///
    /// Adds an element to the test plan.
    ///
    /// Elements represent deployable, instantiable resources required for
    /// trial execution (services, databases, infrastructure, etc.).
    ///
    /// @param element The element to add (must be non-null)
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if element is null or name duplicates existing element
    ///
    TestPlanBuilder withElement(Element element);

    ///
    /// Adds multiple elements at once.
    ///
    /// Elements are added in iteration order.
    ///
    /// @param elements The elements to add (must be non-null)
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if any element is null or duplicates existing names
    ///
    TestPlanBuilder withElements(List<Element> elements);

    ///
    /// Sets execution policies for the test plan.
    ///
    /// Policies control retry behavior, timeouts, error handling, and
    /// intervention modes during execution.
    ///
    /// @param policies The execution policies (must be non-null)
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if policies is null
    ///
    TestPlanBuilder policies(ExecutionPolicies policies);

    ///
    /// Configures policies using a builder callback.
    ///
    /// @param configurator Callback to configure policies builder
    /// @return This builder for method chaining
    ///
    TestPlanBuilder policies(Consumer<ExecutionPolicies.Builder> configurator);

    ///
    /// Sets the optimization strategy for the test plan.
    ///
    /// @param strategy optimization strategy to use
    /// @return This builder for method chaining
    ///
    TestPlanBuilder optimizationStrategy(OptimizationStrategy strategy);

    ///
    /// Creates a new builder based on an existing test plan.
    ///
    /// Copies axes, elements, relationships, and policies from the source plan.
    /// Preserves provenance chain. Name must still be set explicitly.
    ///
    /// @param source The test plan to copy from (must be non-null)
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if source is null
    ///
    TestPlanBuilder basedOn(TestPlan source);

    ///
    /// Explicitly sets the axis ordering.
    ///
    /// This overrides insertion order. All existing axes must be included
    /// in the new ordering.
    ///
    /// @param axisNames Ordered list of axis names (must include all axes exactly once)
    /// @return This builder for method chaining
    /// @throws IllegalArgumentException if axisNames doesn't match existing axes
    ///
    TestPlanBuilder axisOrder(List<String> axisNames);

    ///
    /// Attaches arbitrary metadata to the test plan.
    ///
    /// Metadata can include tags, descriptions, owner information, cost estimates,
    /// or any other custom attributes.
    ///
    /// @param key Metadata key (must be non-null, non-empty)
    /// @param value Metadata value (must be non-null)
    /// @return This builder for method chaining
    ///
    TestPlanBuilder metadata(String key, Object value);

    ///
    /// Attaches multiple metadata entries at once.
    ///
    /// @param metadata Map of metadata key-value pairs (must be non-null)
    /// @return This builder for method chaining
    ///
    TestPlanBuilder metadata(Map<String, Object> metadata);

    ///
    /// Validates the current builder state without building.
    ///
    /// Useful for early validation during interactive plan construction.
    ///
    /// @return Validation result with any errors or warnings
    ///
    ValidationResult validate();

    ///
    /// Calculates the trial space size for the current axes.
    ///
    /// Returns the Cartesian product cardinality: ∏(|axis_i|) for all axes.
    ///
    /// @return Total number of trials that will be generated
    ///
    long estimateTrialSpaceSize();

    ///
    /// Retrieves current axes in insertion order.
    ///
    /// Returns unmodifiable view of current axes. Useful for inspection
    /// during incremental construction.
    ///
    /// @return Current axes (unmodifiable)
    ///
    List<Axis<?>> currentAxes();

    ///
    /// Retrieves current elements in insertion order.
    ///
    /// Returns unmodifiable view of current elements.
    ///
    /// @return Current elements (unmodifiable)
    ///
    List<Element> currentElements();

    ///
    /// Retrieves current execution policies.
    ///
    /// Returns current policies or empty if not yet set.
    ///
    /// @return Current policies if set
    ///
    Optional<ExecutionPolicies> currentPolicies();

    ///
    /// Builds an immutable TestPlan from the current builder state.
    ///
    /// Performs comprehensive validation:
    /// - Name is set and valid
    /// - At least one axis exists
    /// - At least one element exists
    /// - Relationship graph is acyclic
    /// - Element dependencies are satisfied
    /// - Policies are set (or defaults applied)
    /// - Trial space size is reasonable
    ///
    /// @return Immutable TestPlan instance
    /// @throws IllegalStateException if validation fails
    ///
    TestPlan build();

    ///
    /// Clears all builder state, resetting to empty.
    ///
    /// @return This builder for method chaining
    ///
    TestPlanBuilder reset();
}
