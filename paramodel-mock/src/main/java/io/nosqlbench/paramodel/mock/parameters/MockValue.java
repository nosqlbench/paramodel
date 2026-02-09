package io.nosqlbench.paramodel.mock.parameters;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.Value;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Simple value wrapper implementation.
 */
public record MockValue<T>(
    T value,
    String parameterName,
    Instant generatedAt,
    Optional<String> generatorMetadata
) implements Value<T> {

    public MockValue(T value, String parameterName) {
        this(value, parameterName, Instant.now(), Optional.empty());
    }

    @Override
    public ValidationResult validate(Constraint<T> constraint) {
        if (constraint.test(value)) {
            return MockValidationResult.passed();
        }
        return MockValidationResult.failed("Constraint validation failed");
    }

    @Override
    public String fingerprint() {
        return UUID.nameUUIDFromBytes(
            (parameterName + value.toString() + generatedAt.toString()).getBytes()
        ).toString();
    }

    public static <T> MockValue<T> of(T value, String parameterName) {
        return new MockValue<>(value, parameterName);
    }
}
