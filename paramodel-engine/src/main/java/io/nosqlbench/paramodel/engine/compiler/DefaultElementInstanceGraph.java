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

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ElementInstanceGraph;

import java.util.*;
import java.util.Optional;

///
/// Default implementation of {@link ElementInstanceGraph} that eagerly derives
/// the static element-instance topology from a list of {@link AtomicStep}s.
///
/// ## Algorithm
///
/// 1. Scan steps for {@link AtomicStep.DeployElement} to discover all
///    {@code (elementId, instanceNumber)} pairs and their configurations.
///
/// 2. For each deploy step, walk the {@code dependencies()} list via BFS,
///    passing through barriers and non-deploy steps, until upstream
///    {@link AtomicStep.DeployElement} steps are reached. Each such upstream
///    step produces a deduplicated instance-level edge.
///
/// 3. Compute topological order via Kahn's algorithm on the resulting
///    instance-level edge set.
///
/// This mirrors the instance-discovery and edge-inference logic in
/// {@link DefaultLiveElementGraph} but omits all runtime state derivation.
///
public class DefaultElementInstanceGraph implements ElementInstanceGraph {

    private final Set<InstanceNode> nodeSet;
    private final List<InstanceEdge> edgeList;
    private final Map<String, List<InstanceEdge>> edgesFromMap;
    private final Map<String, List<InstanceEdge>> edgesToMap;
    private final List<String> topoOrder;
    private final Map<String, Integer> instanceCounts;

    private record InstanceKey(String elementId, int instanceNumber) {
        String nodeId() { return elementId + ":" + instanceNumber; }
    }

    private DefaultElementInstanceGraph(List<AtomicStep> steps) {
        // Step 1: Build step lookup
        Map<String, AtomicStep> stepById = new LinkedHashMap<>(steps.size());
        for (AtomicStep step : steps) {
            stepById.put(step.id(), step);
        }

        // Step 2: Discover deploy steps and group by instance key
        Map<InstanceKey, List<AtomicStep.DeployElement>> deploysByInstance = new LinkedHashMap<>();
        Map<InstanceKey, Map<String, Object>> configByInstance = new LinkedHashMap<>();

        for (AtomicStep step : steps) {
            if (step instanceof AtomicStep.DeployElement d) {
                InstanceKey key = new InstanceKey(d.elementId(), d.instanceNumber());
                deploysByInstance.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
                // Use the first deploy step's configuration for this instance
                configByInstance.putIfAbsent(key, d.configuration());
            }
        }

        // Step 3: Build trial-index → trial-code lookup from NotifyTrialStart steps
        Map<Integer, String> trialCodeByIndex = new LinkedHashMap<>();
        for (AtomicStep step : steps) {
            if (step instanceof AtomicStep.NotifyTrialStart n) {
                n.trialCode().ifPresent(code -> trialCodeByIndex.put(n.trialIndex(), code));
            }
        }

        // Step 4: Build instance nodes with trial codes
        Set<InstanceNode> nodes = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var entry : deploysByInstance.entrySet()) {
            InstanceKey key = entry.getKey();
            Map<String, Object> config = configByInstance.getOrDefault(key, Map.of());

            // Resolve trial code from the deploy step's trial_index metadata
            Optional<String> trialCode = Optional.empty();
            for (AtomicStep.DeployElement d : entry.getValue()) {
                Object trialIdx = d.metadata().get("trial_index");
                if (trialIdx instanceof Number n) {
                    String code = trialCodeByIndex.get(n.intValue());
                    if (code != null) {
                        trialCode = Optional.of(code);
                        break;
                    }
                }
            }

            nodes.add(new InstanceNode(key.elementId(), key.instanceNumber(), config, trialCode));
            counts.merge(key.elementId(), 1, Integer::sum);
        }
        this.nodeSet = Collections.unmodifiableSet(nodes);
        this.instanceCounts = Collections.unmodifiableMap(counts);

