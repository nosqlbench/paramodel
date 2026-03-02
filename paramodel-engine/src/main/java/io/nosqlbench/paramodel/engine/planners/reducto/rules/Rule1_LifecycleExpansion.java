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

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoGraph;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoNode;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoNodeType;

import java.util.ArrayList;
import java.util.List;

///
/// Rule 1: Element lifecycle expansion.
///
/// Replaces each {@link ReductoNodeType#TRIAL_SEED} node with per-element
/// activate/deactivate (or await) node pairs. For command elements (any
/// shutdown semantics of {@link Element.ShutdownSemantics#COMMAND}):
/// {@code activate → await}. For service elements:
/// {@code activate → deactivate}. The activate→terminate edge is only
/// added for trial elements; non-trial elements get both nodes with no
/// intra-trial edges (wired by later rules).
///
public final class Rule1_LifecycleExpansion implements Rule {

    @Override
    public String name() { return "Rule1_LifecycleExpansion"; }

    @Override
    public void apply(ReductoGraph graph, RuleContext context) {
        List<ReductoNode> seeds = new ArrayList<>(graph.nodesOfType(ReductoNodeType.TRIAL_SEED));

        for (ReductoNode seed : seeds) {
            int trialIdx = seed.trialIndex();

            for (Element elem : context.sortedElements()) {
                String eName = elem.name();
                boolean isTrialElem = context.isTrialElement(eName);
                boolean isCommand = elem.shutdownSemantics() == Element.ShutdownSemantics.COMMAND;

                ReductoNode activate = new ReductoNode(
                    "activate_" + eName + "_t" + trialIdx,
                    ReductoNodeType.ACTIVATE);
                activate.setElementName(eName);
                activate.setTrialIndex(trialIdx);
                graph.addNode(activate);

                if (isCommand) {
                    ReductoNode await = new ReductoNode(
                        "await_" + eName + "_t" + trialIdx,
                        ReductoNodeType.AWAIT);
                    await.setElementName(eName);
                    await.setTrialIndex(trialIdx);
                    graph.addNode(await);
                    if (isTrialElem) {
                        graph.addEdge(activate, await);
                    }
                } else {
                    ReductoNode deactivate = new ReductoNode(
                        "deactivate_" + eName + "_t" + trialIdx,
                        ReductoNodeType.DEACTIVATE);
                    deactivate.setElementName(eName);
                    deactivate.setTrialIndex(trialIdx);
                    graph.addNode(deactivate);

                    if (isTrialElem) {
                        graph.addEdge(activate, deactivate);
                    }
                }
            }

            graph.removeNode(seed);
        }
    }
}
