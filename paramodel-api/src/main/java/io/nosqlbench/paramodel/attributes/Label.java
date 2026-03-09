/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.attributes;

import java.util.Objects;

///
/// An immutable structural property describing what an entity IS.
///
/// Labels represent intrinsic, structurally or semantically immutable
/// properties such as name, type, or identity. They are the strictest
/// tier of the three-tier attribute system — once assigned at
/// construction time, they never change.
///
/// ## Canonical Label Keys
///
/// | Key      | Description                              |
/// |----------|------------------------------------------|
/// | `"name"` | Unique identity within the entity's scope |
/// | `"type"` | Structural classification (e.g. `"service"`, `"command"`) |
///
/// ## Example
///
/// ```java
/// Label name = new Label("name", "postgres");
/// Label type = new Label("type", "service");
/// ```
///
/// @param key   the label key, never null
/// @param value the label value, never null
/// @see Labeled
/// @see Attributed
/// @since 0.2.0
///
public record Label(String key, String value) {

    /// Creates a label with validated non-null key and value.
    public Label {
        Objects.requireNonNull(key, "label key must not be null");
        Objects.requireNonNull(value, "label value must not be null");
    }
}
