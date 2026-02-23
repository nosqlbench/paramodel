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

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.plan.*;

import java.util.*;

///
/// Default implementation of {@link LiveElementGraph} that eagerly computes
/// the per-instance element topology from an {@link ExecutionPlan} and
/// {@link ExecutionState}.
///
/// ## Synthesis Algorithm
///
/// 1. **Discover instances**: Scan steps for {@link AtomicStep.DeployElement},
///    {@link AtomicStep.TeardownElement}, {@link AtomicStep.AwaitElement}, and
///    {@link AtomicStep.NotifyTrialStart} to collect (elementId, instanceNumber) pairs.
///
/// 2. **Build step lookup**: Map from step ID to step for transitive dependency walking.
///
/// 3. **Infer instance-level edges**: For each deploy step, walk dependencies
///    transitively until upstream deploy steps are found. Each pair creates a
///    deduplicated instance-level edge.
///
/// 4. **Build nodes**: For each (elementId, instanceNumber) pair, derive
///    operational state from step completion/in-flight/failed data. Fall back
///    to {@link ExecutionState#elementStates()} when a deploy step has completed
///    and no teardown has started.
///
/// 5. **Derive edge statuses**: From the source node's operational state.
///
/// 6. **Topological sort**: Kahn's algorithm on the instance-level edge set.
///
/// 7. **Compute progress**: Count nodes per operational state.
///
public class DefaultLiveElementGraph implements LiveElementGraph {

    private final ExecutionPlan plan;
    private final ExecutionState state;
    private final Map<String, ElementNode> nodeMap;
    private final List<ElementEdge> edgeList;
    private final Map<String, List<ElementEdge>> edgesFromMap;
    private final Map<String, List<ElementEdge>> edgesToMap;
    private final List<String> topoOrder;
    private final ElementProgress progressSnapshot;

    /// Instance key for grouping nodes by elementId.
    private record InstanceKey(String elementId, int instanceNumber) {
        String nodeId() { return elementId + ":" + instanceNumber; }
    }

