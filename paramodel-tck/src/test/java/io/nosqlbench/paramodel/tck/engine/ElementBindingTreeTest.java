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
package io.nosqlbench.paramodel.tck.engine;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.binding.DefaultElementBindingTree;
import io.nosqlbench.paramodel.engine.binding.DefaultParameterBinder;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.StringParameter;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for {@link DefaultElementBindingTree} and the hierarchical binding cascade.
///
class ElementBindingTreeTest {

    private static final ParameterBinder LENIENT_BINDER =
            new DefaultParameterBinder(BindingPolicy.LENIENT);

    private static final ParameterBinder STRICT_BINDER =
            new DefaultParameterBinder(BindingPolicy.STRICT);

    // ── Single element, no dependencies ─────────────────────────────

    @Test
    void singleElementNoDepsBind() {
        Element storage = MockElement.builder("storage")
                .parameter(IntegerParameter.range("size", 1, 1000))
                .build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("test")
                .elements(List.of(storage))
                .globalInputs(Map.of("region", "us-east-1"))
                .elementInputs("storage", Map.of("size", 100))
                .binder(LENIENT_BINDER)
                .build();

        BindingNode node = tree.node("storage").orElseThrow();
        assertThat(node.binding().toValueMap()).containsEntry("size", 100);
        assertThat(node.cascadedInputs()).containsEntry("region", "us-east-1");
        assertThat(node.cascadedInputs()).containsEntry("size", 100);
        assertThat(node.binding().validationResult().isPassed()).isTrue();
    }

    // ── Linear chain cascade ────────────────────────────────────────

    @Test
    void linearChainCascade() {
        Element a = MockElement.builder("a")
                .parameter(IntegerParameter.range("x", 0, 100))
                .build();
        Element b = MockElement.builder("b")
                .parameter(IntegerParameter.range("y", 0, 100))
                .dependency(a)
                .build();
        Element c = MockElement.builder("c")
                .parameter(IntegerParameter.range("z", 0, 100))
                .dependency(b)
                .build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("chain")
                .elements(List.of(a, b, c))
                .globalInputs(Map.of("g", "global"))
                .elementInputs("a", Map.of("x", 10))
                .elementInputs("b", Map.of("y", 20))
                .elementInputs("c", Map.of("z", 30))
                .binder(LENIENT_BINDER)
                .build();

        BindingNode nodeC = tree.node("c").orElseThrow();
        // C should see global + A's cascade + B's cascade + C's local
        assertThat(nodeC.cascadedInputs()).containsEntry("g", "global");
        assertThat(nodeC.cascadedInputs()).containsEntry("x", 10);
        assertThat(nodeC.cascadedInputs()).containsEntry("y", 20);
        assertThat(nodeC.cascadedInputs()).containsEntry("z", 30);
        assertThat(nodeC.binding().toValueMap()).containsEntry("z", 30);
    }

    // ── Local overrides cascaded ────────────────────────────────────

    @Test
    void localOverridesCascaded() {
        Element parent = MockElement.builder("parent")
                .parameter(IntegerParameter.range("shared", 0, 100))
                .build();
        Element child = MockElement.builder("child")
                .parameter(IntegerParameter.range("shared", 0, 100))
                .dependency(parent)
                .build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("override")
                .elements(List.of(parent, child))
                .elementInputs("parent", Map.of("shared", 10))
                .elementInputs("child", Map.of("shared", 99))
                .binder(LENIENT_BINDER)
                .build();

        assertThat(tree.node("parent").orElseThrow().binding().toValueMap())
                .containsEntry("shared", 10);
        assertThat(tree.node("child").orElseThrow().binding().toValueMap())
                .containsEntry("shared", 99);
        assertThat(tree.node("child").orElseThrow().cascadedInputs())
                .containsEntry("shared", 99);
    }

    // ── Multiple dependencies merge ─────────────────────────────────

    @Test
    void multipleDependenciesMerge() {
        Element d1 = MockElement.builder("d1")
                .parameter(IntegerParameter.range("from_d1", 0, 100))
                .build();
        Element d2 = MockElement.builder("d2")
                .parameter(IntegerParameter.range("from_d2", 0, 100))
                .build();
        Element app = MockElement.builder("app")
                .parameter(IntegerParameter.range("appVal", 0, 100))
                .dependency(d1)
                .dependency(d2)
                .build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("multi")
                .elements(List.of(d1, d2, app))
                .elementInputs("d1", Map.of("from_d1", 1, "overlap", "from-d1"))
                .elementInputs("d2", Map.of("from_d2", 2, "overlap", "from-d2"))
                .binder(LENIENT_BINDER)
                .build();

        BindingNode appNode = tree.node("app").orElseThrow();
        // d2 listed after d1 in dependencies, so d2's cascade overrides d1 for "overlap"
        assertThat(appNode.cascadedInputs()).containsEntry("from_d1", 1);
        assertThat(appNode.cascadedInputs()).containsEntry("from_d2", 2);
        assertThat(appNode.cascadedInputs()).containsEntry("overlap", "from-d2");
    }

