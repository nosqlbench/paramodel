package io.nosqlbench.paramodel.mock.parameters;

import io.nosqlbench.paramodel.parameters.Domain;

import java.util.*;

///
/// Simple range domain implementation for testing.
///
/// Supports Integer and Long ranges with enumeration. Other comparable
/// types support membership testing and sampling but not enumeration.
///
/// @param <T> the comparable type of values in the range
///
public class MockRangeDomain<T extends Comparable<T>> implements Domain.Range<T> {
    private final T min;
    private final T max;

    public MockRangeDomain(T min, T max) {
        this.min = Objects.requireNonNull(min);
        this.max = Objects.requireNonNull(max);
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min must be <= max");
        }
    }

    @Override
    public T min() {
        return min;
    }

    @Override
    public T max() {
        return max;
    }

    @Override
    public boolean contains(T value) {
        if (value == null) return false;
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Long> cardinality() {
        if (min instanceof Integer minInt && max instanceof Integer maxInt) {
            return Optional.of((long) maxInt - minInt + 1);
        }
        if (min instanceof Long minLong && max instanceof Long maxLong) {
            return Optional.of(maxLong - minLong + 1);
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T sample(Random rng) {
        if (min instanceof Integer minInt && max instanceof Integer maxInt) {
            int range = maxInt - minInt + 1;
            return (T) Integer.valueOf(minInt + rng.nextInt(range));
        }
        if (min instanceof Long minLong && max instanceof Long maxLong) {
            long range = maxLong - minLong + 1;
            return (T) Long.valueOf(minLong + (rng.nextLong() % range + range) % range);
        }
        if (min instanceof Double minDbl && max instanceof Double maxDbl) {
            return (T) Double.valueOf(minDbl + rng.nextDouble() * (maxDbl - minDbl));
        }
        // Fallback: return min
        return min;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<T> enumerate() {
        if (min instanceof Integer minInt && max instanceof Integer maxInt) {
            return new Iterator<>() {
                int current = minInt;

                @Override
                public boolean hasNext() {
                    return current <= maxInt;
                }

                @Override
                public T next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return (T) Integer.valueOf(current++);
                }
            };
        }
        if (min instanceof Long minLong && max instanceof Long maxLong) {
            return new Iterator<>() {
                long current = minLong;

                @Override
                public boolean hasNext() {
                    return current <= maxLong;
                }

                @Override
                public T next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return (T) Long.valueOf(current++);
                }
            };
        }
        throw new UnsupportedOperationException("Cannot enumerate non-integer range domain");
    }

    @Override
    public Set<T> boundaryValues() {
        return Set.of(min, max);
    }

    public static <T extends Comparable<T>> MockRangeDomain<T> of(T min, T max) {
        return new MockRangeDomain<>(min, max);
    }
}
