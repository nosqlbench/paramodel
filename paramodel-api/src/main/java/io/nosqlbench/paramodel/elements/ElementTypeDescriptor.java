package io.nosqlbench.paramodel.elements;

import java.util.Map;
import java.util.Set;

/// Declares the validation rules for a concrete element type.
///
/// Implementing systems (e.g., hyperplane) register descriptors to teach the
/// generic paramodel compiler which fields are required, forbidden, or
/// advisory for each element type. This keeps the engine free of hardcoded
/// knowledge about specific element types like "node" or "service".
///
/// @param typeId              unique type identifier (e.g., "node", "service")
/// @param requiredFields      fields that must be non-null (e.g., "image")
/// @param forbiddenFields     fields that must be null, with explanation messages
/// @param fieldWarnings       fields that trigger a warning if present, with messages
/// @param providesInfrastructure true if this type provisions compute infrastructure
public record ElementTypeDescriptor(
        String typeId,
        Set<String> requiredFields,
        Map<String, String> forbiddenFields,
        Map<String, String> fieldWarnings,
        boolean providesInfrastructure
) {
    /// Creates a minimal descriptor with no field constraints.
    public static ElementTypeDescriptor of(String typeId) {
        return new ElementTypeDescriptor(typeId, Set.of(), Map.of(), Map.of(), false);
    }
}
