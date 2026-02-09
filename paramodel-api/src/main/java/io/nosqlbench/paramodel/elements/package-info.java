///
/// Element modeling for parameterized resources, lifecycles, instancing, and relationships.
///
/// ## Overview
///
/// This package defines the element abstraction for resources that participate in
/// study execution. Elements represent anything that must be provisioned, started,
/// health-checked, and stopped during trials.
///
/// Element **types** are not predetermined by the paramodel API — the concrete type
/// taxonomy (e.g. service, environment, cache, dataset) is defined by the adopting
/// system and conveyed through {@link Element#tags() tags}. What makes an element
/// usable is its set of {@link io.nosqlbench.paramodel.parameters.Parameter parameters},
/// which define the element's configurable dimensions.
///
/// ## Element Maturity Levels
///
/// ```
/// Element Model (this package)
///   │  Has parameter models — defines what can be configured
///   ▼
/// Element Instance
///   │  Has bound parameter values — specific configuration chosen
///   ▼
/// Materialized Element
///      Has real-world configuration values — live resource handle
/// ```
///
/// ## Core Types
///
/// ```
/// Element
///   ├── name: String              - Unique identifier within a study
///   ├── parameters: List          - Configurable dimensions (Parameter models)
///   ├── dependencies: List        - Other elements this depends on (DAG)
///   ├── healthCheck: Optional     - Readiness verification strategy
///   └── instancingScope: Optional - PER_TRIAL | PER_GROUP | PER_RUN
///
/// RelationshipType
///   ├── MUTUALLY_EXCLUSIVE  - Serialize access (barriers inserted)
///   ├── SHARED              - Concurrent access to single instance
///   └── INSTANCED_PER       - Fresh instance per scope
/// ```
///
/// ## Relationship to Test Plans
///
/// Elements are declared in {@link io.nosqlbench.paramodel.plan.TestPlan} instances
/// and their relationships determine how the compiler generates execution plans:
///
/// ```
/// TestPlan
///   ├── axes (parameter dimensions)
///   ├── elements (from this package)
///   ├── relationships (Element, Element) → RelationshipType
///   └── policies
/// ```
///
/// @see Element
/// @see RelationshipType
/// @see io.nosqlbench.paramodel.plan.TestPlan
/// @since 0.1.0
///
package io.nosqlbench.paramodel.elements;
