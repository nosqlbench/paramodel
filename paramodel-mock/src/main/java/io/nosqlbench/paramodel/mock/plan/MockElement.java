package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.parameters.DynamicParameterResolver;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ParameterView;

import java.util.*;

///
/// Simple element implementation for testing.
///
/// Creates element models with optional parameters, dependencies,
/// and health checks. The element type is conveyed
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
    private final List<Parameter<?>> resultParameters;
    private final List<Dependency> dependencies;
    private final HealthCheckSpec healthCheck;
    private final ShutdownSemantics shutdownSemantics;
    private final LiveStatusSummary statusSummary;
    private final DynamicParameterResolver dynamicResolver;
    private final List<Parameter<?>> requiredParameters;
    private final Map<String, String> extraTags;

    private MockElement(String name, String type, List<Parameter<?>> parameters,
                        List<Parameter<?>> resultParameters,
                        List<Dependency> dependencies, HealthCheckSpec healthCheck,
                        ShutdownSemantics shutdownSemantics,
                        LiveStatusSummary statusSummary,
                        DynamicParameterResolver dynamicResolver,
                        List<Parameter<?>> requiredParameters,
                        Map<String, String> extraTags) {
        this.name = Objects.requireNonNull(name);
        this.type = type;
        this.parameters = parameters != null ? List.copyOf(parameters) : List.of();
        this.resultParameters = resultParameters != null ? List.copyOf(resultParameters) : List.of();
        this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        this.healthCheck = healthCheck;
        this.shutdownSemantics = shutdownSemantics != null ? shutdownSemantics : ShutdownSemantics.SERVICE;
        this.statusSummary = statusSummary != null ? statusSummary : LiveStatusSummary.inactive();
        this.dynamicResolver = dynamicResolver;
        this.requiredParameters = requiredParameters != null ? List.copyOf(requiredParameters) : List.of();
        this.extraTags = extraTags != null ? Map.copyOf(extraTags) : Map.of();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("name", name);
        if (type != null) {
            tags.put("type", type);
        }
        tags.putAll(extraTags);
        return Collections.unmodifiableMap(tags);
    }

    @Override
    public List<Parameter<?>> parameters() {
        return parameters;
    }

    @Override
    public ParameterView parameterView() {
        if (dynamicResolver != null) {
            return ParameterView.dynamic(requiredParameters, dynamicResolver);
        }
        return ParameterView.of(parameters);
    }

    @Override
    public List<Parameter<?>> resultParameters() {
        return resultParameters;
    }

    @Override
    public List<Dependency> dependencies() {
        return dependencies;
    }

    @Override
    public Optional<HealthCheckSpec> healthCheck() {
        return Optional.ofNullable(healthCheck);
    }

    @Override
    public ShutdownSemantics shutdownSemantics() {
        return shutdownSemantics;
    }

    @Override
    public LiveStatusSummary statusCheck() {
        return statusSummary;
    }

    ///
    /// Creates an element with just a name (no type tag, no parameters).
    ///
    /// @param name element name
    /// @return a simple mock element
    ///
    public static MockElement of(String name) {
        return new MockElement(name, null, List.of(), List.of(), List.of(), null, null, null, null, null, null);
    }

    ///
    /// Creates an element with a name and type tag.
    ///
    /// @param name element name
    /// @param type type tag value (e.g. "service", "cache", "environment")
    /// @return a typed mock element
    ///
    public static MockElement ofType(String name, String type) {
        return new MockElement(name, type, List.of(), List.of(), List.of(), null, null, null, null, null, null);
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
        private final List<Parameter<?>> resultParameters = new ArrayList<>();
        private final List<Dependency> dependencies = new ArrayList<>();
        private HealthCheckSpec healthCheck;
        private ShutdownSemantics shutdownSemantics;
        private LiveStatusSummary statusSummary;
        private DynamicParameterResolver dynamicResolver;
        private final List<Parameter<?>> requiredParameters = new ArrayList<>();
        private final Map<String, String> extraTags = new LinkedHashMap<>();

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
        /// Adds a typed deployment result model for this element.
        ///
        /// @param parameter the result parameter to add
        /// @return this builder
        ///
        public Builder resultParameter(Parameter<?> parameter) {
            this.resultParameters.add(parameter);
            return this;
        }

        ///
        /// Adds a typed dependency edge.
        ///
        /// @param dep the dependency to add
        /// @return this builder
        ///
        public Builder dependency(Dependency dep) {
            this.dependencies.add(dep);
            return this;
        }

        ///
        /// Adds a SHARED dependency on another element (convenience).
        ///
        /// @param target the element this depends on
        /// @return this builder
        ///
        public Builder dependency(Element target) {
            this.dependencies.add(Dependency.shared(target));
            return this;
        }

        ///
        /// Adds a dependency on another element with a specific relationship type.
        ///
        /// @param target the element this depends on
        /// @param type   the relationship type
        /// @return this builder
        ///
        public Builder dependency(Element target, RelationshipType type) {
            this.dependencies.add(new Dependency(target, type));
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
        /// Sets the shutdown semantics for this element.
        ///
        /// @param shutdownSemantics the shutdown semantics
        /// @return this builder
        ///
        public Builder shutdownSemantics(ShutdownSemantics shutdownSemantics) {
            this.shutdownSemantics = shutdownSemantics;
            return this;
        }

        ///
        /// Sets the live status summary for this element.
        ///
        /// @param statusSummary the status summary
        /// @return this builder
        ///
        public Builder statusCheck(LiveStatusSummary statusSummary) {
            this.statusSummary = statusSummary;
            return this;
        }

        ///
        /// Sets the dynamic parameter resolver for this element.
        ///
        /// When set, the element's {@link Element#parameterView()} returns a dynamic
        /// view that uses this resolver. Required parameters should be added via
        /// {@link #requiredParameter(Parameter)}.
        ///
        /// @param resolver the dynamic parameter resolver
        /// @return this builder
        ///
        public Builder dynamicResolver(DynamicParameterResolver resolver) {
            this.dynamicResolver = resolver;
            return this;
        }

        ///
        /// Adds a required parameter for dynamic resolution.
        ///
        /// Required parameters must have defined values before dynamic parameters
        /// can be resolved. These are also added to the element's {@link Element#parameters()}
        /// list.
        ///
        /// @param parameter the required parameter to add
        /// @return this builder
        ///
        public Builder requiredParameter(Parameter<?> parameter) {
            this.requiredParameters.add(parameter);
            this.parameters.add(parameter);
            return this;
        }

        ///
        /// Adds a custom tag to this element.
        ///
        /// @param key the tag key
        /// @param value the tag value
        /// @return this builder
        ///
        public Builder tag(String key, String value) {
            this.extraTags.put(key, value);
            return this;
        }

        ///
        /// Builds the element.
        ///
        /// @return the constructed element
        ///
        public MockElement build() {
            return new MockElement(
                name, type, parameters, resultParameters, dependencies,
                healthCheck, shutdownSemantics, statusSummary,
                dynamicResolver, requiredParameters, extraTags);
        }
    }
}
