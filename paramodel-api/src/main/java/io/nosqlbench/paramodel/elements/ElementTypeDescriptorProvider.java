package io.nosqlbench.paramodel.elements;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Supplies element type descriptors to the paramodel compiler pipeline.
///
/// Implementing systems register a provider to declare which element types
/// are valid and what constraints each type has. The compiler's validation
/// stage uses this to enforce field requirements without hardcoding knowledge
/// of concrete element types.
public interface ElementTypeDescriptorProvider {

    /// Returns all registered element type descriptors.
    List<ElementTypeDescriptor> descriptors();

    /// Returns type aliases that map deprecated names to current type IDs.
    ///
    /// For example, `{"constellation" → "node"}` allows the parser to accept
    /// the old name while normalizing to the canonical type.
    default Map<String, String> typeAliases() {
        return Map.of();
    }

    /// Returns the set of valid type IDs derived from the registered descriptors.
    default Set<String> validTypeIds() {
        return descriptors().stream()
                .map(ElementTypeDescriptor::typeId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /// Looks up a descriptor by type ID.
    default Optional<ElementTypeDescriptor> descriptor(String typeId) {
        return descriptors().stream()
                .filter(d -> d.typeId().equals(typeId))
                .findFirst();
    }

    /// Returns true if any registered type provides infrastructure.
    default boolean hasInfrastructureType() {
        return descriptors().stream().anyMatch(ElementTypeDescriptor::providesInfrastructure);
    }

    /// A no-op provider that accepts any element type without constraints.
    static ElementTypeDescriptorProvider open() {
        return new ElementTypeDescriptorProvider() {
            @Override
            public List<ElementTypeDescriptor> descriptors() {
                return List.of();
            }
        };
    }
}
