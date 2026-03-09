/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.attributes;

import java.util.Map;

///
/// Base contract type for the three-tier attribute system.
///
/// All entities in the attribute system provide a name and a combined
/// view of all their attributes via {@link #attributes()}. The combined
/// view merges labels, traits, and tags (depending on which tiers the
/// entity implements) into a single flat map.
///
/// ## Type Hierarchy
///
/// {@code Attributed} is the root of a flat sibling hierarchy:
///
/// ```
/// Attributed                 (name(), attributes())
///   ├── Labeled              (labels(), label(key))
///   ├── Traits               (traits(), trait(key))
///   └── Tagged               (tags(), tag(key))
/// ```
///
/// Each tier extends {@code Attributed} independently — they are siblings,
/// not a nested chain. Entities may implement one or more tiers:
///
/// - {@link io.nosqlbench.paramodel.elements.Element} and
///   {@link io.nosqlbench.paramodel.plan.Axis} implement all three
///
/// ## Namespace Rule
///
/// A single entity may NOT have more than one attribute for a given
/// key across all three tiers. If a key appears in labels, it must
/// not also appear in traits or tags. Violations are enforced at
/// build time via {@link AttributeSupport#validateNamespace}.
///
/// @see Labeled
/// @see Traits
/// @see Tagged
/// @since 0.2.0
///
public interface Attributed {

    ///
    /// Returns the unique name of this entity within its scope.
    ///
    /// @return the entity name, never null or empty
    ///
    String name();

    ///
    /// Returns an unmodifiable combined view of all attributes across
    /// all tiers this entity implements.
    ///
    /// @return unmodifiable attribute map, never null
    ///
    Map<String, String> attributes();
}
