/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */

///
/// Three-tier attribute system for entity metadata classification.
///
/// ## Overview
///
/// This package defines a hierarchical attribute system with three tiers
/// of increasing flexibility:
///
/// ```
/// Attributed                 (name(), attributes())
///   ├── Labeled              (labels(), label(key))
///   ├── Traits               (traits(), trait(key))
///   └── Tagged               (tags(), tag(key))
/// ```
///
/// ## Tiers
///
/// - **Labels** — strictest; immutable structural properties ("what it IS").
///   Examples: name, type.
/// - **Traits** — type-relational; plug-and-socket capability matching.
///   No canonical keys — adopter extension point.
/// - **Tags** — most flexible; user-mutable categorization.
///   Examples: sweepMode, repetitions, image.
///
/// ## Namespace Rule
///
/// A single entity may NOT have more than one attribute for a given key
/// across all three tiers. Conflicts are caught at build time via
/// {@link AttributeSupport#validateNamespace}.
///
/// ## Records
///
/// Each tier has a corresponding record type ({@link Label}, {@link Trait},
/// {@link Tag}) with non-null validation on key and value.
///
/// @see Attributed
/// @see Labeled
/// @see Traits
/// @see Tagged
/// @see AttributeSupport
/// @since 0.2.0
///
package io.nosqlbench.paramodel.attributes;
