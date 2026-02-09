module io.nosqlbench.paramodel.tck {
    requires transitive io.nosqlbench.paramodel;
    requires transitive org.junit.jupiter.api;
    requires org.assertj.core;

    exports io.nosqlbench.paramodel.tck;
    exports io.nosqlbench.paramodel.tck.compilation;
    exports io.nosqlbench.paramodel.tck.elements;
    exports io.nosqlbench.paramodel.tck.execution;
    exports io.nosqlbench.paramodel.tck.parameters;
    exports io.nosqlbench.paramodel.tck.persistence;
    exports io.nosqlbench.paramodel.tck.security;
    exports io.nosqlbench.paramodel.tck.sequence;
    exports io.nosqlbench.paramodel.tck.plan;
    exports io.nosqlbench.paramodel.tck.util;
}
