/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.plan;

import io.nosqlbench.paramodel.attributes.AttributeSupport;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.parameters.Parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/// A production implementation of {@link Element} with builder, three-tier
/// attribute metadata, fixed configuration bindings, and export definitions.
///
/// Follows the same pattern as {@link DefaultAxis} — immutable fields set via a
/// builder, with adopter-specific metadata conveyed through the three-tier
/// attribute system ({@link #labels()}, {@link #traits()}, {@link #tags()}).
///
/// ## Mutable Fields
///
/// One field is intentionally mutable (volatile) because it is set after
/// construction during a different lifecycle phase:
///
/// - **statusSummary**: Set at runtime by the execution engine as the element
///   progresses through its lifecycle.
///
/// ## Usage
///
/// ```java
/// DefaultElement db = DefaultElement.builder("postgres")
///     .label("type", "service")
///     .maxConcurrency(4)
///     .configuration(Map.of("port", 5432, "max_connections", 100))
///     .exports(Map.of("jdbc_url", "${self.host}:${self.port}/mydb"))
///     .build();
/// ```
public class DefaultElement implements Element {

    private final String name;
    private final Map<String, String> labels;
    private final Map<String, String> traits;
    private final Map<String, String> tags;
    private final Map<String, String> attributes;
    private final List<Parameter<?>> parameters;
    private final List<Parameter<?>> resultParameters;
    private final List<Dependency> dependencies;
    private final Map<String, Object> configuration;
    private final Map<String, String> exports;
    private final HealthCheckSpec healthCheck;
    private final ShutdownSemantics shutdownSemantics;
    private final Boolean trialElement;
    private final Integer maxConcurrencyValue;
    private volatile LiveStatusSummary statusSummary;

