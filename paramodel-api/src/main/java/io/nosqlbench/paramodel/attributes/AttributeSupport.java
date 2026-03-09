/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.attributes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

///
/// Utility methods for the three-tier attribute system.
///
/// Provides namespace validation (no key may appear in more than one tier)
/// and combine methods for building the merged {@link Attributed#attributes()}
/// view. These methods are intended for use by entity builders and
/// constructors — not by consumers of the attribute API.
///
/// ## Usage
///
/// Entity constructors should call {@link #validateNamespace} to enforce
/// the namespace rule, then {@link #combine} to produce the merged view:
///
/// ```java
/// AttributeSupport.validateNamespace(labels, traits, tags);
/// Map<String, String> attributes = AttributeSupport.combine(labels, traits, tags);
/// ```
///
/// For entities implementing only {@link Labeled}, the single-argument
/// {@link #combine(Map)} overload produces the combined view:
///
/// ```java
/// Map<String, String> attributes = AttributeSupport.combine(labels);
/// ```
///
/// @see Attributed
/// @since 0.2.0
///
public final class AttributeSupport {

    private AttributeSupport() {}

    ///
    /// Validates that no key appears in more than one tier.
    ///
    /// @param labels the labels map
    /// @param traits the traits map
    /// @param tags   the tags map
    /// @throws IllegalArgumentException if any key appears in multiple tiers
    ///
    public static void validateNamespace(
            Map<String, String> labels,
            Map<String, String> traits,
            Map<String, String> tags) {
        for (String key : traits.keySet()) {
            if (labels.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Attribute key '" + key + "' appears in both labels and traits");
            }
        }
        for (String key : tags.keySet()) {
            if (labels.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Attribute key '" + key + "' appears in both labels and tags");
            }
            if (traits.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Attribute key '" + key + "' appears in both traits and tags");
            }
        }
    }

    ///
    /// Combines labels, traits, and tags into a single unmodifiable map.
    ///
    /// Labels are added first, then traits, then tags. Since namespace
    /// validation ensures no key conflicts, insertion order is deterministic.
    ///
    /// @param labels the labels map
    /// @param traits the traits map
    /// @param tags   the tags map
    /// @return an unmodifiable combined map
    ///
    public static Map<String, String> combine(
            Map<String, String> labels,
            Map<String, String> traits,
            Map<String, String> tags) {
        var combined = new LinkedHashMap<String, String>(
                labels.size() + traits.size() + tags.size());
        combined.putAll(labels);
        combined.putAll(traits);
        combined.putAll(tags);
        return Collections.unmodifiableMap(combined);
    }

    ///
    /// Combines labels only into an unmodifiable map (for entities that
    /// implement only {@link Labeled}).
    ///
    /// @param labels the labels map
    /// @return an unmodifiable combined map
    ///
    public static Map<String, String> combine(Map<String, String> labels) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(labels));
    }
}
