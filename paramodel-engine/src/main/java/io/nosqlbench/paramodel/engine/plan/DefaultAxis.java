/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.plan;

import io.nosqlbench.paramodel.plan.AttachedParameter;
import io.nosqlbench.paramodel.plan.Axis;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// A production implementation of {@link Axis} as a pure model type.
///
/// Captures the axis name, ordered values, an {@link AttachedParameter}
/// binding, and an optional description. Planning metadata (nesting,
/// repetitions, sweep mode, etc.) is handled by {@link PlanAxis}, not here.
///
/// @param <T> the type of values along this axis
public class DefaultAxis<T> implements Axis<T> {

    private final String name;
    private final List<T> values;
    private final String description;
    private final AttachedParameter<T> attachedParameter;

    private DefaultAxis(Builder<T> builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.values = List.copyOf(builder.values);
        if (this.values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        this.description = builder.description;
        this.attachedParameter = Objects.requireNonNull(builder.attachedParameter,
                "attachedParameter must not be null");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<T> values() {
        return values;
    }

    @Override
    public AttachedParameter<T> attachedParameter() {
        return attachedParameter;
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

    /// Creates a simple axis with name, attached parameter, and values.
    ///
    /// @param name              the axis name
    /// @param attachedParameter the attached parameter binding
    /// @param values            the values to sweep
    /// @param <T>               the value type
    /// @return a new axis instance
    public static <T> DefaultAxis<T> of(String name, AttachedParameter<T> attachedParameter, List<T> values) {
        return DefaultAxis.<T>builder(name)
                .attachedParameter(attachedParameter)
                .values(values)
                .build();
    }

    /// Creates a builder for constructing DefaultAxis instances.
    ///
    /// @param name the axis name
    /// @param <T>  the value type
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
        private AttachedParameter<T> attachedParameter;

        private Builder(String name) {
            this.name = name;
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

        /// Sets the attached parameter binding.
        public Builder<T> attachedParameter(AttachedParameter<T> attachedParameter) {
            this.attachedParameter = attachedParameter;
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
                ", targetElement=" + targetElement() +
                '}';
    }
}
