package io.nosqlbench.paramodel.parameters.types;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


///
/// A built-in parameter for double-precision floating-point values backed by a range domain.
///
/// ## Factory
///
/// ```java
/// Parameter<Double> temperature = DoubleParameter.range("temperature", 0.0, 1.0);
/// ```
///
/// ## Domain Characteristics
///
/// Double parameters are **uncountable** — their cardinality is always empty
/// ({@link Optional#empty()}) and {@link Domain#enumerate()} throws
/// {@link UnsupportedOperationException}. Boundary values are {@code min} and {@code max}.
///
/// @see Parameter
/// @see Domain.Range
/// @since 0.1.0
///
public final class DoubleParameter implements Parameter<Double> {

    private final String name;
    private final DoubleRangeDomain domain;
    private final List<Constraint<Double>> constraints;
    private Double defaultValue;

    private DoubleParameter(String name, DoubleRangeDomain domain) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.domain = Objects.requireNonNull(domain, "domain must not be null");
        this.constraints = new ArrayList<>();
    }

    ///
    /// Creates a double parameter backed by a range domain {@code [min, max]}.
    ///
    /// @param name parameter name
    /// @param min  minimum value (inclusive)
    /// @param max  maximum value (inclusive)
    /// @return range-based double parameter
    /// @throws IllegalArgumentException if min > max or either is NaN
    ///
    public static DoubleParameter range(String name, double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("min and max must not be NaN");
        }
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
        }
        return new DoubleParameter(name, new DoubleRangeDomain(min, max));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "double";
    }

    @Override
    public Domain<Double> domain() {
        return domain;
    }

    @Override
    public Double generate() {
        return domain.sample(ThreadLocalRandom.current());
    }

    @Override
    public Double generateBoundary() {
        Set<Double> boundaries = domain.boundaryValues();
        List<Double> list = new ArrayList<>(boundaries);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    @Override
    public Double generateRandom() {
        return domain.sample(ThreadLocalRandom.current());
    }

    @Override
    public ValidationResult validate(Double value) {
        if (value == null) {
            return new ValidationResult.Failed("value must not be null", List.of("null value"));
        }
        if (!domain.contains(value)) {
            return new ValidationResult.Failed(
                "value " + value + " not in domain",
                List.of("value " + value + " is outside range [" + domain.min() + ", " + domain.max() + "]"));
        }
        List<String> violations = new ArrayList<>();
        for (Constraint<Double> c : constraints) {
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
    public boolean satisfies(Constraint<Double> constraint) {
        for (Double boundary : domain.boundaryValues()) {
            if (constraint.test(boundary)) {
                return true;
            }
        }
        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            if (constraint.test(domain.sample(rng))) {
                return true;
            }
        }
        return false;
    }

    ///
    /// Sets the default value for this parameter. Returns this parameter for chaining.
    ///
    /// The default value must be within this parameter's domain.
    ///
    /// @param value the default value
    /// @return this parameter
    /// @throws IllegalArgumentException if value is not within the domain
    ///
    public DoubleParameter withDefault(double value) {
        if (!domain.contains(value)) {
            throw new IllegalArgumentException(
                "Default value " + value + " is not within domain for parameter '" + name + "'");
        }
        this.defaultValue = value;
        return this;
    }

    @Override
    public Optional<Double> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    ///
    /// Adds a constraint to this parameter. Returns this parameter for chaining.
    ///
    /// @param constraint the constraint to add
    /// @return this parameter
    ///
    public DoubleParameter withConstraint(Constraint<Double> constraint) {
        this.constraints.add(Objects.requireNonNull(constraint));
        return this;
    }

    // --- Package-private domain implementation ---

    private static final class DoubleRangeDomain implements Domain.Range<Double> {
        private final double min;
        private final double max;

        DoubleRangeDomain(double min, double max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public Double min() {
            return min;
        }

        @Override
        public Double max() {
            return max;
        }

        @Override
        public boolean contains(Double value) {
            return value != null && !value.isNaN() && value >= min && value <= max;
        }

        @Override
        public Optional<Long> cardinality() {
            return Optional.empty();
        }

        @Override
        public Double sample(Random rng) {
            return min + rng.nextDouble() * (max - min);
        }

        @Override
        public Iterator<Double> enumerate() {
            throw new UnsupportedOperationException(
                "Double range domains are uncountable and cannot be enumerated");
        }

        @Override
        public Set<Double> boundaryValues() {
            return min == max ? Set.of(min) : Set.of(min, max);
        }
    }
}
