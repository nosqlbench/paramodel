module io.nosqlbench.paramodel.engine {
    requires io.nosqlbench.paramodel;
    requires org.slf4j;

    exports io.nosqlbench.paramodel.engine;
    exports io.nosqlbench.paramodel.engine.compiler;
    exports io.nosqlbench.paramodel.engine.execution;
    exports io.nosqlbench.paramodel.engine.observability;
}
