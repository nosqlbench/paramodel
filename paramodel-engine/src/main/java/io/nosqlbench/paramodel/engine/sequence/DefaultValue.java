package io.nosqlbench.paramodel.engine.sequence;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

public class DefaultValue<T> implements Value<T> {

    private final T value;
    private final String parameterName;
    private final Instant generatedAt;
    private final Optional<String> generatorMetadata;

    public DefaultValue(T value, String parameterName, Instant generatedAt, Optional<String> generatorMetadata) {
        this.value = value;
        this.parameterName = parameterName;
        this.generatedAt = generatedAt;
        this.generatorMetadata = generatorMetadata;
    }

    @Override
    public T value() {
        return value;
    }

    @Override
    public String parameterName() {
        return parameterName;
    }

    @Override
    public Instant generatedAt() {
        return generatedAt;
    }

    @Override
    public Optional<String> generatorMetadata() {
        return generatorMetadata;
    }

    @Override
    public ValidationResult validate(Constraint<T> constraint) {
        try {
            if (constraint.test(value)) {
                return new ValidationResult.Passed();
            } else {
                return new ValidationResult.Failed(
                    "Constraint failed for " + parameterName,
                    java.util.List.of("Constraint failed")
                );
            }
        } catch (Exception e) {
             return new ValidationResult.Failed(
                "Exception during validation for " + parameterName + ": " + e.getMessage(),
                java.util.List.of(e.getMessage())
            );
        }
    }

    @Override
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String content = parameterName + ":" + (value != null ? value.getClass().getName() : "null") + ":" + value;
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
