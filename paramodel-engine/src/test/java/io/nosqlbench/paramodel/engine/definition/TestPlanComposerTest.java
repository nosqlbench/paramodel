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
package io.nosqlbench.paramodel.engine.definition;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.Element.InstancingScope;
import io.nosqlbench.paramodel.engine.TestPlanService;
import io.nosqlbench.paramodel.engine.plan.DefaultTestPlan;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Integration tests for {@link TestPlanComposer} using worked examples from the spec.
///
/// Assertions use paramodel {@link AtomicStep} types ({@link AtomicStep.DeployElement},
/// {@link AtomicStep.ExecuteTrial}, {@link AtomicStep.TeardownElement},
/// {@link AtomicStep.BarrierSync}) produced by the DefaultCompiler pipeline.
class TestPlanComposerTest {
    private TestPlanDefinitionParser parser;
    private TestPlanComposer composer;

    @BeforeEach
    void setUp() {
        parser = new TestPlanDefinitionParser();
        composer = new TestPlanComposer();
    }

    /// Test: One-shot study (no axes, single trial)
    @Test
    void testOneShotStudy() throws IOException {
        String yaml = """
            name: One-Shot Benchmark
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
                parameters:
                  threads: 4
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // Should have exactly 1 trial
        assertThat(plan.size()).isEqualTo(1);

        // Both elements should be present
        assertThat(plan.elements()).hasSize(2);

        // Both elements should be PER_RUN scope — no axes vary their parameters,
        // so the generic inference deploys each once for the study.
        Element server = plan.element("server").orElseThrow();
        assertThat(server.instancingScope()).hasValue(InstancingScope.PER_RUN);

        Element client = plan.element("client").orElseThrow();
        assertThat(client.instancingScope()).hasValue(InstancingScope.PER_RUN);

        // Execution plan should have deploy steps for both elements
        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();
        assertThat(execPlan.steps()).isNotEmpty();

        long deployCount = deploysFor(execPlan, null);
        assertThat(deployCount).isEqualTo(2); // server + client

        // Should have 1 trial execution step
        long trialCount = execPlan.steps().stream()
                .filter(s -> s instanceof AtomicStep.ExecuteTrial)
                .count();
        assertThat(trialCount).isEqualTo(1);
    }

    /// Test: Fixed server, varied client (classic "fixed server, SHARED" pattern)
    @Test
    void testFixedServerPersist() throws IOException {
        String yaml = """
            name: Fixed Server, Varied Client
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
                parameters:
                  threads: 8
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on:
                  - element: server
                    policy: SHARED
            axes:
              - parameter: dataset
                element: client
                values: [sift-1m, deep-1m, glove-1m]
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // 3 datasets = 3 trials
        assertThat(plan.size()).isEqualTo(3);

        // Server should be PER_RUN scope (not varied)
        Element server = plan.element("server").orElseThrow();
        assertThat(server.instancingScope()).hasValue(InstancingScope.PER_RUN);

        // Server should only be deployed once
        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();
        assertThat(deploysFor(execPlan, "server")).isEqualTo(1);

        // Client should be deployed 3 times (once per trial)
        assertThat(deploysFor(execPlan, "client")).isEqualTo(3);
    }

    /// Test: Server parameter sweep (server varies, client follows)
    @Test
    void testServerParameterSweep() throws IOException {
        String yaml = """
            name: Server Parameter Sweep
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            axes:
              - parameter: threads
                element: server
                values: [1, 2, 4, 8]
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // 4 thread values = 4 trials
        assertThat(plan.size()).isEqualTo(4);

        // Server should be PER_TRIAL scope (has axis)
        Element server = plan.element("server").orElseThrow();
        assertThat(server.instancingScope()).hasValue(InstancingScope.PER_TRIAL);

        // Server should be deployed 4 times
        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();
        assertThat(deploysFor(execPlan, "server")).isEqualTo(4);
    }

    /// Test: Cartesian product of axes
    @Test
    void testCartesianProduct() throws IOException {
        String yaml = """
            name: Cartesian Study
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            axes:
              - parameter: threads
                element: server
                values: [1, 2, 4]
                nesting: 0
              - parameter: memory
                element: server
                values: [1g, 2g]
                nesting: 1
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // 3 threads x 2 memory = 6 trials
        assertThat(plan.size()).isEqualTo(6);

        // Each trial should have server assignments with both parameters
        plan.trials().forEach(trial -> {
            assertThat(trial.assignments()).containsKey("server.threads");
            assertThat(trial.assignments()).containsKey("server.memory");
        });

        // Execution plan should have 6 ExecuteTrial steps
        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();
        long trialSteps = execPlan.steps().stream()
                .filter(s -> s instanceof AtomicStep.ExecuteTrial)
                .count();
        assertThat(trialSteps).isEqualTo(6);
    }

