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
/// Default implementation of {@link LiveExecutionGraph} that eagerly computes
/// all step and edge statuses at construction time.
///
/// ## Algorithm
///
/// The computation is O(V+E) where V is the number of steps and E is the
/// number of edges:
///
/// 1. Walk steps in topological order
/// 2. For each step, check runtime state sets first (completed/failed/skipped/
///    in-flight), then derive from dependency statuses
/// 3. For each edge, derive status from source and target step statuses
/// 4. Cache frontier, progress, and element aggregations
///
public class DefaultLiveExecutionGraph implements LiveExecutionGraph {

    private final ExecutionGraph graph;
    private final ExecutionState state;
    private final Map<String, StepStatus> stepStatusById;
    private final Map<AtomicStep, StepStatus> stepStatusMap;
    private final Map<StepStatus, Set<AtomicStep>> stepsByStatusMap;
    private final Map<ExecutionGraph.Edge, EdgeStatus> edgeStatusMap;
    private final Map<EdgeStatus, Set<ExecutionGraph.Edge>> edgesByStatusMap;
    private final List<LiveEdge> liveEdgeList;
    private final Set<AtomicStep> frontierSet;
    private final Set<AtomicStep> activeStepSet;
    private final Progress progressSnapshot;

    private DefaultLiveExecutionGraph(ExecutionGraph graph, ExecutionState state) {
        this.graph = graph;
        this.state = state;

        // Step status computation in topological order
        List<AtomicStep> topoOrder = graph.topologicalSort();
        this.stepStatusById = new LinkedHashMap<>(topoOrder.size());
        this.stepStatusMap = new LinkedHashMap<>(topoOrder.size());

        for (AtomicStep step : topoOrder) {
            StepStatus status = deriveStepStatus(step);
            stepStatusById.put(step.id(), status);
            stepStatusMap.put(step, status);
        }

        // Group steps by status
        Map<StepStatus, Set<AtomicStep>> byStatus = new EnumMap<>(StepStatus.class);
        for (StepStatus s : StepStatus.values()) {
            byStatus.put(s, new LinkedHashSet<>());
        }
        stepStatusMap.forEach((step, status) -> byStatus.get(status).add(step));
        this.stepsByStatusMap = Collections.unmodifiableMap(byStatus);

        // Edge status computation
        List<ExecutionGraph.Edge> edges = graph.edges();
        this.edgeStatusMap = new LinkedHashMap<>(edges.size());
        Map<EdgeStatus, Set<ExecutionGraph.Edge>> byEdgeStatus = new EnumMap<>(EdgeStatus.class);
        for (EdgeStatus s : EdgeStatus.values()) {
            byEdgeStatus.put(s, new LinkedHashSet<>());
        }
        List<LiveEdge> liveEdges = new ArrayList<>(edges.size());

        for (ExecutionGraph.Edge edge : edges) {
            StepStatus sourceStatus = stepStatusById.get(edge.source().id());
            StepStatus targetStatus = stepStatusById.get(edge.target().id());
            EdgeStatus eStatus = deriveEdgeStatus(sourceStatus, targetStatus);
            edgeStatusMap.put(edge, eStatus);
            byEdgeStatus.get(eStatus).add(edge);
            liveEdges.add(new LiveEdge(edge, eStatus, sourceStatus, targetStatus));
        }
        this.edgesByStatusMap = Collections.unmodifiableMap(byEdgeStatus);
        this.liveEdgeList = List.copyOf(liveEdges);

        // Cached aggregations
        this.frontierSet = Collections.unmodifiableSet(byStatus.get(StepStatus.READY));
        this.activeStepSet = Collections.unmodifiableSet(byStatus.get(StepStatus.IN_PROGRESS));

        // Progress
        int totalTrials = 0;
        for (AtomicStep step : graph.steps()) {
            if (step instanceof AtomicStep.TrialStep) totalTrials++;
        }
        this.progressSnapshot = new Progress(
            byStatus.get(StepStatus.COMPLETED).size(),
            byStatus.get(StepStatus.FAILED).size(),
            byStatus.get(StepStatus.SKIPPED).size(),
            byStatus.get(StepStatus.IN_PROGRESS).size(),
            byStatus.get(StepStatus.READY).size(),
            byStatus.get(StepStatus.BLOCKED).size(),
            byStatus.get(StepStatus.UNREACHABLE).size(),
            graph.steps().size(),
            state.completedTrialIds().size(),
            state.inFlightTrialIds().size(),
            totalTrials
        );
    }

    /// Creates a new live execution graph from a static graph and runtime state.
    ///
    /// @param graph the static execution graph
    /// @param state the runtime execution state
    /// @return a new live execution graph
    public static LiveExecutionGraph create(ExecutionGraph graph, ExecutionState state) {
        return new DefaultLiveExecutionGraph(graph, state);
    }

