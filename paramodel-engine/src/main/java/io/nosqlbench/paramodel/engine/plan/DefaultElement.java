/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.plan;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.Parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/// A production implementation of {@link Element} with builder, tag-based metadata,
/// fixed configuration bindings, export definitions, and mutable instancing scope.
///
/// Follows the same pattern as {@link DefaultAxis} — immutable fields set via a
/// builder, with adopter-specific metadata conveyed through {@link #tags()}.
///
/// ## Mutable Fields
///
/// Two fields are intentionally mutable (volatile) because they are set after
/// construction during different lifecycle phases:
///
/// - **instancingScope**: Set by the compiler's normalization/scope derivation
///   stage, which runs after all elements are constructed.
/// - **statusSummary**: Set at runtime by the execution engine as the element
///   progresses through its lifecycle.
///
/// ## Usage
///
/// ```java
/// DefaultElement db = DefaultElement.builder("postgres")
///     .tag("type", "service")
///     .tag("image", "postgres:16")
///     .configuration(Map.of("port", 5432, "max_connections", 100))
///     .exports(Map.of("jdbc_url", "${self.host}:${self.port}/mydb"))
///     .instancingScope(InstancingScope.PER_RUN)
///     .build();
/// ```
public class DefaultElement implements Element {

    private final String name;
    private final Map<String, String> tags;
    private final List<Parameter<?>> parameters;
    private final List<Parameter<?>> resultParameters;
    private final List<Element> dependencies;
    private final Map<String, Object> configuration;
    private final Map<String, String> exports;
    private final HealthCheckSpec healthCheck;
    private volatile InstancingScope instancingScope;
    private volatile boolean scopeExplicit;
    private volatile LiveStatusSummary statusSummary;

    private DefaultElement(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        Map<String, String> tagsCopy = new LinkedHashMap<>(builder.tags);
        tagsCopy.put("name", this.name);
        this.tags = Collections.unmodifiableMap(tagsCopy);
        this.parameters = List.copyOf(builder.parameters);
        this.resultParameters = List.copyOf(builder.resultParameters);
        this.dependencies = List.copyOf(builder.dependencies);
        this.configuration = Collections.unmodifiableMap(new LinkedHashMap<>(builder.configuration));
        this.exports = Collections.unmodifiableMap(new LinkedHashMap<>(builder.exports));
        this.healthCheck = builder.healthCheck;
        this.instancingScope = builder.instancingScope;
        this.scopeExplicit = builder.instancingScope != null;
        this.statusSummary = builder.statusSummary != null
                ? builder.statusSummary
                : LiveStatusSummary.inactive();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        return tags;
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
    public List<Element> dependencies() {
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
    public Optional<HealthCheckSpec> healthCheck() {
        return Optional.ofNullable(healthCheck);
    }

    @Override
    public Optional<InstancingScope> instancingScope() {
        return Optional.ofNullable(instancingScope);
    }

    @Override
    public LiveStatusSummary statusCheck() {
        return statusSummary;
    }

    /// Returns the max concurrency limit for parallel deployments, or empty
    /// if unlimited.
    ///
    /// The value is read from the {@code max_concurrency} tag, which is set
    /// by the composition pipeline from the element definition's properties map.
    ///
    /// @return the max concurrency limit, or empty if unlimited
    public OptionalInt maxConcurrency() {
        String val = tags.get("max_concurrency");
        if (val == null || val.isBlank()) return OptionalInt.empty();
        return OptionalInt.of(Integer.parseInt(val));
    }

    /// Returns {@code true} if the instancing scope was set explicitly by the
    /// user (in the plan definition or builder) rather than inferred by the
    /// compilation pipeline. Only explicitly scoped {@code PER_TRIAL} elements
    /// receive independent concurrent instances per trial; inferred scopes
    /// (typically {@code PER_GROUP}) use fingerprint-based group lifecycle.
    public boolean isScopeExplicit() {
        return scopeExplicit;
    }

    /// Sets the instancing scope. Called during compilation by scope derivation
    /// stages after all elements are constructed and dependency analysis is
    /// complete. Scopes set via this method are considered **inferred** (not
    /// explicit); only scopes provided at construction time via the builder
    /// are marked as explicit (see {@link #isScopeExplicit()}).
    ///
    /// @param scope the derived instancing scope
    public void setInstancingScope(InstancingScope scope) {
        this.instancingScope = scope;
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
    /// All adopter-specific metadata flows through {@link #tag(String, String)}.
    /// For example, hyperplane calls {@code .tag("type", "service")} and
    /// {@code .tag("image", "postgres:16")} rather than using dedicated methods.
    public static class Builder {
        private final String name;
        private final Map<String, String> tags = new LinkedHashMap<>();
        private final List<Parameter<?>> parameters = new ArrayList<>();
        private final List<Parameter<?>> resultParameters = new ArrayList<>();
        private final List<Element> dependencies = new ArrayList<>();
        private final Map<String, Object> configuration = new LinkedHashMap<>();
        private final Map<String, String> exports = new LinkedHashMap<>();
        private HealthCheckSpec healthCheck;
        private InstancingScope instancingScope;
        private LiveStatusSummary statusSummary;

        private Builder(String name) {
            this.name = name;
        }

        /// Sets an arbitrary tag on this element.
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

        /// Adds a dependency on another element.
        ///
        /// @param dependency the element this depends on
        /// @return this builder
        public Builder dependency(Element dependency) {
            this.dependencies.add(dependency);
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

        /// Sets the initial instancing scope.
        ///
        /// @param instancingScope the instancing scope
        /// @return this builder
        public Builder instancingScope(InstancingScope instancingScope) {
            this.instancingScope = instancingScope;
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
                ", type=" + tags.getOrDefault("type", "unknown") +
                ", scope=" + instancingScope +
                '}';
    }
}
