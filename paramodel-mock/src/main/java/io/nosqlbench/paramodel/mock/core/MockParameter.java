package io.nosqlbench.paramodel.mock.core;

import io.nosqlbench.paramodel.core.*;
import io.nosqlbench.paramodel.core.metadata.ParameterMetadata;

import java.util.Objects;
import java.util.Random;

/**
 * Simple mock implementation of Parameter for testing.
 */
public class MockParameter<T> implements Parameter<T> {
    private final String name;
    private final Domain<T> domain;
    private final Random random;

    public MockParameter(String name, Domain<T> domain) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.domain = Objects.requireNonNull(domain, "domain cannot be null");
        this.random = new Random();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Domain<T> domain() {
        return domain;
    }

    @Override
    public T generate() {
        return domain.sample(random);
    }

    @Override
    public T generateBoundary() {
        var boundaries = domain.boundaryValues();
        if (boundaries.isEmpty()) {
            return generate();
        }
        return boundaries.iterator().next();
    }

    @Override
    public T generateRandom() {
        return domain.sample(random);
    }

    @Override
    public ValidationResult validate(T value) {
        if (domain.contains(value)) {
            return MockValidationResult.passed();
        }
        return MockValidationResult.failed("Value not in domain");
    }

    @Override
    public boolean satisfies(Constraint<T> constraint) {
        // Sample test - not comprehensive
        T sample = generate();
        return constraint.test(sample);
    }

    @Override
    public ParameterMetadata metadata() {
        return new MockParameterMetadata(name);
    }

    public static <T> MockParameter<T> of(String name, Domain<T> domain) {
        return new MockParameter<>(name, domain);
    }
}