    /// Test: Element dependency chain (A → B → C)
    @Test
    void testDependencyChain() throws IOException {
        String yaml = """
            name: Dependency Chain
            elements:
              - id: database
                type: SERVICE
                image: postgres:latest
              - id: server
                type: SERVICE
                image: api-server:latest
                depends_on: database
              - id: client
                type: COMMAND
                image: test-client:latest
                depends_on: server
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // All elements should be present
        assertThat(plan.elements()).hasSize(3);

        // Execution plan should respect dependency order
        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();

        // Find first deploy step index for each element
        int dbDeployIdx = findDeployIndex(execPlan, "database");
        int serverDeployIdx = findDeployIndex(execPlan, "server");
        int clientDeployIdx = findDeployIndex(execPlan, "client");

        // Order should be: database < server < client
        assertThat(dbDeployIdx).isLessThan(serverDeployIdx);
        assertThat(serverDeployIdx).isLessThan(clientDeployIdx);
    }

    /// Test: Repetitions
    @Test
    void testRepetitions() throws IOException {
        String yaml = """
            name: Repeated Trials
            elements:
              - id: server
                type: SERVICE
                image: server:latest
              - id: client
                type: COMMAND
                image: client:latest
                depends_on: server
            axes:
              - parameter: threads
                element: server
                values: [1, 2]
                repetitions: 3
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // 2 values x 3 repetitions = 6 trials
        assertThat(plan.size()).isEqualTo(6);
    }

    /// Test: PER_RUN scope derivation for service without axes
    @Test
    void testStudyScopeDerivation() throws IOException {
        String yaml = """
            name: Scope Test
            elements:
              - id: shared-db
                type: SERVICE
                image: postgres:latest
              - id: server
                type: SERVICE
                image: server:latest
                depends_on: shared-db
              - id: client
                type: COMMAND
                image: client:latest
                depends_on: server
            axes:
              - parameter: mode
                element: server
                values: [fast, slow]
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // shared-db: no axes, no tainted upstream → PER_RUN scope
        assertThat(plan.element("shared-db").orElseThrow().instancingScope())
                .hasValue(InstancingScope.PER_RUN);

        // server: has axis → PER_TRIAL scope
        assertThat(plan.element("server").orElseThrow().instancingScope())
                .hasValue(InstancingScope.PER_TRIAL);

        // client: COMMAND → always PER_TRIAL scope
        assertThat(plan.element("client").orElseThrow().instancingScope())
                .hasValue(InstancingScope.PER_TRIAL);
    }

    /// Test: Bindings section applies cross-element parameters
    @Test
    void testBindingsApplied() throws IOException {
        String yaml = """
            name: Bindings Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
                exports:
                  service_addr: "${self.ip}:4567"
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            bindings:
              - parameter: BASE_URL
                element: client
                value: "http://${server.service_addr}"
              - parameter: DATASET
                element: client
                value: "cap:1m"
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // Client should have the bindings applied as fixed configuration
        Element client = plan.element("client").orElseThrow();
        assertThat(client.configuration()).containsEntry("BASE_URL", "http://${server.service_addr}");
        assertThat(client.configuration()).containsEntry("DATASET", "cap:1m");
    }

