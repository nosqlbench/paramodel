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
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoGraph;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoNode;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoNodeType;

import java.util.*;

///
/// Rule 4: Trial notification insertion.
///
/// Inserts {@link ReductoNodeType#NOTIFY_TRIAL_START} and
/// {@link ReductoNodeType#NOTIFY_TRIAL_END} nodes per trial.
///
/// Ordering: non-trial activations → notify-start → trial activations.
/// Trial terminations → notify-end. For each non-trial element group,
/// ALL {@code notify_end} nodes within the group precede the group's
/// deactivation: {@code notify_end(Ti)} → {@code deactivate(E, G)} for
/// every trial Ti in the group, ensuring the deactivation cannot begin
/// until all trials have completed their notify-end processing.
///
/// **Exclusive serialization rerouting:** After inserting notify nodes, this
/// rule detects exclusive serialization edges between trial elements (created
/// by Rule 2) and reroutes them through the notify boundaries. A direct edge
/// {@code deactivate/await(X, Ti) → activate(X, Ti+1)} is replaced by routing
/// through {@code notify_end(Ti) → notify_start(Ti+1)}, since the edges
/// {@code deactivate/await(X, Ti) → notify_end(Ti)} and
/// {@code notify_start(Ti+1) → activate(X, Ti+1)} already exist from the
/// normal notify wiring. This ensures the trial notification lifecycle is
/// coupled to the exclusive serialization order.
///
/// **Non-trial deactivation enforcement:** Any direct edges from trial element
/// termination nodes to non-trial element deactivation nodes are removed. This
/// prevents a race condition where a non-trial element could begin deactivation
/// concurrently with the {@code notify_trial_end} boundary, before the notify
/// event and any returned data have been fully processed. The correct path is
/// {@code trial_terminate → notify_end → non_trial_deactivate}, which is
/// established by the group deactivation wiring (all notify_ends in the group
/// precede the group's deactivation).
///
public final class Rule4_TrialNotifications implements Rule {

    @Override
    public String name() { return "Rule4_TrialNotifications"; }

    @Override
    public void apply(ReductoGraph graph, RuleContext context) {
        long totalTrials = context.enumerator().totalTrials();

        for (int t = 0; t < totalTrials; t++) {
            ReductoNode notifyStart = new ReductoNode(
                "notify_trial_start_" + t, ReductoNodeType.NOTIFY_TRIAL_START);
            notifyStart.setTrialIndex(t);
            graph.addNode(notifyStart);

            ReductoNode notifyEnd = new ReductoNode(
                "notify_trial_end_" + t, ReductoNodeType.NOTIFY_TRIAL_END);
            notifyEnd.setTrialIndex(t);
            graph.addNode(notifyEnd);

            for (Element elem : context.sortedElements()) {
                String eName = elem.name();

                if (!context.isTrialElement(eName)) {
                    ReductoNode activate = findActivateForTrial(graph, context, eName, t);
                    if (activate != null) {
                        graph.addEdge(activate, notifyStart);
                    }
                } else {
                    ReductoNode trialActivate = graph.getNode("activate_" + eName + "_t" + t);
                    if (trialActivate != null) {
                        graph.addEdge(notifyStart, trialActivate);
                    }

                    ReductoNode trialTerminate = findTerminationNode(graph, eName, t);
                    if (trialTerminate != null) {
                        graph.addEdge(trialTerminate, notifyEnd);
                    }
                }
            }

        }

        wireAllNotifyEndsToNonTrialDeactivations(graph, context);
        wireExclusiveSerializationThroughNotify(graph, context);
        enforceNotifyEndBeforeNonTrialDeactivation(graph, context);
    }

