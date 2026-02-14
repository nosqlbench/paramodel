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
import io.nosqlbench.paramodel.mock.persistence.MockJournalStore;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.sequence.TrialStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

///
/// Tests for {@link JournalWriter}.
///
/// @since 0.1.0
///
class JournalWriterTest {

    private static final String EXEC_ID = "exec-1";
    private static final String PLAN_ID = "plan-1";

    private JournalStore store;
    private JournalWriter writer;

    @BeforeEach
    void setUp() {
        store = new MockJournalStore();
        writer = new JournalWriter(store, EXEC_ID, PLAN_ID);
    }

    @Test
    void testSequenceNumbersAreMonotonicallyIncreasing() {
        writer.writeExecutionStarted(Optional.empty(), Map.of());
        writer.writePhaseTransition(
            Executor.ExecutionPhase.INITIALIZING, Executor.ExecutionPhase.DEPLOYING);
        writer.writeStepStarted("step-1", AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty());

        List<JournalEvent> events = store.allEvents(EXEC_ID);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).sequenceNumber()).isEqualTo(1);
        assertThat(events.get(1).sequenceNumber()).isEqualTo(2);
        assertThat(events.get(2).sequenceNumber()).isEqualTo(3);
    }

    @Test
    void testCurrentSequenceTracksNext() {
        assertThat(writer.currentSequence()).isEqualTo(1);

        writer.writeExecutionStarted(Optional.empty(), Map.of());
        assertThat(writer.currentSequence()).isEqualTo(2);

        writer.writePhaseTransition(
            Executor.ExecutionPhase.INITIALIZING, Executor.ExecutionPhase.DEPLOYING);
        assertThat(writer.currentSequence()).isEqualTo(3);
    }

    @Test
    void testExecutionStartedEvent() {
        writer.writeExecutionStarted(Optional.of("cp-1"), Map.of("key", "value"));

        List<JournalEvent> events = store.allEvents(EXEC_ID);
        assertThat(events).hasSize(1);

        JournalEvent.ExecutionStarted event = (JournalEvent.ExecutionStarted) events.getFirst();
        assertThat(event.executionId()).isEqualTo(EXEC_ID);
        assertThat(event.executionPlanId()).isEqualTo(PLAN_ID);
        assertThat(event.resumedFromCheckpointId()).contains("cp-1");
        assertThat(event.configuration()).containsEntry("key", "value");
    }

    @Test
    void testStepStartedEvent() {
        writer.writeStepStarted("step-1", AtomicStep.StepType.DEPLOY_ELEMENT,
            Optional.of(Instant.now().plusSeconds(60)));

        JournalEvent.StepStarted event = (JournalEvent.StepStarted)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.stepId()).isEqualTo("step-1");
        assertThat(event.stepType()).isEqualTo(AtomicStep.StepType.DEPLOY_ELEMENT);
        assertThat(event.deadline()).isPresent();
    }

    @Test
    void testStepCompletedEvent() {
        writer.writeStepCompleted("step-1", AtomicStep.StepType.EXECUTE_TRIAL,
            Duration.ofSeconds(10), Map.of("result", "success"));

        JournalEvent.StepCompleted event = (JournalEvent.StepCompleted)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.stepId()).isEqualTo("step-1");
        assertThat(event.duration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(event.outputs()).containsEntry("result", "success");
    }

    @Test
    void testStepFailedEvent() {
        writer.writeStepFailed("step-1", AtomicStep.StepType.DEPLOY_ELEMENT,
            "TimeoutException", "Deployment timed out", true, 2);

        JournalEvent.StepFailed event = (JournalEvent.StepFailed)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.stepId()).isEqualTo("step-1");
        assertThat(event.errorType()).isEqualTo("TimeoutException");
        assertThat(event.isTransient()).isTrue();
        assertThat(event.attemptNumber()).isEqualTo(2);
    }

    @Test
    void testStepRetryingEvent() {
        writer.writeStepRetrying("step-1", 2, Duration.ofSeconds(5));

        JournalEvent.StepRetrying event = (JournalEvent.StepRetrying)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.stepId()).isEqualTo("step-1");
        assertThat(event.attemptNumber()).isEqualTo(2);
        assertThat(event.backoffDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void testStepSkippedEvent() {
        writer.writeStepSkipped("step-1", "Dependency failed");

        JournalEvent.StepSkipped event = (JournalEvent.StepSkipped)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.stepId()).isEqualTo("step-1");
        assertThat(event.reason()).isEqualTo("Dependency failed");
    }

    @Test
    void testElementStateChangedEvent() {
        writer.writeElementStateChanged("db",
            Element.OperationalState.INACTIVE, Element.OperationalState.RUNNING,
            "Database started");

        JournalEvent.ElementStateChanged event = (JournalEvent.ElementStateChanged)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.elementName()).isEqualTo("db");
        assertThat(event.fromState()).isEqualTo(Element.OperationalState.INACTIVE);
        assertThat(event.toState()).isEqualTo(Element.OperationalState.RUNNING);
        assertThat(event.summary()).isEqualTo("Database started");
    }

    @Test
    void testTrialEvents() {
        writer.writeTrialStarting("trial-1", "step-1");
        writer.writeTrialEnded("trial-1", "step-1", TrialStatus.COMPLETED);

        List<JournalEvent> events = store.allEvents(EXEC_ID);
        assertThat(events).hasSize(2);

        JournalEvent.TrialStarting starting = (JournalEvent.TrialStarting) events.get(0);
        assertThat(starting.trialId()).isEqualTo("trial-1");
        assertThat(starting.stepId()).isEqualTo("step-1");

        JournalEvent.TrialEnded ended = (JournalEvent.TrialEnded) events.get(1);
        assertThat(ended.trialId()).isEqualTo("trial-1");
        assertThat(ended.outcome()).isEqualTo(TrialStatus.COMPLETED);
    }

    @Test
    void testCheckpointCreatedEvent() {
        writer.writeCheckpointCreated("cp-1");

        JournalEvent.CheckpointCreated event = (JournalEvent.CheckpointCreated)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.checkpointId()).isEqualTo("cp-1");
    }

    @Test
    void testExecutionSuspendedEvent() {
        writer.writeExecutionSuspended("User requested pause");

        JournalEvent.ExecutionSuspended event = (JournalEvent.ExecutionSuspended)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.reason()).isEqualTo("User requested pause");
    }

    @Test
    void testExecutionCompletedEvent() {
        writer.writeExecutionCompleted(Executor.ExecutionPhase.COMPLETED, 42, 50);

        JournalEvent.ExecutionCompleted event = (JournalEvent.ExecutionCompleted)
            store.allEvents(EXEC_ID).getFirst();
        assertThat(event.finalPhase()).isEqualTo(Executor.ExecutionPhase.COMPLETED);
        assertThat(event.completedTrialCount()).isEqualTo(42);
        assertThat(event.totalTrialCount()).isEqualTo(50);
    }

    @Test
    void testResumeWriterContinuesSequence() {
        // Write some events with the first writer
        writer.writeExecutionStarted(Optional.empty(), Map.of());
        writer.writePhaseTransition(
            Executor.ExecutionPhase.INITIALIZING, Executor.ExecutionPhase.DEPLOYING);

        // Create a new writer starting from sequence 3 (continuing)
        JournalWriter resumeWriter = new JournalWriter(store, EXEC_ID, PLAN_ID, 3);
        resumeWriter.writeStepStarted("step-1", AtomicStep.StepType.DEPLOY_ELEMENT,
            Optional.empty());

        List<JournalEvent> events = store.allEvents(EXEC_ID);
        assertThat(events).hasSize(3);
        assertThat(events.get(2).sequenceNumber()).isEqualTo(3);
    }

    @Test
    void testAllEventsCarryCorrectIdentity() {
        writer.writeExecutionStarted(Optional.empty(), Map.of());
        writer.writePhaseTransition(
            Executor.ExecutionPhase.INITIALIZING, Executor.ExecutionPhase.DEPLOYING);
        writer.writeStepStarted("step-1", AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty());
        writer.writeStepCompleted("step-1", AtomicStep.StepType.DEPLOY_ELEMENT,
            Duration.ofSeconds(5), Map.of());
        writer.writeCheckpointCreated("cp-1");
        writer.writeExecutionCompleted(Executor.ExecutionPhase.COMPLETED, 0, 0);

        List<JournalEvent> events = store.allEvents(EXEC_ID);
        for (JournalEvent event : events) {
            assertThat(event.executionId()).isEqualTo(EXEC_ID);
            assertThat(event.executionPlanId()).isEqualTo(PLAN_ID);
            assertThat(event.timestamp()).isNotNull();
        }
    }

    @Test
    void testInvalidStartSequenceThrows() {
        assertThatThrownBy(() -> new JournalWriter(store, EXEC_ID, PLAN_ID, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
