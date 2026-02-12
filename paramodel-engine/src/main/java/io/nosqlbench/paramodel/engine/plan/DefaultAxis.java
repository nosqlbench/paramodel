/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.plan;

import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.SamplingStrategy;
import io.nosqlbench.paramodel.plan.Axis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// A production implementation of {@link Axis} with a builder that provides
/// typed convenience methods for common tags such as target element,
/// sweep mode, nesting level, and section labels.
///
/// Tags carry hyperplane-specific metadata without polluting the core
/// paramodel interface. The builder ensures tag keys are consistent
/// across all callers.
///
/// @param <T> the type of values along this axis
public class DefaultAxis<T> implements Axis<T> {

    private final String name;
    private final List<T> values;
    private final String description;
    private final Parameter<T> underlyingParameter;
    private final Map<String, String> tags;

    private DefaultAxis(Builder<T> builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.values = List.copyOf(builder.values);
        if (this.values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        this.description = builder.description;
        this.underlyingParameter = builder.underlyingParameter;
        this.tags = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tags));
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
    public List<T> values() {
        return values;
    }

    @Override
    public List<T> boundaryValues() {
        if (values.size() == 1) {
            return List.of(values.getFirst());
        }
        return List.of(values.getFirst(), values.getLast());
    }

    @Override
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    @Override
    public Optional<Parameter<T>> underlyingParameter() {
        return Optional.ofNullable(underlyingParameter);
    }

    /// Creates a simple axis with name, target element, and values.
    ///
    /// @param name the axis name (parameter name being varied)
    /// @param targetElement the element this axis targets
    /// @param values the values to sweep
    /// @param <T> the value type
    /// @return a new axis instance
    public static <T> DefaultAxis<T> of(String name, String targetElement, List<T> values) {
        return DefaultAxis.<T>builder(name)
                .targetElement(targetElement)
                .values(values)
                .build();
    }

    /// Creates a builder for constructing DefaultAxis instances.
    ///
    /// @param name the axis name
    /// @param <T> the value type
    /// @return a new builder
    public static <T> Builder<T> builder(String name) {
        return new Builder<>(name);
    }

    /// Builder for creating {@link DefaultAxis} instances with fluent API.
    ///
    /// @param <T> the value type
    public static class Builder<T> {
        private final String name;
        private List<T> values = List.of();
        private String description;
        private Parameter<T> underlyingParameter;
        private final Map<String, String> tags = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = name;
            this.tags.put("name", name);
        }

        /// Sets the values for this axis.
        public Builder<T> values(List<T> values) {
            this.values = values;
            return this;
        }

        /// Sets the values for this axis (varargs).
        @SafeVarargs
        public final Builder<T> values(T... values) {
            this.values = List.of(values);
            return this;
        }

        /// Sets the human-readable description.
        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        /// Sets the underlying paramodel parameter.
        public Builder<T> underlyingParameter(Parameter<T> parameter) {
            this.underlyingParameter = parameter;
            return this;
        }

        /// Sets the target element for this axis.
        /// Stored as tag {@code "targetElement"}.
        public Builder<T> targetElement(String targetElement) {
            if (targetElement != null) {
                this.tags.put("targetElement", targetElement);
            }
            return this;
        }

        /// Sets the nesting level for axis ordering.
        /// Stored as tag {@code "nesting"}.
        public Builder<T> nesting(int nesting) {
            this.tags.put("nesting", String.valueOf(nesting));
            return this;
        }

        /// Sets the sweep mode (e.g. SERIAL, CONCURRENT).
        /// Stored as tag {@code "sweepMode"}.
        public Builder<T> sweepMode(String sweepMode) {
            if (sweepMode != null) {
                this.tags.put("sweepMode", sweepMode);
            }
            return this;
        }

        /// Sets the section label for result grouping.
        /// Stored as tag {@code "section"}.
        public Builder<T> section(String section) {
            if (section != null) {
                this.tags.put("section", section);
            }
            return this;
        }

        /// Sets the repetition count.
        /// Stored as tag {@code "repetitions"}.
        public Builder<T> repetitions(int repetitions) {
            this.tags.put("repetitions", String.valueOf(repetitions));
            return this;
        }

        /// Sets the sampling strategy for this axis.
        /// Encoded as tags: {@code sampling_type}, {@code sampling_count},
        /// {@code sampling_seed}.
        public Builder<T> sampling(SamplingStrategy strategy) {
            switch (strategy) {
                case SamplingStrategy.Grid _ -> tags.put("sampling_type", "grid");
                case SamplingStrategy.Linspace l -> {
                    tags.put("sampling_type", "linspace");
                    tags.put("sampling_count", String.valueOf(l.count()));
                }
                case SamplingStrategy.Random r -> {
                    tags.put("sampling_type", "random");
                    tags.put("sampling_count", String.valueOf(r.count()));
                    tags.put("sampling_seed", String.valueOf(r.seed()));
                }
            }
            return this;
        }

        /// Sets an arbitrary tag.
        public Builder<T> tag(String key, String value) {
            this.tags.put(key, value);
            return this;
        }

        /// Builds the axis.
        public DefaultAxis<T> build() {
            return new DefaultAxis<>(this);
        }
    }

    @Override
    public String toString() {
        return "DefaultAxis{" +
                "name='" + name + '\'' +
                ", values=" + values.size() +
                ", targetElement=" + targetElement().orElse("none") +
                '}';
    }
}
