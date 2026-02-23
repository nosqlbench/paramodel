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

import java.util.List;
import java.util.Map;
import java.util.Set;

///
/// Static element-instance topology derived from a compiled {@link ExecutionPlan}.
///
/// Unlike {@link LiveElementGraph}, which requires runtime {@link ExecutionState}
/// to determine operational status, this graph captures the purely structural
/// instance topology produced by the compiler — which elements are instantiated,
/// how many times, and which instance depends on which other instance.
///
/// ## When to use
///
/// Use this graph whenever you need the instance-level topology without runtime
/// state — for example, to render a preview of the compiled plan in a UI, to
/// validate that the compiler produced the expected fan-out, or to compute
/// static metrics like instance count and maximum parallelism.
///
/// ## Derivation
///
/// The graph is synthesized from the {@link AtomicStep.DeployElement} steps in
/// an execution plan:
///
/// 1. Each unique {@code (elementId, instanceNumber)} pair becomes a node.
/// 2. For each deploy step, a BFS walks the step's {@code dependencies()} list,
///    passing through barriers and non-deploy steps, until upstream deploy steps
///    are found. Each such upstream deploy step yields an instance-level edge.
///
/// This mirrors the algorithm in {@link LiveElementGraph} but omits all runtime
/// state derivation.
///
/// @see ExecutionPlan#elementInstanceGraph()
/// @see LiveElementGraph
///
public interface ElementInstanceGraph {

    ///
    /// A single element instance in the compiled plan.
    ///
    /// @param elementId       the element name (e.g. {@code "cassandra"})
    /// @param instanceNumber  zero-based instance index within the element
    /// @param configuration   the bound parameter configuration for this instance
    ///
    record InstanceNode(
        String elementId,
        int instanceNumber,
        Map<String, Object> configuration
    ) {
        /// Returns the composite node identifier {@code "elementId:instanceNumber"}.
        public String nodeId() {
            return elementId + ":" + instanceNumber;
        }
    }

    ///
    /// A directed dependency edge between two element instances.
    ///
    /// The edge direction follows the dependency direction: the source instance
    /// must be deployed before the target instance can begin deployment.
    ///
    /// @param sourceElementId       element name of the upstream instance
    /// @param sourceInstanceNumber  instance number of the upstream instance
    /// @param targetElementId       element name of the downstream instance
    /// @param targetInstanceNumber  instance number of the downstream instance
    ///
    record InstanceEdge(
        String sourceElementId,
        int sourceInstanceNumber,
        String targetElementId,
        int targetInstanceNumber
    ) {
        /// Returns the composite node ID of the source instance.
        public String sourceNodeId() {
            return sourceElementId + ":" + sourceInstanceNumber;
        }

        /// Returns the composite node ID of the target instance.
        public String targetNodeId() {
            return targetElementId + ":" + targetInstanceNumber;
        }
    }

    /// Returns all instance nodes in the graph.
    ///
    /// @return unmodifiable set of instance nodes in topological order
    Set<InstanceNode> nodes();

    /// Returns all instance-level dependency edges.
    ///
    /// @return unmodifiable list of edges
    List<InstanceEdge> edges();

    /// Returns edges originating from the given element (all instances).
    ///
    /// @param elementId the element name
    /// @return edges where the source is any instance of the given element
    List<InstanceEdge> edgesFrom(String elementId);

    /// Returns edges targeting the given element (all instances).
    ///
    /// @param elementId the element name
    /// @return edges where the target is any instance of the given element
    List<InstanceEdge> edgesTo(String elementId);

    /// Returns the instance nodes in topological order.
    ///
    /// @return unmodifiable list of composite node IDs in dependency order
    List<String> topologicalOrder();

    /// Returns the number of instances for a given element.
    ///
    /// @param elementId the element name
    /// @return number of instances, or 0 if the element has no instances
    int instanceCount(String elementId);

    /// Returns the total number of element instances in the graph.
    ///
    /// @return total instance count
    int totalInstances();
}
