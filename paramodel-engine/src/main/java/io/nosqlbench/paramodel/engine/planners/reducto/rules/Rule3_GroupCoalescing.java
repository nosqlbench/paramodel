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
/// Rule 3: Group coalescing (the core reduction).
///
/// For each non-trial element, consecutive trials where the element's
/// configuration is identical are coalesced: per-trial activate/deactivate
/// pairs are replaced by a single pair spanning the group.
///
/// Trial elements are never coalesced. DEDICATED targets coalesce with
/// their owner.
///
public final class Rule3_GroupCoalescing implements Rule {

    @Override
    public String name() { return "Rule3_GroupCoalescing"; }

    @Override
    public void apply(ReductoGraph graph, RuleContext context) {
        long totalTrials = context.enumerator().totalTrials();
        MixedRadixEnumerator enumerator = context.enumerator();
        BindingStateComputer bindingState = context.bindingState();

        Map<String, String> dedicatedOwner = new LinkedHashMap<>();
        for (Element elem : context.sortedElements()) {
            for (Element.Dependency dep : elem.dependencies()) {
                if (dep.type() == RelationshipType.DEDICATED) {
                    dedicatedOwner.put(dep.target().name(), elem.name());
                }
            }
        }

        for (Element elem : context.sortedElements()) {
            String eName = elem.name();

            if (context.isTrialElement(eName)) continue;

            if (dedicatedOwner.containsKey(eName)) {
                coalesceWithOwner(graph, context, eName, totalTrials, dedicatedOwner);
                continue;
            }

            BindingStateComputer.ElementBinding binding = bindingState.binding(eName);
            if (binding == null) continue;

            int bindingLevel = binding.bindingLevel();

            Map<Integer, List<Integer>> groupTrials = new LinkedHashMap<>();
            for (int t = 0; t < totalTrials; t++) {
                int gIdx = enumerator.groupIndex(t, bindingLevel);
                groupTrials.computeIfAbsent(gIdx, k -> new ArrayList<>()).add(t);
            }

            for (var entry : groupTrials.entrySet()) {
                int groupIdx = entry.getKey();
                List<Integer> trials = entry.getValue();
                if (trials.size() <= 1) continue;

                int firstTrial = trials.getFirst();
                ReductoNode groupActivate = graph.getNode("activate_" + eName + "_t" + firstTrial);
                if (groupActivate == null) continue;
                groupActivate.setGroupIndex(groupIdx);

                boolean isCommand = elem.shutdownSemantics() == Element.ShutdownSemantics.COMMAND;
                String terminationType = isCommand ? "await_" : "deactivate_";

                int lastTrial = trials.getLast();
                ReductoNode groupTerminate = graph.getNode(terminationType + eName + "_t" + lastTrial);
                if (groupTerminate != null) {
                    groupTerminate.setGroupIndex(groupIdx);
                }

                // Remove the first trial's terminate node — it is not the group
                // terminate (which lives at lastTrial) but the loop below starts
                // at i=1 to preserve the first trial's activate, so this node
                // would otherwise be missed.
                if (firstTrial != lastTrial) {
                    ReductoNode firstTerminate = graph.getNode(terminationType + eName + "_t" + firstTrial);
                    if (firstTerminate != null && firstTerminate != groupTerminate) {
                        graph.remapEdgesFrom(firstTerminate, groupTerminate);
                        graph.remapEdgesTo(firstTerminate, groupTerminate);
                        graph.removeNode(firstTerminate);
                    }
                }

                for (int i = 1; i < trials.size(); i++) {
                    int t = trials.get(i);

                    ReductoNode perTrialActivate = graph.getNode("activate_" + eName + "_t" + t);
                    if (perTrialActivate != null && perTrialActivate != groupActivate) {
                        graph.remapEdgesFrom(perTrialActivate, groupActivate);
                        graph.remapEdgesTo(perTrialActivate, groupActivate);
                        graph.removeNode(perTrialActivate);
                    }

                    if (t != lastTrial) {
                        ReductoNode perTrialTerminate = graph.getNode(terminationType + eName + "_t" + t);
                        if (perTrialTerminate != null && perTrialTerminate != groupTerminate) {
                            graph.remapEdgesFrom(perTrialTerminate, groupTerminate);
                            graph.remapEdgesTo(perTrialTerminate, groupTerminate);
                            graph.removeNode(perTrialTerminate);
                        }
                    }
                }
            }
        }

        checkExclusiveWarnings(context, bindingState, enumerator);
    }

