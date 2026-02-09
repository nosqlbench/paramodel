package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.Parameter;

import java.util.*;

///
/// Simple element implementation for testing.
///
/// Creates element models with optional parameters, dependencies,
/// health checks, and instancing scopes. The element type is conveyed
/// through tags rather than a fixed enum, matching the paramodel API
/// contract that types are system-defined.
///
/// ## Usage
///
/// ```java
/// // Simple element with no parameters
/// Element elem = MockElement.of("database");
///
/// // Element with a type tag
/// Element svc = MockElement.ofType("api-server", "service");
///
/// // Element with parameters
/// Element db = MockElement.builder("postgres")
///     .type("service")
///     .parameter(IntegerParameter.range("port", 1024, 65535))
///     .parameter(IntegerParameter.range("max_connections", 1, 500))
///     .build();
/// ```
///
public class MockElement implements Element {
    private final String name;
    private final String type;
    private final List<Parameter<?>> parameters;
    private final List<Element> dependencies;
    private final HealthCheckSpec healthCheck;
    private final InstancingScope instancingScope;

    private MockElement(String name, String type, List<Parameter<?>> parameters,
                        List<Element> dependencies, HealthCheckSpec healthCheck,
                        InstancingScope instancingScope) {
        this.name = Objects.requireNonNull(name);
        this.type = type;
        this.parameters = parameters != null ? List.copyOf(parameters) : List.of();
        this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        this.healthCheck = healthCheck;
        this.instancingScope = instancingScope;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        if (type != null) {
            return Map.of("name", name, "type", type);
        }
        return Map.of("name", name);
    }

    @Override
    public List<Parameter<?>> parameters() {
        return parameters;
    }

    @Override
    public List<Element> dependencies() {
        return dependencies;
    }

    @Override
    public Optional<HealthCheckSpec> healthCheck() {
        return Optional.ofNullable(healthCheck);
    }

    @Override
    public Optional<InstancingScope> instancingScope() {
        return Optional.ofNullable(instancingScope);
    }

    ///
    /// Creates an element with just a name (no type tag, no parameters).
    ///
    /// @param name element name
    /// @return a simple mock element
    ///
    public static MockElement of(String name) {
        return new MockElement(name, null, List.of(), List.of(), null, null);
    }

    ///
    /// Creates an element with a name and type tag.
    ///
    /// @param name element name
    /// @param type type tag value (e.g. "service", "cache", "environment")
    /// @return a typed mock element
    ///
    public static MockElement ofType(String name, String type) {
        return new MockElement(name, type, List.of(), List.of(), null, null);
    }

    ///
    /// Creates a builder for constructing elements with full configuration.
    ///
    /// @param name element name
    /// @return a new builder
    ///
    public static Builder builder(String name) {
        return new Builder(name);
    }

    ///
    /// Builder for constructing {@link MockElement} instances.
    ///
    public static class Builder {
        private final String name;
        private String type;
        private final List<Parameter<?>> parameters = new ArrayList<>();
        private final List<Element> dependencies = new ArrayList<>();
        private HealthCheckSpec healthCheck;
        private InstancingScope instancingScope;

        public Builder(String name) {
            this.name = name;
        }

        ///
        /// Sets the type tag for this element.
        ///
        /// @param type type tag value
        /// @return this builder
        ///
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        ///
        /// Adds a parameter model to this element.
        ///
        /// @param parameter the parameter to add
        /// @return this builder
        ///
        public Builder parameter(Parameter<?> parameter) {
            this.parameters.add(parameter);
            return this;
        }

        ///
        /// Adds a dependency on another element.
        ///
        /// @param dependency the element this depends on
        /// @return this builder
        ///
        public Builder dependency(Element dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        ///
        /// Sets the health check specification.
        ///
        /// @param healthCheck the health check spec
        /// @return this builder
        ///
        public Builder healthCheck(HealthCheckSpec healthCheck) {
            this.healthCheck = healthCheck;
            return this;
        }

        ///
        /// Sets the instancing scope.
        ///
        /// @param instancingScope the instancing scope
        /// @return this builder
        ///
        public Builder instancingScope(InstancingScope instancingScope) {
            this.instancingScope = instancingScope;
            return this;
        }

        ///
        /// Builds the element.
        ///
        /// @return the constructed element
        ///
        public MockElement build() {
            return new MockElement(name, type, parameters, dependencies, healthCheck, instancingScope);
        }
    }
}