    // ── Global inputs reach all nodes ───────────────────────────────

    @Test
    void globalInputsReachAllNodes() {
        Element a = MockElement.of("a");
        Element b = MockElement.builder("b").dependency(a).build();
        Element c = MockElement.builder("c").dependency(b).build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("global")
                .elements(List.of(a, b, c))
                .globalInputs(Map.of("env", "prod"))
                .binder(LENIENT_BINDER)
                .build();

        for (BindingNode node : tree.nodesInOrder()) {
            assertThat(node.cascadedInputs()).containsEntry("env", "prod");
        }
        assertThat(tree.root().cascadedInputs()).containsEntry("env", "prod");
    }

    // ── Passthrough in cascade ──────────────────────────────────────

    @Test
    void passthroughInCascade() {
        Element elem = MockElement.builder("elem")
                .parameter(IntegerParameter.range("known", 0, 100))
                .build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("passthrough")
                .elements(List.of(elem))
                .globalInputs(Map.of("unknown_key", "some-value"))
                .elementInputs("elem", Map.of("known", 42))
                .binder(LENIENT_BINDER)
                .build();

        BindingNode node = tree.node("elem").orElseThrow();
        assertThat(node.binding().toValueMap()).containsEntry("known", 42);
        assertThat(node.binding().passthroughValues()).containsEntry("unknown_key", "some-value");
    }

    // ── Diamond dependency ──────────────────────────────────────────

    @Test
    void diamondDependency() {
        Element d = MockElement.builder("d")
                .parameter(IntegerParameter.range("dVal", 0, 100))
                .build();
        Element b = MockElement.builder("b")
                .parameter(IntegerParameter.range("bVal", 0, 100))
                .dependency(d)
                .build();
        Element c = MockElement.builder("c")
                .parameter(IntegerParameter.range("cVal", 0, 100))
                .dependency(d)
                .build();
        Element a = MockElement.builder("a")
                .parameter(IntegerParameter.range("aVal", 0, 100))
                .dependency(b)
                .dependency(c)
                .build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("diamond")
                .elements(List.of(d, b, c, a))
                .elementInputs("d", Map.of("dVal", 1))
                .elementInputs("b", Map.of("bVal", 2))
                .elementInputs("c", Map.of("cVal", 3))
                .elementInputs("a", Map.of("aVal", 4))
                .binder(LENIENT_BINDER)
                .build();

        BindingNode nodeA = tree.node("a").orElseThrow();
        // A sees d's value through both b and c paths
        assertThat(nodeA.cascadedInputs()).containsEntry("dVal", 1);
        assertThat(nodeA.cascadedInputs()).containsEntry("bVal", 2);
        assertThat(nodeA.cascadedInputs()).containsEntry("cVal", 3);
        assertThat(nodeA.cascadedInputs()).containsEntry("aVal", 4);
    }

    // ── Node depth calculation ──────────────────────────────────────

    @Test
    void nodeDepthCalculation() {
        Element root1 = MockElement.of("root1");
        Element mid = MockElement.builder("mid").dependency(root1).build();
        Element leaf = MockElement.builder("leaf").dependency(mid).build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("depth")
                .elements(List.of(root1, mid, leaf))
                .binder(LENIENT_BINDER)
                .build();

        assertThat(tree.root().depth()).isEqualTo(0);
        assertThat(tree.node("root1").orElseThrow().depth()).isEqualTo(1);
        assertThat(tree.node("mid").orElseThrow().depth()).isEqualTo(2);
        assertThat(tree.node("leaf").orElseThrow().depth()).isEqualTo(3);
    }

    // ── Topological order respected ─────────────────────────────────

    @Test
    void topologicalOrderRespected() {
        Element a = MockElement.of("a");
        Element b = MockElement.builder("b").dependency(a).build();
        Element c = MockElement.builder("c").dependency(a).build();
        Element d = MockElement.builder("d").dependency(b).dependency(c).build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("topo")
                .elements(List.of(d, c, b, a)) // intentionally out of order
                .binder(LENIENT_BINDER)
                .build();

        List<String> names = tree.nodesInOrder().stream()
                .map(BindingNode::name).toList();

        // a must come before b and c; b and c must come before d
        assertThat(names.indexOf("a")).isLessThan(names.indexOf("b"));
        assertThat(names.indexOf("a")).isLessThan(names.indexOf("c"));
        assertThat(names.indexOf("b")).isLessThan(names.indexOf("d"));
        assertThat(names.indexOf("c")).isLessThan(names.indexOf("d"));
    }

