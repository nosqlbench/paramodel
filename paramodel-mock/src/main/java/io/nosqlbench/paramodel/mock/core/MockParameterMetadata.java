package io.nosqlbench.paramodel.mock.core;

import io.nosqlbench.paramodel.core.metadata.ParameterMetadata;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Simple parameter metadata implementation.
 */
public record MockParameterMetadata(
    Instant createdAt,
    Optional<String> createdBy,
    Optional<String> description,
    Map<String, String> tags,
    String generationStrategy,
    Optional<String> version
) implements ParameterMetadata {

    public MockParameterMetadata(String name) {
        this(Instant.now(), Optional.empty(), Optional.empty(), Map.of(), "random", Optional.empty());
    }

    public MockParameterMetadata(String name, String description) {
        this(Instant.now(), Optional.empty(), Optional.of(description), Map.of(), "random", Optional.empty());
    }
}