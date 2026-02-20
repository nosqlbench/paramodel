package io.nosqlbench.paramodel.elements;

///
/// Semantic relationship type for a directed dependency edge.
///
/// ## Concept
///
/// {@code RelationshipType} defines how a dependent element relates to its
/// upstream dependency. The relationship type is a property of the
/// {@link Element.Dependency} edge, not of the plan — element A declares
/// how it relates to its dependency B, and A's view of B can differ from
/// C's view of B.
///
/// ## Relationship Semantics
///
/// ```
/// RelationshipType   Concurrency Rule                 Instance Model
/// -------------------------------------------------------------------------
/// SHARED             Can overlap                      Single shared instance
/// EXCLUSIVE          Cannot overlap                   Single instance, serialized
/// DEDICATED          Isolated per dependent           Dedicated instance per dependent
/// LINEAR             Full lifecycle ordering           Shared instance, serial within scope
/// LIFELINE           Target subsumes dependent        Dependent torn down with target
/// ```
///
/// ## Concurrency Model
///
/// ```
/// SHARED:
///   Trial1: ----[B (shared)]----
///   Trial2:       ----[B (shared)]----
///           ^ Both use same instance concurrently
///
/// EXCLUSIVE:
///   Trial1: ----[B]----
///   Trial2:              ----[B]----
///           ^ Barrier prevents overlap
///
/// DEDICATED:
///   Trial1: ----[B instance-1]----
///   Trial2: ----[B instance-2]----
///           ^ Each dependent gets its own instance
///
/// LINEAR:
///   Trial1: [B activate → B deactivate] → [A activate → A deactivate]
///           ^ Full lifecycle ordering within shared trial scope
///
/// LIFELINE:
///   B teardown → A automatically torn down
///           ^ A's lifecycle is subsumed by B
/// ```
///
/// ## Use Cases by Type
///
/// ### SHARED
///
/// Use when:
/// - Resource safely supports concurrent access
/// - Read-only or thread-safe
/// - Cost of creating instances is high
///
/// ### EXCLUSIVE
///
/// Use when:
/// - Resource doesn't support concurrent access
/// - State conflicts would occur
/// - Data corruption risk
///
/// ### DEDICATED
///
/// Use when:
/// - Each dependent needs isolated state
/// - Resource is cheap to instantiate
/// - Test isolation is required
///
/// ### LINEAR
///
/// Use when:
/// - Multiple trial elements exist in the same trial scope
/// - Their operative actions must be performed in a specific sequence
/// - Earlier actions provide state for later ones within the trial
///
/// ### LIFELINE
///
/// Use when:
/// - Dependent cannot outlive its dependency
/// - Dependency teardown implicitly destroys the dependent
/// - Reduces unnecessary teardown steps
///
/// ## Planning Impact
///
/// Relationship types affect plan compilation:
///
/// ```
/// SHARED:     → No barriers needed, single instance lifecycle
/// EXCLUSIVE:  → Compiler inserts serialization barriers
/// DEDICATED:  → Compiler creates per-dependent instances
/// LINEAR:     → Compiler enforces full lifecycle ordering within trial scope
/// LIFELINE:   → Dependent's final teardown step is omitted
/// ```
///
/// Instance lifecycle (when an element is redeployed vs. persisted) is
/// determined by the fingerprint-based group mechanism in the compilation
/// pipeline. If an element's parameters change between trials, it is
/// redeployed automatically; if not, it persists.
///
/// @see Element
/// @see Element.Dependency
/// @see io.nosqlbench.paramodel.plan.TestPlan
/// @since 0.1.0
///
public enum RelationshipType {

    /// Dependency can be concurrently used by multiple dependents. This is
    /// the default relationship type.
    SHARED,

    /// During the dependent's lifetime, no other dependent of the target
    /// can be active. The compiler inserts serialization barriers.
    EXCLUSIVE,

    /// The target gets a dedicated instance for the dependent. The target
    /// is never shared with other elements.
    DEDICATED,

    /// Full lifecycle ordering within shared trial scope.
    ///
    /// The target element Y must be activated and fully deactivated — whether
    /// by an explicit service deactivation or by awaiting a command element's
    /// natural completion — before the dependent element X is activated. This
    /// constraint applies only when X and Y share the same trial scope (same
    /// configuration group). When X and Y are in different trial scopes, the
    /// linear constraint does not apply and their lifecycles are independent.
    ///
    /// This means Y's resources are fully released before X begins, preventing
    /// any residual state, measurement perturbation, or resource contention.
    /// Data flow may be implied between elements in the same trial scope.
    LINEAR,

    /// The target's lifecycle subsumes the dependent's. When the target
    /// tears down, the dependent is automatically torn down.
    LIFELINE;

    /// True when dependents of the target must be serialized.
    ///
    /// @return true if this is {@link #EXCLUSIVE}
    public boolean requiresSerializationBarrier() {
        return this == EXCLUSIVE;
    }

    /// True when the target gets a dedicated instance per dependent.
    ///
    /// @return true if this is {@link #DEDICATED}
    public boolean requiresDedicatedInstance() {
        return this == DEDICATED;
    }

    /// True when the dependency implies a linear execution order within
    /// the same trial scope.
    ///
    /// @return true if this is {@link #LINEAR}
    public boolean isLinear() {
        return this == LINEAR;
    }

    /// True when the target's teardown subsumes the dependent's teardown.
    ///
    /// @return true if this is {@link #LIFELINE}
    public boolean impliesLifecycleCoupling() {
        return this == LIFELINE;
    }
}
