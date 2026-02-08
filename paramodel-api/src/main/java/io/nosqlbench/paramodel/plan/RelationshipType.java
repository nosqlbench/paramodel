package io.nosqlbench.paramodel.plan;

///
/// Semantic relationship between elements determining concurrency and serialization.
///
/// ## Concept
///
/// {@code RelationshipType} defines how elements (resources) interact during execution.
/// The relationship type **fully determines** execution semantics including:
/// - Can elements run concurrently?
/// - Must execution be serialized?
/// - Are instances shared or isolated?
///
/// ## Relationship Semantics
///
/// ```
/// RelationshipType          Concurrency Rule                 Lifecycle
/// ────────────────────────────────────────────────────────────────────────────
/// MUTUALLY_EXCLUSIVE        Cannot overlap                   Serialize access
/// SHARED                    Can overlap                      Shared instance
/// INSTANCED_PER            Independent instances            Per scope
/// ```
///
/// ## Concurrency Model
///
/// ```
/// Timeline:
///   Trial1: ────[Element A]────
///   Trial2:            ────[Element A]────
///
/// MUTUALLY_EXCLUSIVE:
///   Trial1: ────[Element A]────
///   Trial2:                      ────[Element A]────
///           ↑ Barrier prevents overlap
///
/// SHARED:
///   Trial1: ────[Element A (shared)]────
///   Trial2:       ────[Element A (shared)]────
///           ↑ Both use same instance concurrently
///
/// INSTANCED_PER:
///   Trial1: ────[Element A (instance 1)]────
///   Trial2:     ────[Element A (instance 2)]────
///           ↑ Each gets its own instance
/// ```
///
/// ## Use Cases by Type
///
/// ### MUTUALLY_EXCLUSIVE
///
/// Use when:
/// - Resource doesn't support concurrent access
/// - State conflicts would occur
/// - Data corruption risk
///
/// Examples:
/// ```
/// - Single database instance (no connection pooling)
/// - Exclusive file lock
/// - Hardware device (GPU, sensor)
/// - Singleton service
/// ```
///
/// ### SHARED
///
/// Use when:
/// - Resource safely supports concurrent access
/// - Read-only or thread-safe
/// - Cost of creating instances is high
///
/// Examples:
/// ```
/// - Read-only cache
/// - Connection pool
/// - Shared reference data
/// - Thread-safe service client
/// ```
///
/// ### INSTANCED_PER
///
/// Use when:
/// - Each trial needs isolated state
/// - No interference between trials
/// - Resource is lightweight to create
///
/// Examples:
/// ```
/// - Per-trial container
/// - Temporary database
/// - Trial-specific file storage
/// - Isolated test environment
/// ```
///
/// ## Planning Impact
///
/// Relationship types affect plan compilation:
///
/// ```
/// MUTUALLY_EXCLUSIVE:
///   → Compiler inserts barriers
///   → Trials serialized where element used
///   → Single instance lifecycle
///
/// SHARED:
///   → No barriers needed
///   → Trials can run concurrently
///   → Single instance lifecycle
///   → Must ensure thread-safety
///
/// INSTANCED_PER:
///   → No barriers needed
///   → Trials can run concurrently
///   → Multiple instance lifecycles
///   → Each instance isolated
/// ```
///
/// ## Example: Database Relationships
///
/// ```java
/// Element database = Element.database("postgres");
/// Element appServer = Element.service("app-server");
///
/// // Scenario 1: Exclusive database access
/// plan.relationship(database, appServer, RelationshipType.MUTUALLY_EXCLUSIVE);
/// // → Only one trial uses database at a time
///
/// // Scenario 2: Shared connection pool
/// Element connectionPool = Element.pool("db-pool", database);
/// plan.relationship(connectionPool, appServer, RelationshipType.SHARED);
/// // → All trials share the connection pool
///
/// // Scenario 3: Per-trial database
/// plan.relationship(database, appServer, RelationshipType.INSTANCED_PER);
/// plan.instancingScope(database, InstancingScope.PER_TRIAL);
/// // → Each trial gets its own database instance
/// ```
///
/// ## Example: Cache Patterns
///
/// ```java
/// Element cache = Element.cache("response-cache");
///
/// // Shared cache (all trials benefit from cached results)
/// plan.relationship(cache, service, RelationshipType.SHARED);
///
/// // Per-trial cache (isolated, no cross-contamination)
/// plan.relationship(cache, service, RelationshipType.INSTANCED_PER);
/// plan.instancingScope(cache, InstancingScope.PER_TRIAL);
///
/// // Exclusive cache (serialized access)
/// plan.relationship(cache, service, RelationshipType.MUTUALLY_EXCLUSIVE);
/// ```
///
/// ## Barrier Insertion
///
/// Compiler uses relationship types to insert barriers:
///
/// ```
/// Given:
///   Element E used by Trials T1, T2, T3
///   Relationship: MUTUALLY_EXCLUSIVE
///
/// Execution Plan:
///   T1: START → USE(E) → BARRIER(wait E free) → END
///   T2: BARRIER(wait E free) → USE(E) → BARRIER(wait E free) → END
///   T3: BARRIER(wait E free) → USE(E) → END
/// ```
///
/// ## Validation Rules
///
/// The validator ensures:
///
/// ```
/// MUTUALLY_EXCLUSIVE:
///   - Element lifecycle spans all dependent trials
///   - No concurrent access in execution graph
///
/// SHARED:
///   - Element is thread-safe or read-only
///   - Lifecycle covers all concurrent access
///
/// INSTANCED_PER:
///   - Instancing scope is defined
///   - Sufficient resources for all instances
/// ```
///
/// ## Performance Implications
///
/// ```
/// Relationship Type     Concurrency    Resource Cost    Best For
/// ────────────────────────────────────────────────────────────────────
/// MUTUALLY_EXCLUSIVE    Low            1 instance       Safety critical
/// SHARED                High           1 instance       Read-heavy
/// INSTANCED_PER         High           N instances      Isolation needed
/// ```
///
/// @see Element
/// @see TestPlan
/// @see com.paramodel.api.compilation.PlanCompiler
/// @see com.paramodel.api.execution.Barrier
/// @since 0.1.0
///
public enum RelationshipType {