        // Step 4: Compute element-level transitive dependencies, then infer
        // instance-level edges filtered to only declared relationships.
        Map<String, Set<String>> transitiveElementDeps =
            computeTransitiveElementDeps(deploysByInstance);

        Set<String> edgeKeys = new LinkedHashSet<>();
        List<InstanceEdge> rawEdges = new ArrayList<>();

        for (var entry : deploysByInstance.entrySet()) {
            InstanceKey targetInstance = entry.getKey();
            Set<String> allowed = transitiveElementDeps.getOrDefault(
                targetInstance.elementId(), Set.of());
            for (AtomicStep.DeployElement deployStep : entry.getValue()) {
                Set<InstanceKey> upstreamInstances = findUpstreamInstances(
                    deployStep, stepById, targetInstance, allowed);
                for (InstanceKey sourceInstance : upstreamInstances) {
                    String edgeKey = sourceInstance.nodeId() + "->" + targetInstance.nodeId();
                    if (edgeKeys.add(edgeKey)) {
                        rawEdges.add(new InstanceEdge(
                            sourceInstance.elementId(), sourceInstance.instanceNumber(),
                            targetInstance.elementId(), targetInstance.instanceNumber()));
                    }
                }
            }
        }
        this.edgeList = Collections.unmodifiableList(rawEdges);

        // Step 5: Build edge indexes by elementId
        Map<String, List<InstanceEdge>> fromMap = new LinkedHashMap<>();
        Map<String, List<InstanceEdge>> toMap = new LinkedHashMap<>();
        for (InstanceEdge edge : edgeList) {
            fromMap.computeIfAbsent(edge.sourceElementId(), k -> new ArrayList<>()).add(edge);
            toMap.computeIfAbsent(edge.targetElementId(), k -> new ArrayList<>()).add(edge);
        }
        this.edgesFromMap = Collections.unmodifiableMap(fromMap);
        this.edgesToMap = Collections.unmodifiableMap(toMap);