    /// Test: Server deploys match trial count for varied server parameters
    @Test
    void testServerDeployCountMatchesTrialCount() throws IOException {
        String yaml = """
            name: Deploy Count Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            axes:
              - parameter: M
                element: server
                values: [8, 16]
                nesting: 0
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();

        // Server should be deployed 2 times (once per M value)
        assertThat(deploysFor(execPlan, "server")).isEqualTo(2);

        // Client should be deployed 2 times (once per trial)
        assertThat(deploysFor(execPlan, "client")).isEqualTo(2);
    }

    /// Test: Multi-axis server has correct deploy count
    @Test
    void testMultiAxisServerDeployCount() throws IOException {
        String yaml = """
            name: Multi-Axis Deploy Count Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            axes:
              - parameter: M
                element: server
                values: [8, 16]
                nesting: 0
              - parameter: EFC
                element: server
                values: [50, 100]
                nesting: 1
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();

        // 2 x 2 = 4 combinations → 4 server deploys
        assertThat(deploysFor(execPlan, "server")).isEqualTo(4);
    }

    /// Test: Output config is on the element model (element-level concern, not step-level)
    @Test
    void testOutputConfigOnElementModel() throws IOException {
        String yaml = """
            name: Output Config Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
                output:
                  volume: /output
                  format: json
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // Output config should be on the element model via tags
        Element client = plan.element("client").orElseThrow();
        assertThat(client.tags().get("output.volume")).isEqualTo("/output");
        assertThat(client.tags().get("output.format")).isEqualTo("json");
    }

    /// Test: Node role is on the element model
    @Test
    void testNodeRoleOnElementModel() throws IOException {
        String yaml = """
            name: Node Role Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
                node_role: worker
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
                node_role: client-node
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // Node roles are element model concerns, conveyed via tags
        assertThat(plan.element("server").orElseThrow().tags().get("node_role")).isEqualTo("worker");
        assertThat(plan.element("client").orElseThrow().tags().get("node_role")).isEqualTo("client-node");
    }

    /// Test: Multi-phase study with ${output_of:element} references
    @Test
    void testMultiPhaseOutputOfReference() throws IOException {
        String yaml = """
            name: Multi-Phase Study
            elements:
              - id: indexer
                type: COMMAND
                image: indexer:latest
                output:
                  volume: /output
                  format: json
              - id: querier
                type: COMMAND
                image: querier:latest
                depends_on: indexer
                parameters:
                  input: "${output_of:indexer}"
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // Both elements should be present
        assertThat(plan.elements()).hasSize(2);

        // The querier should have the output_of reference in its configuration
        Element querier = plan.element("querier").orElseThrow();
        assertThat(querier.configuration().get("input")).isEqualTo("${output_of:indexer}");

        // Execution plan should deploy both elements
        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();
        assertThat(deploysFor(execPlan, "indexer")).isEqualTo(1);
        assertThat(deploysFor(execPlan, "querier")).isEqualTo(1);
    }

    /// Test: Deploy counts and barriers for INSTANCED_PER with CONCURRENT axis
    @Test
    void testInstancedPerWithConcurrentAxis() throws IOException {
        String yaml = """
            name: INSTANCED_PER Concurrent Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on:
                  - element: server
                    policy: INSTANCED_PER
            axes:
              - parameter: CONC
                element: client
                values: [1, 4, 8]
                mode: CONCURRENT
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // 3 CONC values = 3 trials
        assertThat(plan.size()).isEqualTo(3);

        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();

        // Server deployed once (PER_RUN scope, no axes)
        assertThat(deploysFor(execPlan, "server")).isEqualTo(1);

        // Client deployed 3 times (once per trial)
        assertThat(deploysFor(execPlan, "client")).isEqualTo(3);

        // Should have barrier steps
        long barrierCount = execPlan.steps().stream()
                .filter(s -> s instanceof AtomicStep.BarrierSync)
                .count();
        assertThat(barrierCount).isGreaterThan(0);
    }

