/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.attributes;

import java.util.Map;

///
/// An entity that carries immutable structural labels.
///
/// Labels are the strictest tier of the attribute system. They describe
/// intrinsic properties — what something IS — such as name, type, or
/// identity. Labels are set at construction time and never change.
///
/// Entities implementing only {@code Labeled} (e.g.
/// {@link io.nosqlbench.paramodel.parameters.BindingNode},
/// {@link io.nosqlbench.paramodel.parameters.ElementBindingTree})
/// carry only labels with no traits or tags. Their
/// {@link #attributes()} view returns the same content as {@link #labels()}.
///
/// ## Contract
///
/// - {@link #labels()} MUST contain a {@code "name"} key whose value
///   equals {@link #name()}
/// - The returned map is unmodifiable
/// - {@link #label(String)} is a convenience default that delegates to
///   {@link #labels()}{@code .get(key)}
///
/// @see Label
/// @see Attributed
/// @since 0.2.0
///
public interface Labeled extends Attributed {

    ///
    /// Returns an unmodifiable map of labels for this entity.
    ///
    /// The map MUST contain at minimum a {@code "name"} key whose value
    /// equals {@link #name()}.
    ///
    /// @return unmodifiable label map, never null
    ///
    Map<String, String> labels();

    ///
    /// Returns the value of a specific label.
    ///
    /// @param key the label key
    /// @return the label value, or null if not present
    ///
    default String label(String key) {
        return labels().get(key);
    }
}
