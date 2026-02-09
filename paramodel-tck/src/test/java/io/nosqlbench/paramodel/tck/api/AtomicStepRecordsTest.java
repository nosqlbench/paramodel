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
package io.nosqlbench.paramodel.tck.api;

import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.AtomicStep.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for AtomicStep sealed subtypes, inner records, enums, and exceptions.
///
class AtomicStepRecordsTest {

    private static final ResourceRequirements MINIMAL = ResourceRequirements.minimal();
    private static final Map<String, Object> EMPTY_META = Map.of();

    // ── DeployElement ───────────────────────────────────────────────

    @Test
    void deployElementHasCorrectType() {
        var step = new DeployElement(
            "step-1", "db", Map.of("port", 5432), List.of(),
            List.of(), Optional.of(Duration.ofSeconds(30)),
            MINIMAL, Optional.empty(), EMPTY_META);
        assertThat(step.type()).isEqualTo(StepType.DEPLOY_ELEMENT);
    }

    @Test
    void deployElementDescription() {
        var step = new DeployElement(
            "step-1", "redis", Map.of(), List.of(),
            List.of(), Optional.empty(), MINIMAL, Optional.empty(), EMPTY_META);
        assertThat(step.description()).contains("redis");
    }

    @Test
    void deployElementAccessors() {
        var step = new DeployElement(
            "step-1", "db", Map.of("k", "v"), List.of(),
            List.of("dep-1"), Optional.of(Duration.ofMinutes(1)),
            MINIMAL, Optional.empty(), Map.of("tag", "val"));
        assertThat(step.id()).isEqualTo("step-1");
        assertThat(step.elementId()).isEqualTo("db");
        assertThat(step.configuration()).containsEntry("k", "v");
        assertThat(step.dependencies()).containsExactly("dep-1");
        assertThat(step.estimatedDuration()).hasValue(Duration.ofMinutes(1));
        assertThat(step.resourceRequirements()).isEqualTo(MINIMAL);
        assertThat(step.retryPolicy()).isEmpty();
        assertThat(step.metadata()).containsEntry("tag", "val");
    }

