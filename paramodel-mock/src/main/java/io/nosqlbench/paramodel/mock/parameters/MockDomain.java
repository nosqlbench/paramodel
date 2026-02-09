package io.nosqlbench.paramodel.mock.parameters;

import io.nosqlbench.paramodel.parameters.Domain;

import java.util.*;

/**
 * Simple discrete domain implementation.
 */
public class MockDomain<T> implements Domain.Discrete<T> {
    private final Set<T> values;

    public MockDomain(Set<T> values) {
        this.values = new HashSet<>(values);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public MockDomain(T... values) {
        this.values = new HashSet<>(Arrays.asList(values));
    }

    @Override
    public boolean contains(T value) {
        return values.contains(value);
    }

    @Override
    public Optional<Long> cardinality() {
        return Optional.of((long) values.size());
    }

    @Override
    public T sample(Random rng) {
        if (values.isEmpty()) {
            throw new IllegalStateException("Cannot sample from empty domain");
        }
        int index = rng.nextInt(values.size());
        return new ArrayList<>(values).get(index);
    }

    @Override
    public Iterator<T> enumerate() {
        return values.iterator();
    }

    @Override
    public Set<T> boundaryValues() {
        // For discrete domains, all values are boundaries
        return new HashSet<>(values);
    }

    @Override
    public Set<T> values() {
        return new HashSet<>(values);
    }

    public static <T> MockDomain<T> of(Set<T> values) {
        return new MockDomain<>(values);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> MockDomain<T> of(T... values) {
        return new MockDomain<>(values);
    }
}
