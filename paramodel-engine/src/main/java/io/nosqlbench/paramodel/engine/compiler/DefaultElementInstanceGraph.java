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

        // Step 3: Build instance nodes
        Set<InstanceNode> nodes = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (InstanceKey key : deploysByInstance.keySet()) {
            Map<String, Object> config = configByInstance.getOrDefault(key, Map.of());
            nodes.add(new InstanceNode(key.elementId(), key.instanceNumber(), config));
            counts.merge(key.elementId(), 1, Integer::sum);
        }
        this.nodeSet = Collections.unmodifiableSet(nodes);
        this.instanceCounts = Collections.unmodifiableMap(counts);

        // Step 4: Infer instance-level edges from deploy step dependencies
        Set<String> edgeKeys = new LinkedHashSet<>();
        List<InstanceEdge> rawEdges = new ArrayList<>();

        for (var entry : deploysByInstance.entrySet()) {
            InstanceKey targetInstance = entry.getKey();
            for (AtomicStep.DeployElement deployStep : entry.getValue()) {
                Set<InstanceKey> upstreamInstances = findUpstreamInstances(
                    deployStep, stepById, targetInstance);
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
    /// steps, yielding instance-level edges. Stops at deploy steps and passes
    /// through barriers, notifications, and other non-deploy steps.
    private static Set<InstanceKey> findUpstreamInstances(
            AtomicStep.DeployElement deployStep,
            Map<String, AtomicStep> stepById,
            InstanceKey selfInstance) {

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
                if (!upstreamKey.equals(selfInstance)) {
                    upstreamInstances.add(upstreamKey);
                }
                // Don't walk further past deploy steps
            } else {
                // Walk through non-deploy steps transitively
                workList.addAll(depStep.dependencies());
            }
        }
        return upstreamInstances;
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
