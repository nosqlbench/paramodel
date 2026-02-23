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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DefaultLiveElementGraphTest {

    // --- helpers ---

    private static AtomicStep.DeployElement deploy(String elementId, int instanceNumber,
                                                    String... deps) {
        return new AtomicStep.DeployElement(
            "deploy-" + elementId + "-" + instanceNumber,
            elementId, instanceNumber, Map.of(),
            List.of(deps),
            Optional.of(Duration.ofSeconds(5)),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }

    private static AtomicStep.TeardownElement teardown(String elementId, int instanceNumber,
                                                        String... deps) {
        return new AtomicStep.TeardownElement(
            "teardown-" + elementId + "-" + instanceNumber,
            elementId, instanceNumber, true,
            List.of(deps),
            Optional.of(Duration.ofSeconds(2)),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }

    private static AtomicStep.BarrierSync barrier(String id, String... deps) {
        return new AtomicStep.BarrierSync(
            id, id, List.of(deps),
            Optional.of(Duration.ofSeconds(1)),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }

    private static LiveElementGraph elementGraph(List<AtomicStep> steps, ExecutionState state) {
        ExecutionPlan plan = new StubPlan(steps);
        return DefaultLiveElementGraph.create(plan, state);
    }

    // --- tests ---

    @Test
    @DisplayName("Empty state: all elements are INACTIVE with PENDING edges")
    void emptyState() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1, "deploy-A-1");

        LiveElementGraph graph = elementGraph(
            List.of(deployA, deployB), ExecutionState.empty());

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.node("A").operationalState())
            .isEqualTo(Element.OperationalState.INACTIVE);
        assertThat(graph.node("B").operationalState())
            .isEqualTo(Element.OperationalState.INACTIVE);

        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().getFirst().dependencyStatus())
            .isEqualTo(ElementEdgeStatus.PENDING);

        assertThat(graph.node("A").isDeployed()).isFalse();
        assertThat(graph.node("B").isDeployed()).isFalse();
    }

    @Test
    @DisplayName("Single element deployed: node reflects READY state")
    void singleElementDeployed() {
        AtomicStep.DeployElement deployA = deploy("A", 1);

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.READY)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA), state);

        assertThat(graph.node("A").operationalState())
            .isEqualTo(Element.OperationalState.READY);
        assertThat(graph.node("A").isDeployed()).isTrue();
        assertThat(graph.node("A").isTornDown()).isFalse();
    }

    @Test
    @DisplayName("Chain A → B: edge SATISFIED when A is READY")
    void chainEdgeSatisfied() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1, "deploy-A-1");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.READY)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA, deployB), state);

        assertThat(graph.edges()).hasSize(1);
        LiveElementGraph.ElementEdge edge = graph.edges().getFirst();
        assertThat(edge.sourceElementId()).isEqualTo("A");
        assertThat(edge.sourceInstanceNumber()).isEqualTo(1);
        assertThat(edge.targetElementId()).isEqualTo("B");
        assertThat(edge.targetInstanceNumber()).isEqualTo(1);
        assertThat(edge.dependencyStatus()).isEqualTo(ElementEdgeStatus.SATISFIED);
    }

    @Test
    @DisplayName("Teardown: node isTornDown is true")
    void teardownReflected() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.TeardownElement teardownA = teardown("A", 1, "deploy-A-1");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1", "teardown-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.TERMINATED)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA, teardownA), state);

        assertThat(graph.node("A").isTornDown()).isTrue();
        assertThat(graph.node("A").isDeployed()).isFalse();
        assertThat(graph.node("A").operationalState())
            .isEqualTo(Element.OperationalState.TERMINATED);
    }

    @Test
    @DisplayName("Failure: edge FAILED when upstream element is FAILED")
    void failureEdge() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1, "deploy-A-1");

        ExecutionState state = new ImmutableExecutionState(
            Set.of(), Set.of("deploy-A-1"), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.FAILED)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA, deployB), state);

        assertThat(graph.edges().getFirst().dependencyStatus())
            .isEqualTo(ElementEdgeStatus.FAILED);
        assertThat(graph.hasFailures()).isTrue();
    }

    @Test
    @DisplayName("Progress metrics are correct")
    void progressMetrics() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1, "deploy-A-1");
        AtomicStep.DeployElement deployC = deploy("C", 1);

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1", "deploy-B-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.READY,
                   "B", Element.OperationalState.RUNNING)
        );

        LiveElementGraph graph = elementGraph(
            List.of(deployA, deployB, deployC), state);

        LiveElementGraph.ElementProgress progress = graph.progress();
        assertThat(progress.totalInstances()).isEqualTo(3);
        assertThat(progress.ready()).isEqualTo(1);
        assertThat(progress.running()).isEqualTo(1);
        assertThat(progress.inactive()).isEqualTo(1);
        assertThat(progress.deployed()).isEqualTo(2);
    }

    @Test
    @DisplayName("Topological order returns nodeId strings for chain A → B → C")
    void topologicalOrderChain() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1, "deploy-A-1");
        AtomicStep.DeployElement deployC = deploy("C", 1, "deploy-B-1");

        LiveElementGraph graph = elementGraph(
            List.of(deployA, deployB, deployC), ExecutionState.empty());

        List<String> order = graph.topologicalOrder();
        assertThat(order).containsExactly("A:1", "B:1", "C:1");
    }

    @Test
    @DisplayName("Multiple instances: each instance is a separate node")
    void multipleInstances() {
        AtomicStep.DeployElement deploy1 = deploy("A", 1);
        AtomicStep.DeployElement deploy2 = deploy("A", 2);

        LiveElementGraph graph = elementGraph(
            List.of(deploy1, deploy2), ExecutionState.empty());

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.node("A", 1).instanceNumber()).isEqualTo(1);
        assertThat(graph.node("A", 2).instanceNumber()).isEqualTo(2);
        assertThat(graph.node("A", 1).nodeId()).isEqualTo("A:1");
        assertThat(graph.node("A", 2).nodeId()).isEqualTo("A:2");

        // node(elementId) should throw because it's ambiguous
        assertThatThrownBy(() -> graph.node("A"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ambiguous");
    }

    @Test
    @DisplayName("Multiple instances with different states")
    void multipleInstancesDifferentStates() {
        AtomicStep.DeployElement deploy1 = deploy("A", 1);
        AtomicStep.DeployElement deploy2 = deploy("A", 2);

        // Instance 1 deployed, instance 2 still inactive
        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.READY)
        );

        LiveElementGraph graph = elementGraph(List.of(deploy1, deploy2), state);

        assertThat(graph.node("A", 1).operationalState())
            .isEqualTo(Element.OperationalState.READY);
        assertThat(graph.node("A", 1).isDeployed()).isTrue();

        assertThat(graph.node("A", 2).operationalState())
            .isEqualTo(Element.OperationalState.INACTIVE);
        assertThat(graph.node("A", 2).isDeployed()).isFalse();
    }

    @Test
    @DisplayName("Per-instance state derivation: PROVISIONING when deploy in-flight")
    void provisioningState() {
        AtomicStep.DeployElement deployA = deploy("A", 1);

        ExecutionState state = new ImmutableExecutionState(
            Set.of(), Set.of(), Set.of(), Set.of("deploy-A-1"),
            Set.of(), Set.of(),
            Map.of()
        );

        LiveElementGraph graph = elementGraph(List.of(deployA), state);

        assertThat(graph.node("A").operationalState())
            .isEqualTo(Element.OperationalState.PROVISIONING);
    }

    @Test
    @DisplayName("Per-instance state derivation: STOPPING when teardown in-flight")
    void stoppingState() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.TeardownElement teardownA = teardown("A", 1, "deploy-A-1");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of("teardown-A-1"),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.READY)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA, teardownA), state);

        assertThat(graph.node("A").operationalState())
            .isEqualTo(Element.OperationalState.STOPPING);
    }

    @Test
    @DisplayName("Per-instance state derivation: READY fallback when elementStates empty")
    void readyFallbackWhenNoElementStates() {
        AtomicStep.DeployElement deployA = deploy("A", 1);

        // Deploy completed but elementStates is empty (SimulatingSteppingHandle)
        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of()
        );

        LiveElementGraph graph = elementGraph(List.of(deployA), state);

        assertThat(graph.node("A").operationalState())
            .isEqualTo(Element.OperationalState.READY);
        assertThat(graph.node("A").isDeployed()).isTrue();
    }

    @Test
    @DisplayName("edgesFrom and edgesTo return correct subsets")
    void edgeIndexes() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1, "deploy-A-1");
        AtomicStep.DeployElement deployC = deploy("C", 1, "deploy-A-1");

        LiveElementGraph graph = elementGraph(
            List.of(deployA, deployB, deployC), ExecutionState.empty());

        assertThat(graph.edgesFrom("A")).hasSize(2);
        assertThat(graph.edgesTo("B")).hasSize(1);
        assertThat(graph.edgesTo("C")).hasSize(1);
        assertThat(graph.edgesFrom("B")).isEmpty();
    }

    @Test
    @DisplayName("nodesByState returns filtered nodes")
    void nodesByState() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1);

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.READY)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA, deployB), state);

        assertThat(graph.nodesByState(Element.OperationalState.READY)).hasSize(1);
        assertThat(graph.nodesByState(Element.OperationalState.INACTIVE)).hasSize(1);
    }

    @Test
    @DisplayName("activeNodes returns deployed, non-torn-down elements")
    void activeNodes() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1);
        AtomicStep.TeardownElement teardownA = teardown("A", 1, "deploy-A-1");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1", "deploy-B-1", "teardown-A-1"),
            Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.TERMINATED,
                   "B", Element.OperationalState.READY)
        );

        LiveElementGraph graph = elementGraph(
            List.of(deployA, deployB, teardownA), state);

        assertThat(graph.activeNodes()).hasSize(1);
        assertThat(graph.activeNodes().iterator().next().elementId()).isEqualTo("B");
    }

    @Test
    @DisplayName("isComplete when all elements are TERMINATED or FAILED")
    void isCompleteAllTerminal() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1);

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of("deploy-B-1"), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.TERMINATED,
                   "B", Element.OperationalState.FAILED)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA, deployB), state);
        assertThat(graph.isComplete()).isTrue();
    }

    @Test
    @DisplayName("isComplete false when elements still active")
    void isCompleteNotYet() {
        AtomicStep.DeployElement deployA = deploy("A", 1);

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.RUNNING)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA), state);
        assertThat(graph.isComplete()).isFalse();
    }

    @Test
    @DisplayName("Edge through barrier: transitive dependency inferred")
    void edgeThroughBarrier() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.BarrierSync barrierStep = barrier("barrier-1", "deploy-A-1");
        AtomicStep.DeployElement deployB = deploy("B", 1, "barrier-1");

        LiveElementGraph graph = elementGraph(
            List.of(deployA, barrierStep, deployB), ExecutionState.empty());

        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().getFirst().sourceElementId()).isEqualTo("A");
        assertThat(graph.edges().getFirst().sourceInstanceNumber()).isEqualTo(1);
        assertThat(graph.edges().getFirst().targetElementId()).isEqualTo("B");
        assertThat(graph.edges().getFirst().targetInstanceNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("TERMINATED edge status when upstream is TERMINATED")
    void terminatedEdge() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1, "deploy-A-1");

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.TERMINATED)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA, deployB), state);
        assertThat(graph.edges().getFirst().dependencyStatus())
            .isEqualTo(ElementEdgeStatus.TERMINATED);
    }

    @Test
    @DisplayName("Empty plan yields empty graph")
    void emptyPlan() {
        LiveElementGraph graph = elementGraph(List.of(), ExecutionState.empty());

        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.edges()).isEmpty();
        assertThat(graph.topologicalOrder()).isEmpty();
        assertThat(graph.isComplete()).isTrue();
        assertThat(graph.hasFailures()).isFalse();
    }

    @Test
    @DisplayName("node() throws for unknown element ID")
    void unknownNodeThrows() {
        LiveElementGraph graph = elementGraph(List.of(), ExecutionState.empty());

        assertThatThrownBy(() -> graph.node("nonexistent"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("node(elementId, instanceNumber) throws for unknown instance")
    void unknownInstanceThrows() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        LiveElementGraph graph = elementGraph(List.of(deployA), ExecutionState.empty());

        assertThatThrownBy(() -> graph.node("A", 99))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Deployment percentage computed correctly")
    void deploymentPercentage() {
        AtomicStep.DeployElement deployA = deploy("A", 1);
        AtomicStep.DeployElement deployB = deploy("B", 1);

        ExecutionState state = new ImmutableExecutionState(
            Set.of("deploy-A-1"), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(),
            Map.of("A", Element.OperationalState.READY)
        );

        LiveElementGraph graph = elementGraph(List.of(deployA, deployB), state);
        assertThat(graph.progress().deploymentPercentage()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Instance-qualified edges for multiple instances of different elements")
    void instanceQualifiedEdges() {
        AtomicStep.DeployElement deployA1 = deploy("A", 1);
        AtomicStep.DeployElement deployA2 = deploy("A", 2);
        AtomicStep.DeployElement deployB1 = deploy("B", 1, "deploy-A-1");
        AtomicStep.DeployElement deployB2 = deploy("B", 2, "deploy-A-2");

        LiveElementGraph graph = elementGraph(
            List.of(deployA1, deployA2, deployB1, deployB2), ExecutionState.empty());

        assertThat(graph.nodes()).hasSize(4);
        assertThat(graph.edges()).hasSize(2);

        // Edge A:1 -> B:1
        LiveElementGraph.ElementEdge edge1 = graph.edges().stream()
            .filter(e -> e.sourceInstanceNumber() == 1 && e.targetInstanceNumber() == 1)
            .findFirst().orElseThrow();
        assertThat(edge1.sourceNodeId()).isEqualTo("A:1");
        assertThat(edge1.targetNodeId()).isEqualTo("B:1");

        // Edge A:2 -> B:2
        LiveElementGraph.ElementEdge edge2 = graph.edges().stream()
            .filter(e -> e.sourceInstanceNumber() == 2 && e.targetInstanceNumber() == 2)
            .findFirst().orElseThrow();
        assertThat(edge2.sourceNodeId()).isEqualTo("A:2");
        assertThat(edge2.targetNodeId()).isEqualTo("B:2");
    }

    /// Minimal stub ExecutionPlan for element graph tests.
    private static class StubPlan implements ExecutionPlan {
        private final List<AtomicStep> steps;
        private final ExecutionGraph graph;

        StubPlan(List<AtomicStep> steps) {
            this.steps = List.copyOf(steps);
            this.graph = new DefaultExecutionGraph(steps);
        }

        @Override public String id() { return "test-plan"; }
        @Override public String testPlanFingerprint() { return "test-fp"; }
        @Override public List<AtomicStep> steps() { return steps; }
        @Override public List<String> trialElements() { return List.of(); }
        @Override public List<Barrier> barriers() { return List.of(); }
        @Override public ExecutionGraph executionGraph() { return graph; }
        @Override public TrialOrdering trialOrdering() { throw new UnsupportedOperationException(); }
        @Override public Optional<Duration> estimatedDuration() { return Optional.empty(); }
        @Override public int estimatedMaxParallelism() { return 1; }
        @Override public ResourceRequirements resourceRequirements() {
            return new ResourceRequirements(0, 0, 0, 0, Map.of());
        }
        @Override public Optional<CheckpointStrategy> checkpointStrategy() { return Optional.empty(); }
        @Override public Optional<Checkpoint> latestCheckpoint() { return Optional.empty(); }
        @Override public List<Checkpoint> checkpoints() { return List.of(); }
        @Override public ExecutionResults execute() { throw new UnsupportedOperationException(); }
        @Override public ExecutionResults execute(ExecutionObserver o) { throw new UnsupportedOperationException(); }
        @Override public ExecutionResults executeWithCheckpoints(Duration d) { throw new UnsupportedOperationException(); }
        @Override public ExecutionPlan resumeFrom(Checkpoint c) { throw new UnsupportedOperationException(); }
        @Override public ExecutionPlan withMaxConcurrency(int m) { throw new UnsupportedOperationException(); }
        @Override public ElementInstanceGraph elementInstanceGraph() { throw new UnsupportedOperationException(); }
        @Override public ExecutionPlanMetadata metadata() { throw new UnsupportedOperationException(); }
    }
}
