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

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.Executor.*;
import io.nosqlbench.paramodel.execution.ResourceManager;
import io.nosqlbench.paramodel.execution.ResourceManager.*;
import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.execution.Runtime.*;
import io.nosqlbench.paramodel.execution.Scheduler;
import io.nosqlbench.paramodel.execution.Scheduler.*;
import io.nosqlbench.paramodel.plan.AtomicStep;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for concrete inner types in the execution layer:
/// exceptions, enums, records, and static factory stubs.
///
class ExecutionInnerTypesTest {

    // ── Runtime.Resources record ──────────────────────────────────────

    @Test
    void resourcesAccessors() {
        var r = new Resources(2.0, 4.0, 10.0);
        assertThat(r.cpu()).isEqualTo(2.0);
        assertThat(r.memoryGb()).isEqualTo(4.0);
        assertThat(r.storageGb()).isEqualTo(10.0);
    }

    @Test
    void resourcesOfFactory() {
        var r = Resources.of(8.0, 16.0, 100.0);
        assertThat(r.cpu()).isEqualTo(8.0);
        assertThat(r.memoryGb()).isEqualTo(16.0);
        assertThat(r.storageGb()).isEqualTo(100.0);
    }

    @Test
    void resourcesEquality() {
        var r1 = new Resources(1.0, 2.0, 3.0);
        var r2 = Resources.of(1.0, 2.0, 3.0);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void resourcesToString() {
        var r = Resources.of(2.0, 4.0, 10.0);
        assertThat(r.toString()).contains("2.0").contains("4.0").contains("10.0");
    }

    // ── Runtime.ResourceAvailability record ───────────────────────────

    @Test
    void resourceAvailabilityAccessors() {
        var ra = new ResourceAvailability(16.0, 64.0, 200.0, 10.0);
        assertThat(ra.cpu()).isEqualTo(16.0);
        assertThat(ra.memoryGb()).isEqualTo(64.0);
        assertThat(ra.storageGb()).isEqualTo(200.0);
        assertThat(ra.networkGbps()).isEqualTo(10.0);
    }

    @Test
    void resourceAvailabilityEquality() {
        var ra1 = new ResourceAvailability(8.0, 32.0, 100.0, 5.0);
        var ra2 = new ResourceAvailability(8.0, 32.0, 100.0, 5.0);
        assertThat(ra1).isEqualTo(ra2);
    }

    // ── Runtime.InstanceState enum ────────────────────────────────────

    @Test
    void instanceStateValues() {
        assertThat(InstanceState.values()).containsExactlyInAnyOrder(
            InstanceState.PROVISIONING,
            InstanceState.STARTING,
            InstanceState.HEALTH_CHECK,
            InstanceState.READY,
            InstanceState.UNHEALTHY,
            InstanceState.STOPPING,
            InstanceState.TERMINATED);
    }

    // ── Runtime.DeploymentException ───────────────────────────────────

    @Test
    void deploymentExceptionBasic() {
        var ex = new DeploymentException("deploy failed");
        assertThat(ex.getMessage()).isEqualTo("deploy failed");
        assertThat(ex).isInstanceOf(Exception.class)
            .isNotInstanceOf(RuntimeException.class);
    }

    @Test
    void deploymentExceptionWithCause() {
        var cause = new RuntimeException("network");
        var ex = new DeploymentException("deploy failed", cause);
        assertThat(ex.getMessage()).isEqualTo("deploy failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    // ── Runtime.TrialExecutionException ───────────────────────────────

    @Test
    void trialExecutionExceptionBasic() {
        var ex = new TrialExecutionException("trial failed");
        assertThat(ex.getMessage()).isEqualTo("trial failed");
        assertThat(ex).isInstanceOf(Exception.class)
            .isNotInstanceOf(RuntimeException.class);
    }

    @Test
    void trialExecutionExceptionWithCause() {
        var cause = new RuntimeException("timeout");
        var ex = new TrialExecutionException("trial failed", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    // ── Runtime.InsufficientResourcesException ────────────────────────

    @Test
    void insufficientResourcesException() {
        var ex = new InsufficientResourcesException("not enough CPU");
        assertThat(ex.getMessage()).isEqualTo("not enough CPU");
        assertThat(ex).isInstanceOf(Exception.class)
            .isNotInstanceOf(RuntimeException.class);
    }

    // ── Runtime.TimeoutException ──────────────────────────────────────

    @Test
    void timeoutException() {
        var ex = new Runtime.TimeoutException("timed out");
        assertThat(ex.getMessage()).isEqualTo("timed out");
        assertThat(ex).isInstanceOf(Exception.class)
            .isNotInstanceOf(RuntimeException.class);
    }

    // ── Runtime static factory stubs ──────────────────────────────────

    @Test
    void runtimeCreateThrows() {
        assertThatThrownBy(Runtime::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void runtimeCreateWithConfigThrows() {
        assertThatThrownBy(() -> Runtime.create(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Runtime builder stubs ─────────────────────────────────────────

    @Test
    void deploymentRequestBuilderThrows() {
        assertThatThrownBy(DeploymentRequest::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void trialExecutionRequestBuilderThrows() {
        assertThatThrownBy(TrialExecutionRequest::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Executor.ExecutionPhase enum ──────────────────────────────────

    @Test
    void executionPhaseValues() {
        assertThat(ExecutionPhase.values()).containsExactlyInAnyOrder(
            ExecutionPhase.INITIALIZING,
            ExecutionPhase.DEPLOYING,
            ExecutionPhase.EXECUTING,
            ExecutionPhase.TEARING_DOWN,
            ExecutionPhase.COMPLETED,
            ExecutionPhase.FAILED,
            ExecutionPhase.CANCELLED);
    }

    // ── Executor.ExecutionFailedException ─────────────────────────────

    @Test
    void executionFailedExceptionBasic() {
        var ex = new ExecutionFailedException(
            "exec-1", ExecutionPhase.DEPLOYING, "deployment crashed");
        assertThat(ex.executionId()).isEqualTo("exec-1");
        assertThat(ex.failedPhase()).isEqualTo(ExecutionPhase.DEPLOYING);
        assertThat(ex.getMessage()).isEqualTo("deployment crashed");
        assertThat(ex).isInstanceOf(Exception.class)
            .isNotInstanceOf(RuntimeException.class);
    }

    @Test
    void executionFailedExceptionWithCause() {
        var cause = new RuntimeException("OOM");
        var ex = new ExecutionFailedException(
            "exec-2", ExecutionPhase.EXECUTING, "trial failed", cause);
        assertThat(ex.executionId()).isEqualTo("exec-2");
        assertThat(ex.failedPhase()).isEqualTo(ExecutionPhase.EXECUTING);
        assertThat(ex.getMessage()).isEqualTo("trial failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    // ── Executor static factory stubs ─────────────────────────────────

    @Test
    void executorCreateThrows() {
        assertThatThrownBy(Executor::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void executorCreateWithConfigThrows() {
        assertThatThrownBy(() -> Executor.create(null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void executorConfigBuilderThrows() {
        assertThatThrownBy(ExecutorConfig::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Scheduler.SchedulingPolicy enum ───────────────────────────────

    @Test
    void schedulingPolicyValues() {
        assertThat(SchedulingPolicy.values()).containsExactlyInAnyOrder(
            SchedulingPolicy.FIFO,
            SchedulingPolicy.PRIORITY,
            SchedulingPolicy.FAIR,
            SchedulingPolicy.RESOURCE_AWARE);
    }

    // ── Scheduler.Priority enum ───────────────────────────────────────

    @Test
    void schedulerPriorityValues() {
        assertThat(Priority.values()).containsExactlyInAnyOrder(
            Priority.LOW,
            Priority.NORMAL,
            Priority.HIGH,
            Priority.CRITICAL);
    }

    // ── Scheduler static factory stubs ────────────────────────────────

    @Test
    void schedulerCreateThrows() {
        assertThatThrownBy(Scheduler::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void schedulerCreateWithPolicyThrows() {
        assertThatThrownBy(() -> Scheduler.create(SchedulingPolicy.FIFO))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void schedulerCreateWithConfigThrows() {
        assertThatThrownBy(() -> Scheduler.create((SchedulerConfig) null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void schedulerConfigBuilderThrows() {
        assertThatThrownBy(SchedulerConfig::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── ResourceManager.PoolPriority enum ─────────────────────────────

    @Test
    void poolPriorityValues() {
        assertThat(PoolPriority.values()).containsExactlyInAnyOrder(
            PoolPriority.LOW,
            PoolPriority.NORMAL,
            PoolPriority.HIGH,
            PoolPriority.CRITICAL);
    }

    // ── ResourceManager.AllocationRecord record ───────────────────────

    @Test
    void allocationRecordAccessors() {
        var now = Instant.now();
        var resources = Resources.of(2.0, 4.0, 10.0);
        var record = new AllocationRecord(
            "alloc-1", "trial-42", resources,
            now, Optional.empty(), Duration.ofMinutes(5));

        assertThat(record.allocationId()).isEqualTo("alloc-1");
        assertThat(record.owner()).isEqualTo("trial-42");
        assertThat(record.resources()).isEqualTo(resources);
        assertThat(record.allocatedAt()).isEqualTo(now);
        assertThat(record.releasedAt()).isEmpty();
        assertThat(record.duration()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void allocationRecordWithReleasedAt() {
        var start = Instant.now();
        var end = start.plus(Duration.ofMinutes(10));
        var record = new AllocationRecord(
            "alloc-2", "trial-43", Resources.of(1.0, 2.0, 5.0),
            start, Optional.of(end), Duration.ofMinutes(10));

        assertThat(record.releasedAt()).hasValue(end);
    }

    @Test
    void allocationRecordEquality() {
        var now = Instant.now();
        var resources = Resources.of(1.0, 1.0, 1.0);
        var r1 = new AllocationRecord("a", "o", resources, now, Optional.empty(), Duration.ZERO);
        var r2 = new AllocationRecord("a", "o", resources, now, Optional.empty(), Duration.ZERO);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    // ── ResourceManager static factory stubs ──────────────────────────

    @Test
    void resourceManagerCreateThrows() {
        assertThatThrownBy(ResourceManager::create)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resourceManagerCreateWithCapacityThrows() {
        assertThatThrownBy(() -> ResourceManager.create(Resources.of(1, 1, 1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resourceRequestBuilderThrows() {
        assertThatThrownBy(ResourceManager.ResourceRequest::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resourceQuotaBuilderThrows() {
        assertThatThrownBy(ResourceManager.ResourceQuota::builder)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── AtomicStep.RetryPolicy static factory stubs ───────────────────

    @Test
    void retryPolicyExponentialBackoffThrows() {
        assertThatThrownBy(() -> AtomicStep.RetryPolicy.exponentialBackoff(3))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void retryPolicyFixedDelayThrows() {
        assertThatThrownBy(() -> AtomicStep.RetryPolicy.fixedDelay(3, Duration.ofSeconds(1)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void retryPolicyNoRetryThrows() {
        assertThatThrownBy(AtomicStep.RetryPolicy::noRetry)
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── AtomicStep.StepResult static factory stubs ────────────────────

    @Test
    void stepResultSuccessThrows() {
        assertThatThrownBy(() -> AtomicStep.StepResult.success(Duration.ZERO, Map.of()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stepResultFailureThrows() {
        assertThatThrownBy(() -> AtomicStep.StepResult.failure(Duration.ZERO, new RuntimeException()))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
