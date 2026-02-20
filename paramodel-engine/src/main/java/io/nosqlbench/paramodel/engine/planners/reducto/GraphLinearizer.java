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
package io.nosqlbench.paramodel.engine.planners.reducto;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.compiler.DefaultBarrier;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

///
/// Converts a finalized {@link ReductoGraph} into a flat list of
/// {@link AtomicStep} records and {@link Barrier} records.
///
/// The graph is topologically sorted and each {@link ReductoNode} is
/// mapped to the appropriate {@link AtomicStep} subtype based on its
/// {@link ReductoNodeType}.
///
public final class GraphLinearizer {

    /// Result of graph linearization.
    ///
    /// @param steps    the ordered atomic steps
    /// @param barriers the barriers (from readiness gates)
    public record Result(List<AtomicStep> steps, List<Barrier> barriers) {}

    private GraphLinearizer() {}

    /// Linearizes the graph into steps and barriers.
    ///
    /// @param graph           the finalized graph (after all rules)
    /// @param trials          the trial list (for trial IDs)
    /// @param sortedElements  topologically sorted elements
    /// @param trialElementNames names of trial elements
    /// @param instanceTracker per-element instance counter
    /// @return linearization result
    public static Result linearize(ReductoGraph graph, List<Trial> trials,
                                    List<Element> sortedElements,
                                    List<String> trialElementNames,
                                    Map<String, int[]> instanceTracker) {
        List<ReductoNode> ordered = graph.topologicalOrder();
        List<AtomicStep> steps = new ArrayList<>();
        List<Barrier> barriers = new ArrayList<>();

        Map<String, Element> elementMap = new LinkedHashMap<>();
        for (Element e : sortedElements) elementMap.put(e.name(), e);

        List<String> allElementNames = sortedElements.stream().map(Element::name).toList();

        for (ReductoNode node : ordered) {
            List<String> deps = new ArrayList<>();
            for (ReductoNode pred : node.predecessors()) {
                deps.add(pred.id());
            }

            switch (node.type()) {
                case START -> steps.add(new AtomicStep.CheckpointState(
                    node.id(), "start",
                    deps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    Map.of("type", "start")));

                case END -> steps.add(new AtomicStep.CheckpointState(
                    node.id(), "end",
                    deps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    Map.of("type", "end")));

                case ACTIVATE -> {
                    Element elem = elementMap.get(node.elementName());
                    int instNum = nextInstance(instanceTracker, node.elementName());

                    Map<String, Object> config = new LinkedHashMap<>();
                    if (elem != null) {
                        config.putAll(elem.configuration());
                    }

                    // Overlay trial-specific parameter assignments
                    if (node.trialIndex() >= 0 && node.trialIndex() < trials.size()) {
                        Trial trial = trials.get(node.trialIndex());
                        String prefix = node.elementName() + ".";
                        for (var entry : trial.assignments().entrySet()) {
                            String key = entry.getKey();
                            if (key.startsWith(prefix)) {
                                config.put(key.substring(prefix.length()), entry.getValue().value());
                            } else if (elem != null) {
                                // Try bare parameter name match
                                for (var param : elem.parameters()) {
                                    if (param.name().equals(key)) {
                                        config.put(key, entry.getValue().value());
                                    }
                                }
                            }
                        }
                    }

                    Map<String, Object> meta = new LinkedHashMap<>(node.metadata());
                    meta.put("element", node.elementName());
                    if (node.trialIndex() >= 0) meta.put("trial_index", node.trialIndex());
                    if (node.groupIndex() >= 0) meta.put("group_index", node.groupIndex());

                    steps.add(new AtomicStep.DeployElement(
                        node.id(),
                        node.elementName(),
                        instNum,
                        config,
                        deps,
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.minimal(),
                        Optional.empty(),
                        meta));
                }

                case DEACTIVATE -> {
                    int instNum = currentInstance(instanceTracker, node.elementName());

                    Map<String, Object> meta = new LinkedHashMap<>(node.metadata());
                    meta.put("element", node.elementName());
                    if (node.trialIndex() >= 0) meta.put("trial_index", node.trialIndex());
                    if (node.groupIndex() >= 0) meta.put("group_index", node.groupIndex());

                    steps.add(new AtomicStep.TeardownElement(
                        node.id(),
                        node.elementName(),
                        instNum,
                        true,
                        deps,
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.none(),
                        Optional.empty(),
                        meta));
                }

                case AWAIT -> {
                    int instNum = currentInstance(instanceTracker, node.elementName());
                    int trialIdx = node.trialIndex();
                    String trialId = trialIdx >= 0 && trialIdx < trials.size()
                        ? trials.get(trialIdx).id() : "trial_" + trialIdx;

                    Map<String, String> bindings = new LinkedHashMap<>();
                    for (String eName : allElementNames) {
                        bindings.put(eName, eName + "_inst_" + currentInstance(instanceTracker, eName));
                    }

                    Map<String, Object> meta = new LinkedHashMap<>(node.metadata());
                    meta.put("element", node.elementName());
                    meta.put("trial_index", trialIdx);

                    steps.add(new AtomicStep.AwaitElement(
                        node.id(),
                        node.elementName(),
                        instNum,
                        trialId,
                        bindings,
                        deps,
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.minimal(),
                        Optional.empty(),
                        meta));
                }

                case NOTIFY_TRIAL_START -> {
                    int trialIdx = node.trialIndex();
                    String trialId = trialIdx >= 0 && trialIdx < trials.size()
                        ? trials.get(trialIdx).id() : "trial_" + trialIdx;
                    Optional<String> trialCode = Optional.ofNullable(
                        (String) node.metadata().get("trial_code"));

                    Map<String, Object> meta = new LinkedHashMap<>(node.metadata());
                    meta.put("trial_index", trialIdx);

                    steps.add(new AtomicStep.NotifyTrialStart(
                        node.id(),
                        trialId,
                        trialIdx,
                        trialCode,
                        allElementNames,
                        deps,
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.none(),
                        Optional.empty(),
                        meta));
                }

                case NOTIFY_TRIAL_END -> {
                    int trialIdx = node.trialIndex();
                    String trialId = trialIdx >= 0 && trialIdx < trials.size()
                        ? trials.get(trialIdx).id() : "trial_" + trialIdx;
                    Optional<String> trialCode = Optional.ofNullable(
                        (String) node.metadata().get("trial_code"));

                    Map<String, Object> meta = new LinkedHashMap<>(node.metadata());
                    meta.put("trial_index", trialIdx);

                    steps.add(new AtomicStep.NotifyTrialEnd(
                        node.id(),
                        trialId,
                        trialIdx,
                        trialCode,
                        allElementNames,
                        AtomicStep.ShutdownReason.NORMAL,
                        deps,
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.none(),
                        Optional.empty(),
                        meta));
                }

                case READINESS_GATE -> {
                    String barrierId = "barrier_" + node.id();

                    steps.add(new AtomicStep.BarrierSync(
                        node.id(),
                        barrierId,
                        deps,
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.none(),
                        Optional.empty(),
                        Map.of("element", node.elementName() != null ? node.elementName() : "")));

                    barriers.add(new DefaultBarrier(
                        barrierId,
                        Barrier.BarrierType.ELEMENT_READY,
                        (node.elementName() != null ? node.elementName() : "unknown") + " readiness gate",
                        deps,
                        List.of(),
                        null,
                        Barrier.TimeoutAction.FAIL_FAST,
                        Map.of("element", node.elementName() != null ? node.elementName() : "")));
                }

                case TRIAL_SEED ->
                    throw new IllegalStateException("TRIAL_SEED nodes should have been expanded by Rule 1");
            }
        }

        return new Result(steps, barriers);
    }

    private static int nextInstance(Map<String, int[]> tracker, String elementName) {
        int[] counter = tracker.computeIfAbsent(elementName, k -> new int[]{0});
        return counter[0]++;
    }

    private static int currentInstance(Map<String, int[]> tracker, String elementName) {
        int[] counter = tracker.get(elementName);
        return (counter != null && counter[0] > 0) ? counter[0] - 1 : 0;
    }
}
