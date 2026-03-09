/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.plan;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.*;
import java.util.function.Predicate;

/// A lightweight parameter stub used by {@link DefaultAxis} when the
/// axis definition does not reference a fully resolved parameter model.
///
/// This is the minimum viable {@link Parameter} implementation: it
/// carries only a name and an unbounded domain. It is used during
/// plan composition when the axis's parameter is known only by name.
///
/// @param <T> the value type
public final class AxisParameter<T> implements Parameter<T> {

    private final String name;
    private static final UnboundedDomain<?> DOMAIN = new UnboundedDomain<>();

    public AxisParameter(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public String name() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Domain<T> domain() {
        return (Domain<T>) DOMAIN;
    }

    @Override
    public T generate() {
        throw new UnsupportedOperationException("AxisParameter is a name-only stub");
    }

    @Override
    public T generateBoundary() {
        throw new UnsupportedOperationException("AxisParameter is a name-only stub");
    }

    @Override
    public T generateRandom() {
        throw new UnsupportedOperationException("AxisParameter is a name-only stub");
    }

    @Override
    public ValidationResult validate(T value) {
        return new ValidationResult.Passed();
    }

    @Override
    public boolean satisfies(Constraint<T> constraint) {
        return true;
    }

    @Override
    public String type() {
        return "axis-stub";
    }

    @Override
    public String toString() {
        return "AxisParameter{name='" + name + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Parameter<?> that)) return false;
        return name.equals(that.name());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    private static final class UnboundedDomain<T> implements Domain.Custom<T> {
        @Override
        public Predicate<T> membership() {
            return _ -> true;
        }

        @Override
        public String description() {
            return "Unbounded axis domain";
        }

        @Override
        public boolean contains(T value) {
            return true;
        }

        @Override
        public Optional<Long> cardinality() {
            return Optional.empty();
        }

        @Override
        public T sample(Random rng) {
            throw new UnsupportedOperationException("Unbounded domain cannot be sampled");
        }

        @Override
        public Iterator<T> enumerate() {
            throw new UnsupportedOperationException("Unbounded domain cannot be enumerated");
        }

        @Override
        public Set<T> boundaryValues() {
            return Set.of();
        }
    }
}
