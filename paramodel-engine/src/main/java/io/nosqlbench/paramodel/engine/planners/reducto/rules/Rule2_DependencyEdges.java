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
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.planners.reducto.*;

import java.util.*;

///
/// Rule 2: Dependency edge materialization.
///
/// Adds edges between element lifecycle nodes based on the declared
/// dependency relationships (SHARED, EXCLUSIVE, DEDICATED, LINEAR, LIFELINE).
///
/// For EXCLUSIVE dependencies, serialization edges are only added between
/// consecutive trials where the exclusive target is the same instance (same
/// group). When the target has different parameter values at trials T and T+1,
/// they are distinct instances with no resource conflict, and the dependent
/// elements may run in parallel.
///
public final class Rule2_DependencyEdges implements Rule {

    @Override
    public String name() { return "Rule2_DependencyEdges"; }

    @Override
    public void apply(ReductoGraph graph, RuleContext context) {
        long totalTrials = context.enumerator().totalTrials();

        for (Element elem : context.sortedElements()) {
            for (Element.Dependency dep : elem.dependencies()) {
                String xName = elem.name();
                String yName = dep.target().name();
                RelationshipType relType = dep.type();

                switch (relType) {
                    case SHARED -> applyShared(graph, xName, yName, totalTrials);
                    case EXCLUSIVE -> applyExclusive(graph, context, xName, yName, totalTrials);
                    case DEDICATED -> applyDedicated(graph, xName, yName, totalTrials);
                    case LINEAR -> applyLinear(graph, context, xName, yName, totalTrials);
                    case LIFELINE -> applyLifeline(graph, xName, yName, totalTrials);
                }
            }
        }
    }

    private void applyShared(ReductoGraph graph, String xName, String yName, long totalTrials) {
        for (int t = 0; t < totalTrials; t++) {
            ReductoNode activateY = findNode(graph, "activate_" + yName + "_t" + t);
            ReductoNode activateX = findNode(graph, "activate_" + xName + "_t" + t);
            ReductoNode terminateX = findTerminationNode(graph, xName, t);
            ReductoNode terminateY = findTerminationNode(graph, yName, t);

            if (activateY != null && activateX != null) {
                graph.addEdge(activateY, activateX);
            }
            if (terminateX != null && terminateY != null) {
                graph.addEdge(terminateX, terminateY);
            }
        }
    }

    private void applyExclusive(ReductoGraph graph, RuleContext context,
                                 String xName, String yName, long totalTrials) {
        applyShared(graph, xName, yName, totalTrials);

        Set<String> exclusiveDepNames = new LinkedHashSet<>();
        for (Element elem : context.sortedElements()) {
            for (Element.Dependency dep : elem.dependencies()) {
                if (dep.target().name().equals(yName) && dep.type() == RelationshipType.EXCLUSIVE) {
                    exclusiveDepNames.add(elem.name());
                }
            }
        }

        for (int t = 0; t < totalTrials; t++) {
            List<String> activeInTrial = new ArrayList<>();
            for (String depName : exclusiveDepNames) {
                if (findNode(graph, "activate_" + depName + "_t" + t) != null) {
                    activeInTrial.add(depName);
                }
            }
            if (activeInTrial.size() > 1) {
                context.addWarning(ReductoWarning.w002(activeInTrial.get(0),
                    activeInTrial.get(1), yName, t));
            }
        }

        // Self-serialization: only when the exclusive target is the SAME instance
        // (same group) at both trials. When the target has different parameter values
        // at T and T+1, they are distinct instances with no exclusion conflict.
        for (String depName : exclusiveDepNames) {
            for (int t = 0; t < totalTrials - 1; t++) {
                if (!context.bindingState().sameGroupForElement(
                        yName, context.enumerator(), t, t + 1)) {
                    continue;
                }
                ReductoNode terminateCurrent = findTerminationNode(graph, depName, t);
                ReductoNode activateNext = findNode(graph, "activate_" + depName + "_t" + (t + 1));
                if (terminateCurrent != null && activateNext != null) {
                    graph.addEdge(terminateCurrent, activateNext);
                }
            }
        }

        // Cross-element serialization: same constraint — only when the exclusive
        // target is the same instance across consecutive trials.
        List<String> depList = new ArrayList<>(exclusiveDepNames);
        for (int i = 0; i < depList.size(); i++) {
            for (int j = i + 1; j < depList.size(); j++) {
                String nameA = depList.get(i);
                String nameB = depList.get(j);
                for (int t = 0; t < totalTrials - 1; t++) {
                    if (!context.bindingState().sameGroupForElement(
                            yName, context.enumerator(), t, t + 1)) {
                        continue;
                    }
                    ReductoNode terminateA = findTerminationNode(graph, nameA, t);
                    ReductoNode activateB = findNode(graph, "activate_" + nameB + "_t" + (t + 1));
                    if (terminateA != null && activateB != null) {
                        graph.addEdge(terminateA, activateB);
                    }
                    ReductoNode terminateB = findTerminationNode(graph, nameB, t);
                    ReductoNode activateA = findNode(graph, "activate_" + nameA + "_t" + (t + 1));
                    if (terminateB != null && activateA != null) {
                        graph.addEdge(terminateB, activateA);
                    }
                }
            }
        }
    }

