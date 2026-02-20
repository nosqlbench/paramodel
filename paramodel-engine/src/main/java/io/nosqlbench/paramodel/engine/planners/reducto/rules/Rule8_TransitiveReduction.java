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

import java.util.*;

///
/// Rule 8: Transitive reduction.
///
/// Removes redundant transitive edges from the graph. For each node N with
/// more than one successor, if a successor S is transitively reachable
/// through another successor of N, the direct edge N → S is removed.
///
/// This runs after Rule 7 (start/end materialization) to catch all edges
/// including sentinel connections. Since {@code DefaultExecutionGraph}
/// already provides {@code transitiveDependencies()} for runtime queries,
/// the explicit transitive edges are redundant and clutter the graph.
///
public final class Rule8_TransitiveReduction implements Rule {

    @Override
    public String name() { return "Rule8_TransitiveReduction"; }

    @Override
    public void apply(ReductoGraph graph, RuleContext context) {
        List<ReductoNode> topoOrder = graph.topologicalOrder();

        for (ReductoNode node : topoOrder) {
            List<ReductoNode> successors = new ArrayList<>(node.successors());
            if (successors.size() <= 1) continue;

            // For each successor S, check if S is reachable from any other successor
            Set<ReductoNode> reachableFromOtherSuccessors = new HashSet<>();
            for (ReductoNode succ : successors) {
                collectReachable(succ, reachableFromOtherSuccessors);
            }

            for (ReductoNode succ : successors) {
                // S is transitive if it is reachable from some other successor T
                // (i.e., reachable through T's transitive closure, not counting the
                // direct edge N → S). We check: is S reachable from any successor
                // of any other direct successor of N?
                boolean isTransitive = false;
                for (ReductoNode otherSucc : successors) {
                    if (otherSucc == succ) continue;
                    if (isReachableFrom(otherSucc, succ)) {
                        isTransitive = true;
                        break;
                    }
                }
                if (isTransitive) {
                    graph.removeEdge(node, succ);
                }
            }
        }
    }

    /// Checks whether {@code target} is reachable from {@code source} by
    /// traversing successors (BFS). Does not follow the direct source node
    /// itself — only its transitive successors.
    private boolean isReachableFrom(ReductoNode source, ReductoNode target) {
        Set<ReductoNode> visited = new HashSet<>();
        Deque<ReductoNode> queue = new ArrayDeque<>();
        for (ReductoNode succ : source.successors()) {
            queue.add(succ);
            visited.add(succ);
        }
        while (!queue.isEmpty()) {
            ReductoNode current = queue.poll();
            if (current == target) return true;
            for (ReductoNode succ : current.successors()) {
                if (visited.add(succ)) {
                    queue.add(succ);
                }
            }
        }
        return false;
    }

    /// Collects all nodes reachable from {@code start} into the given set
    /// (used for pre-computation when needed).
    private void collectReachable(ReductoNode start, Set<ReductoNode> reachable) {
        Deque<ReductoNode> queue = new ArrayDeque<>();
        for (ReductoNode succ : start.successors()) {
            if (reachable.add(succ)) {
                queue.add(succ);
            }
        }
        while (!queue.isEmpty()) {
            ReductoNode current = queue.poll();
            for (ReductoNode succ : current.successors()) {
                if (reachable.add(succ)) {
                    queue.add(succ);
                }
            }
        }
    }
}
