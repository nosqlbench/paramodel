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
package io.nosqlbench.paramodel.engine.execution.journal;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.mock.persistence.MockCheckpointStore;
import io.nosqlbench.paramodel.mock.persistence.MockJournalStore;
import io.nosqlbench.paramodel.persistence.CheckpointStore;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.TrialStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests for {@link DefaultJournalStateReconstructor}.
///
/// @since 0.1.0
///
class DefaultJournalStateReconstructorTest {

    private static final String EXEC_ID = "exec-1";
    private static final String PLAN_ID = "plan-1";

    private JournalStore journalStore;
    private CheckpointStore checkpointStore;
    private ExecutionPlan plan;
    private DefaultJournalStateReconstructor reconstructor;

    @BeforeEach
    void setUp() {
        journalStore = new MockJournalStore();
        checkpointStore = new MockCheckpointStore();
        plan = StubExecutionPlan.withId(PLAN_ID);
        reconstructor = new DefaultJournalStateReconstructor();
    }

    @Test
    void testEmptyJournalProducesEmptySnapshot() {
        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.completedStepIds()).isEmpty();
        assertThat(snapshot.failedStepIds()).isEmpty();
        assertThat(snapshot.skippedStepIds()).isEmpty();
        assertThat(snapshot.inFlightStepIds()).isEmpty();
        assertThat(snapshot.completedTrialIds()).isEmpty();
        assertThat(snapshot.inFlightTrialIds()).isEmpty();
        assertThat(snapshot.elementStates()).isEmpty();
        assertThat(snapshot.lastSequenceNumber()).isEqualTo(0);
        assertThat(snapshot.baseCheckpointId()).isEmpty();
        assertThat(snapshot.wasCleanShutdown()).isFalse();
    }

    @Test
    void testFullReplayReconstructsAllState() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.PhaseTransition(
            2, EXEC_ID, PLAN_ID, now,
            Executor.ExecutionPhase.INITIALIZING, Executor.ExecutionPhase.DEPLOYING));
        journalStore.append(new JournalEvent.StepStarted(
            3, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty()));
        journalStore.append(new JournalEvent.StepCompleted(
            4, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Duration.ofSeconds(5), Map.of()));
        journalStore.append(new JournalEvent.ElementStateChanged(
            5, EXEC_ID, PLAN_ID, now, "db",
            Element.OperationalState.INACTIVE, Element.OperationalState.RUNNING, "Started"));
        journalStore.append(new JournalEvent.PhaseTransition(
            6, EXEC_ID, PLAN_ID, now,
            Executor.ExecutionPhase.DEPLOYING, Executor.ExecutionPhase.EXECUTING));
        journalStore.append(new JournalEvent.TrialStarting(
            7, EXEC_ID, PLAN_ID, now, "trial-1", "step-2"));
        journalStore.append(new JournalEvent.TrialEnded(
            8, EXEC_ID, PLAN_ID, now, "trial-1", "step-2", TrialStatus.COMPLETED));
        journalStore.append(new JournalEvent.StepStarted(
            9, EXEC_ID, PLAN_ID, now, "step-2",
            AtomicStep.StepType.TRIAL_STEP, Optional.empty()));
        journalStore.append(new JournalEvent.StepCompleted(
            10, EXEC_ID, PLAN_ID, now, "step-2",
            AtomicStep.StepType.TRIAL_STEP, Duration.ofSeconds(10), Map.of()));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.executionId()).isEqualTo(EXEC_ID);
        assertThat(snapshot.executionPlanId()).isEqualTo(PLAN_ID);
        assertThat(snapshot.currentPhase()).isEqualTo(Executor.ExecutionPhase.EXECUTING);
        assertThat(snapshot.completedStepIds()).containsExactly("step-1", "step-2");
        assertThat(snapshot.completedTrialIds()).containsExactly("trial-1");
        assertThat(snapshot.inFlightStepIds()).isEmpty();
        assertThat(snapshot.inFlightTrialIds()).isEmpty();
        assertThat(snapshot.elementStates()).containsEntry("db", Element.OperationalState.RUNNING);
        assertThat(snapshot.lastSequenceNumber()).isEqualTo(10);
    }

    @Test
    void testInFlightStepDetection() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.of(now.plusSeconds(60))));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.inFlightStepIds()).containsExactly("step-1");
        assertThat(snapshot.completedStepIds()).isEmpty();
        assertThat(snapshot.inFlightStepDetails()).containsKey("step-1");
        assertThat(snapshot.inFlightStepDetails().get("step-1").deadline()).isPresent();
    }

    @Test
    void testInFlightTrialDetection() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.TrialStarting(
            2, EXEC_ID, PLAN_ID, now, "trial-1", "step-1"));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.inFlightTrialIds()).containsExactly("trial-1");
        assertThat(snapshot.completedTrialIds()).isEmpty();
    }

    @Test
    void testCleanShutdownDetection() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.ExecutionSuspended(
            2, EXEC_ID, PLAN_ID, now, "User requested pause"));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.wasCleanShutdown()).isTrue();
    }

    @Test
    void testCrashShutdownDetection() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.TRIAL_STEP, Optional.empty()));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.wasCleanShutdown()).isFalse();
    }

    @Test
    void testFailedStepTracking() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty()));
        journalStore.append(new JournalEvent.StepFailed(
            3, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, "TimeoutException",
            "Deployment timed out", false, 1));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.failedStepIds()).containsExactly("step-1");
        assertThat(snapshot.inFlightStepIds()).isEmpty();
    }

    @Test
    void testSkippedStepTracking() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepSkipped(
            2, EXEC_ID, PLAN_ID, now, "step-1", "Dependency failed"));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.skippedStepIds()).containsExactly("step-1");
    }

    @Test
    void testElementStateTracking() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.ElementStateChanged(
            2, EXEC_ID, PLAN_ID, now, "db",
            Element.OperationalState.INACTIVE, Element.OperationalState.PROVISIONING,
            "Provisioning started"));
        journalStore.append(new JournalEvent.ElementStateChanged(
            3, EXEC_ID, PLAN_ID, now, "db",
            Element.OperationalState.PROVISIONING, Element.OperationalState.RUNNING,
            "Running"));
        journalStore.append(new JournalEvent.ElementStateChanged(
            4, EXEC_ID, PLAN_ID, now, "cache",
            Element.OperationalState.INACTIVE, Element.OperationalState.READY,
            "Cache ready"));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.elementStates())
            .containsEntry("db", Element.OperationalState.RUNNING)
            .containsEntry("cache", Element.OperationalState.READY);
    }

    @Test
    void testReconstructionIsIdempotent() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty()));
        journalStore.append(new JournalEvent.StepCompleted(
            3, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Duration.ofSeconds(5), Map.of()));

        ExecutionSnapshot snapshot1 = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);
        ExecutionSnapshot snapshot2 = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot1.completedStepIds()).isEqualTo(snapshot2.completedStepIds());
        assertThat(snapshot1.inFlightStepIds()).isEqualTo(snapshot2.inFlightStepIds());
        assertThat(snapshot1.lastSequenceNumber()).isEqualTo(snapshot2.lastSequenceNumber());
    }

    @Test
    void testReplayFromCheckpoint() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty()));
        journalStore.append(new JournalEvent.StepCompleted(
            3, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Duration.ofSeconds(5), Map.of()));
        journalStore.append(new JournalEvent.CheckpointCreated(
            4, EXEC_ID, PLAN_ID, now, "cp-1"));
        journalStore.append(new JournalEvent.StepStarted(
            5, EXEC_ID, PLAN_ID, now, "step-2",
            AtomicStep.StepType.TRIAL_STEP, Optional.empty()));
        journalStore.append(new JournalEvent.StepCompleted(
            6, EXEC_ID, PLAN_ID, now, "step-2",
            AtomicStep.StepType.TRIAL_STEP, Duration.ofSeconds(10), Map.of()));

        checkpointStore.saveCheckpoint(createCheckpoint("cp-1", PLAN_ID,
            List.of("step-1"), List.of()));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, plan, journalStore, checkpointStore);

        assertThat(snapshot.completedStepIds()).containsExactly("step-1", "step-2");
        assertThat(snapshot.baseCheckpointId()).isPresent().contains("cp-1");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Executor.Checkpoint createCheckpoint(
            String checkpointId, String planId,
            List<String> completedStepIds, List<String> completedTrialIds) {
        Instant now = Instant.now();
        return new Executor.Checkpoint() {
            @Override public String checkpointId() { return checkpointId; }
            @Override public String executionPlanId() { return planId; }
            @Override public Instant createdAt() { return now; }
            @Override public List<String> completedTrialIds() { return completedTrialIds; }
            @Override public List<String> completedStepIds() { return completedStepIds; }
            @Override public Map<String, Object> state() { return Map.of(); }
        };
    }
}