    // ── Validation aggregation ──────────────────────────────────────

    @Test
    void validationAggregation() {
        // strict binder with a required param and no input → validation failure
        Element elem = MockElement.builder("elem")
                .parameter(IntegerParameter.range("required", 0, 100))
                .build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("validation")
                .elements(List.of(elem))
                .binder(STRICT_BINDER)
                .build();

        assertThat(tree.validationResult().isFailed()).isTrue();
        assertThat(tree.validationResult().violations()).isNotEmpty();
    }

    // ── Empty element with no parameters ────────────────────────────

    @Test
    void emptyElementNoParams() {
        Element empty = MockElement.of("empty");

        ElementBindingTree tree = DefaultElementBindingTree.builder("empty")
                .elements(List.of(empty))
                .globalInputs(Map.of("k", "v"))
                .binder(LENIENT_BINDER)
                .build();

        BindingNode node = tree.node("empty").orElseThrow();
        assertThat(node.binding().assignments()).isEmpty();
        assertThat(node.binding().validationResult().isPassed()).isTrue();
        assertThat(node.cascadedInputs()).containsEntry("k", "v");
    }

    // ── Node identified by Labels name ──────────────────────────────

    @Test
    void nodeIdentifiedByLabelsName() {
        Element db = MockElement.ofType("database", "service");

        ElementBindingTree tree = DefaultElementBindingTree.builder("identity")
                .elements(List.of(db))
                .binder(LENIENT_BINDER)
                .build();

        BindingNode node = tree.node("database").orElseThrow();
        assertThat(node.name()).isEqualTo("database");
        assertThat(node.element()).isPresent();
        assertThat(node.element().get().name()).isEqualTo("database");
    }

    // ── Root node properties ────────────────────────────────────────

    @Test
    void rootNodeProperties() {
        ElementBindingTree tree = DefaultElementBindingTree.builder("root-test")
                .elements(List.of(MockElement.of("a")))
                .globalInputs(Map.of("g", "val"))
                .binder(LENIENT_BINDER)
                .build();

        BindingNode root = tree.root();
        assertThat(root.isRoot()).isTrue();
        assertThat(root.element()).isEmpty();
        assertThat(root.depth()).isEqualTo(0);
        assertThat(root.name()).isEqualTo("root");
        assertThat(root.cascadedInputs()).containsEntry("g", "val");
        assertThat(root.parents()).isEmpty();
        assertThat(root.binding().assignments()).isEmpty();
    }

    // ── Resolved bindings map ───────────────────────────────────────

    @Test
    void resolvedBindingsMap() {
        Element a = MockElement.builder("a")
                .parameter(IntegerParameter.range("x", 0, 100))
                .build();
        Element b = MockElement.builder("b")
                .parameter(IntegerParameter.range("y", 0, 100))
                .build();

        ElementBindingTree tree = DefaultElementBindingTree.builder("resolved")
                .elements(List.of(a, b))
                .elementInputs("a", Map.of("x", 10))
                .elementInputs("b", Map.of("y", 20))
                .binder(LENIENT_BINDER)
                .build();

        Map<String, ParameterBinding> bindings = tree.resolvedBindings();
        assertThat(bindings).containsKeys("a", "b");
        assertThat(bindings.get("a").toValueMap()).containsEntry("x", 10);
        assertThat(bindings.get("b").toValueMap()).containsEntry("y", 20);
    }

    // ── Labels interface contract ──────────────────────────────────

    @Test
    void labelsInterfaceContract() {
        Element elem = MockElement.ofType("myelem", "service");

        ElementBindingTree tree = DefaultElementBindingTree.builder("tag-test")
                .elements(List.of(elem))
                .binder(LENIENT_BINDER)
                .build();

        // Tree itself
        assertThat(tree.name()).isEqualTo("tag-test");
        assertThat(tree.labels()).containsEntry("name", "tag-test");
        assertThat(tree.labels()).containsEntry("type", "binding-tree");

        // Element node delegates to element labels
        BindingNode node = tree.node("myelem").orElseThrow();
        assertThat(node.labels().get("name")).isEqualTo(node.name());

        // Root node labels
        assertThat(tree.root().labels().get("name")).isEqualTo("root");
        assertThat(tree.root().labels()).containsEntry("type", "binding-root");
    }
}
