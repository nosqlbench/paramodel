/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.attributes;

import java.util.Objects;

///
/// A user-mutable categorization property.
///
/// Tags are the most flexible tier of the attribute system. Users can
/// add, remove, and modify tags to categorize, filter, and configure
/// entities according to their own preferences. Any key that is not
/// a label or a trait is classified as a tag by default.
///
/// ## Canonical Tag Keys
///
/// | Key                | Description                                 |
/// |--------------------|---------------------------------------------|
/// | `"sweepMode"`       | Axis sweep mode (`"serial"`, `"concurrent"`) |
/// | `"nesting"`         | Axis nesting depth for Cartesian products   |
/// | `"repetitions"`     | Number of repeated trials per axis value    |
/// | `"sampling_*"`      | Sampling strategy configuration             |
/// | `"section"`         | Axis section grouping identifier            |
///
/// ## Example
///
/// ```java
/// Tag sweep = new Tag("sweepMode", "serial");
/// Tag env   = new Tag("environment", "staging");
/// ```
///
/// @param key   the tag key, never null
/// @param value the tag value, never null
/// @see Tagged
/// @see Attributed
/// @since 0.2.0
///
public record Tag(String key, String value) {

    /// Creates a tag with validated non-null key and value.
    public Tag {
        Objects.requireNonNull(key, "tag key must not be null");
        Objects.requireNonNull(value, "tag value must not be null");
    }
}
