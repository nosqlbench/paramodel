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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/// Unit tests for {@link ReductoGraph} mutable DAG operations.
class ReductoGraphTest {

    @Test
    @DisplayName("addNode and getNode: basic insertion and retrieval")
    void addAndGetNode() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode node = new ReductoNode("n1", ReductoNodeType.ACTIVATE);
        graph.addNode(node);

        assertThat(graph.getNode("n1")).isSameAs(node);
        assertThat(graph.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("addEdge: creates bidirectional predecessor/successor links")
    void addEdgeBidirectional() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.ACTIVATE);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.DEACTIVATE);
        graph.addNode(a);
        graph.addNode(b);

        graph.addEdge(a, b);

        assertThat(a.successors()).contains(b);
        assertThat(b.predecessors()).contains(a);
    }

    @Test
    @DisplayName("removeNode: cleans up all edges")
    void removeNodeCleansEdges() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge(a, b);
        graph.addEdge(b, c);

        graph.removeNode(b);

        assertThat(graph.getNode("b")).isNull();
        assertThat(a.successors()).isEmpty();
        assertThat(c.predecessors()).isEmpty();
        assertThat(graph.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("remapEdgesTo: redirects incoming edges to new target")
    void remapEdgesTo() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode oldTarget = new ReductoNode("old", ReductoNodeType.ACTIVATE);
        ReductoNode newTarget = new ReductoNode("new", ReductoNodeType.ACTIVATE);
        graph.addNode(a);
        graph.addNode(oldTarget);
        graph.addNode(newTarget);
        graph.addEdge(a, oldTarget);

        graph.remapEdgesTo(oldTarget, newTarget);

        assertThat(a.successors()).contains(newTarget);
        assertThat(a.successors()).doesNotContain(oldTarget);
        assertThat(newTarget.predecessors()).contains(a);
        assertThat(oldTarget.predecessors()).isEmpty();
    }

    @Test
    @DisplayName("remapEdgesFrom: redirects outgoing edges to new source")
    void remapEdgesFrom() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode oldSource = new ReductoNode("old", ReductoNodeType.ACTIVATE);
        ReductoNode newSource = new ReductoNode("new", ReductoNodeType.ACTIVATE);
        ReductoNode target = new ReductoNode("target", ReductoNodeType.DEACTIVATE);
        graph.addNode(oldSource);
        graph.addNode(newSource);
        graph.addNode(target);
        graph.addEdge(oldSource, target);

        graph.remapEdgesFrom(oldSource, newSource);

        assertThat(newSource.successors()).contains(target);
        assertThat(oldSource.successors()).isEmpty();
        assertThat(target.predecessors()).contains(newSource);
        assertThat(target.predecessors()).doesNotContain(oldSource);
    }

    @Test
    @DisplayName("roots: returns nodes with no predecessors")
    void rootsComputation() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge(a, b);
        graph.addEdge(b, c);

        Set<String> rootIds = graph.roots().stream().map(ReductoNode::id).collect(Collectors.toSet());
        assertThat(rootIds).containsExactly("a");
    }

    @Test
    @DisplayName("leaves: returns nodes with no successors")
    void leavesComputation() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge(a, b);
        graph.addEdge(b, c);

        Set<String> leafIds = graph.leaves().stream().map(ReductoNode::id).collect(Collectors.toSet());
        assertThat(leafIds).containsExactly("c");
    }

    @Test
    @DisplayName("topologicalOrder: returns valid ordering for linear chain")
    void topologicalOrderLinearChain() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addEdge(a, b);
        graph.addEdge(b, c);

        List<ReductoNode> order = graph.topologicalOrder();
        List<String> ids = order.stream().map(ReductoNode::id).toList();

        assertThat(ids).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("topologicalOrder: diamond dependency produces valid ordering")
    void topologicalOrderDiamond() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode s = new ReductoNode("s", ReductoNodeType.START);
        ReductoNode a = new ReductoNode("a", ReductoNodeType.ACTIVATE);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode e = new ReductoNode("e", ReductoNodeType.END);
        graph.addNode(s);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(e);
        graph.addEdge(s, a);
        graph.addEdge(s, b);
        graph.addEdge(a, e);
        graph.addEdge(b, e);

        List<ReductoNode> order = graph.topologicalOrder();
        List<String> ids = order.stream().map(ReductoNode::id).toList();

        // s must be first, e must be last
        assertThat(ids.getFirst()).isEqualTo("s");
        assertThat(ids.getLast()).isEqualTo("e");
        assertThat(ids).hasSize(4);
    }

    @Test
    @DisplayName("hasCycle: returns false for DAG")
    void noCycleInDag() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        graph.addNode(a);
        graph.addNode(b);
        graph.addEdge(a, b);

        assertThat(graph.hasCycle()).isFalse();
    }

    @Test
    @DisplayName("nodesOfType: filters by node type")
    void nodesOfType() {
        ReductoGraph graph = new ReductoGraph();
        graph.addNode(new ReductoNode("a1", ReductoNodeType.ACTIVATE));
        graph.addNode(new ReductoNode("a2", ReductoNodeType.ACTIVATE));
        graph.addNode(new ReductoNode("d1", ReductoNodeType.DEACTIVATE));

        assertThat(graph.nodesOfType(ReductoNodeType.ACTIVATE)).hasSize(2);
        assertThat(graph.nodesOfType(ReductoNodeType.DEACTIVATE)).hasSize(1);
        assertThat(graph.nodesOfType(ReductoNodeType.START)).isEmpty();
    }

    @Test
    @DisplayName("nodesForElement: filters by element name")
    void nodesForElement() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a1", ReductoNodeType.ACTIVATE);
        a.setElementName("server");
        ReductoNode b = new ReductoNode("a2", ReductoNodeType.ACTIVATE);
        b.setElementName("client");
        graph.addNode(a);
        graph.addNode(b);

        assertThat(graph.nodesForElement("server")).hasSize(1);
        assertThat(graph.nodesForElement("client")).hasSize(1);
        assertThat(graph.nodesForElement("unknown")).isEmpty();
    }
}
