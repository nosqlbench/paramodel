module io.nosqlbench.paramodel.engine {
    requires transitive io.nosqlbench.paramodel;
    requires org.slf4j;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;

    exports io.nosqlbench.paramodel.engine;
    exports io.nosqlbench.paramodel.engine.compiler;
    exports io.nosqlbench.paramodel.engine.binding;
    exports io.nosqlbench.paramodel.engine.definition;
    exports io.nosqlbench.paramodel.engine.execution;
    exports io.nosqlbench.paramodel.engine.execution.journal;
    exports io.nosqlbench.paramodel.engine.plan;
    exports io.nosqlbench.paramodel.engine.sequence;
}