    private void applyDedicated(ReductoGraph graph, String xName, String yName, long totalTrials) {
        for (int t = 0; t < totalTrials; t++) {
            ReductoNode activateY = findNode(graph, "activate_" + yName + "_t" + t);
            ReductoNode activateX = findNode(graph, "activate_" + xName + "_t" + t);
            ReductoNode terminateX = findTerminationNode(graph, xName, t);
            ReductoNode terminateY = findTerminationNode(graph, yName, t);

            if (activateY != null && activateX != null) {
                graph.addEdge(activateY, activateX);
            }
            if (terminateX != null && terminateY != null) {
                graph.addEdge(terminateX, terminateY);
            }

            if (activateY != null) {
                activateY.putMetadata("dedicated_to", xName);
            }
        }
    }

    private void applyLinear(ReductoGraph graph, RuleContext context,
                              String xName, String yName, long totalTrials) {
        for (int t = 0; t < totalTrials; t++) {
            ReductoNode terminateY = findTerminationNode(graph, yName, t);
            ReductoNode activateX = findNode(graph, "activate_" + xName + "_t" + t);

            if (terminateY != null && activateX != null) {
                graph.addEdge(terminateY, activateX);
            }
        }
    }

    private void applyLifeline(ReductoGraph graph, String xName, String yName, long totalTrials) {
        for (int t = 0; t < totalTrials; t++) {
            ReductoNode activateY = findNode(graph, "activate_" + yName + "_t" + t);
            ReductoNode activateX = findNode(graph, "activate_" + xName + "_t" + t);

            if (activateY != null && activateX != null) {
                graph.addEdge(activateY, activateX);
            }

            ReductoNode deactivateX = findNode(graph, "deactivate_" + xName + "_t" + t);
            ReductoNode deactivateY = findNode(graph, "deactivate_" + yName + "_t" + t);
            if (deactivateX != null && deactivateY != null) {
                graph.remapEdgesTo(deactivateX, deactivateY);
                graph.remapEdgesFrom(deactivateX, deactivateY);
                graph.removeNode(deactivateX);
            }
        }
    }

    private ReductoNode findNode(ReductoGraph graph, String id) {
        return graph.getNode(id);
    }

    private ReductoNode findTerminationNode(ReductoGraph graph, String elementName, int trialIdx) {
        ReductoNode await = graph.getNode("await_" + elementName + "_t" + trialIdx);
        if (await != null) return await;
        return graph.getNode("deactivate_" + elementName + "_t" + trialIdx);
    }
}
