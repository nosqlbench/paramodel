package io.nosqlbench.paramodel.mock.elements;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.parameters.types.BooleanParameter;
import io.nosqlbench.paramodel.parameters.types.DoubleParameter;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.SelectionParameter;
import io.nosqlbench.paramodel.plan.TestPlan;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

///
/// Factory for pre-built virtual element definitions modeling a realistic infrastructure.
///
/// ## Dependency DAG
///
/// The virtual elements form a directed acyclic graph:
///
/// ```
///   node (PER_TRIAL, TCP health check)
///     │
///     └──depends on──▶ daemon (PER_RUN, HTTP health check)
///                         │
///                         └──depends on──▶ dataset (no scope, no health check)
/// ```
///
/// ## Elements
///
/// - **dataset** — a static data resource with size, format, and region parameters
/// - **daemon** — a long-running service with workers, port, debug, and heap parameters
/// - **node** — a compute instance with instance type, CPU, memory, and spot parameters
///
/// ## Usage
///
/// ```java
/// // Individual elements
/// Element ds = VirtualElements.dataset();
/// Element dm = VirtualElements.daemon();
/// Element nd = VirtualElements.node();
///
/// // All elements in dependency order
/// List<Element> all = VirtualElements.all();
///
/// // Full study specification with relationships
/// VirtualElements.StudySpec spec = VirtualElements.withRelationships();
/// ```
///
/// @see MockElement
/// @see MockHealthCheckSpec
/// @since 0.1.0
///
public final class VirtualElements {

    private VirtualElements() {}

    ///
    /// Creates a dataset element — a static data resource with no dependencies.
    ///
    /// Parameters:
    /// - `size_gb` — dataset size in GB, range [1, 1000]
    /// - `format` — storage format: parquet, csv, or json
    /// - `region` — deployment region: us-east-1, us-west-2, or eu-west-1
    ///
    /// @return dataset element with no dependencies, no health check, no instancing scope
    ///
    public static MockElement dataset() {
        return MockElement.builder("dataset")
            .type("dataset")
            .parameter(IntegerParameter.range("size_gb", 1, 1000))
            .parameter(SelectionParameter.of("format", Set.of("parquet", "csv", "json")))
            .parameter(SelectionParameter.of("region", Set.of("us-east-1", "us-west-2", "eu-west-1")))
            .build();
    }

    ///
    /// Creates a daemon element that depends on the default dataset.
    ///
    /// Parameters:
    /// - `workers` — worker thread count, range [1, 64]
    /// - `port` — listen port, range [1024, 65535]
    /// - `debug` — debug mode on/off
    /// - `heap_gb` — JVM heap size in GB, range [0.5, 32.0]
    ///
    /// @return daemon element depending on a default dataset, with HTTP health check and PER_RUN scope
    ///
    public static MockElement daemon() {
        return daemon(dataset());
    }

    ///
    /// Creates a daemon element that depends on the specified dataset.
    ///
    /// @param dataset the dataset element this daemon reads from
    /// @return daemon element depending on specified dataset, with HTTP health check and PER_RUN scope
    ///
    public static MockElement daemon(Element dataset) {
        return MockElement.builder("daemon")
            .type("daemon")
            .parameter(IntegerParameter.range("workers", 1, 64))
            .parameter(IntegerParameter.range("port", 1024, 65535))
            .parameter(BooleanParameter.of("debug"))
            .parameter(DoubleParameter.range("heap_gb", 0.5, 32.0))
            .dependency(dataset)
            .healthCheck(MockHealthCheckSpec.http(Duration.ofSeconds(30)))
            .instancingScope(Element.InstancingScope.PER_RUN)
            .build();
    }

    ///
    /// Creates a node element that depends on the default daemon (which depends on the default dataset).
    ///
    /// Parameters:
    /// - `instance_type` — cloud instance type: t3.medium, t3.large, m5.xlarge, or m5.2xlarge
    /// - `cpu_cores` — CPU cores, range [1, 32]
    /// - `memory_gb` — RAM in GB, range [1.0, 256.0]
    /// - `spot_instance` — use spot pricing on/off
    ///
    /// @return node element depending on a default daemon, with TCP health check and PER_TRIAL scope
    ///
    public static MockElement node() {
        return node(daemon());
    }

    ///
    /// Creates a node element that depends on the specified daemon.
    ///
    /// @param daemon the daemon element this node hosts
    /// @return node element depending on specified daemon, with TCP health check and PER_TRIAL scope
    ///
    public static MockElement node(Element daemon) {
        return MockElement.builder("node")
            .type("node")
            .parameter(SelectionParameter.of("instance_type",
                Set.of("t3.medium", "t3.large", "m5.xlarge", "m5.2xlarge")))
            .parameter(IntegerParameter.range("cpu_cores", 1, 32))
            .parameter(DoubleParameter.range("memory_gb", 1.0, 256.0))
            .parameter(BooleanParameter.of("spot_instance"))
            .dependency(daemon)
            .healthCheck(MockHealthCheckSpec.tcp(Duration.ofSeconds(15)))
            .instancingScope(Element.InstancingScope.PER_TRIAL)
            .build();
    }

    ///
    /// Returns all three virtual elements in dependency order: dataset, daemon, node.
    ///
    /// The returned elements are wired together: daemon depends on dataset,
    /// node depends on daemon.
    ///
    /// @return list of [dataset, daemon, node] in dependency order
    ///
    public static List<Element> all() {
        MockElement ds = dataset();
        MockElement dm = daemon(ds);
        MockElement nd = node(dm);
        return List.of(ds, dm, nd);
    }

    ///
    /// Returns a full study specification with elements and relationship types.
    ///
    /// Relationships:
    /// - dataset ↔ daemon: {@link RelationshipType#SHARED} (static data, shared by all)
    /// - daemon ↔ node: {@link RelationshipType#INSTANCED_PER} (each node gets its own daemon view)
    ///
    /// @return study specification with elements in dependency order and relationship map
    ///
    public static StudySpec withRelationships() {
        List<Element> elements = all();
        Element ds = elements.get(0);
        Element dm = elements.get(1);
        Element nd = elements.get(2);

        Map<TestPlan.ElementPair, RelationshipType> relationships = Map.of(
            new TestPlan.ElementPair(ds, dm), RelationshipType.SHARED,
            new TestPlan.ElementPair(dm, nd), RelationshipType.INSTANCED_PER
        );

        return new StudySpec(elements, relationships);
    }

    ///
    /// A complete study specification containing elements and their relationships.
    ///
    /// @param elements      elements in dependency order (leaf nodes first)
    /// @param relationships map of element pairs to relationship types
    ///
    public record StudySpec(
        List<Element> elements,
        Map<TestPlan.ElementPair, RelationshipType> relationships
    ) {
        public StudySpec {
            elements = List.copyOf(elements);
            relationships = Map.copyOf(relationships);
        }
    }
}
