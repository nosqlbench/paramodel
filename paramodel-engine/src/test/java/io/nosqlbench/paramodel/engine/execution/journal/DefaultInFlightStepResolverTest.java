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

import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.mock.persistence.MockCheckpointStore;
import io.nosqlbench.paramodel.mock.persistence.MockJournalStore;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests for {@link DefaultInFlightStepResolver}.
///
/// @since 0.1.0
///
class DefaultInFlightStepResolverTest {

    private static final String EXEC_ID = "exec-1";
    private static final String PLAN_ID = "plan-1";

    private DefaultInFlightStepResolver resolver;
    private DefaultJournalStateReconstructor reconstructor;
    private JournalStore journalStore;

    @BeforeEach
    void setUp() {
        resolver = new DefaultInFlightStepResolver();
        reconstructor = new DefaultJournalStateReconstructor();
        journalStore = new MockJournalStore();
    }

    @Test
    void testTimedOutStep() {
        Instant past = Instant.now().minusSeconds(120);
        Instant pastDeadline = Instant.now().minusSeconds(60);

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, past, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, past, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.of(pastDeadline)));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, StubExecutionPlan.withId(PLAN_ID),
            journalStore, new MockCheckpointStore());

        Map<String, InFlightStepResolver.StepResolution> resolutions =
            resolver.resolve(snapshot, StubExecutionPlan.withId(PLAN_ID));

        assertThat(resolutions).containsKey("step-1");
        assertThat(resolutions.get("step-1").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.TIMED_OUT);
    }

    @Test
    void testCleanShutdownResumesStep() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.TRIAL_STEP, Optional.empty()));
        journalStore.append(new JournalEvent.ExecutionSuspended(
            3, EXEC_ID, PLAN_ID, now, "User pause"));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, StubExecutionPlan.withId(PLAN_ID),
            journalStore, new MockCheckpointStore());

        Map<String, InFlightStepResolver.StepResolution> resolutions =
            resolver.resolve(snapshot, StubExecutionPlan.withId(PLAN_ID));

        assertThat(resolutions.get("step-1").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.RESUME);
    }

    @Test
    void testIdempotentDeployStepRetries() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty()));
        // No completion — simulates crash

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, StubExecutionPlan.withId(PLAN_ID),
            journalStore, new MockCheckpointStore());

        Map<String, InFlightStepResolver.StepResolution> resolutions =
            resolver.resolve(snapshot, StubExecutionPlan.withId(PLAN_ID));

        assertThat(resolutions.get("step-1").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.RETRY);
    }

    @Test
    void testIdempotentTrialStepRetries() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.TRIAL_STEP, Optional.empty()));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, StubExecutionPlan.withId(PLAN_ID),
            journalStore, new MockCheckpointStore());

        Map<String, InFlightStepResolver.StepResolution> resolutions =
            resolver.resolve(snapshot, StubExecutionPlan.withId(PLAN_ID));

        assertThat(resolutions.get("step-1").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.RETRY);
    }

    @Test
    void testNonIdempotentBarrierStepFails() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.BARRIER_SYNC, Optional.empty()));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, StubExecutionPlan.withId(PLAN_ID),
            journalStore, new MockCheckpointStore());

        Map<String, InFlightStepResolver.StepResolution> resolutions =
            resolver.resolve(snapshot, StubExecutionPlan.withId(PLAN_ID));

        assertThat(resolutions.get("step-1").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.FAIL);
    }

    @Test
    void testNonIdempotentCheckpointStepFails() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.CHECKPOINT_STATE, Optional.empty()));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, StubExecutionPlan.withId(PLAN_ID),
            journalStore, new MockCheckpointStore());

        Map<String, InFlightStepResolver.StepResolution> resolutions =
            resolver.resolve(snapshot, StubExecutionPlan.withId(PLAN_ID));

        assertThat(resolutions.get("step-1").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.FAIL);
    }

    @Test
    void testMixedScenarios() {
        Instant past = Instant.now().minusSeconds(120);
        Instant pastDeadline = Instant.now().minusSeconds(60);

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, past, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, past, "step-timed-out",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.of(pastDeadline)));
        journalStore.append(new JournalEvent.StepStarted(
            3, EXEC_ID, PLAN_ID, past, "step-retryable",
            AtomicStep.StepType.TRIAL_STEP, Optional.empty()));
        journalStore.append(new JournalEvent.StepStarted(
            4, EXEC_ID, PLAN_ID, past, "step-barrier",
            AtomicStep.StepType.CHECKPOINT_STATE, Optional.empty()));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, StubExecutionPlan.withId(PLAN_ID),
            journalStore, new MockCheckpointStore());

        Map<String, InFlightStepResolver.StepResolution> resolutions =
            resolver.resolve(snapshot, StubExecutionPlan.withId(PLAN_ID));

        assertThat(resolutions).hasSize(3);
        assertThat(resolutions.get("step-timed-out").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.TIMED_OUT);
        assertThat(resolutions.get("step-retryable").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.RETRY);
        assertThat(resolutions.get("step-barrier").action())
            .isEqualTo(InFlightStepResolver.ResolutionAction.FAIL);
    }

    @Test
    void testNoInFlightStepsProducesEmptyResolutions() {
        Instant now = Instant.now();

        journalStore.append(new JournalEvent.ExecutionStarted(
            1, EXEC_ID, PLAN_ID, now, Optional.empty(), Map.of()));
        journalStore.append(new JournalEvent.StepStarted(
            2, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty()));
        journalStore.append(new JournalEvent.StepCompleted(
            3, EXEC_ID, PLAN_ID, now, "step-1",
            AtomicStep.StepType.DEPLOY_ELEMENT, Duration.ofSeconds(5), Map.of()));

        ExecutionSnapshot snapshot = reconstructor.reconstruct(
            EXEC_ID, StubExecutionPlan.withId(PLAN_ID),
            journalStore, new MockCheckpointStore());

        Map<String, InFlightStepResolver.StepResolution> resolutions =
            resolver.resolve(snapshot, StubExecutionPlan.withId(PLAN_ID));

        assertThat(resolutions).isEmpty();
    }
}