    /// Finds the activate node for an element at a given trial.
    ///
    /// For coalesced elements, the per-trial activate node may have been removed
    /// by Rule 3. In that case, we find the group-level activate whose
    /// {@code groupIndex} matches the trial's computed group at the element's
    /// binding level. If only one activate node exists (single-group or
    /// run-scoped element), it is returned directly.
    private ReductoNode findActivateForTrial(ReductoGraph graph, RuleContext context,
                                              String elementName, int trialIdx) {
        ReductoNode direct = graph.getNode("activate_" + elementName + "_t" + trialIdx);
        if (direct != null) return direct;

        // Collect all remaining activate nodes for this element
        List<ReductoNode> activates = new ArrayList<>();
        for (ReductoNode node : graph.nodesForElement(elementName)) {
            if (node.type() == ReductoNodeType.ACTIVATE) {
                activates.add(node);
            }
        }

        if (activates.isEmpty()) return null;
        if (activates.size() == 1) return activates.getFirst();

        // Multiple groups — match by group index
        int targetGroup = context.bindingState()
            .groupIndexForElement(elementName, context.enumerator(), trialIdx);
        for (ReductoNode node : activates) {
            if (node.groupIndex() == targetGroup) {
                return node;
            }
        }

        // Fallback: shouldn't reach here, but return the first if group matching fails
        return activates.getFirst();
    }

    /// Wires each {@code notify_trial_end} to the non-trial deactivation node
    /// that covers that trial.
    ///
    /// For coalesced elements, all {@code notify_trial_end} nodes within the
    /// group are wired to the single group deactivation node (at the last trial
    /// in the group). For un-coalesced elements (e.g. DEDICATED targets whose
    /// owner is a trial element), each per-trial deactivation node receives its
    /// own {@code notify_trial_end} edge.
    ///
    /// This ensures that every non-trial deactivation happens-after its
    /// corresponding {@code notify_trial_end} processing completes.
    private void wireAllNotifyEndsToNonTrialDeactivations(ReductoGraph graph, RuleContext context) {
        long totalTrials = context.enumerator().totalTrials();

        for (Element elem : context.sortedElements()) {
            if (context.isTrialElement(elem.name())) continue;

            String eName = elem.name();
            boolean isCommand = elem.shutdownSemantics() == Element.ShutdownSemantics.COMMAND;
            String prefix = isCommand ? "await_" : "deactivate_";

            for (int t = 0; t < totalTrials; t++) {
                // Find the deactivation node that covers this trial.
                // For un-coalesced elements (DEDICATED targets), the per-trial
                // node exists directly. For coalesced elements, we search forward
                // to the group boundary where the group deactivation lives.
                ReductoNode deactivateNode = graph.getNode(prefix + eName + "_t" + t);
                if (deactivateNode == null) {
                    // Coalesced: walk forward to the group's last trial
                    for (int ft = t + 1; ft < totalTrials; ft++) {
                        ReductoNode candidate = graph.getNode(prefix + eName + "_t" + ft);
                        if (candidate != null) {
                            deactivateNode = candidate;
                            break;
                        }
                    }
                }

                if (deactivateNode != null) {
                    ReductoNode notifyEnd = graph.getNode("notify_trial_end_" + t);
                    if (notifyEnd != null) {
                        graph.addEdge(notifyEnd, deactivateNode);
                    }
                }
            }
        }
    }

    /// Reroutes exclusive serialization edges through notify boundaries.
    ///
    /// For each **trial element** with EXCLUSIVE dependencies, finds direct edges from
    /// termination nodes to next-trial activation nodes and removes them,
    /// replacing them with {@code notify_end(Ti) → notify_start(Ti+1)} edges.
    /// The termination → notify_end and notify_start → activation edges already
    /// exist from the main notify wiring loop.
    ///
    /// **Non-trial elements are not rerouted.** For non-trial elements (such as
    /// DEDICATED targets with a trial-element owner), the notify wiring direction
    /// is reversed: {@code activate(B) → notify_start} and
    /// {@code notify_end → deactivate(B)}. Rerouting the serialization edge through
    /// the notify boundary would allow the next trial's activation to run in parallel
    /// with the current trial's deactivation, violating the exclusive constraint.
    /// Non-trial serialization edges are left intact.
    private void wireExclusiveSerializationThroughNotify(ReductoGraph graph, RuleContext context) {
        long totalTrials = context.enumerator().totalTrials();

        // Collect all exclusive dep relationships: for each target Y, all elements that exclusively depend on Y
        Map<String, Set<String>> exclusiveDepsOf = new LinkedHashMap<>();
        for (Element elem : context.sortedElements()) {
            for (Element.Dependency dep : elem.dependencies()) {
                if (dep.type() == RelationshipType.EXCLUSIVE) {
                    exclusiveDepsOf.computeIfAbsent(dep.target().name(), k -> new LinkedHashSet<>())
                        .add(elem.name());
                }
            }
        }

        // For each exclusive target, process only trial element dependents
        for (Map.Entry<String, Set<String>> entry : exclusiveDepsOf.entrySet()) {
            Set<String> allExclusiveDeps = entry.getValue();

            // Filter to trial elements only — non-trial element serialization edges
            // must remain direct (see javadoc above)
            List<String> trialDeps = new ArrayList<>();
            for (String depName : allExclusiveDeps) {
                if (context.isTrialElement(depName)) {
                    trialDeps.add(depName);
                }
            }

            // Self-serialization: deactivate(X, Ti) → activate(X, Ti+1)
            for (String depName : trialDeps) {
                rerouteThroughNotify(graph, depName, depName, totalTrials);
            }

            // Cross-element serialization: deactivate(X, Ti) → activate(Z, Ti+1) for X != Z
            for (int i = 0; i < trialDeps.size(); i++) {
                for (int j = 0; j < trialDeps.size(); j++) {
                    if (i == j) continue;
                    rerouteThroughNotify(graph, trialDeps.get(i), trialDeps.get(j), totalTrials);
                }
            }
        }
    }