    /// Coalesces a DEDICATED target element with its owner's grouping.
    ///
    /// The effective binding level is resolved by walking up the DEDICATED
    /// ownership chain. If the owner is itself a DEDICATED target of another
    /// element, the grouping cascades from the root of the chain. This ensures
    /// that transitive DEDICATED relationships produce the correct number of
    /// instances: if A→B→C are DEDICATED and C has binding level N, then
    /// both B and A will have the same number of groups as C.
    private void coalesceWithOwner(ReductoGraph graph, RuleContext context,
                                    String dedicatedName, long totalTrials,
                                    Map<String, String> dedicatedOwner) {
        String ownerName = dedicatedOwner.get(dedicatedName);
        if (ownerName == null) return;

        if (context.isTrialElement(ownerName)) return;

        int bindingLevel = resolveEffectiveBindingLevel(context, ownerName, dedicatedOwner);
        MixedRadixEnumerator enumerator = context.enumerator();

        Map<Integer, List<Integer>> groupTrials = new LinkedHashMap<>();
        for (int t = 0; t < totalTrials; t++) {
            int gIdx = enumerator.groupIndex(t, bindingLevel);
            groupTrials.computeIfAbsent(gIdx, k -> new ArrayList<>()).add(t);
        }

        Element dedicatedElem = context.element(dedicatedName);
        boolean isCommand = dedicatedElem != null
            && dedicatedElem.shutdownSemantics() == Element.ShutdownSemantics.COMMAND;
        String terminationType = isCommand ? "await_" : "deactivate_";

        for (var entry : groupTrials.entrySet()) {
            int groupIdx = entry.getKey();
            List<Integer> trials = entry.getValue();
            if (trials.size() <= 1) continue;

            int firstTrial = trials.getFirst();
            int lastTrial = trials.getLast();

            ReductoNode groupActivate = graph.getNode("activate_" + dedicatedName + "_t" + firstTrial);
            if (groupActivate == null) continue;
            groupActivate.setGroupIndex(groupIdx);

            ReductoNode groupTerminate = graph.getNode(terminationType + dedicatedName + "_t" + lastTrial);
            if (groupTerminate != null) {
                groupTerminate.setGroupIndex(groupIdx);
            }

            // Remove the first trial's terminate node (same fix as main coalescing)
            if (firstTrial != lastTrial) {
                ReductoNode firstTerminate = graph.getNode(terminationType + dedicatedName + "_t" + firstTrial);
                if (firstTerminate != null && firstTerminate != groupTerminate) {
                    graph.remapEdgesFrom(firstTerminate, groupTerminate);
                    graph.remapEdgesTo(firstTerminate, groupTerminate);
                    graph.removeNode(firstTerminate);
                }
            }

            for (int i = 1; i < trials.size(); i++) {
                int t = trials.get(i);

                ReductoNode perTrialActivate = graph.getNode("activate_" + dedicatedName + "_t" + t);
                if (perTrialActivate != null && perTrialActivate != groupActivate) {
                    graph.remapEdgesFrom(perTrialActivate, groupActivate);
                    graph.remapEdgesTo(perTrialActivate, groupActivate);
                    graph.removeNode(perTrialActivate);
                }

                if (t != lastTrial) {
                    ReductoNode perTrialTerminate = graph.getNode(terminationType + dedicatedName + "_t" + t);
                    if (perTrialTerminate != null && perTrialTerminate != groupTerminate) {
                        graph.remapEdgesFrom(perTrialTerminate, groupTerminate);
                        graph.remapEdgesTo(perTrialTerminate, groupTerminate);
                        graph.removeNode(perTrialTerminate);
                    }
                }
            }
        }
    }

    /// Resolves the effective binding level for an element by walking up the
    /// DEDICATED ownership chain. If the element is itself a DEDICATED target,
    /// its effective binding level is determined by the root of the chain —
    /// the first ancestor that is not a DEDICATED target.
    ///
    /// @param context        rule context
    /// @param elementName    the element whose effective binding level to resolve
    /// @param dedicatedOwner mapping from DEDICATED target name to owner name
    /// @return the effective binding level
    private int resolveEffectiveBindingLevel(RuleContext context, String elementName,
                                              Map<String, String> dedicatedOwner) {
        String current = elementName;
        Set<String> visited = new HashSet<>();
        while (dedicatedOwner.containsKey(current)) {
            if (!visited.add(current)) break; // cycle guard
            current = dedicatedOwner.get(current);
        }

        BindingStateComputer.ElementBinding binding = context.bindingState().binding(current);
        if (binding == null) return 0;
        return binding.bindingLevel();
    }

    private void checkExclusiveWarnings(RuleContext context,
                                         BindingStateComputer bindingState,
                                         MixedRadixEnumerator enumerator) {
        for (Element elem : context.sortedElements()) {
            for (Element.Dependency dep : elem.dependencies()) {
                if (dep.type() == RelationshipType.EXCLUSIVE) {
                    String targetName = dep.target().name();
                    BindingStateComputer.ElementBinding targetBinding = bindingState.binding(targetName);
                    BindingStateComputer.ElementBinding depBinding = bindingState.binding(elem.name());

                    if (targetBinding != null && depBinding != null
                        && targetBinding.bindingLevel() < depBinding.bindingLevel()) {
                        int scopeDiff = depBinding.bindingLevel() - targetBinding.bindingLevel();
                        if (scopeDiff >= 2) {
                            int dependentCount = (int) enumerator.trialsPerGroup(targetBinding.bindingLevel());
                            context.addWarning(ReductoWarning.w001(
                                elem.name(), targetName,
                                targetBinding.bindingLevel(), dependentCount));
                        }
                    }
                }
            }
        }
    }
}