    @Test
    void deployElementExecuteThrows() {
        var step = new DeployElement(
            "step-1", "db", Map.of(), List.of(),
            List.of(), Optional.empty(), MINIMAL, Optional.empty(), EMPTY_META);
        assertThatThrownBy(() -> step.execute(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ExecuteTrial ────────────────────────────────────────────────

    @Test
    void executeTrialHasCorrectType() {
        var step = new ExecuteTrial(
            "step-2", "trial-1", Map.of("db", "db_inst_1"),
            List.of("step-1"), Optional.of(Duration.ofMinutes(5)),
            MINIMAL, Optional.empty(), EMPTY_META);
        assertThat(step.type()).isEqualTo(StepType.EXECUTE_TRIAL);
    }

    @Test
    void executeTrialDescription() {
        var step = new ExecuteTrial(
            "step-2", "trial-42", Map.of(), List.of(),
            Optional.empty(), MINIMAL, Optional.empty(), EMPTY_META);
        assertThat(step.description()).contains("trial-42");
    }

    @Test
    void executeTrialAccessors() {
        var step = new ExecuteTrial(
            "step-2", "trial-1", Map.of("db", "inst-1"),
            List.of("dep-1", "dep-2"), Optional.empty(),
            MINIMAL, Optional.empty(), EMPTY_META);
        assertThat(step.trialId()).isEqualTo("trial-1");
        assertThat(step.elementBindings()).containsEntry("db", "inst-1");
        assertThat(step.dependencies()).hasSize(2);
    }

    // ── TeardownElement ─────────────────────────────────────────────

    @Test
    void teardownElementHasCorrectType() {
        var step = new TeardownElement(
            "step-3", "db", true, List.of("step-2"),
            Optional.of(Duration.ofSeconds(10)), MINIMAL,
            Optional.empty(), EMPTY_META);
        assertThat(step.type()).isEqualTo(StepType.TEARDOWN_ELEMENT);
    }

    @Test
    void teardownElementDescription() {
        var step = new TeardownElement(
            "step-3", "cache", false, List.of(),
            Optional.empty(), MINIMAL, Optional.empty(), EMPTY_META);
        assertThat(step.description()).contains("cache");
    }

    @Test
    void teardownElementAccessors() {
        var step = new TeardownElement(
            "step-3", "db", true, List.of("barrier-1"),
            Optional.empty(), MINIMAL, Optional.empty(), EMPTY_META);
        assertThat(step.elementId()).isEqualTo("db");
        assertThat(step.collectArtifacts()).isTrue();
    }

    // ── BarrierSync ─────────────────────────────────────────────────

    @Test
    void barrierSyncHasCorrectType() {
        var step = new BarrierSync(
            "step-4", "barrier-1", List.of("step-1", "step-2"),
            Optional.empty(), ResourceRequirements.none(),
            Optional.empty(), EMPTY_META);
        assertThat(step.type()).isEqualTo(StepType.BARRIER_SYNC);
    }

    @Test
    void barrierSyncDescription() {
        var step = new BarrierSync(
            "step-4", "all-deployed", List.of("a", "b", "c"),
            Optional.empty(), ResourceRequirements.none(),
            Optional.empty(), EMPTY_META);
        assertThat(step.description()).contains("all-deployed");
        assertThat(step.description()).contains("3");
    }

    @Test
    void barrierSyncAccessors() {
        var step = new BarrierSync(
            "step-4", "barrier-x", List.of("dep-1"),
            Optional.of(Duration.ofSeconds(5)), ResourceRequirements.none(),
            Optional.empty(), EMPTY_META);
        assertThat(step.barrierId()).isEqualTo("barrier-x");
    }

    // ── CheckpointState ─────────────────────────────────────────────

    @Test
    void checkpointStateHasCorrectType() {
        var step = new CheckpointState(
            "step-5", "ckpt-1", List.of("step-4"),
            Optional.empty(), ResourceRequirements.none(),
            Optional.empty(), EMPTY_META);
        assertThat(step.type()).isEqualTo(StepType.CHECKPOINT_STATE);
    }

    @Test
    void checkpointStateDescription() {
        var step = new CheckpointState(
            "step-5", "ckpt-42", List.of(),
            Optional.empty(), ResourceRequirements.none(),
            Optional.empty(), EMPTY_META);
        assertThat(step.description()).contains("ckpt-42");
    }

    @Test
    void checkpointStateAccessors() {
        var step = new CheckpointState(
            "step-5", "ckpt-1", List.of("dep-1"),
            Optional.of(Duration.ofSeconds(1)), ResourceRequirements.none(),
            Optional.empty(), Map.of("auto", true));
        assertThat(step.checkpointId()).isEqualTo("ckpt-1");
        assertThat(step.metadata()).containsEntry("auto", true);
    }

    // ── StepType enum ───────────────────────────────────────────────

    @Test
    void stepTypeValuesExist() {
        assertThat(StepType.values()).containsExactlyInAnyOrder(
            StepType.DEPLOY_ELEMENT,
            StepType.EXECUTE_TRIAL,
            StepType.TEARDOWN_ELEMENT,
            StepType.BARRIER_SYNC,
            StepType.CHECKPOINT_STATE);
    }

    // ── ResourceRequirements ────────────────────────────────────────

    @Test
    void resourceRequirementsMinimal() {
        var rr = ResourceRequirements.minimal();
        assertThat(rr.cpu()).isGreaterThan(0);
        assertThat(rr.memoryMb()).isGreaterThan(0);
        assertThat(rr.storageGb()).isGreaterThan(0);
        assertThat(rr.networkGbps()).isGreaterThan(0);
    }

    @Test
    void resourceRequirementsNone() {
        var rr = ResourceRequirements.none();
        assertThat(rr.cpu()).isEqualTo(0);
        assertThat(rr.memoryMb()).isEqualTo(0);
        assertThat(rr.storageGb()).isEqualTo(0);
        assertThat(rr.networkGbps()).isEqualTo(0);
    }

    @Test
    void resourceRequirementsCustom() {
        var rr = new ResourceRequirements(4.0, 8192, 100, 1.0);
        assertThat(rr.cpu()).isEqualTo(4.0);
        assertThat(rr.memoryMb()).isEqualTo(8192);
        assertThat(rr.storageGb()).isEqualTo(100);
        assertThat(rr.networkGbps()).isEqualTo(1.0);
    }

    // ── HealthCheck record ──────────────────────────────────────────

    @Test
    void healthCheckAccessors() {
        var hc = new HealthCheck(
            "http-check", HealthCheckType.HTTP, "http://localhost:8080/health",
            Duration.ofSeconds(5), Duration.ofSeconds(1), 3);
        assertThat(hc.name()).isEqualTo("http-check");
        assertThat(hc.type()).isEqualTo(HealthCheckType.HTTP);
        assertThat(hc.target()).isEqualTo("http://localhost:8080/health");
        assertThat(hc.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(hc.interval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(hc.requiredConsecutivePasses()).isEqualTo(3);
    }

    // ── HealthCheckType enum ────────────────────────────────────────

    @Test
    void healthCheckTypeValues() {
        assertThat(HealthCheckType.values()).containsExactlyInAnyOrder(
            HealthCheckType.HTTP,
            HealthCheckType.TCP,
            HealthCheckType.COMMAND,
            HealthCheckType.CUSTOM);
    }

    // ── InstanceStatus enum ─────────────────────────────────────────

    @Test
    void instanceStatusValues() {
        assertThat(InstanceStatus.values()).containsExactlyInAnyOrder(
            InstanceStatus.DEPLOYING,
            InstanceStatus.HEALTHY,
            InstanceStatus.UNHEALTHY,
            InstanceStatus.TEARDOWN);
    }

    // ── ElementInstance record ───────────────────────────────────────

    @Test
    void elementInstanceAccessors() {
        var inst = new ElementInstance(
            "inst-1", "db", "localhost:5432",
            Map.of("port", 5432), InstanceStatus.HEALTHY);
        assertThat(inst.instanceId()).isEqualTo("inst-1");
        assertThat(inst.elementId()).isEqualTo("db");
        assertThat(inst.endpoint()).isEqualTo("localhost:5432");
        assertThat(inst.configuration()).containsEntry("port", 5432);
        assertThat(inst.status()).isEqualTo(InstanceStatus.HEALTHY);
    }

    // ── StepExecutionException ──────────────────────────────────────

    @Test
    void stepExecutionExceptionBasic() {
        var ex = new StepExecutionException("step-1", "deploy failed", true);
        assertThat(ex.stepId()).isEqualTo("step-1");
        assertThat(ex.getMessage()).isEqualTo("deploy failed");
        assertThat(ex.isTransient()).isTrue();
    }

    @Test
    void stepExecutionExceptionWithCause() {
        var cause = new RuntimeException("timeout");
        var ex = new StepExecutionException("step-2", "execution failed", cause, false);
        assertThat(ex.stepId()).isEqualTo("step-2");
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.isTransient()).isFalse();
    }

    // ── Sealed permits ──────────────────────────────────────────────

    @Test
    void atomicStepSealedSubtypes() {
        AtomicStep deploy = new DeployElement(
            "1", "e", Map.of(), List.of(), List.of(),
            Optional.empty(), MINIMAL, Optional.empty(), EMPTY_META);
        AtomicStep execute = new ExecuteTrial(
            "2", "t", Map.of(), List.of(),
            Optional.empty(), MINIMAL, Optional.empty(), EMPTY_META);
        AtomicStep teardown = new TeardownElement(
            "3", "e", false, List.of(),
            Optional.empty(), MINIMAL, Optional.empty(), EMPTY_META);
        AtomicStep barrier = new BarrierSync(
            "4", "b", List.of(),
            Optional.empty(), ResourceRequirements.none(), Optional.empty(), EMPTY_META);
        AtomicStep checkpoint = new CheckpointState(
            "5", "ckpt", List.of(),
            Optional.empty(), ResourceRequirements.none(), Optional.empty(), EMPTY_META);

        assertThat(deploy).isInstanceOf(AtomicStep.class);
        assertThat(execute).isInstanceOf(AtomicStep.class);
        assertThat(teardown).isInstanceOf(AtomicStep.class);
        assertThat(barrier).isInstanceOf(AtomicStep.class);
        assertThat(checkpoint).isInstanceOf(AtomicStep.class);
    }
}