    private DefaultLiveElementGraph(ExecutionPlan plan, ExecutionState state) {
        this.plan = plan;
        this.state = state;

        List<AtomicStep> steps = plan.steps();

        // Step 1: Build step lookup
        Map<String, AtomicStep> stepById = new LinkedHashMap<>(steps.size());
        for (AtomicStep step : steps) {
            stepById.put(step.id(), step);
        }

        // Step 2: Discover instances and collect deploy/teardown info per instance
        Set<InstanceKey> allInstances = new LinkedHashSet<>();
        Map<InstanceKey, List<AtomicStep.DeployElement>> deployStepsByInstance = new LinkedHashMap<>();
        Map<InstanceKey, List<AtomicStep.TeardownElement>> teardownStepsByInstance = new LinkedHashMap<>();

        for (AtomicStep step : steps) {
            switch (step) {
                case AtomicStep.DeployElement d -> {
                    InstanceKey key = new InstanceKey(d.elementId(), d.instanceNumber());
                    allInstances.add(key);
                    deployStepsByInstance.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
                }
                case AtomicStep.TeardownElement t -> {
                    InstanceKey key = new InstanceKey(t.elementId(), t.instanceNumber());
                    allInstances.add(key);
                    teardownStepsByInstance.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
                }
                case AtomicStep.AwaitElement a -> {
                    InstanceKey key = new InstanceKey(a.elementId(), a.instanceNumber());
                    allInstances.add(key);
                }
                case AtomicStep.NotifyTrialStart nts -> {
                    for (String name : nts.elementNames()) {
                        allInstances.add(new InstanceKey(name, 0));
                    }
                }
                default -> {}
            }
        }

        // Step 3: Infer instance-level edges from deploy step dependencies
        Set<String> edgeKeys = new LinkedHashSet<>();
        List<ElementEdge> rawEdges = new ArrayList<>();

        for (var entry : deployStepsByInstance.entrySet()) {
            InstanceKey targetInstance = entry.getKey();
            for (AtomicStep.DeployElement deployStep : entry.getValue()) {
                Set<InstanceKey> upstreamInstances = findUpstreamInstances(
                    deployStep, stepById, targetInstance);
                for (InstanceKey sourceInstance : upstreamInstances) {
                    String edgeKey = sourceInstance.nodeId() + "->" + targetInstance.nodeId();
                    if (edgeKeys.add(edgeKey)) {
                        rawEdges.add(new ElementEdge(
                            sourceInstance.elementId(), sourceInstance.instanceNumber(),
                            targetInstance.elementId(), targetInstance.instanceNumber(),
                            ElementEdgeStatus.PENDING)); // status computed below
                    }
                }
            }
        }

        // Step 4: Build nodes with per-instance state derivation
        this.nodeMap = new LinkedHashMap<>();
        Set<String> completedStepIds = state.completedStepIds();
        Set<String> inFlightStepIds = state.inFlightStepIds();
        Set<String> failedStepIds = state.failedStepIds();

        for (InstanceKey instance : allInstances) {
            Element.OperationalState opState = deriveInstanceState(
                instance, deployStepsByInstance, teardownStepsByInstance,
                completedStepIds, inFlightStepIds, failedStepIds,
                state.elementStates());

            boolean hasCompletedDeploy = false;
            List<AtomicStep.DeployElement> deploys = deployStepsByInstance.getOrDefault(
                instance, List.of());
            for (AtomicStep.DeployElement d : deploys) {
                if (completedStepIds.contains(d.id())) {
                    hasCompletedDeploy = true;
                    break;
                }
            }

            boolean hasCompletedTeardown = false;
            List<AtomicStep.TeardownElement> teardowns = teardownStepsByInstance.getOrDefault(
                instance, List.of());
            for (AtomicStep.TeardownElement t : teardowns) {
                if (completedStepIds.contains(t.id())) {
                    hasCompletedTeardown = true;
                    break;
                }
            }

            boolean isDeployed = hasCompletedDeploy && !hasCompletedTeardown;
            String nodeId = instance.nodeId();

            nodeMap.put(nodeId, new ElementNode(
                instance.elementId(), instance.instanceNumber(),
                opState, isDeployed, hasCompletedTeardown));
        }

        // Step 5: Derive edge statuses from source node operational state
        this.edgeList = new ArrayList<>(rawEdges.size());
        for (ElementEdge raw : rawEdges) {
            String sourceNodeId = raw.sourceNodeId();
            ElementNode sourceNode = nodeMap.get(sourceNodeId);
            ElementEdgeStatus edgeStatus = deriveEdgeStatus(
                sourceNode != null ? sourceNode.operationalState() : Element.OperationalState.INACTIVE);
            edgeList.add(new ElementEdge(
                raw.sourceElementId(), raw.sourceInstanceNumber(),
                raw.targetElementId(), raw.targetInstanceNumber(),
                edgeStatus));
        }

        // Build edge indexes (by elementId, aggregating all instances)
        Map<String, List<ElementEdge>> fromMap = new LinkedHashMap<>();
        Map<String, List<ElementEdge>> toMap = new LinkedHashMap<>();
        for (ElementEdge edge : edgeList) {
            fromMap.computeIfAbsent(edge.sourceElementId(), k -> new ArrayList<>()).add(edge);
            toMap.computeIfAbsent(edge.targetElementId(), k -> new ArrayList<>()).add(edge);
        }
        this.edgesFromMap = Collections.unmodifiableMap(fromMap);
        this.edgesToMap = Collections.unmodifiableMap(toMap);

        // Step 6: Topological sort via Kahn's algorithm on nodeIds
        Set<String> allNodeIds = new LinkedHashSet<>();
        for (InstanceKey inst : allInstances) {
            allNodeIds.add(inst.nodeId());
        }
        this.topoOrder = computeTopologicalOrder(allNodeIds, edgeList);

        // Step 7: Compute progress
        int deployed = 0, running = 0, ready = 0, failed = 0, terminated = 0, inactive = 0;
        for (ElementNode node : nodeMap.values()) {
            switch (node.operationalState()) {
                case RUNNING -> { deployed++; running++; }
                case READY -> { deployed++; ready++; }
                case FAILED -> failed++;
                case TERMINATED -> terminated++;
                case INACTIVE -> inactive++;
                default -> {
                    if (node.isDeployed()) deployed++;
                }
            }
        }
        this.progressSnapshot = new ElementProgress(
            nodeMap.size(), deployed, running, ready, failed, terminated, inactive);
    }

    /// Creates a new live element graph from an execution plan and runtime state.
    ///
    /// @param plan the execution plan
    /// @param state the runtime execution state
    /// @return a new live element graph
    public static LiveElementGraph create(ExecutionPlan plan, ExecutionState state) {
        return new DefaultLiveElementGraph(plan, state);
    }

