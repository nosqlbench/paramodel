package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.Element;

import java.util.*;

/**
 * Simple element implementation.
 */
public class MockElement implements Element {
    private final String name;
    private final ElementType type;
    private final Map<String, Object> configuration;
    private final List<Element> dependencies;
    private final HealthCheckSpec healthCheck;
    private final InstancingScope instancingScope;

    public MockElement(String name, ElementType type, Map<String, Object> configuration,
                       HealthCheckSpec healthCheck, InstancingScope instancingScope) {
        this(name, type, configuration, List.of(), healthCheck, instancingScope);
    }

    public MockElement(String name, ElementType type, Map<String, Object> configuration,
                       List<Element> dependencies, HealthCheckSpec healthCheck,
                       InstancingScope instancingScope) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.configuration = configuration != null ? Map.copyOf(configuration) : Map.of();
        this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        this.healthCheck = healthCheck;
        this.instancingScope = instancingScope;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ElementType type() {
        return type;
    }

    @Override
    public Map<String, Object> configuration() {
        return configuration;
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

    public static MockElement service(String name) {
        return new MockElement(name, ElementType.SERVICE, Map.of(), null, null);
    }

    public static MockElement environment(String name) {
        return new MockElement(name, ElementType.ENVIRONMENT, Map.of(), null, null);
    }

    public static MockElement cache(String name) {
        return new MockElement(name, ElementType.CACHE, Map.of(), null, null);
    }

    public static MockElement dataset(String name) {
        return new MockElement(name, ElementType.DATASET, Map.of(), null, null);
    }

    public static MockElement tool(String name) {
        return new MockElement(name, ElementType.TOOL, Map.of(), null, null);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private ElementType type = ElementType.SERVICE;
        private Map<String, Object> configuration = Map.of();
        private List<Element> dependencies = new ArrayList<>();
        private HealthCheckSpec healthCheck;
        private InstancingScope instancingScope;

        public Builder(String name) {
            this.name = name;
        }

        public Builder type(ElementType type) {
            this.type = type;
            return this;
        }

        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder dependency(Element dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public Builder healthCheck(HealthCheckSpec healthCheck) {
            this.healthCheck = healthCheck;
            return this;
        }

        public Builder instancingScope(InstancingScope instancingScope) {
            this.instancingScope = instancingScope;
            return this;
        }

        public MockElement build() {
            return new MockElement(name, type, configuration, dependencies, healthCheck, instancingScope);
        }
    }
}
