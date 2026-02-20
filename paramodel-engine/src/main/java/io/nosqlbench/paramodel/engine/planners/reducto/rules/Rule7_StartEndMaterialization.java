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
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoNode;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoNodeType;

///
/// Rule 7: Start and end materialization.
///
/// Adds {@link ReductoNodeType#START} and {@link ReductoNodeType#END}
/// sentinel nodes. START connects to all roots; all leaves connect to END.
/// Validates the graph is acyclic.
///
public final class Rule7_StartEndMaterialization implements Rule {

    @Override
    public String name() { return "Rule7_StartEndMaterialization"; }

    @Override
    public void apply(ReductoGraph graph, RuleContext context) {
        ReductoNode start = new ReductoNode("start", ReductoNodeType.START);
        graph.addNode(start);

        for (ReductoNode root : graph.roots()) {
            if (root != start) {
                graph.addEdge(start, root);
            }
        }

        ReductoNode end = new ReductoNode("end", ReductoNodeType.END);
        graph.addNode(end);

        for (ReductoNode leaf : graph.leaves()) {
            if (leaf != end) {
                graph.addEdge(leaf, end);
            }
        }

        if (graph.hasCycle()) {
            throw new IllegalStateException(
                "Reducto graph contains a cycle after all rules applied. "
                + "This indicates a configuration error in element dependencies.");
        }
    }
}
