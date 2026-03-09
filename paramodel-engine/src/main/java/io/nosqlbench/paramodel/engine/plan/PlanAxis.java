/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.plan;

import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.SamplingStrategy;
import io.nosqlbench.paramodel.plan.AttachedParameter;
import io.nosqlbench.paramodel.plan.Axis;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// A compiled axis with planning metadata attached.
///
/// Created by {@link io.nosqlbench.paramodel.engine.definition.TestPlanComposer}
/// from an axis definition. Wraps the pure {@link Axis} model and adds
/// planning-specific fields that the compilation pipeline reads directly
/// as typed values instead of parsing from string tags.
///
/// @param <T> the type of values along this axis
public class PlanAxis<T> implements Axis<T> {

    private final Axis<T> axis;
    private final int nesting;
    private final int repetitions;
    private final String sweepMode;
    private final String section;
    private final SamplingStrategy sampling;

    private PlanAxis(Builder<T> builder) {
        this.axis = Objects.requireNonNull(builder.axis, "axis must not be null");
        this.nesting = builder.nesting;
        this.repetitions = builder.repetitions;
        this.sweepMode = builder.sweepMode != null ? builder.sweepMode : "SERIAL";
        this.section = builder.section;
        this.sampling = builder.sampling != null ? builder.sampling : SamplingStrategy.grid();
    }

    // --- Axis<T> delegation ---

    @Override
    public String name() {
        return axis.name();
    }

    @Override
    public List<T> values() {
        return axis.values();
    }

    @Override
    public AttachedParameter<T> attachedParameter() {
        return axis.attachedParameter();
    }

    @Override
    public List<T> boundaryValues() {
        return axis.boundaryValues();
    }

    @Override
    public Optional<String> description() {
        return axis.description();
    }

    /// Returns the wrapped axis.
    ///
    /// @return the underlying axis
    public Axis<T> axis() {
        return axis;
    }

    // --- Planning metadata ---

    /// Returns the nesting level for axis ordering.
    ///
    /// @return the nesting level (0-based)
    public int nesting() {
        return nesting;
    }

    /// Returns the repetition count.
    ///
    /// @return number of repetitions, at least 1
    public int repetitions() {
        return repetitions;
    }

    /// Returns the sweep mode.
    ///
    /// @return "SERIAL" or "CONCURRENT"
    public String sweepMode() {
        return sweepMode;
    }

    /// Returns the section label for result grouping.
    ///
    /// @return section name, or null if not set
    public String section() {
        return section;
    }

    /// Returns the sampling strategy.
    ///
    /// @return the sampling strategy, never null
    public SamplingStrategy sampling() {
        return sampling;
    }

    @Override
    public String toString() {
        return "PlanAxis{" +
                "name='" + name() + '\'' +
                ", values=" + values().size() +
                ", targetElement=" + targetElement() +
                ", nesting=" + nesting +
                ", repetitions=" + repetitions +
                ", sweepMode=" + sweepMode +
                '}';
    }

    /// Creates a builder for constructing {@link PlanAxis} instances.
    ///
    /// @param axis the axis to wrap
    /// @param <T>  the value type
    /// @return a new builder
    public static <T> Builder<T> builder(Axis<T> axis) {
        return new Builder<>(axis);
    }

    /// Builder for creating {@link PlanAxis} instances with fluent API.
    ///
    /// @param <T> the value type
    public static class Builder<T> {
        private final Axis<T> axis;
        private int nesting;
        private int repetitions = 1;
        private String sweepMode;
        private String section;
        private SamplingStrategy sampling;

        private Builder(Axis<T> axis) {
            this.axis = axis;
        }

        /// Sets the nesting level.
        public Builder<T> nesting(int nesting) {
            this.nesting = nesting;
            return this;
        }

        /// Sets the repetition count.
        public Builder<T> repetitions(int repetitions) {
            this.repetitions = repetitions;
            return this;
        }

        /// Sets the sweep mode.
        public Builder<T> sweepMode(String sweepMode) {
            this.sweepMode = sweepMode;
            return this;
        }

        /// Sets the section label.
        public Builder<T> section(String section) {
            this.section = section;
            return this;
        }

        /// Sets the sampling strategy.
        public Builder<T> sampling(SamplingStrategy sampling) {
            this.sampling = sampling;
            return this;
        }

        /// Builds the plan axis.
        public PlanAxis<T> build() {
            return new PlanAxis<>(this);
        }
    }
}
