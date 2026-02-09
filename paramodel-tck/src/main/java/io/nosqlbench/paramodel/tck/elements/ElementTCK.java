package io.nosqlbench.paramodel.tck.elements;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for Element contract.
///
/// Validates that implementations correctly:
/// - Provide non-null, non-empty names
/// - Return tags containing at minimum a "name" entry
/// - Return non-null parameter, dependency, health check, and scope values
/// - Support element construction with dependencies and health checks
///
/// @see Element
/// @since 0.1.0
///
public abstract class ElementTCK {
    protected ElementTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testElementHasName() {
        Element element = getProvider().createElement("database");

        assertThat(element.name()).isNotNull();
        assertThat(element.name()).isNotEmpty();
        assertThat(element.name()).isEqualTo("database");
    }

    @Test
    public void testElementHasTags() {
        Element element = getProvider().createElement("cache");

        assertThat(element.tags()).isNotNull();
        assertThat(element.tags()).containsKey("name");
        assertThat(element.tags().get("name")).isEqualTo("cache");
    }

    @Test
    public void testElementHasParameters() {
        Element element = getProvider().createElement("service");

        assertThat(element.parameters()).isNotNull();
    }

    @Test
    public void testElementHasDependencies() {
        Element element = getProvider().createElement("app-server");

        assertThat(element.dependencies()).isNotNull();
    }

    @Test
    public void testElementHealthCheck() {
        Element element = getProvider().createElement("api");

        // healthCheck() should return a non-null Optional
        assertThat(element.healthCheck()).isNotNull();
    }

    @Test
    public void testElementInstancingScope() {
        Element element = getProvider().createElement("worker");

        // instancingScope() should return a non-null Optional
        assertThat(element.instancingScope()).isNotNull();
    }

    @Test
    public void testElementWithType() {
        Element element = getProvider().createTypedElement("postgres", "service");

        assertThat(element.name()).isEqualTo("postgres");
        assertThat(element.tags()).containsKey("type");
        assertThat(element.tags().get("type")).isEqualTo("service");
    }

    @Test
    public void testElementWithDependencies() {
        Element dep = getProvider().createElement("storage");
        Element element = getProvider().createElementWithDependencies("database",
            java.util.List.of(dep));

        assertThat(element.dependencies()).isNotEmpty();
        assertThat(element.dependencies()).hasSize(1);
    }

    @Test
    public void testElementWithHealthCheck() {
        Element.HealthCheckSpec healthCheck = getProvider().createHealthCheckSpec(
            "HTTP", java.time.Duration.ofSeconds(30));
        Element element = getProvider().createElementWithHealthCheck("service", healthCheck);

        assertThat(element.healthCheck()).isPresent();
        assertThat(element.healthCheck().get().type()).isEqualTo("HTTP");
        assertThat(element.healthCheck().get().timeout()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(element.healthCheck().get().maxRetries()).isGreaterThanOrEqualTo(0);
        assertThat(element.healthCheck().get().retryInterval()).isNotNull();
    }

    @Test
    public void testElementWithInstancingScope() {
        Element element = getProvider().createElementWithScope("container",
            Element.InstancingScope.PER_TRIAL);

        assertThat(element.instancingScope()).isPresent();
        assertThat(element.instancingScope().get()).isEqualTo(Element.InstancingScope.PER_TRIAL);
    }
}
