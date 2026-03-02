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

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.compiler.DefaultCompiler;
import io.nosqlbench.paramodel.engine.compiler.StepGenerationStage;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.StringParameter;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/// Integration tests for the reducto step generation strategy.
///
/// Tests the full pipeline from test plan construction through the
/// reducto strategy to execution plan output.
class ReductoIntegrationTest {

    @Test
    @DisplayName("Worked example: 3×2×3 = 18 trials, coalesced service deploys")
    void workedExample3x2x3() {
        // From reducto.md: element a (service) with param_x[3] and param_y[2],
        // element b (command trial) with param_u[3], b depends on a (SHARED).
        var paramX = IntegerParameter.range("param_x", 1, 3);
        var paramY = IntegerParameter.range("param_y", 10, 20);
        Element a = MockElement.builder("a")
            .parameter(paramX)
            .parameter(paramY)
            .build();

        var paramU = StringParameter.of("param_u");
        Element b = MockElement.builder("b")
            .parameter(paramU)
            .dependency(a)
            .build();

        var axisX = MockAxis.of("param_x", 1, 2, 3);
        var axisY = MockAxis.of("param_y", 10, 20);
        var axisU = MockAxis.of("param_u", "asm", "dra", "ghi");

        TestPlan plan = MockTestPlan.builder()
            .name("worked-example-3x2x3")
            .axis(axisX)
            .axis(axisY)
            .axis(axisU)
            .element(a)
            .element(b)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // Element a is concretely bound at level 2 (param_x, param_y):
        // 3×2 = 6 group-level activate/deactivate pairs
        long aDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("a"))
            .count();
        assertThat(aDeploys).isEqualTo(6);

