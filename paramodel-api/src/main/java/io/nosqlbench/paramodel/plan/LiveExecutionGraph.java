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
package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.elements.Element;

import java.util.List;
import java.util.Map;
import java.util.Set;

///
/// # LiveExecutionGraph
///
/// An annotated view of an {@link ExecutionGraph} combined with runtime
/// {@link ExecutionState} to produce per-step and per-edge status
/// information. This is a pure function of its two inputs:
///
/// ```
/// LiveExecutionGraph = f(ExecutionGraph, ExecutionState)
/// ```
///
/// ## Purpose
///
/// The live execution graph enables real-time visualization of which steps
/// are active, completed, blocked, or unreachable. It is the primary API
/// for building execution dashboards, stepping debuggers, and progress
/// monitors.
///
/// ## Status Derivation
///
/// Step statuses are derived by walking the graph in topological order:
///
/// ```
/// For each step (in topological order):
///   1. Check runtime state sets (completed/failed/skipped/in-flight)
///   2. If not in any set, derive from dependency statuses:
///      - Any dep FAILED/SKIPPED/UNREACHABLE → UNREACHABLE
///      - All deps COMPLETED → READY
///      - Otherwise → BLOCKED
/// ```
///
/// Edge statuses are derived from the source and target step statuses.
///
/// ## Thread Safety
///
/// Instances are immutable snapshots — safe for concurrent reads. To
/// observe changes, create a new instance with updated state.
///
/// @see ExecutionGraph
/// @see ExecutionState
/// @see StepStatus
/// @see EdgeStatus
///
public interface LiveExecutionGraph {

    /// Returns the underlying static execution graph.
    ///
    /// @return the execution graph
    ExecutionGraph graph();

    /// Returns the execution state used to derive this live view.
    ///
    /// @return the execution state
    ExecutionState state();

    /// Returns the computed status of a step.
    ///
    /// @param step the step to query
    /// @return the step's status
    /// @throws IllegalArgumentException if the step is not in the graph
    StepStatus stepStatus(AtomicStep step);

    /// Returns the computed status of a step by ID.
    ///
    /// @param stepId the step ID to query
    /// @return the step's status
    /// @throws IllegalArgumentException if no step with this ID exists
    StepStatus stepStatus(String stepId);

    /// Returns all steps with the given status.
    ///
    /// @param status the status to filter by
    /// @return steps with the given status (unmodifiable)
    Set<AtomicStep> stepsByStatus(StepStatus status);

    /// Returns a map of every step to its computed status.
    ///
    /// @return step-to-status mapping (unmodifiable)
    Map<AtomicStep, StepStatus> allStepStatuses();

    /// Returns the computed status of an edge.
    ///
    /// @param edge the edge to query
    /// @return the edge's status
    EdgeStatus edgeStatus(ExecutionGraph.Edge edge);

    /// Returns all edges with the given status.
    ///
    /// @param status the status to filter by
    /// @return edges with the given status (unmodifiable)
    Set<ExecutionGraph.Edge> edgesByStatus(EdgeStatus status);

    /// Returns an annotated view of an edge including source and target
    /// step statuses.
    ///
    /// @param edge the edge to annotate
    /// @return the annotated live edge
    LiveEdge liveEdge(ExecutionGraph.Edge edge);

    /// Returns annotated views of all edges.
    ///
    /// @return all live edges (unmodifiable)
    List<LiveEdge> liveEdges();

    /// Returns the frontier — steps that are currently {@link StepStatus#READY}
    /// and eligible for execution.
    ///
    /// @return frontier steps (unmodifiable)
    Set<AtomicStep> frontier();

    /// Returns the number of steps in the frontier.
    ///
    /// @return frontier size (current parallelism opportunity)
    int frontierSize();

    /// Returns steps that are currently {@link StepStatus#IN_PROGRESS}.
    ///
    /// @return active steps (unmodifiable)
    Set<AtomicStep> activeSteps();

    /// Returns the IDs of all steps in the active subgraph (IN_PROGRESS
    /// or READY).
    ///
    /// @return active subgraph step IDs (unmodifiable)
    Set<String> activeSubgraphStepIds();

    /// Returns the names of elements that have at least one active
    /// (IN_PROGRESS) step.
    ///
    /// @return active element names (unmodifiable)
    Set<String> activeElementNames();

    /// Returns the IDs of trials that are currently in-flight.
    ///
    /// @return active trial IDs (unmodifiable)
    Set<String> activeTrialIds();

    /// Returns the IDs of trials that have completed.
    ///
    /// @return completed trial IDs (unmodifiable)
    Set<String> completedTrialIds();

    /// Returns the operational state of a named element.
    ///
    /// @param elementName the element name
    /// @return the element's operational state, or
    ///         {@link Element.OperationalState#INACTIVE} if unknown
    Element.OperationalState elementState(String elementName);

    /// Returns aggregate progress metrics.
    ///
    /// @return progress snapshot
    Progress progress();

    /// Returns {@code true} if all steps have reached a terminal status.
    ///
    /// @return true if execution is complete
    boolean isComplete();

    /// Returns {@code true} if any step has {@link StepStatus#FAILED}.
    ///
    /// @return true if there are failures
    boolean hasFailures();

    /// Returns {@code true} if any step has {@link StepStatus#UNREACHABLE}.
    ///
    /// @return true if there are unreachable steps
    boolean hasUnreachableSteps();

    /// An annotated edge combining the static edge with runtime
    /// source and target step statuses.
    ///
    /// @param edge the static edge
    /// @param status the derived edge status
    /// @param sourceStatus the source step's status
    /// @param targetStatus the target step's status
    record LiveEdge(
        ExecutionGraph.Edge edge,
        EdgeStatus status,
        StepStatus sourceStatus,
        StepStatus targetStatus
    ) {}

    /// Aggregate progress metrics for the execution.
    ///
    /// @param completed number of completed steps
    /// @param failed number of failed steps
    /// @param skipped number of skipped steps
    /// @param inProgress number of in-progress steps
    /// @param ready number of ready steps
    /// @param blocked number of blocked steps
    /// @param unreachable number of unreachable steps
    /// @param totalSteps total number of steps
    /// @param completedTrials number of completed trials
    /// @param inFlightTrials number of in-flight trials
    /// @param totalTrials total number of trial steps
    record Progress(
        int completed,
        int failed,
        int skipped,
        int inProgress,
        int ready,
        int blocked,
        int unreachable,
        int totalSteps,
        int completedTrials,
        int inFlightTrials,
        int totalTrials
    ) {

        /// Returns the completion percentage as a value between 0.0 and 100.0.
        ///
        /// Terminal steps (completed + failed + skipped + unreachable) are
        /// counted as "done" for percentage purposes.
        ///
        /// @return completion percentage
        public double completionPercentage() {
            if (totalSteps == 0) return 100.0;
            int terminal = completed + failed + skipped + unreachable;
            return (double) terminal / totalSteps * 100.0;
        }

        /// Returns the success rate as a value between 0.0 and 1.0.
        ///
        /// Calculated as completed / (completed + failed). Returns 1.0
        /// if no steps have completed or failed.
        ///
        /// @return success rate
        public double successRate() {
            int resolved = completed + failed;
            if (resolved == 0) return 1.0;
            return (double) completed / resolved;
        }
    }
}