        // Step 6: Topological sort via Kahn's algorithm
        Set<String> allNodeIds = new LinkedHashSet<>();
        for (InstanceKey key : deploysByInstance.keySet()) {
            allNodeIds.add(key.nodeId());
        }
        this.topoOrder = computeTopologicalOrder(allNodeIds, edgeList);
    }

    /// Creates a new element instance graph from a list of atomic steps.
    ///
    /// @param steps the steps from an {@link io.nosqlbench.paramodel.plan.ExecutionPlan}
    /// @return a new element instance graph
    public static ElementInstanceGraph create(List<AtomicStep> steps) {
        return new DefaultElementInstanceGraph(steps);
    }

    /// Walks dependencies transitively from a deploy step to find upstream deploy
    /// steps, yielding instance-level edges.  Stops at deploy steps and passes
    /// through all non-deploy steps (including notification barriers).
    ///
    /// The discovered upstream deploy steps are then filtered against the
    /// element-level dependency set so that only elements the target actually
    /// declares a (transitive) dependency on produce edges.  This prevents
    /// notification fan-in from creating spurious edges between unrelated
    /// elements that happen to share the same trial lifecycle boundary.
    ///
    /// When a deploy step for the **same** element is encountered (a different
    /// instance produced by serial reuse), the BFS walks through it instead of
    /// stopping.  This allows the graph to discover the upstream element
    /// instances that serial-reuse instances transitively depend on through
    /// the teardown/notify/redeploy chain.
    private static Set<InstanceKey> findUpstreamInstances(
            AtomicStep.DeployElement deployStep,
            Map<String, AtomicStep> stepById,
            InstanceKey selfInstance,
            Set<String> allowedUpstreamElements) {

        Set<InstanceKey> upstreamInstances = new LinkedHashSet<>();
        Deque<String> workList = new ArrayDeque<>(deployStep.dependencies());
        Set<String> visited = new HashSet<>();

        while (!workList.isEmpty()) {
            String depId = workList.poll();
            if (!visited.add(depId)) continue;

            AtomicStep depStep = stepById.get(depId);
            if (depStep == null) continue;

            if (depStep instanceof AtomicStep.DeployElement d) {
                InstanceKey upstreamKey = new InstanceKey(d.elementId(), d.instanceNumber());
                if (!upstreamKey.equals(selfInstance)
                        && allowedUpstreamElements.contains(d.elementId())) {
                    // Found an allowed upstream element — add edge and stop here
                    upstreamInstances.add(upstreamKey);
                } else if (d.elementId().equals(selfInstance.elementId())) {
                    // Same element, different instance (serial reuse boundary) —
                    // walk through to find the upstream element instances behind it
                    workList.addAll(depStep.dependencies());
                }
                // For unrelated elements not in the allowed set, stop walking
            } else {
                // Walk through all non-deploy steps (barriers, notifications, etc.)
                workList.addAll(depStep.dependencies());
            }
        }
        return upstreamInstances;
    }

    /// Builds the transitive element-level dependency set for each element
    /// from the {@code element_deps} metadata embedded in deploy steps by
    /// the graph linearizer.
    ///
    /// @return map from element name → set of all transitively depended-on element names
    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> computeTransitiveElementDeps(
            Map<InstanceKey, List<AtomicStep.DeployElement>> deploysByInstance) {

        // Extract direct deps from deploy step metadata
        Map<String, Set<String>> directDeps = new LinkedHashMap<>();
        for (var entry : deploysByInstance.entrySet()) {
            String element = entry.getKey().elementId();
            directDeps.putIfAbsent(element, new LinkedHashSet<>());
            for (AtomicStep.DeployElement deploy : entry.getValue()) {
                Object depsMeta = deploy.metadata().get("element_deps");
                if (depsMeta instanceof List<?> list) {
                    for (Object item : list) {
                        directDeps.get(element).add(item.toString());
                    }
                }
            }
        }

        // Transitive closure
        Map<String, Set<String>> transitive = new LinkedHashMap<>();
        for (String element : directDeps.keySet()) {
            Set<String> closed = new LinkedHashSet<>();
            Deque<String> stack = new ArrayDeque<>(directDeps.getOrDefault(element, Set.of()));
            while (!stack.isEmpty()) {
                String dep = stack.poll();
                if (closed.add(dep)) {
                    stack.addAll(directDeps.getOrDefault(dep, Set.of()));
                }
            }
            transitive.put(element, closed);
        }
        return transitive;
    }

    /// Kahn's algorithm topological sort on the instance node IDs.
    private static List<String> computeTopologicalOrder(
            Set<String> nodeIds, List<InstanceEdge> edges) {

        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (String id : nodeIds) {
            inDegree.put(id, 0);
            adjacency.put(id, new ArrayList<>());
        }
        for (InstanceEdge edge : edges) {
            adjacency.computeIfAbsent(edge.sourceNodeId(), k -> new ArrayList<>())
                .add(edge.targetNodeId());
            inDegree.merge(edge.targetNodeId(), 1, Integer::sum);
        }

        Deque<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>(nodeIds.size());
        while (!queue.isEmpty()) {
            String node = queue.poll();
            result.add(node);
            for (String neighbor : adjacency.getOrDefault(node, List.of())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public Set<InstanceNode> nodes() {
        return nodeSet;
    }

    @Override
    public List<InstanceEdge> edges() {
        return edgeList;
    }

    @Override
    public List<InstanceEdge> edgesFrom(String elementId) {
        return edgesFromMap.getOrDefault(elementId, List.of());
    }

    @Override
    public List<InstanceEdge> edgesTo(String elementId) {
        return edgesToMap.getOrDefault(elementId, List.of());
    }

    @Override
    public List<String> topologicalOrder() {
        return topoOrder;
    }

    @Override
    public int instanceCount(String elementId) {
        return instanceCounts.getOrDefault(elementId, 0);
    }

    @Override
    public int totalInstances() {
        return nodeSet.size();
    }
}
