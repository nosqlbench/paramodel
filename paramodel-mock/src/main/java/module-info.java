///
/// Paramodel Mock module providing simple in-memory implementations for testing.
///
/// This module contains concrete implementations of paramodel-api contracts
/// suitable for unit tests, examples, and prototyping.
///
/// @since 0.1.0
///
module io.nosqlbench.paramodel.mock {
    requires transitive io.nosqlbench.paramodel;

    exports io.nosqlbench.paramodel.mock.compilation;
    exports io.nosqlbench.paramodel.mock.execution;
    exports io.nosqlbench.paramodel.mock.parameters;
    exports io.nosqlbench.paramodel.mock.sequence;
    exports io.nosqlbench.paramodel.mock.persistence;
    exports io.nosqlbench.paramodel.mock.security;
    exports io.nosqlbench.paramodel.mock.plan;
    exports io.nosqlbench.paramodel.mock.util;
    exports io.nosqlbench.paramodel.mock.elements;
}