    private DefaultElement(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");

        var labelsCopy = new LinkedHashMap<>(builder.labels);
        labelsCopy.put("name", this.name);
        this.labels = Collections.unmodifiableMap(labelsCopy);
        this.traits = Collections.unmodifiableMap(new LinkedHashMap<>(builder.traits));
        this.tags = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tags));
        AttributeSupport.validateNamespace(this.labels, this.traits, this.tags);
        this.attributes = AttributeSupport.combine(this.labels, this.traits, this.tags);

        this.parameters = List.copyOf(builder.parameters);
        this.resultParameters = List.copyOf(builder.resultParameters);
        this.dependencies = List.copyOf(builder.dependencies);
        this.configuration = Collections.unmodifiableMap(new LinkedHashMap<>(builder.configuration));
        this.exports = Collections.unmodifiableMap(new LinkedHashMap<>(builder.exports));
        this.healthCheck = builder.healthCheck;
        this.shutdownSemantics = builder.shutdownSemantics != null
                ? builder.shutdownSemantics : ShutdownSemantics.SERVICE;
        this.trialElement = builder.trialElement;
        this.maxConcurrencyValue = builder.maxConcurrency;
        this.statusSummary = builder.statusSummary != null
                ? builder.statusSummary
                : LiveStatusSummary.inactive();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> labels() {
        return labels;
    }

    @Override
    public Map<String, String> traits() {
        return traits;
    }

    @Override
    public Map<String, String> tags() {
        return tags;
    }

    @Override
    public Map<String, String> attributes() {
        return attributes;
    }

    @Override
    public List<Parameter<?>> parameters() {
        return parameters;
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
    public Map<String, Object> configuration() {
        return configuration;
    }

    @Override
    public Map<String, String> exports() {
        return exports;
    }

    @Override
    public ShutdownSemantics shutdownSemantics() {
        return shutdownSemantics;
    }

    @Override
    public Optional<Boolean> trialElement() {
        return Optional.ofNullable(trialElement);
    }

    @Override
    public Optional<HealthCheckSpec> healthCheck() {
        return Optional.ofNullable(healthCheck);
    }

    @Override
    public LiveStatusSummary statusCheck() {
        return statusSummary;
    }

    /// Returns the max concurrency limit for parallel deployments, or empty
    /// if unlimited.
    ///
    /// @return the max concurrency limit, or empty if unlimited
    @Override
    public OptionalInt maxConcurrency() {
        if (maxConcurrencyValue == null) return OptionalInt.empty();
        return OptionalInt.of(maxConcurrencyValue);
    }

    /// Sets the live status summary. Called at runtime by the execution engine
    /// as the element progresses through its lifecycle.
    ///
    /// @param status the current live status
    public void setStatusSummary(LiveStatusSummary status) {
        this.statusSummary = status;
    }

    /// Creates a builder for constructing {@link DefaultElement} instances.
    ///
    /// @param name the element name
    /// @return a new builder
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /// Builder for creating {@link DefaultElement} instances with fluent API.
    ///
    /// Metadata is classified into three tiers:
    /// - {@link #label(String, String)} for immutable structural properties
    /// - {@link #trait(String, String)} for type-relational capabilities
    /// - {@link #tag(String, String)} for user-mutable categorization
    public static class Builder {
        private final String name;
        private final Map<String, String> labels = new LinkedHashMap<>();
        private final Map<String, String> traits = new LinkedHashMap<>();
        private final Map<String, String> tags = new LinkedHashMap<>();
        private final List<Parameter<?>> parameters = new ArrayList<>();
        private final List<Parameter<?>> resultParameters = new ArrayList<>();
        private final List<Dependency> dependencies = new ArrayList<>();
        private final Map<String, Object> configuration = new LinkedHashMap<>();
        private final Map<String, String> exports = new LinkedHashMap<>();
        private HealthCheckSpec healthCheck;
        private ShutdownSemantics shutdownSemantics;
        private Boolean trialElement;
        private Integer maxConcurrency;
        private LiveStatusSummary statusSummary;

        private Builder(String name) {
            this.name = name;
        }

        /// Sets an immutable label on this element.
        ///
        /// @param key label key
        /// @param value label value
        /// @return this builder
        public Builder label(String key, String value) {
            this.labels.put(key, value);
            return this;
        }

        /// Sets a type-relational trait on this element.
        ///
        /// @param key trait key
        /// @param value trait value
        /// @return this builder
        public Builder trait(String key, String value) {
            this.traits.put(key, value);
            return this;
        }

        /// Sets a user-mutable tag on this element.
        ///
        /// @param key tag key
        /// @param value tag value
        /// @return this builder
        public Builder tag(String key, String value) {
            this.tags.put(key, value);
            return this;
        }

        /// Adds a parameter model to this element.
        ///
        /// @param parameter the parameter to add
        /// @return this builder
        public Builder parameter(Parameter<?> parameter) {
            this.parameters.add(parameter);
            return this;
        }

        /// Adds a result parameter model to this element.
        ///
        /// @param parameter the result parameter to add
        /// @return this builder
        public Builder resultParameter(Parameter<?> parameter) {
            this.resultParameters.add(parameter);
            return this;
        }

        /// Adds a typed dependency edge.
        ///
        /// @param dep the dependency to add
        /// @return this builder
        public Builder dependency(Dependency dep) {
            this.dependencies.add(dep);
            return this;
        }

        /// Adds a dependency on another element with a specific relationship type.
        ///
        /// @param target the element this depends on
        /// @param type   the relationship type
        /// @return this builder
        public Builder dependency(Element target, RelationshipType type) {
            this.dependencies.add(new Dependency(target, type));
            return this;
        }

        /// Adds a SHARED dependency on another element (convenience).
        ///
        /// @param target the element this depends on
        /// @return this builder
        public Builder dependency(Element target) {
            this.dependencies.add(Dependency.shared(target));
            return this;
        }

        /// Sets the fixed configuration bindings for this element.
        ///
        /// @param configuration the configuration map
        /// @return this builder
        public Builder configuration(Map<String, Object> configuration) {
            this.configuration.putAll(configuration);
            return this;
        }

        /// Sets a single configuration binding.
        ///
        /// @param key the parameter name
        /// @param value the fixed value
        /// @return this builder
        public Builder configuration(String key, Object value) {
            this.configuration.put(key, value);
            return this;
        }

        /// Sets the export definitions for this element.
        ///
        /// @param exports the exports map
        /// @return this builder
        public Builder exports(Map<String, String> exports) {
            this.exports.putAll(exports);
            return this;
        }

        /// Sets the health check specification.
        ///
        /// @param healthCheck the health check spec
        /// @return this builder
        public Builder healthCheck(HealthCheckSpec healthCheck) {
            this.healthCheck = healthCheck;
            return this;
        }

        /// Sets the shutdown semantics for this element.
        ///
        /// @param shutdownSemantics the shutdown semantics
        /// @return this builder
        public Builder shutdownSemantics(ShutdownSemantics shutdownSemantics) {
            this.shutdownSemantics = shutdownSemantics;
            return this;
        }

        /// Sets the explicit trial-element override.
        ///
        /// @param trialElement true to force trial, false to force non-trial, null for auto
        /// @return this builder
        public Builder trialElement(Boolean trialElement) {
            this.trialElement = trialElement;
            return this;
        }

        /// Sets the maximum concurrency limit for parallel deployments.
        ///
        /// @param maxConcurrency the concurrency limit
        /// @return this builder
        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        /// Sets the initial live status summary.
        ///
        /// @param statusSummary the status summary
        /// @return this builder
        public Builder statusSummary(LiveStatusSummary statusSummary) {
            this.statusSummary = statusSummary;
            return this;
        }

        /// Builds the element.
        ///
        /// @return the constructed element
        public DefaultElement build() {
            return new DefaultElement(this);
        }
    }

    @Override
    public String toString() {
        return "DefaultElement{" +
                "name='" + name + '\'' +
                ", type=" + labels.getOrDefault("type", "unknown") +
                '}';
    }
}
