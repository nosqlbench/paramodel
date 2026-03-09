package io.nosqlbench.paramodel.parameters.types;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


///
/// A built-in parameter for boolean values with exactly two values: {@code true} and {@code false}.
///
/// ## Factory
///
/// ```java
/// Parameter<Boolean> enableCache = BooleanParameter.of("enable_cache");
/// ```
///
/// ## Domain
///
/// The domain is always {@code {true, false}} with cardinality 2. Both values are
/// boundary values.
///
/// @see Parameter
/// @see Domain.Discrete
/// @since 0.1.0
///
public final class BooleanParameter implements Parameter<Boolean> {

    private final String name;
    private final BooleanDomain domain;
    private final List<Constraint<Boolean>> constraints;
    private Boolean defaultValue;

    private BooleanParameter(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.domain = new BooleanDomain();
        this.constraints = new ArrayList<>();
    }

    ///
    /// Creates a boolean parameter with the given name.
    ///
    /// @param name parameter name
    /// @return boolean parameter
    ///
    public static BooleanParameter of(String name) {
        return new BooleanParameter(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "boolean";
    }

    @Override
    public Domain<Boolean> domain() {
        return domain;
    }

    @Override
    public Boolean generate() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    @Override
    public Boolean generateBoundary() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    @Override
    public Boolean generateRandom() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    @Override
    public ValidationResult validate(Boolean value) {
        if (value == null) {
            return new ValidationResult.Failed("value must not be null", List.of("null value"));
        }
        List<String> violations = new ArrayList<>();
        for (Constraint<Boolean> c : constraints) {
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
    public boolean satisfies(Constraint<Boolean> constraint) {
        return constraint.test(true) || constraint.test(false);
    }

    ///
    /// Sets the default value for this parameter. Returns this parameter for chaining.
    ///
    /// @param value the default value
    /// @return this parameter
    ///
    public BooleanParameter withDefault(boolean value) {
        this.defaultValue = value;
        return this;
    }

    @Override
    public Optional<Boolean> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    ///
    /// Adds a constraint to this parameter. Returns this parameter for chaining.
    ///
    /// @param constraint the constraint to add
    /// @return this parameter
    ///
    public BooleanParameter withConstraint(Constraint<Boolean> constraint) {
        this.constraints.add(Objects.requireNonNull(constraint));
        return this;
    }

    // --- Package-private domain implementation ---

    private static final class BooleanDomain implements Domain.Discrete<Boolean> {
        private static final Set<Boolean> VALUES = Set.of(true, false);
        private static final List<Boolean> ORDERED = List.of(false, true);

        @Override
        public Set<Boolean> values() {
            return VALUES;
        }

        @Override
        public boolean contains(Boolean value) {
            return value != null;
        }

        @Override
        public Optional<Long> cardinality() {
            return Optional.of(2L);
        }

        @Override
        public Boolean sample(Random rng) {
            return rng.nextBoolean();
        }

        @Override
        public Iterator<Boolean> enumerate() {
            return ORDERED.iterator();
        }

        @Override
        public Set<Boolean> boundaryValues() {
            return VALUES;
        }
    }
}
