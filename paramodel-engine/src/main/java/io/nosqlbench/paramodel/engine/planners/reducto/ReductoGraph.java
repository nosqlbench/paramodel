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

import java.util.*;

///
/// Mutable directed acyclic graph (DAG) for the reducto step planner.
///
/// Provides bidirectional edge tracking through {@link ReductoNode#predecessors()}
/// and {@link ReductoNode#successors()} sets. All edge mutations are routed through
/// this class to keep both sides consistent.
///
public final class ReductoGraph {

    private final Map<String, ReductoNode> nodes = new LinkedHashMap<>();

    /// Adds a node to the graph.
    ///
    /// @param node the node to add
    /// @throws IllegalArgumentException if a node with the same ID already exists
    public void addNode(ReductoNode node) {
        if (nodes.containsKey(node.id())) {
            throw new IllegalArgumentException("Duplicate node ID: " + node.id());
        }
        nodes.put(node.id(), node);
    }

    /// Removes a node and cleans up all edges referencing it.
    ///
    /// @param node the node to remove
    public void removeNode(ReductoNode node) {
        for (ReductoNode pred : new ArrayList<>(node.predecessors())) {
            pred.successors().remove(node);
        }
        for (ReductoNode succ : new ArrayList<>(node.successors())) {
            succ.predecessors().remove(node);
        }
        node.predecessors().clear();
        node.successors().clear();
        nodes.remove(node.id());
    }

    /// Adds a directed edge from {@code from} to {@code to}.
    ///
    /// @param from source node
    /// @param to   target node
    public void addEdge(ReductoNode from, ReductoNode to) {
        from.successors().add(to);
        to.predecessors().add(from);
    }

    /// Removes a directed edge from {@code from} to {@code to}.
    ///
    /// @param from source node
    /// @param to   target node
    public void removeEdge(ReductoNode from, ReductoNode to) {
        from.successors().remove(to);
        to.predecessors().remove(from);
    }

    /// Remaps all incoming edges that target {@code oldTarget} to point at {@code newTarget}.
    ///
    /// After this call, every node that had an edge to {@code oldTarget} now has an
    /// edge to {@code newTarget} instead. Existing edges of {@code oldTarget} as a
    /// predecessor are cleaned up.
    ///
    /// @param oldTarget the original target node
    /// @param newTarget the replacement target node
    public void remapEdgesTo(ReductoNode oldTarget, ReductoNode newTarget) {
        for (ReductoNode pred : new ArrayList<>(oldTarget.predecessors())) {
            pred.successors().remove(oldTarget);
            pred.successors().add(newTarget);
            newTarget.predecessors().add(pred);
        }
        oldTarget.predecessors().clear();
    }

    /// Remaps all outgoing edges from {@code oldSource} to originate from {@code newSource}.
    ///
    /// @param oldSource the original source node
    /// @param newSource the replacement source node
    public void remapEdgesFrom(ReductoNode oldSource, ReductoNode newSource) {
        for (ReductoNode succ : new ArrayList<>(oldSource.successors())) {
            succ.predecessors().remove(oldSource);
            succ.predecessors().add(newSource);
            newSource.successors().add(succ);
        }
        oldSource.successors().clear();
    }

    /// Returns the node with the given ID, or {@code null} if not found.
    ///
    /// @param id node ID
    /// @return the node, or null
    public ReductoNode getNode(String id) {
        return nodes.get(id);
    }

    /// Returns all nodes in insertion order.
    ///
    /// @return unmodifiable collection of nodes
    public Collection<ReductoNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /// Returns the number of nodes in the graph.
    public int size() {
        return nodes.size();
    }

    /// Returns all root nodes (nodes with no predecessors).
    ///
    /// @return list of root nodes
    public List<ReductoNode> roots() {
        List<ReductoNode> roots = new ArrayList<>();
        for (ReductoNode node : nodes.values()) {
            if (node.predecessors().isEmpty()) {
                roots.add(node);
            }
        }
        return roots;
    }

    /// Returns all leaf nodes (nodes with no successors).
    ///
    /// @return list of leaf nodes
    public List<ReductoNode> leaves() {
        List<ReductoNode> leaves = new ArrayList<>();
        for (ReductoNode node : nodes.values()) {
            if (node.successors().isEmpty()) {
                leaves.add(node);
            }
        }
        return leaves;
    }

    /// Returns a topological ordering of all nodes using Kahn's algorithm.
    ///
    /// @return topologically sorted list
    /// @throws IllegalStateException if the graph contains a cycle
    public List<ReductoNode> topologicalOrder() {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (ReductoNode node : nodes.values()) {
            inDegree.put(node.id(), node.predecessors().size());
        }
        Queue<ReductoNode> queue = new LinkedList<>();
        for (ReductoNode node : nodes.values()) {
            if (inDegree.get(node.id()) == 0) {
                queue.add(node);
            }
        }
        List<ReductoNode> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            ReductoNode node = queue.poll();
            result.add(node);
            for (ReductoNode succ : node.successors()) {
                int newDegree = inDegree.get(succ.id()) - 1;
                inDegree.put(succ.id(), newDegree);
                if (newDegree == 0) {
                    queue.add(succ);
                }
            }
        }
        if (result.size() != nodes.size()) {
            throw new IllegalStateException("Graph contains a cycle; topological sort incomplete ("
                + result.size() + " of " + nodes.size() + " nodes sorted)");
        }
        return result;
    }

    /// Returns {@code true} if the graph contains a cycle.
    ///
    /// @return true if cyclic
    public boolean hasCycle() {
        try {
            topologicalOrder();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /// Returns all nodes matching the given type.
    ///
    /// @param type node type to filter by
    /// @return list of matching nodes
    public List<ReductoNode> nodesOfType(ReductoNodeType type) {
        List<ReductoNode> result = new ArrayList<>();
        for (ReductoNode node : nodes.values()) {
            if (node.type() == type) {
                result.add(node);
            }
        }
        return result;
    }

    /// Returns all nodes associated with the given element name.
    ///
    /// @param elementName element name
    /// @return list of matching nodes
    public List<ReductoNode> nodesForElement(String elementName) {
        List<ReductoNode> result = new ArrayList<>();
        for (ReductoNode node : nodes.values()) {
            if (elementName.equals(node.elementName())) {
                result.add(node);
            }
        }
        return result;
    }

    /// Returns all nodes associated with the given trial index.
    ///
    /// @param trialIndex trial index
    /// @return list of matching nodes
    public List<ReductoNode> nodesForTrial(int trialIndex) {
        List<ReductoNode> result = new ArrayList<>();
        for (ReductoNode node : nodes.values()) {
            if (node.trialIndex() == trialIndex) {
                result.add(node);
            }
        }
        return result;
    }
}
