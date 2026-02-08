module io.nosqlbench.paramodel.tck {
    requires io.nosqlbench.paramodel;
    requires org.junit.jupiter.api;
    requires org.assertj.core;

    exports io.nosqlbench.paramodel.tck;
    exports io.nosqlbench.paramodel.tck.core;
    exports io.nosqlbench.paramodel.tck.sequence;
    exports io.nosqlbench.paramodel.tck.plan;
}
