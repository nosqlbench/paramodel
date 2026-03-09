package io.nosqlbench.paramodel.mock.elements;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.types.BooleanParameter;
import io.nosqlbench.paramodel.parameters.types.DoubleParameter;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.SelectionParameter;
import io.nosqlbench.paramodel.parameters.types.StringParameter;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for {@link VirtualElements} exercising the full Element API surface.
///
class VirtualElementsTest {

    // -----------------------------------------------------------------------
    // Element structure tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Element Structure")
    class ElementStructure {

        @Test
        @DisplayName("dataset has correct name, type, parameters, no dependencies, no health check")
        void testDatasetElement() {
            Element dataset = VirtualElements.dataset();

            assertThat(dataset.name()).isEqualTo("dataset");
            assertThat(dataset.labels()).containsEntry("type", "dataset");
            assertThat(dataset.parameters()).hasSize(3);
            assertThat(dataset.dependencies()).isEmpty();
            assertThat(dataset.healthCheck()).isEmpty();
        }

        @Test
        @DisplayName("daemon has correct name, type, 4 parameters, depends on dataset, health check")
        void testDaemonElement() {
            Element daemon = VirtualElements.daemon();

            assertThat(daemon.name()).isEqualTo("daemon");
            assertThat(daemon.labels()).containsEntry("type", "daemon");
            assertThat(daemon.parameters()).hasSize(4);
            assertThat(daemon.dependencies()).hasSize(1);
            assertThat(daemon.dependencies().get(0).target().name()).isEqualTo("dataset");
            assertThat(daemon.healthCheck()).isPresent();
        }

        @Test
        @DisplayName("node has correct name, type, 4 parameters, depends on daemon, health check")
        void testNodeElement() {
            Element node = VirtualElements.node();

            assertThat(node.name()).isEqualTo("node");
            assertThat(node.labels()).containsEntry("type", "node");
            assertThat(node.parameters()).hasSize(4);
            assertThat(node.dependencies()).hasSize(1);
            assertThat(node.dependencies().get(0).target().name()).isEqualTo("daemon");
            assertThat(node.healthCheck()).isPresent();
        }

        @Test
        @DisplayName("elements can model deployment result parameters separately from input parameters")
        void testElementResultParameters() {
            Element service = MockElement.builder("service")
                .parameter(IntegerParameter.range("replicas", 1, 5))
                .resultParameter(StringParameter.of("endpoint"))
                .resultParameter(StringParameter.of("credential_id"))
                .build();

            assertThat(service.parameters()).hasSize(1);
            assertThat(parameterNames(service.parameters())).containsExactly("replicas");
            assertThat(service.resultParameters()).hasSize(2);
            assertThat(parameterNames(service.resultParameters()))
                .containsExactly("endpoint", "credential_id");
        }
    }

    // -----------------------------------------------------------------------
    // Parameter tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Parameters")
    class Parameters {

        @Test
        @DisplayName("dataset parameters have correct names and domains")
        void testDatasetParameters() {
            Element dataset = VirtualElements.dataset();
            List<Parameter<?>> params = dataset.parameters();

            assertThat(parameterNames(params))
                .containsExactly("size_gb", "format", "region");

            Parameter<?> sizeGb = findParameter(params, "size_gb");
            assertThat(sizeGb).isInstanceOf(IntegerParameter.class);

            Parameter<?> format = findParameter(params, "format");
            assertThat(format).isInstanceOf(SelectionParameter.class);

            Parameter<?> region = findParameter(params, "region");
            assertThat(region).isInstanceOf(SelectionParameter.class);
        }

        @Test
        @DisplayName("daemon parameters have correct names and support generation")
        void testDaemonParameters() {
            Element daemon = VirtualElements.daemon();
            List<Parameter<?>> params = daemon.parameters();

            assertThat(parameterNames(params))
                .containsExactly("workers", "port", "debug", "heap_gb");

            for (Parameter<?> param : params) {
                Object value = param.generate();
                assertThat(value).isNotNull();

                Object boundary = param.generateBoundary();
                assertThat(boundary).isNotNull();

                Object random = param.generateRandom();
                assertThat(random).isNotNull();
            }
        }

