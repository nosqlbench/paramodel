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
package io.nosqlbench.paramodel.engine.planners.reducto.rules;

import io.nosqlbench.paramodel.engine.planners.reducto.ReductoGraph;

///
/// A named graph transformation rule in the reducto step planner.
///
/// Rules are applied in sequence to the mutable {@link ReductoGraph},
/// transforming it from a flat list of trial seeds into a fully
/// structured execution DAG.
///
public interface Rule {

    /// Returns the name of this rule for diagnostics.
    ///
    /// @return rule name
    String name();

    /// Applies this transformation to the graph.
    ///
    /// @param graph   the mutable graph to transform
    /// @param context shared data for all rules
    void apply(ReductoGraph graph, RuleContext context);
}
