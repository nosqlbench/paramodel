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

///
/// Rule 5: Health check readiness gates.
///
/// For each {@link ReductoNodeType#ACTIVATE} node where the element defines
/// a health check, inserts a {@link ReductoNodeType#READINESS_GATE} node
/// between the activation and its successors.
///
public final class Rule5_HealthCheckGates implements Rule {

    @Override
    public String name() { return "Rule5_HealthCheckGates"; }

    @Override
    public void apply(ReductoGraph graph, RuleContext context) {
        for (Element elem : context.sortedElements()) {
            if (elem.healthCheck().isEmpty()) continue;

            for (ReductoNode activate : new ArrayList<>(graph.nodesForElement(elem.name()))) {
                if (activate.type() != ReductoNodeType.ACTIVATE) continue;

                ReductoNode gate = new ReductoNode(
                    context.nextNodeId("readiness_gate_" + elem.name()),
                    ReductoNodeType.READINESS_GATE);
                gate.setElementName(elem.name());
                gate.setTrialIndex(activate.trialIndex());
                gate.setGroupIndex(activate.groupIndex());
                graph.addNode(gate);

                for (ReductoNode succ : new ArrayList<>(activate.successors())) {
                    if (succ.type() == ReductoNodeType.DEACTIVATE
                        && elem.name().equals(succ.elementName())) {
                        continue;
                    }
                    if (succ.type() == ReductoNodeType.AWAIT
                        && elem.name().equals(succ.elementName())) {
                        continue;
                    }

                    graph.removeEdge(activate, succ);
                    graph.addEdge(gate, succ);
                }

                graph.addEdge(activate, gate);
            }
        }
    }
}
