///
/// Paramodel API module providing contract interfaces for parameter modeling and study execution.
///
/// This module contains ONLY interface definitions with no implementation code.
/// It defines the contract types that all conforming implementations must satisfy.
///
/// ## Module Structure
///
/// ```
/// io.nosqlbench.paramodel
/// ├── core          - Core parameter modeling (Parameter, Domain, Constraint, Value)
/// ├── sequence      - Sequence generation and validation
/// ├── plan          - Test Plan and Execution Plan contracts
/// ├── compilation   - Plan compilation and validation
/// ├── execution     - Runtime execution contracts
/// ├── observability - Monitoring and telemetry
/// ├── persistence   - Result and artifact storage
/// ├── cost          - Cost estimation and simulation
/// ├── security      - Access control
/// ├── versioning    - Version management and provenance
/// └── util          - Common utilities
/// ```
///
/// @since 0.1.0
///
module io.nosqlbench.paramodel {
    // Export all API packages
    exports io.nosqlbench.paramodel.core;
    exports io.nosqlbench.paramodel.core.metadata;
    exports io.nosqlbench.paramodel.sequence;
    exports io.nosqlbench.paramodel.plan;
    exports io.nosqlbench.paramodel.plan.policies;
    exports io.nosqlbench.paramodel.compilation;
    exports io.nosqlbench.paramodel.execution;
    exports io.nosqlbench.paramodel.observability;
    exports io.nosqlbench.paramodel.persistence;
    exports io.nosqlbench.paramodel.cost;
    exports io.nosqlbench.paramodel.security;
    exports io.nosqlbench.paramodel.versioning;
    exports io.nosqlbench.paramodel.util;
}
