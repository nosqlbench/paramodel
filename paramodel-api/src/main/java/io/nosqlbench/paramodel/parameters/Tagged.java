package io.nosqlbench.paramodel.parameters;

import java.util.Map;

///
/// A consistently named and tagged entity within the paramodel type system.
///
/// ## Concept
///
/// {@code Tagged} provides a unified mechanism for naming and categorizing
/// entities across the paramodel API. Any type that has a name and can carry
/// descriptive key-value tags implements this interface.
///
/// ## Special Tags
///
/// Two tag keys have reserved semantics:
///
/// - **{@code "name"}**: The entity's unique name within its scope. The value
///   of {@code tags().get("name")} MUST equal {@link #name()}.
/// - **{@code "type"}**: The entity's type classification, defined by the
///   adopting system. For example, an {@code Element} in a system that defines
///   a "service" type would have {@code "type" → "service"}, while a
///   {@code Parameter} might carry {@code "type" → "discrete"}.
///
/// ## Contract
///
/// - {@link #name()} MUST return a non-null, non-empty string
/// - {@link #tags()} MUST return an unmodifiable map
/// - {@code tags()} MUST contain at minimum a {@code "name"} key
/// - {@code tags().get("name")} MUST equal {@code name()}
/// - {@code tags()} SHOULD contain a {@code "type"} key when the entity
///   has a meaningful type classification
///
/// ## Usage Example
///
/// ```java
/// Tagged entity = ...;
/// String name = entity.name();
/// Map<String, String> tags = entity.tags();
///
/// assert tags.get("name").equals(name);
///
/// // Filter by type (type values are defined by the adopting system)
/// if ("service".equals(tags.get("type"))) {
///     // handle service element
/// }
/// ```
///
/// @see Parameter
/// @see io.nosqlbench.paramodel.elements.Element
/// @see io.nosqlbench.paramodel.plan.Axis
/// @since 0.1.0
///
public interface Tagged {

    ///
    /// Returns the unique name of this entity within its scope.
    ///
    /// This is a convenience accessor equivalent to {@code tags().get("name")}.
    ///
    /// @return the entity name, never null or empty
    ///
    String name();

    ///
    /// Returns an unmodifiable map of tags describing this entity.
    ///
    /// The map MUST contain at minimum a {@code "name"} entry whose value
    /// equals {@link #name()}. It SHOULD contain a {@code "type"} entry
    /// when the entity has a meaningful type classification.
    ///
    /// @return unmodifiable tag map, never null
    ///
    Map<String, String> tags();
}
