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

import io.nosqlbench.paramodel.engine.planners.reducto.rules.Rule8_TransitiveReduction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/// Unit tests for {@link Rule8_TransitiveReduction}.
class Rule8_TransitiveReductionTest {

    @Test
    @DisplayName("removes transitive edge in A→B→C with direct A→C")
    void removesSimpleTransitiveEdge() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        graph.addEdge(a, b);
        graph.addEdge(b, c);
        graph.addEdge(a, c); // transitive — a→b→c already exists

        new Rule8_TransitiveReduction().apply(graph, null);

        assertThat(a.successors()).containsExactly(b);
        assertThat(a.successors()).doesNotContain(c);
        assertThat(b.successors()).contains(c);
        assertThat(c.predecessors()).containsExactly(b);
    }

    @Test
    @DisplayName("preserves all edges in a diamond (no transitive edges)")
    void preservesDiamondEdges() {
        // A → B, A → C, B → D, C → D — no transitive edges
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.ACTIVATE);
        ReductoNode d = new ReductoNode("d", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);

        graph.addEdge(a, b);
        graph.addEdge(a, c);
        graph.addEdge(b, d);
        graph.addEdge(c, d);

        new Rule8_TransitiveReduction().apply(graph, null);

        assertThat(a.successors()).containsExactlyInAnyOrder(b, c);
        assertThat(d.predecessors()).containsExactlyInAnyOrder(b, c);
    }

    @Test
    @DisplayName("removes multiple transitive edges in longer chain")
    void removesMultipleTransitiveEdges() {
        // A → B → C → D, with direct A→C and A→D
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.DEACTIVATE);
        ReductoNode d = new ReductoNode("d", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);

        graph.addEdge(a, b);
        graph.addEdge(b, c);
        graph.addEdge(c, d);
        graph.addEdge(a, c); // transitive via a→b→c
        graph.addEdge(a, d); // transitive via a→b→c→d

        new Rule8_TransitiveReduction().apply(graph, null);

        assertThat(a.successors()).containsExactly(b);
        assertThat(b.successors()).containsExactly(c);
        assertThat(c.successors()).containsExactly(d);
    }

    @Test
    @DisplayName("single node graph: no-op")
    void singleNodeNoOp() {
        ReductoGraph graph = new ReductoGraph();
        graph.addNode(new ReductoNode("only", ReductoNodeType.START));

        new Rule8_TransitiveReduction().apply(graph, null);

        assertThat(graph.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("linear chain: no transitive edges to remove")
    void linearChainUnchanged() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);

        graph.addEdge(a, b);
        graph.addEdge(b, c);

        new Rule8_TransitiveReduction().apply(graph, null);

        assertThat(a.successors()).containsExactly(b);
        assertThat(b.successors()).containsExactly(c);
    }

    @Test
    @DisplayName("diamond with transitive shortcut: removes shortcut only")
    void diamondWithTransitiveShortcut() {
        // A → B → D, A → C → D, A → D (transitive)
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.ACTIVATE);
        ReductoNode d = new ReductoNode("d", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);

        graph.addEdge(a, b);
        graph.addEdge(a, c);
        graph.addEdge(b, d);
        graph.addEdge(c, d);
        graph.addEdge(a, d); // transitive

        new Rule8_TransitiveReduction().apply(graph, null);

        assertThat(a.successors()).containsExactlyInAnyOrder(b, c);
        assertThat(a.successors()).doesNotContain(d);
        assertThat(d.predecessors()).containsExactlyInAnyOrder(b, c);
    }

    @Test
    @DisplayName("graph remains acyclic after transitive reduction")
    void graphRemainsAcyclic() {
        ReductoGraph graph = new ReductoGraph();
        ReductoNode a = new ReductoNode("a", ReductoNodeType.START);
        ReductoNode b = new ReductoNode("b", ReductoNodeType.ACTIVATE);
        ReductoNode c = new ReductoNode("c", ReductoNodeType.DEACTIVATE);
        ReductoNode d = new ReductoNode("d", ReductoNodeType.END);
        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);

        graph.addEdge(a, b);
        graph.addEdge(a, c);
        graph.addEdge(b, c);
        graph.addEdge(b, d);
        graph.addEdge(c, d);
        graph.addEdge(a, d); // transitive

        new Rule8_TransitiveReduction().apply(graph, null);

        assertThat(graph.hasCycle()).isFalse();
    }
}