        // Element b is a trial element: 18 per-trial activate instances
        long bDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("b"))
            .count();
        assertThat(bDeploys).isEqualTo(18);

        // b has SERVICE semantics (MockElement default), so it gets TeardownElement steps
        long bTeardowns = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("b"))
            .count();
        assertThat(bTeardowns).isEqualTo(18);

        // DAG should be acyclic
        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Single element, single axis: no coalescing needed")
    void singleElementSingleAxis() {
        var paramT = IntegerParameter.range("threads", 1, 8);
        Element svc = MockElement.builder("svc")
            .parameter(paramT)
            .build();

        var axis = MockAxis.of("threads", 1, 2, 4, 8);

        TestPlan plan = MockTestPlan.builder()
            .name("single-elem-reducto")
            .axis(axis)
            .element(svc)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // svc is the only element and also the trial element;
        // trial elements are not coalesced, so 4 deploys
        long deploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("svc"))
            .count();
        assertThat(deploys).isEqualTo(4);

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Run-scoped element: deployed once for entire run")
    void runScopedElement() {
        Element db = MockElement.of("db");
        var paramM = IntegerParameter.range("mode", 1, 2);
        Element app = MockElement.builder("app")
            .parameter(paramM)
            .dependency(db)
            .build();

        var axis = MockAxis.of("mode", 1, 2, 3);

        TestPlan plan = MockTestPlan.builder()
            .name("run-scoped-reducto")
            .axis(axis)
            .element(db)
            .element(app)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // db has no varying parameters → run-scoped, deployed once
        long dbDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("db"))
            .count();
        assertThat(dbDeploys).isEqualTo(1);

        // db should have at least 1 teardown
        long dbTeardowns = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("db"))
            .count();
        assertThat(dbTeardowns).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Run-scoped element with EXCLUSIVE command: exactly one deploy and one teardown")
    void runScopedExclusiveCommandSingleDeployTeardown() {
        // Regression: run-scoped node with an EXCLUSIVE command dependent across
        // multiple trials must produce exactly 1 deploy and 1 teardown for the node.
        // Rule 3 coalescing must remove the first trial's deactivate node; previously
        // the loop skipped it, leaving a spurious deactivate_node_t0 alongside the
        // correct group-level deactivate_node_tN.
        Element node = MockElement.of("node");
        var paramDs = StringParameter.of("dataset");
        Element cmd = MockElement.builder("cmd")
            .parameter(paramDs)
            .dependency(node, RelationshipType.EXCLUSIVE)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        var axis = MockAxis.of("dataset", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8");

        TestPlan plan = MockTestPlan.builder()
            .name("run-scoped-exclusive-cmd")
            .axis(axis)
            .element(node)
            .element(cmd)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        long nodeDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("node"))
            .count();
        assertThat(nodeDeploys).as("run-scoped node should be deployed exactly once").isEqualTo(1);

        long nodeTeardowns = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("node"))
            .count();
        assertThat(nodeTeardowns).as("run-scoped node should be torn down exactly once").isEqualTo(1);

        // cmd is a trial element with COMMAND semantics: 8 deploys, 8 awaits
        long cmdDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("cmd"))
            .count();
        assertThat(cmdDeploys).isEqualTo(8);

        long cmdAwaits = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.AwaitElement ae && ae.elementId().equals("cmd"))
            .count();
        assertThat(cmdAwaits).isEqualTo(8);

        // With EXCLUSIVE deps, trials are sequential: notify_end_0 → notify_start_1 → ...
        // So the node teardown's direct dependency on notify_trial_end_7 transitively
        // covers all earlier notify_ends (Rule 8 removes the redundant transitive edges).
        AtomicStep nodeTeardownStep = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("node"))
            .findFirst().orElseThrow();
        assertThat(nodeTeardownStep.dependencies())
            .as("node teardown should directly depend on the last notify_trial_end")
            .contains("notify_trial_end_7");

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Run-scoped SHARED node teardown depends on all notify_trial_ends")
    void runScopedSharedTeardownDependsOnAllNotifyEnds() {
        // Mirrors the user-reported issue: run-scoped node with SHARED command dependent.
        // 8 trials, the node teardown must depend on all 8 notify_trial_end nodes.
        Element node = MockElement.of("node");
        var paramDs = StringParameter.of("dataset");
        Element cmd = MockElement.builder("cmd")
            .parameter(paramDs)
            .dependency(node)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        var axis = MockAxis.of("dataset", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8");

        TestPlan plan = MockTestPlan.builder()
            .name("run-scoped-shared-all-notify-ends")
            .axis(axis)
            .element(node)
            .element(cmd)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // node teardown must depend on ALL 8 notify_trial_end steps
        AtomicStep nodeTeardown = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("node"))
            .findFirst().orElseThrow();
        for (int t = 0; t < 8; t++) {
            assertThat(nodeTeardown.dependencies())
                .as("node teardown should depend on notify_trial_end_%d", t)
                .contains("notify_trial_end_" + t);
        }

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Run-scoped element with SHARED dep: exactly one deploy and one teardown")
    void runScopedSharedExactlyOneTeardown() {
        // Strengthens the existing runScopedElement test to assert exactly 1 teardown.
        Element db = MockElement.of("db");
        var paramM = IntegerParameter.range("mode", 1, 2);
        Element app = MockElement.builder("app")
            .parameter(paramM)
            .dependency(db)
            .build();

        var axis = MockAxis.of("mode", 1, 2, 3);

        TestPlan plan = MockTestPlan.builder()
            .name("run-scoped-exact-teardown")
            .axis(axis)
            .element(db)
            .element(app)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        long dbDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("db"))
            .count();
        assertThat(dbDeploys).isEqualTo(1);

        long dbTeardowns = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("db"))
            .count();
        assertThat(dbTeardowns).as("run-scoped element should have exactly one teardown").isEqualTo(1);

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("DEDICATED target: per-trial deactivations each depend on their notify_trial_end")
    void dedicatedTargetPerTrialDeactivationsDependOnNotifyEnd() {
        // Regression: DEDICATED targets aren't coalesced (owner is trial element),
        // so they have per-trial deactivation nodes. Each must depend on its
        // corresponding notify_trial_end, not be orphaned.
        Element node = MockElement.of("node");
        var paramDs = StringParameter.of("dataset");
        Element cmd = MockElement.builder("cmd")
            .parameter(paramDs)
            .dependency(node, RelationshipType.DEDICATED)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        var axis = MockAxis.of("dataset", "d1", "d2", "d3", "d4");

        TestPlan plan = MockTestPlan.builder()
            .name("dedicated-per-trial-deactivation")
            .axis(axis)
            .element(node)
            .element(cmd)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // DEDICATED target should have 4 deploys and 4 teardowns (one per trial)
        long nodeDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("node"))
            .count();
        assertThat(nodeDeploys).isEqualTo(4);

        long nodeTeardowns = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("node"))
            .count();
        assertThat(nodeTeardowns).isEqualTo(4);

        // Each per-trial teardown must depend on its corresponding notify_trial_end
        for (int t = 0; t < 4; t++) {
            final int trial = t;
            AtomicStep teardown = execPlan.steps().stream()
                .filter(s -> s instanceof AtomicStep.TeardownElement te
                    && te.elementId().equals("node")
                    && s.id().contains("_t" + trial))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "Missing teardown for node at trial " + trial));
            assertThat(teardown.dependencies())
                .as("node teardown at t%d should depend on notify_trial_end_%d", trial, trial)
                .contains("notify_trial_end_" + trial);
        }

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("EXCLUSIVE dep with different target instances: trials run in parallel")
    void exclusiveDepDifferentTargetInstancesRunInParallel() {
        // When the exclusive target has different parameter values at each trial,
        // they are distinct instances — no exclusion conflict, so trials should
        // NOT be serialized. The command at T0 (using i4i-4xlarge) and T1 (using
        // m5d-4xlarge) access different node instances and can run concurrently.
        var paramInst = StringParameter.of("instance_type");
        Element node = MockElement.builder("node")
            .parameter(paramInst)
            .build();
        var paramDs = StringParameter.of("dataset");
        Element cmd = MockElement.builder("cmd")
            .parameter(paramDs)
            .dependency(node, RelationshipType.EXCLUSIVE)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        var axisDs = MockAxis.of("dataset", "d1");
        var axisInst = MockAxis.of("instance_type", "i4i-4xlarge", "m5d-4xlarge");

        TestPlan plan = MockTestPlan.builder()
            .name("exclusive-different-instances")
            .axis(axisDs)
            .axis(axisInst)
            .element(node)
            .element(cmd)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // Trial T1's notify_trial_start should NOT depend on T0's notify_trial_end
        // (they should be independent parallel flows)
        AtomicStep notifyStart1 = execPlan.steps().stream()
            .filter(s -> s.id().equals("notify_trial_start_1"))
            .findFirst().orElseThrow();
        assertThat(notifyStart1.dependencies())
            .as("trial 1 start should not be serialized after trial 0 end")
            .doesNotContain("notify_trial_end_0");

        // Both activate_node steps should only depend on "start", not on each other
        AtomicStep activateNode0 = execPlan.steps().stream()
            .filter(s -> s.id().equals("activate_node_t0"))
            .findFirst().orElseThrow();
        AtomicStep activateNode1 = execPlan.steps().stream()
            .filter(s -> s.id().equals("activate_node_t1"))
            .findFirst().orElseThrow();
        assertThat(activateNode0.dependencies()).doesNotContain("deactivate_node_t1");
        assertThat(activateNode1.dependencies()).doesNotContain("await_cmd_t0");

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Trial codes appear in execution plan step metadata")
    void trialCodesInStepMetadata() {
        // 3×2 = 6 trials. a has param_x[3], b has param_y[2].
        // Trial codes should be narrow-mode hex: 2 digits per trial.
        var paramX = IntegerParameter.range("param_x", 1, 3);
        Element a = MockElement.builder("a")
            .parameter(paramX)
            .build();

        var paramY = StringParameter.of("param_y");
        Element b = MockElement.builder("b")
            .parameter(paramY)
            .dependency(a)
            .build();

        var axisX = MockAxis.of("param_x", 1, 2, 3);
        var axisY = MockAxis.of("param_y", "lo", "hi");

        TestPlan plan = MockTestPlan.builder()
            .name("trial-codes-test")
            .axis(axisX)
            .axis(axisY)
            .element(a)
            .element(b)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // Verify trial codes on notify_trial_start steps
        for (int t = 0; t < 6; t++) {
            final int trial = t;
            AtomicStep notifyStart = execPlan.steps().stream()
                .filter(s -> s.id().equals("notify_trial_start_" + trial))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing notify_trial_start_" + trial));
            assertThat(notifyStart.metadata())
                .as("notify_trial_start_%d should have trial_code", trial)
                .containsKey("trial_code");
            String trialCode = (String) notifyStart.metadata().get("trial_code");
            assertThat(trialCode).startsWith("0x");
        }

        // Verify specific trial codes: trial 0 = (0,0) → 0x00, trial 3 = (1,1) → 0x11
        AtomicStep start0 = execPlan.steps().stream()
            .filter(s -> s.id().equals("notify_trial_start_0")).findFirst().orElseThrow();
        assertThat(start0.metadata().get("trial_code")).isEqualTo("0x00");

        AtomicStep start3 = execPlan.steps().stream()
            .filter(s -> s.id().equals("notify_trial_start_3")).findFirst().orElseThrow();
        assertThat(start3.metadata().get("trial_code")).isEqualTo("0x11");

        // Verify trial codes on deploy steps for trial elements
        AtomicStep deployB_t0 = execPlan.steps().stream()
            .filter(s -> s.id().equals("activate_b_t0")).findFirst().orElseThrow();
        assertThat(deployB_t0.metadata().get("trial_code")).isEqualTo("0x00");

        AtomicStep deployB_t5 = execPlan.steps().stream()
            .filter(s -> s.id().equals("activate_b_t5")).findFirst().orElseThrow();
        assertThat(deployB_t5.metadata().get("trial_code")).isEqualTo("0x21");

        // Verify first-class trialCode() accessor on NotifyTrialStart
        AtomicStep.NotifyTrialStart nts0 = (AtomicStep.NotifyTrialStart) start0;
        assertThat(nts0.trialCode()).isPresent().hasValue("0x00");

        AtomicStep.NotifyTrialStart nts3 = (AtomicStep.NotifyTrialStart) start3;
        assertThat(nts3.trialCode()).isPresent().hasValue("0x11");

        // Verify first-class trialCode() accessor on NotifyTrialEnd
        AtomicStep.NotifyTrialEnd nte0 = execPlan.steps().stream()
            .filter(s -> s.id().equals("notify_trial_end_0"))
            .map(AtomicStep.NotifyTrialEnd.class::cast)
            .findFirst().orElseThrow();
        assertThat(nte0.trialCode()).isPresent().hasValue("0x00");
    }

    @Test
    @DisplayName("DEDICATED→EXCLUSIVE: A has dedicated B, B exclusively accesses C")
    void dedicatedExclusiveChainSerializesCorrectly() {
        // A (trial element) has DEDICATED dep on B (non-trial, per-trial instances).
        // B has EXCLUSIVE dep on C (run-scoped).
        // B's serialization edges on C must remain direct (not rerouted through notify
        // boundaries), otherwise B instances could overlap — violating exclusivity.
        Element c = MockElement.of("storage");
        var paramDs = StringParameter.of("dataset");
        Element b = MockElement.builder("node")
            .dependency(c, RelationshipType.EXCLUSIVE)
            .build();
        Element a = MockElement.builder("cmd")
            .parameter(paramDs)
            .dependency(b, RelationshipType.DEDICATED)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        var axis = MockAxis.of("dataset", "d1", "d2", "d3");

        TestPlan plan = MockTestPlan.builder()
            .name("dedicated-exclusive-chain")
            .axis(axis)
            .element(c)
            .element(b)
            .element(a)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // C (run-scoped EXCLUSIVE target) should have exactly 1 deploy and 1 teardown
        long cDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("storage"))
            .count();
        assertThat(cDeploys).isEqualTo(1);

        // B (DEDICATED to trial-element A) should have 3 deploys and 3 teardowns
        long bDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("node"))
            .count();
        assertThat(bDeploys).isEqualTo(3);

        long bTeardowns = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("node"))
            .count();
        assertThat(bTeardowns).isEqualTo(3);

        // A (trial command element) should have 3 deploys and 3 await steps
        long aDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("cmd"))
            .count();
        assertThat(aDeploys).isEqualTo(3);

        // B's per-trial activations must be serialized: activate_node_t1 must
        // transitively depend on deactivate_node_t0 (direct or through notify boundary).
        // The key constraint is that no two node instances are active simultaneously.
        AtomicStep activateNode1 = execPlan.steps().stream()
            .filter(s -> s.id().equals("activate_node_t1"))
            .findFirst().orElseThrow();

        // activate_node_t1 should depend on deactivate_node_t0 (directly, since B is
        // non-trial and its serialization edges are not rerouted through notify)
        assertThat(activateNode1.dependencies())
            .as("node_t1 activation should depend on node_t0 deactivation (exclusive serialization)")
            .contains("deactivate_node_t0");

        if (aDeploys == 3) {
            AtomicStep activateNode2 = execPlan.steps().stream()
                .filter(s -> s.id().equals("activate_node_t2"))
                .findFirst().orElseThrow();
            assertThat(activateNode2.dependencies())
                .as("node_t2 activation should depend on node_t1 deactivation")
                .contains("deactivate_node_t1");
        }

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Transitive DEDICATED chain: trial→A→B→C each DEDICATED — all get per-trial instances")
    void transitiveDedicatedChainProducesPerTrialInstances() {
        // Regression: when trial→A is DEDICATED and A→B is DEDICATED and B→C is
        // DEDICATED, all of A, B, and C should have per-trial instances. Previously,
        // coalesceWithOwner used the direct owner's bindingLevel (which was 0 for
        // elements with no varying params), collapsing transitive DEDICATED targets
        // into a single instance.
        //
        // Chain: testclient (trial, 4 values) → database → victoria → globalconfig
        Element globalconfig = MockElement.of("globalconfig");
        Element victoria = MockElement.builder("victoria")
            .dependency(globalconfig, RelationshipType.DEDICATED)
            .build();
        Element database = MockElement.builder("database")
            .dependency(victoria, RelationshipType.DEDICATED)
            .build();
        var paramDelay = IntegerParameter.range("startup_delay_ms", 1000, 7000);
        Element testclient = MockElement.builder("testclient")
            .parameter(paramDelay)
            .dependency(database, RelationshipType.DEDICATED)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        var axisDelay = MockAxis.of("startup_delay_ms", 1000, 3000, 5000, 7000);

        TestPlan plan = MockTestPlan.builder()
            .name("transitive-dedicated-chain")
            .axis(axisDelay)
            .element(globalconfig)
            .element(victoria)
            .element(database)
            .element(testclient)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // testclient is the trial element with 4 values → 4 deploys
        long tcDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("testclient"))
            .count();
        assertThat(tcDeploys).as("testclient (trial element) should have 4 deploys").isEqualTo(4);

        // database is DEDICATED to testclient (trial element) → 4 instances (not coalesced)
        long dbDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("database"))
            .count();
        assertThat(dbDeploys).as("database (DEDICATED to trial element) should have 4 instances").isEqualTo(4);

        // victoria is DEDICATED to database → should also have 4 instances
        // (transitive: victoria → database → testclient, testclient has 4 trials)
        long vicDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("victoria"))
            .count();
        assertThat(vicDeploys)
            .as("victoria (DEDICATED to database, transitively to trial element) should have 4 instances")
            .isEqualTo(4);

        // globalconfig is DEDICATED to victoria → should also have 4 instances
        // (transitive: globalconfig → victoria → database → testclient)
        long gcDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("globalconfig"))
            .count();
        assertThat(gcDeploys)
            .as("globalconfig (3-hop transitive DEDICATED to trial element) should have 4 instances")
            .isEqualTo(4);

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("DEDICATED chain with interior axis: leaf trial element, axis on middle element — all get silos")
    void dedicatedChainInteriorAxisProducesSilos() {
        // Regression: when the varying axis is on an interior element of the
        // DEDICATED chain (database) rather than the leaf (testclient), the leaf
        // inherits trial scope via NormalizationStage forward propagation, but
        // BindingStateComputer gives it bindingLevel=0 (no owned params).
        // resolveEffectiveBindingLevel must use the max binding level across
        // the entire chain, not just the root's level.
        //
        // Chain: testclient (leaf, no params) → database (has axis) → victoria → globalconfig
        // All deps are DEDICATED. testclient is the trial element.
        Element globalconfig = MockElement.of("globalconfig");
        Element victoria = MockElement.builder("victoria")
            .dependency(globalconfig, RelationshipType.DEDICATED)
            .build();
        var paramDelay = IntegerParameter.range("startup_delay_ms", 1000, 7000);
        Element database = MockElement.builder("database")
            .parameter(paramDelay)
            .dependency(victoria, RelationshipType.DEDICATED)
            .build();
        Element testclient = MockElement.builder("testclient")
            .dependency(database, RelationshipType.DEDICATED)
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        var axisDelay = MockAxis.of("startup_delay_ms", 1000, 3000, 5000, 7000);

        TestPlan plan = MockTestPlan.builder()
            .name("dedicated-interior-axis-silos")
            .axis(axisDelay)
            .element(globalconfig)
            .element(victoria)
            .element(database)
            .element(testclient)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // testclient (trial element, leaf) should have 4 deploys
        long tcDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("testclient"))
            .count();
        assertThat(tcDeploys).as("testclient (trial element) should have 4 deploys").isEqualTo(4);

        // database (owns the axis, DEDICATED to testclient) should have 4 instances
        long dbDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("database"))
            .count();
        assertThat(dbDeploys).as("database (DEDICATED, owns axis) should have 4 instances").isEqualTo(4);

        // victoria (no params, DEDICATED to database) should have 4 instances — not collapsed to 1
        long vicDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("victoria"))
            .count();
        assertThat(vicDeploys)
            .as("victoria (DEDICATED, no params) should have 4 instances via chain max binding level")
            .isEqualTo(4);

        // globalconfig (no params, DEDICATED to victoria) should have 4 instances
        long gcDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("globalconfig"))
            .count();
        assertThat(gcDeploys)
            .as("globalconfig (DEDICATED, no params) should have 4 instances via chain max binding level")
            .isEqualTo(4);

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("SHARED intermediary inherits upstream binding level from dependency chain")
    void sharedIntermediaryInheritsUpstreamBindingLevel() {
        // A (with axis, 2 values) → B (no axis, SHARED on A) → C (with axis, 3 values, trial element)
        // B has no axes of its own, but sits inside A's dependency chain.
        // B must inherit A's group level (2 groups), not collapse into a single group.
        // Total trials: 2×3 = 6.
        var paramX = IntegerParameter.range("param_x", 1, 2);
        Element a = MockElement.builder("a")
            .parameter(paramX)
            .build();

        Element b = MockElement.builder("b")
            .dependency(a)
            .build();

        var paramU = StringParameter.of("param_u");
        Element c = MockElement.builder("c")
            .parameter(paramU)
            .dependency(b)
            .build();

        var axisX = MockAxis.of("param_x", 1, 2);
        var axisU = MockAxis.of("param_u", "p", "q", "r");

        TestPlan plan = MockTestPlan.builder()
            .name("shared-intermediary-propagation")
            .axis(axisX)
            .axis(axisU)
            .element(a)
            .element(b)
            .element(c)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // A: 2 deploys (coalesced at level 1, one per axis value)
        long aDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("a"))
            .count();
        assertThat(aDeploys).as("A should have 2 deploys (one per axis value)").isEqualTo(2);

        // B: 2 deploys (inherited A's group level, matching A's groups)
        // NOT 1 — the pre-fix behavior that collapsed B into a single group
        long bDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("b"))
            .count();
        assertThat(bDeploys)
            .as("B should have 2 deploys (inherits A's group level from dependency chain)")
            .isEqualTo(2);

        // C: 6 deploys (trial element, not coalesced)
        long cDeploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("c"))
            .count();
        assertThat(cDeploys).as("C (trial element) should have 6 deploys").isEqualTo(6);

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Deep dependency chain: correct topological ordering")
    void deepDependencyChain() {
        Element e = MockElement.of("e");
        Element d = MockElement.builder("d").dependency(e).build();
        Element c = MockElement.builder("c").dependency(d).build();
        Element b = MockElement.builder("b").dependency(c).build();
        Element a = MockElement.builder("a").dependency(b).build();

        var axis = MockAxis.of("mode", "test");

        TestPlan plan = MockTestPlan.builder()
            .name("deep-chain-reducto")
            .axis(axis)
            .element(e)
            .element(d)
            .element(c)
            .element(b)
            .element(a)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        List<AtomicStep.DeployElement> deploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement)
            .map(AtomicStep.DeployElement.class::cast)
            .toList();

        assertThat(deploys).hasSizeGreaterThanOrEqualTo(5);
        // e should be deployed first (root of dependency chain)
        assertThat(deploys.getFirst().elementId()).isEqualTo("e");

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("No elements: compilation succeeds with warning")
    void noElements() {
        var axis = MockAxis.of("mode", "test");

        TestPlan plan = MockTestPlan.builder()
            .name("empty-reducto")
            .axis(axis)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings())
            .anyMatch(w -> w.message().contains("no elements"));
    }

    @Test
    @DisplayName("Single COMMAND element, no axes: linear graph with notify boundaries")
    void singleCommandElementNoAxes() {
        // A single COMMAND element with no axes and no dependencies should
        // produce a linear execution graph:
        // start → notify_trial_start → activate → await → notify_trial_end → end
        Element cmd = MockElement.builder("demo-command-1")
            .shutdownSemantics(Element.ShutdownSemantics.COMMAND)
            .build();

        TestPlan plan = MockTestPlan.builder()
            .name("single-command-no-axes")
            .element(cmd)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // Exactly 1 deploy (activate) and 1 await
        long deploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("demo-command-1"))
            .count();
        assertThat(deploys).as("single element should have exactly 1 deploy").isEqualTo(1);

        long awaits = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.AwaitElement ae && ae.elementId().equals("demo-command-1"))
            .count();
        assertThat(awaits).as("single COMMAND element should have exactly 1 await").isEqualTo(1);

        // notify_trial_start and notify_trial_end should exist
        AtomicStep notifyStart = execPlan.steps().stream()
            .filter(s -> s.id().equals("notify_trial_start_0"))
            .findFirst().orElseThrow(() -> new AssertionError("Missing notify_trial_start_0"));
        AtomicStep notifyEnd = execPlan.steps().stream()
            .filter(s -> s.id().equals("notify_trial_end_0"))
            .findFirst().orElseThrow(() -> new AssertionError("Missing notify_trial_end_0"));

        // notify steps should have non-empty elementNames
        AtomicStep.NotifyTrialStart nts = (AtomicStep.NotifyTrialStart) notifyStart;
        assertThat(nts.elementNames())
            .as("notify_trial_start elementNames should contain the element")
            .contains("demo-command-1");

        // activate should depend on notify_trial_start
        AtomicStep activate = execPlan.steps().stream()
            .filter(s -> s.id().equals("activate_demo-command-1_t0"))
            .findFirst().orElseThrow();
        assertThat(activate.dependencies())
            .as("activate should depend on notify_trial_start")
            .contains("notify_trial_start_0");

        // notify_trial_end should depend on the await step
        assertThat(notifyEnd.dependencies())
            .as("notify_trial_end should depend on await")
            .contains("await_demo-command-1_t0");

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    @Test
    @DisplayName("Single SERVICE element, no axes: linear graph with notify boundaries")
    void singleServiceElementNoAxes() {
        // A single SERVICE element with no axes and no dependencies should
        // produce a linear execution graph:
        // start → notify_trial_start → activate → teardown → notify_trial_end → end
        Element svc = MockElement.builder("demo-service-1")
            .build();  // default is SERVICE semantics

        TestPlan plan = MockTestPlan.builder()
            .name("single-service-no-axes")
            .element(svc)
            .build();

        Compiler.CompilationResult result = compileReducto(plan);
        assertThat(result.isSuccess()).isTrue();

        ExecutionPlan execPlan = result.executionPlan().get();

        // Exactly 1 deploy and 1 teardown
        long deploys = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.DeployElement de && de.elementId().equals("demo-service-1"))
            .count();
        assertThat(deploys).as("single element should have exactly 1 deploy").isEqualTo(1);

        long teardowns = execPlan.steps().stream()
            .filter(s -> s instanceof AtomicStep.TeardownElement te && te.elementId().equals("demo-service-1"))
            .count();
        assertThat(teardowns).as("single SERVICE element should have exactly 1 teardown").isEqualTo(1);

        // notify_trial_start and notify_trial_end should exist
        AtomicStep notifyStart = execPlan.steps().stream()
            .filter(s -> s.id().equals("notify_trial_start_0"))
            .findFirst().orElseThrow(() -> new AssertionError("Missing notify_trial_start_0"));
        AtomicStep notifyEnd = execPlan.steps().stream()
            .filter(s -> s.id().equals("notify_trial_end_0"))
            .findFirst().orElseThrow(() -> new AssertionError("Missing notify_trial_end_0"));

        // activate should depend on notify_trial_start
        AtomicStep activate = execPlan.steps().stream()
            .filter(s -> s.id().equals("activate_demo-service-1_t0"))
            .findFirst().orElseThrow();
        assertThat(activate.dependencies())
            .as("activate should depend on notify_trial_start")
            .contains("notify_trial_start_0");

        // notify_trial_end should depend on the teardown step
        assertThat(notifyEnd.dependencies())
            .as("notify_trial_end should depend on teardown")
            .contains("deactivate_demo-service-1_t0");

        assertThat(execPlan.executionGraph().isAcyclic()).isTrue();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Compiler.CompilationResult compileReducto(TestPlan plan) {
        return compile(plan, "reducto");
    }

    private Compiler.CompilationResult compile(TestPlan plan, String strategy) {
        DefaultCompiler compiler = DefaultCompiler.builder()
            .standardPipeline()
            .options(new Compiler.CompilerOptions() {
                @Override public Compiler.CompilationStrategy strategy() { return Compiler.CompilationStrategy.BALANCED; }
                @Override public Compiler.OptimizationLevel optimizationLevel() { return Compiler.OptimizationLevel.STANDARD; }
                @Override public long maxTrialSpaceSize() { return 1_000_000; }
                @Override public boolean parallelCompilation() { return false; }
                @Override public boolean dryRun() { return false; }
                @Override public Map<String, Object> customOptions() {
                    return Map.of(StepGenerationStage.OPTION_STRATEGY, strategy);
                }
            })
            .build();
        return compiler.compile(plan);
    }
}
