package io.nosqlbench.paramodel.parameters.types;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

///
/// A built-in parameter for integer values, supporting both range and discrete domains.
///
/// ## Factories
///
/// ```java
/// // Range-based: all integers in [min, max]
/// Parameter<Integer> threads = IntegerParameter.range("threads", 1, 64);
///
/// // Discrete: specific integer values
/// Parameter<Integer> batchSize = IntegerParameter.of("batch_size", Set.of(32, 64, 128, 256));
/// ```
///
/// ## Range Domain
///
/// For range-based parameters, values are sampled uniformly from {@code [min, max]}.
/// Boundary values are {@code min} and {@code max}. Cardinality is {@code max - min + 1}.
///
/// ## Discrete Domain
///
/// For discrete parameters, values are drawn from an explicit set. Boundary values
/// are the minimum and maximum of the set.
///
/// @see Parameter
/// @see Domain.Range
/// @see Domain.Discrete
/// @since 0.1.0
///
public final class IntegerParameter implements Parameter<Integer> {

    private final String name;
    private final Domain<Integer> domain;
    private final List<Constraint<Integer>> constraints;

    private IntegerParameter(String name, Domain<Integer> domain) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.domain = Objects.requireNonNull(domain, "domain must not be null");
        this.constraints = new ArrayList<>();
    }

    ///
    /// Creates an integer parameter backed by a range domain {@code [min, max]}.
    ///
    /// @param name parameter name
    /// @param min  minimum value (inclusive)
    /// @param max  maximum value (inclusive)
    /// @return range-based integer parameter
    /// @throws IllegalArgumentException if min > max
    ///
    public static IntegerParameter range(String name, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
        }
        return new IntegerParameter(name, new IntegerRangeDomain(min, max));
    }

    ///
    /// Creates an integer parameter backed by a discrete domain of specific values.
    ///
    /// @param name   parameter name
    /// @param values the set of valid integer values
    /// @return discrete integer parameter
    /// @throws IllegalArgumentException if values is null or empty
    ///
    public static IntegerParameter of(String name, Set<Integer> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be null or empty");
        }
        return new IntegerParameter(name, new IntegerDiscreteDomain(values));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        return Map.of("name", name, "type", "integer");
    }

    @Override
    public Domain<Integer> domain() {
        return domain;
    }

    @Override
    public Integer generate() {
        return domain.sample(ThreadLocalRandom.current());
    }

    @Override
    public Integer generateBoundary() {
        Set<Integer> boundaries = domain.boundaryValues();
        if (boundaries.isEmpty()) {
            return generate();
        }
        List<Integer> list = new ArrayList<>(boundaries);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    @Override
    public Integer generateRandom() {
        return domain.sample(ThreadLocalRandom.current());
    }

    @Override
    public ValidationResult validate(Integer value) {
        if (value == null) {
            return new ValidationResult.Failed("value must not be null", List.of("null value"));
        }
        if (!domain.contains(value)) {
            return new ValidationResult.Failed(
                "value " + value + " not in domain",
                List.of("value " + value + " is outside the parameter domain"));
        }
        List<String> violations = new ArrayList<>();
        for (Constraint<Integer> c : constraints) {
            if (!c.test(value)) {
                violations.add("constraint failed: " + c.description());
            }
        }
        if (!violations.isEmpty()) {
            return new ValidationResult.Failed(
                "value " + value + " violates constraints", violations);
        }
        return new ValidationResult.Passed();
    }

    @Override
    public boolean satisfies(Constraint<Integer> constraint) {
        for (Integer boundary : domain.boundaryValues()) {
            if (constraint.test(boundary)) {
                return true;
            }
        }
        // Sample a few values to check
        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            if (constraint.test(domain.sample(rng))) {
                return true;
            }
        }
        return false;
    }

    ///
    /// Adds a constraint to this parameter. Returns this parameter for chaining.
    ///
    /// @param constraint the constraint to add
    /// @return this parameter
    ///
    public IntegerParameter withConstraint(Constraint<Integer> constraint) {
        this.constraints.add(Objects.requireNonNull(constraint));
        return this;
    }

    // --- Package-private domain implementations ---

    private static final class IntegerRangeDomain implements Domain.Range<Integer> {
        private final int min;
        private final int max;

        IntegerRangeDomain(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public Integer min() {
            return min;
        }

        @Override
        public Integer max() {
            return max;
        }

        @Override
        public boolean contains(Integer value) {
            return value != null && value >= min && value <= max;
        }

        @Override
        public Optional<Long> cardinality() {
            return Optional.of((long) max - min + 1);
        }

        @Override
        public Integer sample(Random rng) {
            return min + rng.nextInt(max - min + 1);
        }

        @Override
        public Iterator<Integer> enumerate() {
            return new Iterator<>() {
                int current = min;

                @Override
                public boolean hasNext() {
                    return current <= max;
                }

                @Override
                public Integer next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return current++;
                }
            };
        }

        @Override
        public Set<Integer> boundaryValues() {
            return min == max ? Set.of(min) : Set.of(min, max);
        }
    }

    private static final class IntegerDiscreteDomain implements Domain.Discrete<Integer> {
        private final Set<Integer> values;
        private final List<Integer> sortedValues;

        IntegerDiscreteDomain(Set<Integer> values) {
            this.values = Set.copyOf(values);
            this.sortedValues = new ArrayList<>(this.values);
            Collections.sort(this.sortedValues);
        }

        @Override
        public Set<Integer> values() {
            return values;
        }

        @Override
        public boolean contains(Integer value) {
            return value != null && values.contains(value);
        }

        @Override
        public Optional<Long> cardinality() {
            return Optional.of((long) values.size());
        }

        @Override
        public Integer sample(Random rng) {
            return sortedValues.get(rng.nextInt(sortedValues.size()));
        }

        @Override
        public Iterator<Integer> enumerate() {
            return sortedValues.iterator();
        }

        @Override
        public Set<Integer> boundaryValues() {
            if (sortedValues.size() == 1) {
                return Set.of(sortedValues.get(0));
            }
            return Set.of(sortedValues.get(0), sortedValues.get(sortedValues.size() - 1));
        }
    }
}
