///
/// Paramodel Mock module providing simple in-memory implementations for testing.
///
/// This module contains concrete implementations of paramodel-api contracts
/// suitable for unit tests, examples, and prototyping.
///
/// @since 0.1.0
///
module io.nosqlbench.paramodel.mock {
    requires io.nosqlbench.paramodel;

    exports io.nosqlbench.paramodel.mock;
    exports io.nosqlbench.paramodel.mock.core;
    exports io.nosqlbench.paramodel.mock.sequence;
    exports io.nosqlbench.paramodel.mock.plan;
}
