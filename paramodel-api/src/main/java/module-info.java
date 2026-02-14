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
/// ├── parameters    - Parameter modeling (Parameter, Domain, Constraint, Value)
/// ├── elements      - Element modeling (Element, RelationshipType)
/// ├── sequence      - Sequence generation and validation
/// ├── plan          - Test Plan and Execution Plan contracts
/// ├── compilation   - Plan compilation and validation
/// ├── execution     - Runtime execution contracts
/// ├── persistence   - Result and artifact storage
/// ├── security      - Access control (deferred)
/// └── util          - Common utilities
/// ```
///
/// @since 0.1.0
///
module io.nosqlbench.paramodel {
    // Export all API packages
    exports io.nosqlbench.paramodel.parameters;
    exports io.nosqlbench.paramodel.parameters.types;
    exports io.nosqlbench.paramodel.elements;
    exports io.nosqlbench.paramodel.sequence;
    exports io.nosqlbench.paramodel.plan;
    exports io.nosqlbench.paramodel.plan.policies;
    exports io.nosqlbench.paramodel.compilation;
    exports io.nosqlbench.paramodel.execution;
    exports io.nosqlbench.paramodel.execution.journal;
    exports io.nosqlbench.paramodel.persistence;
    exports io.nosqlbench.paramodel.security;
    exports io.nosqlbench.paramodel.util;

    // SPI contracts for element discovery
    uses io.nosqlbench.paramodel.elements.ElementProvider;
    uses io.nosqlbench.paramodel.elements.ElementTypeDescriptorProvider;
}
