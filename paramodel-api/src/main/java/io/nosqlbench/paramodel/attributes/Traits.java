/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.attributes;

import java.util.Map;

///
/// An entity that carries type-relational traits with plug-and-socket semantics.
///
/// Traits are the middle tier of the attribute system. They describe
/// categoric, type-relational properties used for structural relationship
/// matching. The paramodel engine does not consume any trait keys
/// directly — the trait tier exists as an adopter extension point for
/// type-relational matching.
///
/// {@code Traits} extends {@link Attributed} independently — it is a
/// sibling of {@link Labeled} and {@link Tagged}, not a subtype. An
/// entity may implement {@code Traits} alongside {@code Labeled} and/or
/// {@code Tagged} without any inheritance relationship between the tiers.
///
/// ## Contract
///
/// - The returned map from {@link #traits()} is unmodifiable
/// - {@link #trait(String)} is a convenience default that delegates to
///   {@link #traits()}{@code .get(key)}
/// - Trait keys must not overlap with label or tag keys on the same entity
///
/// @see Trait
/// @see Labeled
/// @see Tagged
/// @since 0.2.0
///
public interface Traits extends Attributed {

    ///
    /// Returns an unmodifiable map of traits for this entity.
    ///
    /// @return unmodifiable trait map, never null
    ///
    Map<String, String> traits();

    ///
    /// Returns the value of a specific trait.
    ///
    /// @param key the trait key
    /// @return the trait value, or null if not present
    ///
    default String trait(String key) {
        return traits().get(key);
    }
}