    /// Reroutes direct exclusive serialization edges from the termination of
    /// {@code fromElement} at trial Ti to the activation of {@code toElement}
    /// at trial Ti+1, redirecting them through the notify boundary nodes.
    private void rerouteThroughNotify(ReductoGraph graph, String fromElement, String toElement, long totalTrials) {
        for (int t = 0; t < totalTrials - 1; t++) {
            ReductoNode terminateCurrent = findTerminationNode(graph, fromElement, t);
            ReductoNode activateNext = graph.getNode("activate_" + toElement + "_t" + (t + 1));

            if (terminateCurrent == null || activateNext == null) continue;
            if (!terminateCurrent.successors().contains(activateNext)) continue;

            ReductoNode notifyEnd = graph.getNode("notify_trial_end_" + t);
            ReductoNode notifyStart = graph.getNode("notify_trial_start_" + (t + 1));

            if (notifyEnd == null || notifyStart == null) continue;

            // Remove the direct exclusive serialization edge
            graph.removeEdge(terminateCurrent, activateNext);

            // Add the notify boundary edge (idempotent — addEdge on a Set is safe)
            graph.addEdge(notifyEnd, notifyStart);
        }
    }

    /// Removes direct edges from trial element termination nodes to non-trial
    /// element deactivation nodes, enforcing that the path goes through
    /// {@code notify_trial_end} instead.
    ///
    /// This prevents a race condition where a non-trial element could begin
    /// deactivation before the {@code notify_trial_end} event has been fully
    /// processed. The correct control flow is:
    /// {@code trial_terminate → notify_end → non_trial_deactivate}.
    private void enforceNotifyEndBeforeNonTrialDeactivation(ReductoGraph graph, RuleContext context) {
        Set<String> trialElementNames = new HashSet<>(context.trialElementNames());

        for (ReductoNode node : new ArrayList<>(graph.nodes())) {
            // Only non-trial deactivation/await nodes
            if (node.type() != ReductoNodeType.DEACTIVATE && node.type() != ReductoNodeType.AWAIT) continue;
            String eName = node.elementName();
            if (eName == null || trialElementNames.contains(eName)) continue;

            // Find predecessors that are trial element termination nodes
            for (ReductoNode pred : new ArrayList<>(node.predecessors())) {
                if (pred.type() != ReductoNodeType.DEACTIVATE && pred.type() != ReductoNodeType.AWAIT) continue;
                String predElement = pred.elementName();
                if (predElement == null || !trialElementNames.contains(predElement)) continue;

                // This is a direct trial_terminate → non_trial_deactivate edge; remove it
                graph.removeEdge(pred, node);
            }
        }
    }

    private ReductoNode findTerminationNode(ReductoGraph graph, String elementName, int trialIdx) {
        ReductoNode await = graph.getNode("await_" + elementName + "_t" + trialIdx);
        if (await != null) return await;
        return graph.getNode("deactivate_" + elementName + "_t" + trialIdx);
    }
}
