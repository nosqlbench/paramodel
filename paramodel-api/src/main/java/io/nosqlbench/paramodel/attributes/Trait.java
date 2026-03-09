/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.attributes;

import java.util.Objects;

///
/// A type-relational property with plug-and-socket semantics.
///
/// Traits describe categoric, type-relational capabilities with
/// plug-and-socket semantics. For example, a downstream consumer may
/// require a connection to something with a particular trait, enabling
/// structural relationship matching without tight coupling.
///
/// The paramodel engine does not define any canonical trait keys — the
/// trait tier exists as an adopter extension point. Adopters may define
/// their own trait keys for type-relational matching.
///
/// ## Example
///
/// ```java
/// Trait dbType = new Trait("database", "postgres");
/// ```
///
/// @param key   the trait key, never null
/// @param value the trait value, never null
/// @see Traits
/// @see Attributed
/// @since 0.2.0
///
public record Trait(String key, String value) {

    /// Creates a trait with validated non-null key and value.
    public Trait {
        Objects.requireNonNull(key, "trait key must not be null");
        Objects.requireNonNull(value, "trait value must not be null");
    }
}
