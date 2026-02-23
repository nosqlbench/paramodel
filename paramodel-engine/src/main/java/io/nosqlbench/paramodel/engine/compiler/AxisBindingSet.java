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

/// Describes which axes bind to an element, determining its group level
/// and lifecycle.
///
/// The group level ({@link #depth()}) is the number of bound axes. An
/// element at level K persists for contiguous trial blocks where all K
/// bound axis values are constant, and is redeployed at group boundaries
/// when the fingerprint changes. Level 0 is not a special case — it
/// simply means zero bound axes, so the element's group spans the entire
/// run (no boundaries exist to trigger redeployment).
///
/// Computed by {@link NormalizationStage} from parameter-axis overlap and
/// dependency propagation. This is a compilation artifact — elements
/// themselves do not know or declare their binding set.
public record AxisBindingSet(Set<String> boundAxes) {
    public AxisBindingSet {
        boundAxes = Set.copyOf(boundAxes);
    }

    /// Returns the group level — the number of bound axes.
    ///
    /// An element at level K is bound to K axes and redeploys at group
    /// boundaries when bound axis values change.
    public int depth() { return boundAxes.size(); }

    /// Returns true if this element has no bound axes (group level 0).
    public boolean isRunScoped() { return boundAxes.isEmpty(); }

    /// Creates a binding set with no bound axes (group level 0).
    public static AxisBindingSet runScoped() { return new AxisBindingSet(Set.of()); }

    /// Creates a binding set with the given bound axes.
    public static AxisBindingSet of(Set<String> axes) { return new AxisBindingSet(axes); }
}