    /// Test: Deploy counts for INSTANCED_PER without CONCURRENT axis
    @Test
    void testInstancedPerWithoutConcurrentAxis() throws IOException {
        String yaml = """
            name: INSTANCED_PER Serial Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on:
                  - element: server
                    policy: INSTANCED_PER
            axes:
              - parameter: dataset
                element: client
                values: [sift, deep, glove]
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // 3 datasets = 3 trials
        assertThat(plan.size()).isEqualTo(3);

        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();

        // Server deployed once (PER_RUN scope)
        assertThat(deploysFor(execPlan, "server")).isEqualTo(1);

        // Client deployed 3 times
        assertThat(deploysFor(execPlan, "client")).isEqualTo(3);
    }

    /// Test: PlanPreview includes per-element deploy counts
    @Test
    void testPreviewCostEstimation() throws IOException {
        String yaml = """
            name: Cost Estimation Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
                node_role: worker
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
                node_role: client-node
            axes:
              - parameter: dataset
                element: client
                values: [sift-1m, deep-1m, glove-1m]
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        TestPlanService service = new TestPlanService();
        TestPlanService.PlanPreview preview = service.preview(plan);

        // Basic counts
        assertThat(preview.elementCount()).isEqualTo(2);
        assertThat(preview.axisCount()).isEqualTo(1);
        assertThat(preview.trialCount()).isEqualTo(3);

        // Deploy steps: 1 server + 3 clients = 4
        assertThat(preview.deploySteps()).isEqualTo(4);

        // Trial execution steps: 3
        assertThat(preview.trialSteps()).isEqualTo(3);

        // Teardown steps present
        assertThat(preview.teardownSteps()).isGreaterThan(0);

        // Per-element deploy counts
        assertThat(preview.deploysPerElement()).containsEntry("server", 1);
        assertThat(preview.deploysPerElement()).containsEntry("client", 3);

        // Node role utilization
        assertThat(preview.nodeRoleUsage()).containsEntry("worker", 1);
        assertThat(preview.nodeRoleUsage()).containsEntry("client-node", 3);

        // toString should be readable
        String previewStr = preview.toString();
        assertThat(previewStr).contains("Cost Estimation Test");
        assertThat(previewStr).contains("Deploys per element");
        assertThat(previewStr).contains("Node role utilization");
    }

    /// Test: SHARED savings are computed when scope reuse reduces deploys
    @Test
    void testPreviewSharedSavings() throws IOException {
        String yaml = """
            name: SHARED Savings Test
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on:
                  - element: server
                    policy: SHARED
            axes:
              - parameter: M
                element: server
                values: [8, 16]
                nesting: 0
              - parameter: dataset
                element: client
                values: [sift, deep]
                nesting: 1
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        // 2 M values x 2 datasets = 4 trials
        assertThat(plan.size()).isEqualTo(4);

        TestPlanService service = new TestPlanService();
        TestPlanService.PlanPreview preview = service.preview(plan);

        // Server should be deployed 2 times (once per M, SHARED across datasets)
        assertThat(preview.deploysPerElement().get("server")).isEqualTo(2);

        // Client should be deployed 4 times (once per trial)
        assertThat(preview.deploysPerElement().get("client")).isEqualTo(4);

        // Worst case = 4 (server) + 4 (client) = 8; actual = 2 + 4 = 6; savings = 2
        assertThat(preview.sharedSavings()).isEqualTo(2);

        // toString should mention optimization
        assertThat(preview.toString()).contains("deploys saved via SHARED reuse");
    }

    /// Test: Instance numbers on a server parameter sweep.
    @Test
    void testInstanceNumbersOnServerSweep() throws IOException {
        String yaml = """
            name: Instance Number Sweep
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            axes:
              - parameter: threads
                element: server
                values: [1, 2, 4, 8]
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();

        // Server deploy steps should have instance numbers 0, 1, 2, 3
        var serverDeploys = deploysForElement(execPlan, "server");
        assertThat(serverDeploys).hasSize(4);
        assertThat(serverDeploys.stream().map(AtomicStep.DeployElement::instanceNumber).toList())
                .containsExactly(0, 1, 2, 3);

        // Server teardown steps should have valid instance numbers
        var serverTeardowns = teardownsForElement(execPlan, "server");
        for (AtomicStep.TeardownElement teardown : serverTeardowns) {
            assertThat(teardown.instanceNumber()).isGreaterThanOrEqualTo(0);
            assertThat(teardown.instanceNumber()).isLessThan(4);
        }
    }

