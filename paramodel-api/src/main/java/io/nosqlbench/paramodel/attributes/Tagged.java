/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.attributes;

import java.util.Map;

///
/// An entity that carries user-mutable tags.
///
/// Tags are the most flexible tier of the attribute system. Users can
/// add, remove, and modify tags to categorize and filter entities
/// according to their own preferences. Any metadata key that is not
/// classified as a label or trait defaults to this tier.
///
/// {@code Tagged} extends {@link Attributed} independently — it is a
/// sibling of {@link Labeled} and {@link Traits}, not a subtype. An
/// entity may implement {@code Tagged} alongside {@code Labeled} and/or
/// {@code Traits} without any inheritance relationship between the tiers.
///
/// ## Contract
///
/// - The returned map from {@link #tags()} is unmodifiable
/// - {@link #tag(String)} is a convenience default that delegates to
///   {@link #tags()}{@code .get(key)}
/// - Tag keys must not overlap with label or trait keys on the same entity
///
/// @see Tag
/// @see Traits
/// @see Labeled
/// @since 0.2.0
///
public interface Tagged extends Attributed {

    ///
    /// Returns an unmodifiable map of tags for this entity.
    ///
    /// @return unmodifiable tag map, never null
    ///
    Map<String, String> tags();

    ///
    /// Returns the value of a specific tag.
    ///
    /// @param key the tag key
    /// @return the tag value, or null if not present
    ///
    default String tag(String key) {
        return tags().get(key);
    }
}
