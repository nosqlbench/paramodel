/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nosqlbench.paramodel.engine.compiler;

import java.util.Set;

/// Describes which axes bind to an element, determining its group lifecycle.
///
/// Computed by {@link NormalizationStage} from parameter-axis overlap and
/// dependency propagation. This is a compilation artifact — elements
/// themselves do not know or declare their binding set.
///
/// - Depth 0 (empty set): element deploys once for the entire run.
/// - Depth K: element persists for contiguous trial blocks where all
///   K bound axis values are constant. Redeployed at group boundaries
///   when the fingerprint changes.
public record AxisBindingSet(Set<String> boundAxes) {
    public AxisBindingSet {
        boundAxes = Set.copyOf(boundAxes);
    }

    /// Returns the number of bound axes (binding depth).
    public int depth() { return boundAxes.size(); }

    /// Returns true if this element is run-scoped (no bound axes).
    public boolean isRunScoped() { return boundAxes.isEmpty(); }

    /// Creates a run-scoped binding set (depth 0).
    public static AxisBindingSet runScoped() { return new AxisBindingSet(Set.of()); }

    /// Creates a binding set with the given bound axes.
    public static AxisBindingSet of(Set<String> axes) { return new AxisBindingSet(axes); }
}