    /// Derives the operational state for a specific element instance from step
    /// completion data.
    ///
    /// The derivation order is:
    /// 1. Teardown step completed -> TERMINATED
    /// 2. Teardown step in-flight -> STOPPING
    /// 3. Deploy step failed -> FAILED
    /// 4. Deploy step completed, no teardown -> fall back to elementStates() or READY
    /// 5. Deploy step in-flight -> PROVISIONING
    /// 6. Otherwise -> INACTIVE
    private static Element.OperationalState deriveInstanceState(
            InstanceKey instance,
            Map<InstanceKey, List<AtomicStep.DeployElement>> deployStepsByInstance,
            Map<InstanceKey, List<AtomicStep.TeardownElement>> teardownStepsByInstance,
            Set<String> completedStepIds,
            Set<String> inFlightStepIds,
            Set<String> failedStepIds,
            Map<String, Element.OperationalState> elementStates) {

        // Check teardown steps first
        List<AtomicStep.TeardownElement> teardowns = teardownStepsByInstance.getOrDefault(
            instance, List.of());
        for (AtomicStep.TeardownElement t : teardowns) {
            if (completedStepIds.contains(t.id())) {
                return Element.OperationalState.TERMINATED;
            }
        }
        for (AtomicStep.TeardownElement t : teardowns) {
            if (inFlightStepIds.contains(t.id())) {
                return Element.OperationalState.STOPPING;
            }
        }

        // Check deploy steps
        List<AtomicStep.DeployElement> deploys = deployStepsByInstance.getOrDefault(
            instance, List.of());
        for (AtomicStep.DeployElement d : deploys) {
            if (failedStepIds.contains(d.id())) {
                return Element.OperationalState.FAILED;
            }
        }
        boolean hasCompletedDeploy = false;
        for (AtomicStep.DeployElement d : deploys) {
            if (completedStepIds.contains(d.id())) {
                hasCompletedDeploy = true;
                break;
            }
        }
        if (hasCompletedDeploy) {
            // Fall back to elementStates if available (keyed by element name)
            Element.OperationalState fromElementStates = elementStates.get(instance.elementId());
            return fromElementStates != null ? fromElementStates : Element.OperationalState.READY;
        }
        for (AtomicStep.DeployElement d : deploys) {
            if (inFlightStepIds.contains(d.id())) {
                return Element.OperationalState.PROVISIONING;
            }
        }

        return Element.OperationalState.INACTIVE;
    }

    /// Walks dependencies transitively from a deploy step to find upstream deploy
    /// steps, yielding instance-level edges. Stops at deploy steps and skips through
    /// barrier, notification, and other non-element steps.
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

    private static ElementEdgeStatus deriveEdgeStatus(Element.OperationalState opState) {
        return switch (opState) {
            case READY, RUNNING -> ElementEdgeStatus.SATISFIED;
            case FAILED -> ElementEdgeStatus.FAILED;
            case TERMINATED -> ElementEdgeStatus.TERMINATED;
            default -> ElementEdgeStatus.PENDING;
        };
    }

    private static List<String> computeTopologicalOrder(
            Set<String> nodeIds, List<ElementEdge> edges) {

        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (String id : nodeIds) {
            inDegree.put(id, 0);
            adjacency.put(id, new ArrayList<>());
        }
        for (ElementEdge edge : edges) {
            String sourceNodeId = edge.sourceNodeId();
            String targetNodeId = edge.targetNodeId();
            adjacency.computeIfAbsent(sourceNodeId, k -> new ArrayList<>())
                .add(targetNodeId);
            inDegree.merge(targetNodeId, 1, Integer::sum);
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
    public ExecutionPlan plan() {
        return plan;
    }

    @Override
    public ExecutionState state() {
        return state;
    }

    @Override
    public Set<ElementNode> nodes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(nodeMap.values()));
    }

    @Override
    public ElementNode node(String elementId) {
        List<ElementNode> matches = new ArrayList<>();
        for (ElementNode node : nodeMap.values()) {
            if (node.elementId().equals(elementId)) {
                matches.add(node);
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No element with ID: " + elementId);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                "Ambiguous: element '" + elementId + "' has " + matches.size()
                + " instances. Use node(elementId, instanceNumber) instead.");
        }
        return matches.getFirst();
    }

    @Override
    public ElementNode node(String elementId, int instanceNumber) {
        String nodeId = elementId + ":" + instanceNumber;
        ElementNode node = nodeMap.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException(
                "No element node with ID: " + elementId + ", instance: " + instanceNumber);
        }
        return node;
    }

    @Override
    public List<ElementEdge> edges() {
        return Collections.unmodifiableList(edgeList);
    }

    @Override
    public List<ElementEdge> edgesFrom(String elementId) {
        return edgesFromMap.getOrDefault(elementId, List.of());
    }

    @Override
    public List<ElementEdge> edgesTo(String elementId) {
        return edgesToMap.getOrDefault(elementId, List.of());
    }

    @Override
    public Set<ElementNode> nodesByState(Element.OperationalState opState) {
        Set<ElementNode> result = new LinkedHashSet<>();
        for (ElementNode node : nodeMap.values()) {
            if (node.operationalState() == opState) {
                result.add(node);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<ElementNode> activeNodes() {
        Set<ElementNode> result = new LinkedHashSet<>();
        for (ElementNode node : nodeMap.values()) {
            if (node.isDeployed() && !node.isTornDown()) {
                result.add(node);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public List<String> topologicalOrder() {
        return topoOrder;
    }

    @Override
    public ElementProgress progress() {
        return progressSnapshot;
    }

    @Override
    public boolean isComplete() {
        if (nodeMap.isEmpty()) return true;
        for (ElementNode node : nodeMap.values()) {
            Element.OperationalState opState = node.operationalState();
            if (opState != Element.OperationalState.TERMINATED
                && opState != Element.OperationalState.FAILED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasFailures() {
        for (ElementNode node : nodeMap.values()) {
            if (node.operationalState() == Element.OperationalState.FAILED) {
                return true;
            }
        }
        return false;
    }
}