    /// Test: PER_RUN-scoped element gets instance 0 on deploy and teardown.
    @Test
    void testStudyScopeInstanceNumberZero() throws IOException {
        String yaml = """
            name: Study Scope Instance
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
                parameters:
                  threads: 4
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();

        // PER_RUN-scoped server should have instance number 0 on deploy
        var serverDeploys = deploysForElement(execPlan, "server");
        assertThat(serverDeploys).hasSize(1);
        assertThat(serverDeploys.getFirst().instanceNumber()).isEqualTo(0);

        // Server teardown should also carry instance number 0
        var serverTeardowns = teardownsForElement(execPlan, "server");
        assertThat(serverTeardowns).isNotEmpty();
        assertThat(serverTeardowns.getFirst().instanceNumber()).isEqualTo(0);
    }

    /// Test: Two elements each start their instance number counters at 0 independently.
    @Test
    void testMultipleElementsIndependentCounters() throws IOException {
        String yaml = """
            name: Independent Counters
            elements:
              - id: database
                type: SERVICE
                image: postgres:latest
              - id: server
                type: SERVICE
                image: jvector:latest
                depends_on: database
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            axes:
              - parameter: threads
                element: server
                values: [1, 2]
                nesting: 0
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();

        // Database (PER_RUN scope) should have instance 0
        var dbDeploys = deploysForElement(execPlan, "database");
        assertThat(dbDeploys).hasSize(1);
        assertThat(dbDeploys.getFirst().instanceNumber()).isEqualTo(0);

        // Server deploys should have instances 0, 1 (independent of database)
        var serverDeploys = deploysForElement(execPlan, "server");
        assertThat(serverDeploys).hasSize(2);
        assertThat(serverDeploys.stream().map(AtomicStep.DeployElement::instanceNumber).toList())
                .containsExactly(0, 1);

        // Client deploys should have instances 0, 1 (independent of server and database)
        var clientDeploys = deploysForElement(execPlan, "client");
        assertThat(clientDeploys).hasSize(2);
        assertThat(clientDeploys.stream().map(AtomicStep.DeployElement::instanceNumber).toList())
                .containsExactly(0, 1);
    }

    /// Test: Execution plan DAG is acyclic
    @Test
    void testExecutionPlanIsAcyclicDag() throws IOException {
        String yaml = """
            name: DAG Test
            elements:
              - id: db
                type: SERVICE
                image: postgres:latest
              - id: app
                type: SERVICE
                image: app:latest
                depends_on: db
              - id: bench
                type: COMMAND
                image: bench:latest
                depends_on: app
            axes:
              - parameter: threads
                element: app
                values: [1, 2, 4]
            """;

        TestPlanDefinition def = parser.parseString(yaml);
        DefaultTestPlan plan = composer.compose(def);

        ExecutionPlan execPlan = plan.getExecutionPlan().orElseThrow();
        assertThat(execPlan.executionGraph()).isNotNull();
        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    // ── Helper methods ─────────────────────────────────────────────────

    /// Returns the number of {@link AtomicStep.DeployElement} steps for the given element,
    /// or for all elements if elementId is null.
    private long deploysFor(ExecutionPlan plan, String elementId) {
        return plan.steps().stream()
                .filter(s -> s instanceof AtomicStep.DeployElement d
                        && (elementId == null || d.elementId().equals(elementId)))
                .count();
    }

    /// Returns all {@link AtomicStep.DeployElement} steps for the given element in order.
    private List<AtomicStep.DeployElement> deploysForElement(ExecutionPlan plan, String elementId) {
        return plan.steps().stream()
                .filter(s -> s instanceof AtomicStep.DeployElement d && d.elementId().equals(elementId))
                .map(AtomicStep.DeployElement.class::cast)
                .toList();
    }

    /// Returns all {@link AtomicStep.TeardownElement} steps for the given element in order.
    private List<AtomicStep.TeardownElement> teardownsForElement(ExecutionPlan plan, String elementId) {
        return plan.steps().stream()
                .filter(s -> s instanceof AtomicStep.TeardownElement t && t.elementId().equals(elementId))
                .map(AtomicStep.TeardownElement.class::cast)
                .toList();
    }

    /// Finds the index of the first {@link AtomicStep.DeployElement} step for the given element.
    private int findDeployIndex(ExecutionPlan plan, String elementId) {
        List<AtomicStep> steps = plan.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i) instanceof AtomicStep.DeployElement d && d.elementId().equals(elementId)) {
                return i;
            }
        }
        return -1;
    }
}