        @Test
        @DisplayName("node parameters have correct names and validate out-of-domain values")
        void testNodeParameters() {
            Element node = VirtualElements.node();
            List<Parameter<?>> params = node.parameters();

            assertThat(parameterNames(params))
                .containsExactly("instance_type", "cpu_cores", "memory_gb", "spot_instance");

            // cpu_cores out of range should fail
            IntegerParameter cpuCores = (IntegerParameter) findParameter(params, "cpu_cores");
            assertThat(cpuCores.validate(0).isFailed()).isTrue();
            assertThat(cpuCores.validate(33).isFailed()).isTrue();
            assertThat(cpuCores.validate(16).isPassed()).isTrue();

            // memory_gb out of range should fail
            DoubleParameter memoryGb = (DoubleParameter) findParameter(params, "memory_gb");
            assertThat(memoryGb.validate(0.5).isFailed()).isTrue();
            assertThat(memoryGb.validate(257.0).isFailed()).isTrue();
            assertThat(memoryGb.validate(128.0).isPassed()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Dependency DAG tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Dependency DAG")
    class DependencyDAG {

        @Test
        @DisplayName("node depends on daemon, daemon depends on dataset — transitive chain")
        void testDependencyChain() {
            List<Element> all = VirtualElements.all();
            Element ds = all.get(0);
            Element dm = all.get(1);
            Element nd = all.get(2);

            assertThat(ds.dependencies()).isEmpty();
            assertThat(dm.dependencies()).hasSize(1);
            assertThat(dm.dependencies().get(0).target().name()).isEqualTo("dataset");
            assertThat(nd.dependencies()).hasSize(1);
            assertThat(nd.dependencies().get(0).target().name()).isEqualTo("daemon");

            // Transitive: node's daemon's dataset is the same dataset
            Element transitiveDep = nd.dependencies().get(0).target().dependencies().get(0).target();
            assertThat(transitiveDep.name()).isEqualTo("dataset");
        }

        @Test
        @DisplayName("custom dataset wiring is reflected in daemon dependencies")
        void testCustomDependencies() {
            MockElement customDs = MockElement.builder("custom-dataset")
                .type("dataset")
                .build();
            Element dm = VirtualElements.daemon(customDs);

            assertThat(dm.dependencies()).hasSize(1);
            assertThat(dm.dependencies().get(0).target().name()).isEqualTo("custom-dataset");
        }

        @Test
        @DisplayName("dependency graph has no cycles")
        void testNoCyclicDependencies() {
            List<Element> all = VirtualElements.all();
            Set<String> visited = new HashSet<>();
            for (Element element : all) {
                assertNoCycles(element, visited, new LinkedHashSet<>());
            }
        }

        private void assertNoCycles(Element element, Set<String> globalVisited, LinkedHashSet<String> path) {
            String name = element.name();
            assertThat(path).as("Cycle detected: " + path + " -> " + name).doesNotContain(name);
            if (globalVisited.contains(name)) {
                return;
            }
            globalVisited.add(name);
            path.add(name);
            for (Element.Dependency dep : element.dependencies()) {
                assertNoCycles(dep.target(), globalVisited, new LinkedHashSet<>(path));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Health check tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Health Checks")
    class HealthChecks {

        @Test
        @DisplayName("daemon has health check with 30s timeout and 3 retries")
        void testDaemonHealthCheck() {
            Element daemon = VirtualElements.daemon();
            Element.HealthCheckSpec hc = daemon.healthCheck().orElseThrow();

            assertThat(hc.timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(hc.maxRetries()).isEqualTo(3);
            assertThat(hc.retryInterval()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("node has health check with 15s timeout")
        void testNodeHealthCheck() {
            Element node = VirtualElements.node();
            Element.HealthCheckSpec hc = node.healthCheck().orElseThrow();

            assertThat(hc.timeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(hc.maxRetries()).isEqualTo(3);
        }

        @Test
        @DisplayName("dataset has no health check")
        void testDatasetNoHealthCheck() {
            Element dataset = VirtualElements.dataset();
            assertThat(dataset.healthCheck()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Relationship and TestPlan integration tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Relationships and TestPlan Integration")
    class RelationshipsAndPlan {

        @Test
        @DisplayName("build a TestPlan from VirtualElements.all() with parameter-derived axes")
        void testBuildTestPlanWithVirtualElements() {
            List<Element> elements = VirtualElements.all();

            MockTestPlan plan = MockTestPlan.builder()
                .name("virtual-elements-study")
                .axis(MockAxis.of("size_gb", List.of(1, 100, 500, 1000)))
                .axis(MockAxis.of("workers", List.of(1, 16, 64)))
                .axis(MockAxis.of("cpu_cores", List.of(1, 8, 32)))
                .element(elements.get(0))
                .element(elements.get(1))
                .element(elements.get(2))
                .build();

            assertThat(plan.name()).isEqualTo("virtual-elements-study");
            assertThat(plan.axes()).hasSize(3);
            assertThat(plan.elements()).hasSize(3);
            assertThat(plan.trialSpaceSize()).isEqualTo(4L * 3L * 3L);

            ValidationResult result = plan.validate();
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("plan built from VirtualElements can be committed to produce ExecutionPlan")
        void testPlanCommitWithVirtualElements() {
            List<Element> elements = VirtualElements.all();

            MockTestPlan plan = MockTestPlan.builder()
                .name("commit-test")
                .axis(MockAxis.of("size_gb", List.of(1, 500)))
                .element(elements.get(0))
                .element(elements.get(1))
                .element(elements.get(2))
                .build();

            assertThat(plan.isCommitted()).isFalse();

            ExecutionPlan execPlan = plan.commit();
            assertThat(execPlan).isNotNull();
            assertThat(execPlan.id()).isNotNull();
            assertThat(plan.isCommitted()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Value generation and validation tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Value Generation and Validation")
    class ValueGeneration {

        @Test
        @DisplayName("all parameters generate valid values")
        void testParameterValueGeneration() {
            List<Element> all = VirtualElements.all();

            for (Element element : all) {
                for (Parameter<?> param : element.parameters()) {
                    Object value = param.generate();
                    assertThat(value)
                        .as("Generated value for %s.%s", element.name(), param.name())
                        .isNotNull();
                    assertValidation(param, value);
                }
            }
        }

        @Test
        @DisplayName("boundary values are within domain")
        void testBoundaryValues() {
            List<Element> all = VirtualElements.all();

            for (Element element : all) {
                for (Parameter<?> param : element.parameters()) {
                    Object boundary = param.generateBoundary();
                    assertThat(boundary)
                        .as("Boundary value for %s.%s", element.name(), param.name())
                        .isNotNull();
                    assertValidation(param, boundary);
                }
            }
        }

        @Test
        @DisplayName("validation rejects out-of-domain values for typed parameters")
        void testValidationRejectsOutOfDomain() {
            Element daemon = VirtualElements.daemon();

            IntegerParameter workers = (IntegerParameter) findParameter(daemon.parameters(), "workers");
            assertThat(workers.validate(0).isFailed()).isTrue();
            assertThat(workers.validate(65).isFailed()).isTrue();
            assertThat(workers.validate(32).isPassed()).isTrue();

            IntegerParameter port = (IntegerParameter) findParameter(daemon.parameters(), "port");
            assertThat(port.validate(1023).isFailed()).isTrue();
            assertThat(port.validate(65536).isFailed()).isTrue();
            assertThat(port.validate(8080).isPassed()).isTrue();

            DoubleParameter heapGb = (DoubleParameter) findParameter(daemon.parameters(), "heap_gb");
            assertThat(heapGb.validate(0.4).isFailed()).isTrue();
            assertThat(heapGb.validate(32.1).isFailed()).isTrue();
            assertThat(heapGb.validate(16.0).isPassed()).isTrue();

            SelectionParameter format = (SelectionParameter) findParameter(
                VirtualElements.dataset().parameters(), "format");
            assertThat(format.validate(List.of("parquet")).isPassed()).isTrue();
            assertThat(format.validate(List.of("xml")).isFailed()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // all() ordering test
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("all() returns elements in dependency order: dataset, daemon, node")
    void testAllDependencyOrder() {
        List<Element> all = VirtualElements.all();

        assertThat(all).hasSize(3);
        assertThat(all.get(0).name()).isEqualTo("dataset");
        assertThat(all.get(1).name()).isEqualTo("daemon");
        assertThat(all.get(2).name()).isEqualTo("node");
    }

    // -----------------------------------------------------------------------
    // MockHealthCheckSpec tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("MockHealthCheckSpec")
    class HealthCheckSpecTests {

        @Test
        @DisplayName("withTimeout factory produces spec with defaults")
        void testWithTimeoutFactory() {
            MockHealthCheckSpec spec = MockHealthCheckSpec.withTimeout(Duration.ofSeconds(20));
            assertThat(spec.timeout()).isEqualTo(Duration.ofSeconds(20));
            assertThat(spec.maxRetries()).isEqualTo(3);
            assertThat(spec.retryInterval()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("custom constructor allows full configuration")
        void testCustomConstructor() {
            MockHealthCheckSpec spec = new MockHealthCheckSpec(
                Duration.ofSeconds(60), 5, Duration.ofSeconds(10));
            assertThat(spec.timeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(spec.maxRetries()).isEqualTo(5);
            assertThat(spec.retryInterval()).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("constructor rejects negative maxRetries")
        void testRejectsNegativeRetries() {
            assertThatThrownBy(() -> new MockHealthCheckSpec(
                Duration.ofSeconds(30), -1, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static List<String> parameterNames(List<Parameter<?>> params) {
        return params.stream().map(Parameter::name).toList();
    }

    private static Parameter<?> findParameter(List<Parameter<?>> params, String name) {
        return params.stream()
            .filter(p -> p.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Parameter not found: " + name));
    }

    @SuppressWarnings("unchecked")
    private static <T> void assertValidation(Parameter<T> param, Object value) {
        ValidationResult result = param.validate((T) value);
        assertThat(result.isPassed())
            .as("Validation of %s with value %s should pass", param.name(), value)
            .isTrue();
    }
}