    ///
    /// Elements cannot be used concurrently; execution must serialize where overlap would occur.
    ///
    /// ## Semantics
    ///
    /// ```
    /// ∀ trials t1, t2 using element E:
    ///   execution(t1, E) ∩ execution(t2, E) = ∅
    ///
    /// (No temporal overlap of element usage)
    /// ```
    ///
    /// ## Execution Pattern
    ///
    /// ```
    /// Trial1: ────────[E]─────────done
    /// Trial2:                         ────────[E]─────────done
    /// Trial3:                                             ────────[E]─────────
    ///
    /// Timeline → Strictly sequential
    /// ```
    ///
    /// ## Use Cases
    ///
    /// - Non-thread-safe resources
    /// - State conflicts
    /// - Exclusive locks required
    /// - Hardware constraints
    ///
    /// ## Example
    ///
    /// ```java
    /// Element gpu = Element.device("gpu-0");
    /// Element model = Element.service("ml-model");
    ///
    /// plan.relationship(gpu, model, RelationshipType.MUTUALLY_EXCLUSIVE);
    /// // Only one trial can use GPU at a time
    /// ```
    ///
    MUTUALLY_EXCLUSIVE,

    ///
    /// Element instances/resources may be shared concurrently by dependent components/trials where safe.
    ///
    /// ## Semantics
    ///
    /// ```
    /// ∀ trials t1, t2, ..., tn using element E:
    ///   All share single instance of E
    ///   Concurrent access is safe
    /// ```
    ///
    /// ## Execution Pattern
    ///
    /// ```
    /// Trial1: ────────[E (shared)]─────────done
    /// Trial2:     ────────[E (shared)]─────────done
    /// Trial3:         ────────[E (shared)]─────────done
    ///
    /// Timeline → Overlapping access to same instance
    /// ```
    ///
    /// ## Safety Requirements
    ///
    /// Element MUST be:
    /// - Thread-safe, OR
    /// - Read-only, OR
    /// - Safely shareable by design
    ///
    /// ## Use Cases
    ///
    /// - Connection pools
    /// - Read-only caches
    /// - Shared reference data
    /// - Thread-safe services
    ///
    /// ## Example
    ///
    /// ```java
    /// Element cache = Element.cache("embedding-cache");
    /// Element encoder = Element.service("text-encoder");
    ///
    /// plan.relationship(cache, encoder, RelationshipType.SHARED);
    /// // All trials share the same cache instance
    /// // Cache lookups/inserts must be thread-safe
    /// ```
    ///
    SHARED,

    ///
    /// A fresh instance is created per defined scope (e.g., per trial, per group, per run).
    ///
    /// ## Semantics
    ///
    /// ```
    /// ∀ trials t1, t2, ..., tn using element E:
    ///   Each gets instance E_1, E_2, ..., E_n
    ///   Instances are independent and isolated
    /// ```
    ///
    /// ## Execution Pattern
    ///
    /// ```
    /// Trial1: ────────[E1 (isolated)]─────────done
    /// Trial2:     ────────[E2 (isolated)]─────────done
    /// Trial3:         ────────[E3 (isolated)]─────────done
    ///
    /// Timeline → Concurrent, independent instances
    /// ```
    ///
    /// ## Instancing Scopes
    ///
    /// ```
    /// InstancingScope.PER_TRIAL:
    ///   - Each trial gets its own instance
    ///   - Lifecycle: start before trial, stop after trial
    ///
    /// InstancingScope.PER_GROUP:
    ///   - Trials in same group share instance
    ///   - Different groups get different instances
    ///
    /// InstancingScope.PER_RUN:
    ///   - All trials in run share instance
    ///   - Different runs get different instances
    /// ```
    ///
    /// ## Use Cases
    ///
    /// - Per-trial isolation required
    /// - Avoid cross-trial contamination
    /// - Lightweight resource creation
    /// - State must be independent
    ///
    /// ## Example
    ///
    /// ```java
    /// Element container = Element.container("test-environment");
    ///
    /// plan.relationship(container, testService, RelationshipType.INSTANCED_PER);
    /// plan.instancingScope(container, InstancingScope.PER_TRIAL);
    /// // Each trial gets its own isolated container
    ///
    /// Element database = Element.database("temp-db");
    /// plan.relationship(database, app, RelationshipType.INSTANCED_PER);
    /// plan.instancingScope(database, InstancingScope.PER_GROUP);
    /// // Trials in same group share a database
    /// // Different groups get different databases
    /// ```
    ///
    INSTANCED_PER;

    ///
    /// Checks if this relationship allows concurrent access.
    ///
    /// @return true if SHARED or INSTANCED_PER
    ///
    public boolean allowsConcurrency() {
        return this != MUTUALLY_EXCLUSIVE;
    }

    ///
    /// Checks if this relationship requires a shared instance.
    ///
    /// @return true if SHARED or MUTUALLY_EXCLUSIVE (single instance)
    ///
    public boolean requiresSingleInstance() {
        return this != INSTANCED_PER;
    }

    ///
    /// Checks if this relationship requires serialization barriers.
    ///
    /// @return true if MUTUALLY_EXCLUSIVE
    ///
    public boolean requiresBarriers() {
        return this == MUTUALLY_EXCLUSIVE;
    }
}