    private StepStatus deriveStepStatus(AtomicStep step) {
        String id = step.id();
        if (state.completedStepIds().contains(id)) return StepStatus.COMPLETED;
        if (state.failedStepIds().contains(id)) return StepStatus.FAILED;
        if (state.skippedStepIds().contains(id)) return StepStatus.SKIPPED;
        if (state.inFlightStepIds().contains(id)) return StepStatus.IN_PROGRESS;

        // Derive from dependencies
        List<String> deps = step.dependencies();
        if (deps.isEmpty()) return StepStatus.READY;

        boolean allCompleted = true;
        for (String depId : deps) {
            StepStatus depStatus = stepStatusById.get(depId);
            if (depStatus == null) {
                allCompleted = false;
                continue;
            }
            if (depStatus == StepStatus.FAILED
                || depStatus == StepStatus.SKIPPED
                || depStatus == StepStatus.UNREACHABLE) {
                return StepStatus.UNREACHABLE;
            }
            if (depStatus != StepStatus.COMPLETED) {
                allCompleted = false;
            }
        }
        return allCompleted ? StepStatus.READY : StepStatus.BLOCKED;
    }

    private static EdgeStatus deriveEdgeStatus(StepStatus sourceStatus, StepStatus targetStatus) {
        if (sourceStatus == StepStatus.FAILED
            || sourceStatus == StepStatus.SKIPPED
            || sourceStatus == StepStatus.UNREACHABLE) {
            return EdgeStatus.FAILED;
        }
        if (sourceStatus == StepStatus.COMPLETED && targetStatus == StepStatus.IN_PROGRESS) {
            return EdgeStatus.ACTIVE;
        }
        if (sourceStatus == StepStatus.COMPLETED) {
            return EdgeStatus.SATISFIED;
        }
        return EdgeStatus.PENDING;
    }

    @Override
    public ExecutionGraph graph() {
        return graph;
    }

    @Override
    public ExecutionState state() {
        return state;
    }

    @Override
    public StepStatus stepStatus(AtomicStep step) {
        StepStatus status = stepStatusMap.get(step);
        if (status == null) {
            throw new IllegalArgumentException("Step not in graph: " + step.id());
        }
        return status;
    }

    @Override
    public StepStatus stepStatus(String stepId) {
        StepStatus status = stepStatusById.get(stepId);
        if (status == null) {
            throw new IllegalArgumentException("No step with ID: " + stepId);
        }
        return status;
    }

    @Override
    public Set<AtomicStep> stepsByStatus(StepStatus status) {
        return Collections.unmodifiableSet(stepsByStatusMap.get(status));
    }

    @Override
    public Map<AtomicStep, StepStatus> allStepStatuses() {
        return Collections.unmodifiableMap(stepStatusMap);
    }

    @Override
    public EdgeStatus edgeStatus(ExecutionGraph.Edge edge) {
        EdgeStatus status = edgeStatusMap.get(edge);
        if (status == null) {
            throw new IllegalArgumentException("Edge not in graph");
        }
        return status;
    }

    @Override
    public Set<ExecutionGraph.Edge> edgesByStatus(EdgeStatus status) {
        return Collections.unmodifiableSet(edgesByStatusMap.get(status));
    }

    @Override
    public LiveEdge liveEdge(ExecutionGraph.Edge edge) {
        StepStatus sourceStatus = stepStatusById.get(edge.source().id());
        StepStatus targetStatus = stepStatusById.get(edge.target().id());
        return new LiveEdge(edge, edgeStatus(edge), sourceStatus, targetStatus);
    }

    @Override
    public List<LiveEdge> liveEdges() {
        return liveEdgeList;
    }

    @Override
    public Set<AtomicStep> frontier() {
        return frontierSet;
    }

    @Override
    public int frontierSize() {
        return frontierSet.size();
    }

    @Override
    public Set<AtomicStep> activeSteps() {
        return activeStepSet;
    }

    @Override
    public Set<String> activeSubgraphStepIds() {
        Set<String> result = new LinkedHashSet<>();
        for (var entry : stepStatusMap.entrySet()) {
            if (entry.getValue() == StepStatus.IN_PROGRESS
                || entry.getValue() == StepStatus.READY) {
                result.add(entry.getKey().id());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<String> activeElementNames() {
        Set<String> result = new LinkedHashSet<>();
        for (AtomicStep step : activeStepSet) {
            switch (step) {
                case AtomicStep.DeployElement d -> result.add(d.elementId());
                case AtomicStep.TeardownElement t -> result.add(t.elementId());
                case AtomicStep.TrialStep t -> result.addAll(t.elementBindings().keySet());
                case AtomicStep.AwaitElement a -> result.add(a.elementId());
                default -> {}
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<String> activeTrialIds() {
        return state.inFlightTrialIds();
    }

    @Override
    public Set<String> completedTrialIds() {
        return state.completedTrialIds();
    }

    @Override
    public Element.OperationalState elementState(String elementName) {
        return state.elementStates().getOrDefault(elementName, Element.OperationalState.INACTIVE);
    }

    @Override
    public Progress progress() {
        return progressSnapshot;
    }

    @Override
    public boolean isComplete() {
        for (StepStatus status : stepStatusMap.values()) {
            if (!status.isTerminal()) return false;
        }
        return true;
    }

    @Override
    public boolean hasFailures() {
        return !stepsByStatusMap.get(StepStatus.FAILED).isEmpty();
    }

    @Override
    public boolean hasUnreachableSteps() {
        return !stepsByStatusMap.get(StepStatus.UNREACHABLE).isEmpty();
    }
}
