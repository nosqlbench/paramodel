package io.nosqlbench.paramodel.mock.core;

import io.nosqlbench.paramodel.core.metadata.ParameterMetadata;

import java.util.Map;
import java.util.Optional;

/**
 * Simple parameter metadata implementation.
 */
public record MockParameterMetadata(
    String name,
    Optional<String> description,
    Map<String, String> tags
) implements ParameterMetadata {

    public MockParameterMetadata(String name) {
        this(name, Optional.empty(), Map.of());
    }

    public MockParameterMetadata(String name, String description) {
        this(name, Optional.of(description), Map.of());
    }
}
