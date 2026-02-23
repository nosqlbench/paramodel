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
import java.util.Set;

///
/// # LiveElementGraph
///
/// A synthesized element-level topology view derived from an {@link ExecutionPlan}
/// and runtime {@link ExecutionState}. While {@link LiveExecutionGraph} shows per-step
/// status, this interface shows per-instance element topology: which element
/// instances exist, how they depend on each other, and their current operational
/// states.
///
/// Each logical instance of an element (e.g. `deploy("cassandra", 1)` and
/// `deploy("cassandra", 2)`) appears as a separate node in the graph, identified
/// by a composite `nodeId` of the form `"elementId:instanceNumber"`.
///
/// This is a pure function of its two inputs:
///
/// ```
/// LiveElementGraph = f(ExecutionPlan, ExecutionState)
/// ```
///
/// ## Purpose
///
/// The live element graph enables real-time visualization of element topology
/// and operational state. It answers questions like:
///
/// - Which element instances are deployed and operational?
/// - Which instances depend on which others?
/// - Are all upstream dependencies satisfied?
/// - How far along is the overall deployment?
///
/// ## Thread Safety
///
/// Instances are immutable snapshots — safe for concurrent reads. To
/// observe changes, create a new instance with updated state.
///
/// @see LiveExecutionGraph
/// @see ExecutionPlan
/// @see ExecutionState
/// @see ElementEdgeStatus
///
public interface LiveElementGraph {

    /// Returns the execution plan used to derive this element graph.
    ///
    /// @return the execution plan
    ExecutionPlan plan();

    /// Returns the execution state used to derive this element graph.
    ///
    /// @return the execution state
    ExecutionState state();

    /// Returns all element nodes in this graph.
    ///
    /// @return element nodes (unmodifiable)
    Set<ElementNode> nodes();

    /// Returns the element node for the given element ID, if there is
    /// exactly one instance. Throws if the element has multiple instances
    /// (use {@link #node(String, int)} instead) or does not exist.
    ///
    /// @param elementId the element ID to look up
    /// @return the element node
    /// @throws IllegalArgumentException if no element with this ID exists
    ///     or if multiple instances exist (ambiguous)
    ElementNode node(String elementId);

    /// Returns the element node for a specific instance.
    ///
    /// @param elementId the element ID
    /// @param instanceNumber the instance number
    /// @return the element node
    /// @throws IllegalArgumentException if no node with this ID and instance exists
    ElementNode node(String elementId, int instanceNumber);

    /// Returns all element-level dependency edges.
    ///
    /// @return element edges (unmodifiable)
    List<ElementEdge> edges();

    /// Returns edges originating from any instance of the given element.
    ///
    /// @param elementId the source element ID
    /// @return outgoing edges (unmodifiable)
    List<ElementEdge> edgesFrom(String elementId);

    /// Returns edges targeting any instance of the given element.
    ///
    /// @param elementId the target element ID
    /// @return incoming edges (unmodifiable)
    List<ElementEdge> edgesTo(String elementId);

    /// Returns all element nodes with the given operational state.
    ///
    /// @param opState the operational state to filter by
    /// @return matching element nodes (unmodifiable)
    Set<ElementNode> nodesByState(Element.OperationalState opState);

    /// Returns element nodes that are currently active (deployed and not
    /// terminated).
    ///
    /// @return active element nodes (unmodifiable)
    Set<ElementNode> activeNodes();

    /// Returns node IDs in topological order based on instance-level
    /// dependencies. Node IDs are composite strings of the form
    /// `"elementId:instanceNumber"`.
    ///
    /// @return node IDs in topological order (unmodifiable)
    List<String> topologicalOrder();

    /// Returns aggregate element instance progress metrics.
    ///
    /// @return element progress snapshot
    ElementProgress progress();

    /// Returns {@code true} if all element instances have reached a terminal
    /// state (TERMINATED or FAILED) or no elements exist.
    ///
    /// @return true if element lifecycle is complete
    boolean isComplete();

    /// Returns {@code true} if any element instance has {@link Element.OperationalState#FAILED}.
    ///
    /// @return true if there are element failures
    boolean hasFailures();

    /// An element instance node combining identity with runtime state.
    ///
    /// Each (elementId, instanceNumber) pair is a separate node in the graph.
    ///
    /// @param elementId the element identifier
    /// @param instanceNumber the instance number for this element
    /// @param operationalState the instance's current operational state
    /// @param isDeployed true if this instance has been deployed and not torn down
    /// @param isTornDown true if a teardown step has completed for this instance
    record ElementNode(
        String elementId,
        int instanceNumber,
        Element.OperationalState operationalState,
        boolean isDeployed,
        boolean isTornDown
    ) {
        /// Composite node identifier: "elementId:instanceNumber".
        ///
        /// @return the composite node ID
        public String nodeId() {
            return elementId + ":" + instanceNumber;
        }
    }

    /// An instance-level dependency edge with runtime status.
    ///
    /// @param sourceElementId the upstream element ID
    /// @param sourceInstanceNumber the upstream instance number
    /// @param targetElementId the downstream element ID
    /// @param targetInstanceNumber the downstream instance number
    /// @param dependencyStatus the status of this dependency
    record ElementEdge(
        String sourceElementId,
        int sourceInstanceNumber,
        String targetElementId,
        int targetInstanceNumber,
        ElementEdgeStatus dependencyStatus
    ) {
        /// Composite source node identifier.
        ///
        /// @return source nodeId as "elementId:instanceNumber"
        public String sourceNodeId() {
            return sourceElementId + ":" + sourceInstanceNumber;
        }

        /// Composite target node identifier.
        ///
        /// @return target nodeId as "elementId:instanceNumber"
        public String targetNodeId() {
            return targetElementId + ":" + targetInstanceNumber;
        }
    }

    /// Aggregate progress metrics for element instances.
    ///
    /// @param totalInstances total number of element instances
    /// @param deployed number of instances currently deployed (not torn down)
    /// @param running number of instances in RUNNING state
    /// @param ready number of instances in READY state
    /// @param failed number of instances in FAILED state
    /// @param terminated number of instances in TERMINATED state
    /// @param inactive number of instances in INACTIVE state
    record ElementProgress(
        int totalInstances,
        int deployed,
        int running,
        int ready,
        int failed,
        int terminated,
        int inactive
    ) {
        /// Returns the deployment percentage as a value between 0.0 and 100.0.
        ///
        /// @return deployment percentage
        public double deploymentPercentage() {
            if (totalInstances == 0) return 100.0;
            return (double) deployed / totalInstances * 100.0;
        }
    }
}
