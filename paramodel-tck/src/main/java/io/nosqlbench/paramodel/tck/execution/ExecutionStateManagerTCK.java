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
package io.nosqlbench.paramodel.tck.execution;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.execution.ExecutionStateManager;
import io.nosqlbench.paramodel.execution.ExecutionStateManager.IdempotencyClass;
import io.nosqlbench.paramodel.execution.ExecutionStateManager.InFlightResolution;
import io.nosqlbench.paramodel.execution.ExecutionStateManager.RecoveryResult;
import io.nosqlbench.paramodel.execution.ExecutionStateManager.ResolutionAction;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.TrialResult;
import io.nosqlbench.paramodel.sequence.TrialStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Technology Compatibility Kit tests for {@link ExecutionStateManager} implementations.
///
/// Validates the core contract:
/// - Event recording and recovery
/// - Checkpoint protocol (journal → checkpoint → truncate)
/// - Crash recovery with idempotency-based resolution
/// - Clean shutdown recovery with RESUME resolution
/// - Idempotency classification for all step types
/// - Trial result persistence (save, retrieve, scoped queries)
/// - Cleanup of persisted state (including trial results)
/// - Stateful execution flow resume at key points
///
/// ## Usage
///
/// Subclass this TCK and implement {@link #createExecutionStateManager()} and
/// {@link #createExecutionPlan(String, List)} to provide concrete instances.
///
/// ```java
/// public class MockExecutionStateManagerTest extends ExecutionStateManagerTCK {
///     @Override
///     protected ExecutionStateManager createExecutionStateManager() {
///         return new MockExecutionStateManager();
///     }
///
///     @Override
///     protected ExecutionPlan createExecutionPlan(String planId, List<AtomicStep> steps) {
///         return MockExecutionPlan.builder(planId, "fp-1")
///             .steps(steps).build();
///     }
/// }
/// ```
///
/// @see ExecutionStateManager
/// @since 0.1.0
///
public abstract class ExecutionStateManagerTCK {

    /// Creates a new TCK test instance.
    protected ExecutionStateManagerTCK() {}

    /// Returns a fresh {@link ExecutionStateManager} instance for testing.
    protected abstract ExecutionStateManager createExecutionStateManager();

    /// Creates an {@link ExecutionPlan} with the given ID and steps.
    ///
    /// @param planId the execution plan identifier
    /// @param steps the atomic steps in the plan
    /// @return a new execution plan
    protected abstract ExecutionPlan createExecutionPlan(String planId, List<AtomicStep> steps);

    /// Creates a checkpoint with the given parameters.
    ///
    /// @param checkpointId checkpoint identifier
    /// @param executionPlanId execution plan identifier
    /// @param completedStepIds completed step IDs
    /// @param completedTrialIds completed trial IDs
    /// @return a new checkpoint
    protected abstract Executor.Checkpoint createCheckpoint(
        String checkpointId, String executionPlanId,
        List<String> completedStepIds, List<String> completedTrialIds);

    /// Creates a {@link TrialResult} for testing result persistence.
    ///
    /// @param trialId the trial identifier
    /// @param status the trial status
    /// @return a new trial result
    protected abstract TrialResult createTrialResult(String trialId, TrialStatus status);

    private ExecutionStateManager manager;

    private static final String EXEC_ID = "exec-1";
    private static final String PLAN_ID = "plan-1";

    @BeforeEach
    void setUp() {
        manager = createExecutionStateManager();
    }

    // -----------------------------------------------------------------------
    // Event Recording
    // -----------------------------------------------------------------------

    @Nested
    class EventRecording {

        @Test
        void recordEventsAndRecoverCompletedSteps() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            // Record a full step lifecycle: started → completed
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(3, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepStarted(4, "step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(stepCompleted(5, "step-2", AtomicStep.StepType.TRIAL_STEP));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.completedStepIds()).containsExactlyInAnyOrder("step-1", "step-2");
            assertThat(result.failedStepIds()).isEmpty();
            assertThat(result.inFlightStepIds()).isEmpty();
        }

        @Test
        void recordEventsAndRecoverFailedSteps() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepFailed(3, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.completedStepIds()).isEmpty();
            assertThat(result.failedStepIds()).containsExactly("step-1");
        }

        @Test
        void recordEventsAndRecoverSkippedSteps() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepSkipped(2, "step-1"));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.skippedStepIds()).containsExactly("step-1");
        }

        @Test
        void recordTrialLifecycleAndRecover() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(trialStarting(2, "trial-1", "step-2"));
            manager.recordEvent(trialEnded(3, "trial-1", "step-2"));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.completedTrialIds()).containsExactly("trial-1");
            assertThat(result.inFlightTrialIds()).isEmpty();
        }

        @Test
        void recordElementStateChanges() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(elementStateChanged(2, "db",
                Element.OperationalState.INACTIVE, Element.OperationalState.PROVISIONING));
            manager.recordEvent(elementStateChanged(3, "db",
                Element.OperationalState.PROVISIONING, Element.OperationalState.READY));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.elementStates()).containsEntry("db", Element.OperationalState.READY);
        }
    }

    // -----------------------------------------------------------------------
    // Checkpoint Protocol
    // -----------------------------------------------------------------------

    @Nested
    class CheckpointProtocol {

        @Test
        void checkpointThenRecoverShowsOnlyPostCheckpointEvents() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            // Phase 1: record some events, then checkpoint
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(3, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(checkpointCreated(4, "cp-1"));

            Executor.Checkpoint cp = createCheckpoint("cp-1", PLAN_ID,
                List.of("step-1"), List.of());
            manager.checkpoint(cp);

            // Phase 2: record more events after checkpoint
            manager.recordEvent(stepStarted(5, "step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(stepCompleted(6, "step-2", AtomicStep.StepType.TRIAL_STEP));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            // Both steps should be in completed: step-1 from checkpoint, step-2 from replay
            assertThat(result.completedStepIds()).containsExactlyInAnyOrder("step-1", "step-2");
        }

        @Test
        void multipleCheckpointsRecoverFromLatest() {
            ExecutionPlan plan = planWithMultipleSteps();

            // Checkpoint 1 after step-1
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(3, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(checkpointCreated(4, "cp-1"));
            manager.checkpoint(createCheckpoint("cp-1", PLAN_ID,
                List.of("step-1"), List.of()));

            // Checkpoint 2 after step-2
            manager.recordEvent(stepStarted(5, "step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(stepCompleted(6, "step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(checkpointCreated(7, "cp-2"));
            manager.checkpoint(createCheckpoint("cp-2", PLAN_ID,
                List.of("step-1", "step-2"), List.of("trial-1")));

            // Step-3 after second checkpoint
            manager.recordEvent(stepStarted(8, "step-3", AtomicStep.StepType.TEARDOWN_ELEMENT));
            manager.recordEvent(stepCompleted(9, "step-3", AtomicStep.StepType.TEARDOWN_ELEMENT));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.completedStepIds())
                .containsExactlyInAnyOrder("step-1", "step-2", "step-3");
            assertThat(result.completedTrialIds()).containsExactly("trial-1");
        }
    }

    // -----------------------------------------------------------------------
    // Crash Recovery
    // -----------------------------------------------------------------------

    @Nested
    class CrashRecovery {

        @Test
        void idempotentStepInFlightAfterCrashGetsRetry() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            // Simulate crash: step started but never completed, no suspension event
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            // <crash happens here — no StepCompleted, no ExecutionSuspended>

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.inFlightStepIds()).containsExactly("step-1");
            assertThat(result.wasCleanShutdown()).isFalse();
            assertThat(result.inFlightResolutions()).containsKey("step-1");
            assertThat(result.inFlightResolutions().get("step-1").action())
                .isEqualTo(ResolutionAction.RETRY);
        }

        @Test
        void nonIdempotentStepInFlightAfterCrashGetsFail() {
            ExecutionPlan plan = planWithBarrierStep();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "barrier-1", AtomicStep.StepType.BARRIER_SYNC));
            // <crash>

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.inFlightStepIds()).containsExactly("barrier-1");
            assertThat(result.inFlightResolutions().get("barrier-1").action())
                .isEqualTo(ResolutionAction.FAIL);
        }

        @Test
        void timedOutStepGetsTimedOutResolution() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            // Step with a deadline in the past
            Instant pastDeadline = Instant.now().minus(Duration.ofHours(1));
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(new JournalEvent.StepStarted(
                2, EXEC_ID, PLAN_ID, Instant.now(),
                "step-1", AtomicStep.StepType.DEPLOY_ELEMENT, Optional.of(pastDeadline)));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.inFlightResolutions().get("step-1").action())
                .isEqualTo(ResolutionAction.TIMED_OUT);
        }

        @Test
        void trialInFlightAfterCrashIsDetected() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(trialStarting(2, "trial-1", "step-2"));
            // <crash — trial never ended>

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.inFlightTrialIds()).containsExactly("trial-1");
            assertThat(result.completedTrialIds()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Clean Shutdown Recovery
    // -----------------------------------------------------------------------

    @Nested
    class CleanShutdownRecovery {

        @Test
        void cleanShutdownInFlightStepsGetResumeResolution() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            // Clean shutdown while step is in-flight
            manager.recordSuspension(EXEC_ID, PLAN_ID, "User requested pause");

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.wasCleanShutdown()).isTrue();
            assertThat(result.inFlightStepIds()).containsExactly("step-1");
            assertThat(result.inFlightResolutions().get("step-1").action())
                .isEqualTo(ResolutionAction.RESUME);
        }

        @Test
        void cleanShutdownWithNoInFlightSteps() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(3, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordSuspension(EXEC_ID, PLAN_ID, "Planned maintenance");

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.wasCleanShutdown()).isTrue();
            assertThat(result.inFlightStepIds()).isEmpty();
            assertThat(result.inFlightResolutions()).isEmpty();
            assertThat(result.completedStepIds()).containsExactly("step-1");
        }
    }

    // -----------------------------------------------------------------------
    // Idempotency Classification
    // -----------------------------------------------------------------------

    @Nested
    class IdempotencyClassification {

        @Test
        void deployElementIsIdempotent() {
            assertThat(manager.stepIdempotencyClass(AtomicStep.StepType.DEPLOY_ELEMENT))
                .isEqualTo(IdempotencyClass.IDEMPOTENT);
        }

        @Test
        void trialStepIsIdempotent() {
            assertThat(manager.stepIdempotencyClass(AtomicStep.StepType.TRIAL_STEP))
                .isEqualTo(IdempotencyClass.IDEMPOTENT);
        }

        @Test
        void barrierSyncIsNonIdempotent() {
            assertThat(manager.stepIdempotencyClass(AtomicStep.StepType.BARRIER_SYNC))
                .isEqualTo(IdempotencyClass.NON_IDEMPOTENT);
        }

        @Test
        void checkpointStateIsNonIdempotent() {
            assertThat(manager.stepIdempotencyClass(AtomicStep.StepType.CHECKPOINT_STATE))
                .isEqualTo(IdempotencyClass.NON_IDEMPOTENT);
        }

        @Test
        void teardownElementIsNonIdempotent() {
            assertThat(manager.stepIdempotencyClass(AtomicStep.StepType.TEARDOWN_ELEMENT))
                .isEqualTo(IdempotencyClass.NON_IDEMPOTENT);
        }

        @Test
        void notifyTrialStartIsNonIdempotent() {
            assertThat(manager.stepIdempotencyClass(AtomicStep.StepType.NOTIFY_TRIAL_START))
                .isEqualTo(IdempotencyClass.NON_IDEMPOTENT);
        }

        @Test
        void notifyTrialEndIsNonIdempotent() {
            assertThat(manager.stepIdempotencyClass(AtomicStep.StepType.NOTIFY_TRIAL_END))
                .isEqualTo(IdempotencyClass.NON_IDEMPOTENT);
        }

        @Test
        void awaitElementIsNonIdempotent() {
            assertThat(manager.stepIdempotencyClass(AtomicStep.StepType.AWAIT_ELEMENT))
                .isEqualTo(IdempotencyClass.NON_IDEMPOTENT);
        }
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    @Nested
    class Cleanup {

        @Test
        void cleanupRemovesAllState() {
            ExecutionPlan plan = planWithDeployAndTrialSteps();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(3, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));

            manager.cleanup(EXEC_ID);

            RecoveryResult result = manager.recover(EXEC_ID, plan);
            assertThat(result.completedStepIds()).isEmpty();
            assertThat(result.failedStepIds()).isEmpty();
            assertThat(result.inFlightStepIds()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // isStepCompleted
    // -----------------------------------------------------------------------

    @Nested
    class StepCompletionCheck {

        @Test
        void isStepCompletedReturnsTrueForCompletedStep() {
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(3, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));

            assertThat(manager.isStepCompleted(EXEC_ID, "step-1")).isTrue();
        }

        @Test
        void isStepCompletedReturnsFalseForInFlightStep() {
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));

            assertThat(manager.isStepCompleted(EXEC_ID, "step-1")).isFalse();
        }

        @Test
        void isStepCompletedReturnsFalseForUnknownStep() {
            assertThat(manager.isStepCompleted(EXEC_ID, "nonexistent")).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Result Persistence
    // -----------------------------------------------------------------------

    @Nested
    class ResultPersistence {

        @Test
        void saveAndRetrieveTrialResult() {
            TrialResult result = createTrialResult("trial-1", TrialStatus.COMPLETED);
            manager.saveTrialResult(EXEC_ID, result);

            Optional<TrialResult> retrieved = manager.getTrialResult("trial-1");
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().trial().id()).isEqualTo("trial-1");
            assertThat(retrieved.get().status()).isEqualTo(TrialStatus.COMPLETED);
        }

        @Test
        void getTrialResultsForExecution() {
            TrialResult r1 = createTrialResult("trial-1", TrialStatus.COMPLETED);
            TrialResult r2 = createTrialResult("trial-2", TrialStatus.FAILED);
            TrialResult r3 = createTrialResult("trial-3", TrialStatus.COMPLETED);

            manager.saveTrialResult(EXEC_ID, r1);
            manager.saveTrialResult(EXEC_ID, r2);
            manager.saveTrialResult("other-exec", r3);

            List<TrialResult> execResults = manager.getTrialResults(EXEC_ID);
            assertThat(execResults).hasSize(2);
            assertThat(execResults).extracting(r -> r.trial().id())
                .containsExactlyInAnyOrder("trial-1", "trial-2");

            List<TrialResult> otherResults = manager.getTrialResults("other-exec");
            assertThat(otherResults).hasSize(1);
            assertThat(otherResults.get(0).trial().id()).isEqualTo("trial-3");
        }

        @Test
        void cleanupRemovesTrialResults() {
            TrialResult r1 = createTrialResult("trial-1", TrialStatus.COMPLETED);
            TrialResult r2 = createTrialResult("trial-2", TrialStatus.COMPLETED);
            manager.saveTrialResult(EXEC_ID, r1);
            manager.saveTrialResult(EXEC_ID, r2);

            manager.cleanup(EXEC_ID);

            assertThat(manager.getTrialResults(EXEC_ID)).isEmpty();
            assertThat(manager.getTrialResult("trial-1")).isEmpty();
            assertThat(manager.getTrialResult("trial-2")).isEmpty();
        }

        @Test
        void getTrialResultForNonexistentTrialReturnsEmpty() {
            assertThat(manager.getTrialResult("nonexistent")).isEmpty();
        }

        @Test
        void saveTrialResultOverwritesPrevious() {
            TrialResult first = createTrialResult("trial-1", TrialStatus.FAILED);
            TrialResult second = createTrialResult("trial-1", TrialStatus.COMPLETED);

            manager.saveTrialResult(EXEC_ID, first);
            manager.saveTrialResult(EXEC_ID, second);

            Optional<TrialResult> retrieved = manager.getTrialResult("trial-1");
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().status()).isEqualTo(TrialStatus.COMPLETED);

            // Should still be one result for the execution, not two
            assertThat(manager.getTrialResults(EXEC_ID)).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // Stateful Execution Flow Resume Tests
    // -----------------------------------------------------------------------

    @Nested
    class StatefulExecutionFlowResume {

        ///
        /// Simulates a complete deploy → execute → teardown flow that is
        /// interrupted at the deployment phase and then resumed. Verifies
        /// that the resumed execution sees exactly the correct completed,
        /// in-flight, and pending state.
        ///
        @Test
        void resumeAfterCrashDuringDeployment() {
            ExecutionPlan plan = planWithFullLifecycle();

            // Phase 1: Fresh start — deploy element A succeeds, deploy B crashes mid-flight
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(phaseTransition(2,
                Executor.ExecutionPhase.INITIALIZING, Executor.ExecutionPhase.DEPLOYING));
            manager.recordEvent(elementStateChanged(3, "elem-A",
                Element.OperationalState.INACTIVE, Element.OperationalState.PROVISIONING));
            manager.recordEvent(stepStarted(4, "deploy-A", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(5, "deploy-A", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(elementStateChanged(6, "elem-A",
                Element.OperationalState.PROVISIONING, Element.OperationalState.READY));
            manager.recordEvent(stepStarted(7, "deploy-B", AtomicStep.StepType.DEPLOY_ELEMENT));
            // <CRASH during deploy-B>

            RecoveryResult result1 = manager.recover(EXEC_ID, plan);

            // deploy-A completed, deploy-B in-flight with RETRY
            assertThat(result1.completedStepIds()).containsExactly("deploy-A");
            assertThat(result1.inFlightStepIds()).containsExactly("deploy-B");
            assertThat(result1.inFlightResolutions().get("deploy-B").action())
                .isEqualTo(ResolutionAction.RETRY);
            assertThat(result1.elementStates())
                .containsEntry("elem-A", Element.OperationalState.READY);
            assertThat(result1.wasCleanShutdown()).isFalse();

            // Phase 2: Resume — retry deploy-B and continue to execution
            // (Sequence continues from where we left off)
            manager.recordEvent(stepStarted(8, "deploy-B", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(9, "deploy-B", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(elementStateChanged(10, "elem-B",
                Element.OperationalState.INACTIVE, Element.OperationalState.READY));
            manager.recordEvent(phaseTransition(11,
                Executor.ExecutionPhase.DEPLOYING, Executor.ExecutionPhase.EXECUTING));
            manager.recordEvent(stepStarted(12, "trial-1", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(13, "trial-1", "trial-1"));
            manager.recordEvent(trialEnded(14, "trial-1", "trial-1"));
            manager.recordEvent(stepCompleted(15, "trial-1", AtomicStep.StepType.TRIAL_STEP));

            RecoveryResult result2 = manager.recover(EXEC_ID, plan);

            assertThat(result2.completedStepIds())
                .containsExactlyInAnyOrder("deploy-A", "deploy-B", "trial-1");
            assertThat(result2.inFlightStepIds()).isEmpty();
            assertThat(result2.completedTrialIds()).containsExactly("trial-1");
            assertThat(result2.elementStates())
                .containsEntry("elem-A", Element.OperationalState.READY)
                .containsEntry("elem-B", Element.OperationalState.READY);
        }

        ///
        /// Simulates an execution that is cleanly suspended mid-trial,
        /// checkpointed, and then resumed. Verifies that post-resume
        /// recovery merges checkpoint state with post-checkpoint events.
        ///
        @Test
        void resumeFromCheckpointAfterCleanSuspension() {
            ExecutionPlan plan = planWithMultipleTrials();

            // Phase 1: Run trials 1-2, checkpoint, start trial 3, suspend cleanly
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(phaseTransition(2,
                Executor.ExecutionPhase.INITIALIZING, Executor.ExecutionPhase.EXECUTING));

            // Trial 1 completes
            manager.recordEvent(stepStarted(3, "trial-step-1", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(4, "trial-1", "trial-step-1"));
            manager.recordEvent(trialEnded(5, "trial-1", "trial-step-1"));
            manager.recordEvent(stepCompleted(6, "trial-step-1", AtomicStep.StepType.TRIAL_STEP));

            // Trial 2 completes
            manager.recordEvent(stepStarted(7, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(8, "trial-2", "trial-step-2"));
            manager.recordEvent(trialEnded(9, "trial-2", "trial-step-2"));
            manager.recordEvent(stepCompleted(10, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));

            // Checkpoint after trial 2
            manager.recordEvent(checkpointCreated(11, "cp-1"));
            manager.checkpoint(createCheckpoint("cp-1", PLAN_ID,
                List.of("trial-step-1", "trial-step-2"),
                List.of("trial-1", "trial-2")));

            // Trial 3 starts, then clean suspension
            manager.recordEvent(stepStarted(12, "trial-step-3", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(13, "trial-3", "trial-step-3"));
            manager.recordSuspension(EXEC_ID, PLAN_ID, "User requested pause");

            RecoveryResult result1 = manager.recover(EXEC_ID, plan);

            // Trials 1+2 from checkpoint, trial-step-3 in-flight with RESUME
            assertThat(result1.completedStepIds())
                .containsExactlyInAnyOrder("trial-step-1", "trial-step-2");
            assertThat(result1.completedTrialIds())
                .containsExactlyInAnyOrder("trial-1", "trial-2");
            assertThat(result1.inFlightStepIds()).containsExactly("trial-step-3");
            assertThat(result1.inFlightTrialIds()).containsExactly("trial-3");
            assertThat(result1.wasCleanShutdown()).isTrue();
            assertThat(result1.inFlightResolutions().get("trial-step-3").action())
                .isEqualTo(ResolutionAction.RESUME);

            // Phase 2: Resume — complete trial 3 and trial 4
            // Note: sequence continues from the last known number
            long nextSeq = 15; // after suspension at 14
            manager.recordEvent(stepStarted(nextSeq++, "trial-step-3", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(nextSeq++, "trial-3", "trial-step-3"));
            manager.recordEvent(trialEnded(nextSeq++, "trial-3", "trial-step-3"));
            manager.recordEvent(stepCompleted(nextSeq++, "trial-step-3", AtomicStep.StepType.TRIAL_STEP));

            manager.recordEvent(stepStarted(nextSeq++, "trial-step-4", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(nextSeq++, "trial-4", "trial-step-4"));
            manager.recordEvent(trialEnded(nextSeq++, "trial-4", "trial-step-4"));
            manager.recordEvent(stepCompleted(nextSeq++, "trial-step-4", AtomicStep.StepType.TRIAL_STEP));

            RecoveryResult result2 = manager.recover(EXEC_ID, plan);

            assertThat(result2.completedStepIds()).containsExactlyInAnyOrder(
                "trial-step-1", "trial-step-2", "trial-step-3", "trial-step-4");
            assertThat(result2.completedTrialIds()).containsExactlyInAnyOrder(
                "trial-1", "trial-2", "trial-3", "trial-4");
            assertThat(result2.inFlightStepIds()).isEmpty();
            assertThat(result2.inFlightTrialIds()).isEmpty();
        }

        ///
        /// Simulates a full deploy → trial → teardown lifecycle where
        /// a crash occurs during teardown. Verifies that non-idempotent
        /// teardown steps are resolved as FAIL while idempotent steps
        /// that completed earlier are preserved.
        ///
        @Test
        void crashDuringTeardownResolvesCorrectly() {
            ExecutionPlan plan = planWithFullLifecycle();

            manager.recordEvent(executionStarted(1));
            // Deploy
            manager.recordEvent(stepStarted(2, "deploy-A", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(3, "deploy-A", AtomicStep.StepType.DEPLOY_ELEMENT));
            // Trial
            manager.recordEvent(stepStarted(4, "trial-1", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(5, "trial-1", "trial-1"));
            manager.recordEvent(trialEnded(6, "trial-1", "trial-1"));
            manager.recordEvent(stepCompleted(7, "trial-1", AtomicStep.StepType.TRIAL_STEP));
            // Teardown — crashes mid-flight
            manager.recordEvent(phaseTransition(8,
                Executor.ExecutionPhase.EXECUTING, Executor.ExecutionPhase.TEARING_DOWN));
            manager.recordEvent(stepStarted(9, "teardown-A", AtomicStep.StepType.TEARDOWN_ELEMENT));
            // <CRASH during teardown>

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            assertThat(result.completedStepIds())
                .containsExactlyInAnyOrder("deploy-A", "trial-1");
            assertThat(result.completedTrialIds()).containsExactly("trial-1");
            assertThat(result.inFlightStepIds()).containsExactly("teardown-A");
            // Teardown is non-idempotent → FAIL
            assertThat(result.inFlightResolutions().get("teardown-A").action())
                .isEqualTo(ResolutionAction.FAIL);
        }

        ///
        /// Simulates multiple crashes and resumes at different points in
        /// the execution flow, verifying that state accumulates correctly
        /// across all recovery cycles.
        ///
        @Test
        void multipleRestartsAccumulateStateCorrectly() {
            ExecutionPlan plan = planWithMultipleTrials();

            // Run 1: Start and complete trial-1, crash during trial-2
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "trial-step-1", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(3, "trial-1", "trial-step-1"));
            manager.recordEvent(trialEnded(4, "trial-1", "trial-step-1"));
            manager.recordEvent(stepCompleted(5, "trial-step-1", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(stepStarted(6, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));
            // <CRASH 1>

            RecoveryResult after1 = manager.recover(EXEC_ID, plan);
            assertThat(after1.completedStepIds()).containsExactly("trial-step-1");
            assertThat(after1.inFlightStepIds()).containsExactly("trial-step-2");
            assertThat(after1.inFlightResolutions().get("trial-step-2").action())
                .isEqualTo(ResolutionAction.RETRY);

            // Run 2: Retry trial-2, complete it, crash during trial-3
            manager.recordEvent(stepStarted(7, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(8, "trial-2", "trial-step-2"));
            manager.recordEvent(trialEnded(9, "trial-2", "trial-step-2"));
            manager.recordEvent(stepCompleted(10, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(stepStarted(11, "trial-step-3", AtomicStep.StepType.TRIAL_STEP));
            // <CRASH 2>

            RecoveryResult after2 = manager.recover(EXEC_ID, plan);
            assertThat(after2.completedStepIds())
                .containsExactlyInAnyOrder("trial-step-1", "trial-step-2");
            assertThat(after2.completedTrialIds())
                .containsExactlyInAnyOrder("trial-1", "trial-2");
            assertThat(after2.inFlightStepIds()).containsExactly("trial-step-3");

            // Run 3: Complete trial-3 and finish
            manager.recordEvent(stepStarted(12, "trial-step-3", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(13, "trial-3", "trial-step-3"));
            manager.recordEvent(trialEnded(14, "trial-3", "trial-step-3"));
            manager.recordEvent(stepCompleted(15, "trial-step-3", AtomicStep.StepType.TRIAL_STEP));

            RecoveryResult after3 = manager.recover(EXEC_ID, plan);
            assertThat(after3.completedStepIds())
                .containsExactlyInAnyOrder("trial-step-1", "trial-step-2", "trial-step-3");
            assertThat(after3.completedTrialIds())
                .containsExactlyInAnyOrder("trial-1", "trial-2", "trial-3");
            assertThat(after3.inFlightStepIds()).isEmpty();
        }

        ///
        /// Verifies that element states are preserved across checkpoint
        /// and resume boundaries, and that the resumed execution sees
        /// the correct element states.
        ///
        @Test
        void elementStatesPreservedAcrossCheckpointAndResume() {
            ExecutionPlan plan = planWithFullLifecycle();

            manager.recordEvent(executionStarted(1));
            manager.recordEvent(elementStateChanged(2, "elem-A",
                Element.OperationalState.INACTIVE, Element.OperationalState.PROVISIONING));
            manager.recordEvent(elementStateChanged(3, "elem-A",
                Element.OperationalState.PROVISIONING, Element.OperationalState.READY));
            manager.recordEvent(elementStateChanged(4, "elem-B",
                Element.OperationalState.INACTIVE, Element.OperationalState.PROVISIONING));
            manager.recordEvent(stepStarted(5, "deploy-A", AtomicStep.StepType.DEPLOY_ELEMENT));
            manager.recordEvent(stepCompleted(6, "deploy-A", AtomicStep.StepType.DEPLOY_ELEMENT));

            // Checkpoint
            manager.recordEvent(checkpointCreated(7, "cp-1"));
            manager.checkpoint(createCheckpoint("cp-1", PLAN_ID,
                List.of("deploy-A"), List.of()));

            // More element state changes after checkpoint
            manager.recordEvent(elementStateChanged(8, "elem-B",
                Element.OperationalState.PROVISIONING, Element.OperationalState.READY));
            manager.recordEvent(elementStateChanged(9, "elem-A",
                Element.OperationalState.READY, Element.OperationalState.RUNNING));

            RecoveryResult result = manager.recover(EXEC_ID, plan);

            // elem-A transitioned to RUNNING after checkpoint
            assertThat(result.elementStates())
                .containsEntry("elem-A", Element.OperationalState.RUNNING)
                .containsEntry("elem-B", Element.OperationalState.READY);
        }

        ///
        /// Verifies that isStepCompleted correctly reflects state after
        /// various execution flow phases.
        ///
        @Test
        void isStepCompletedReflectsExecutionProgress() {
            manager.recordEvent(executionStarted(1));

            // Step not started yet
            assertThat(manager.isStepCompleted(EXEC_ID, "step-1")).isFalse();

            // Step started but not completed
            manager.recordEvent(stepStarted(2, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            assertThat(manager.isStepCompleted(EXEC_ID, "step-1")).isFalse();

            // Step completed
            manager.recordEvent(stepCompleted(3, "step-1", AtomicStep.StepType.DEPLOY_ELEMENT));
            assertThat(manager.isStepCompleted(EXEC_ID, "step-1")).isTrue();

            // Other step still not completed
            assertThat(manager.isStepCompleted(EXEC_ID, "step-2")).isFalse();
        }

        ///
        /// Simulates a scenario where execution is suspended, resumed,
        /// more work is done, then suspended again. Verifies that
        /// multiple suspend/resume cycles work correctly.
        ///
        @Test
        void multipleSuspendResumeCycles() {
            ExecutionPlan plan = planWithMultipleTrials();

            // Cycle 1: Start, do trial-1, suspend
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "trial-step-1", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(3, "trial-1", "trial-step-1"));
            manager.recordEvent(trialEnded(4, "trial-1", "trial-step-1"));
            manager.recordEvent(stepCompleted(5, "trial-step-1", AtomicStep.StepType.TRIAL_STEP));
            manager.recordSuspension(EXEC_ID, PLAN_ID, "Pause 1");

            RecoveryResult r1 = manager.recover(EXEC_ID, plan);
            assertThat(r1.wasCleanShutdown()).isTrue();
            assertThat(r1.completedTrialIds()).containsExactly("trial-1");

            // Cycle 2: Resume, do trial-2, suspend again
            manager.recordEvent(stepStarted(7, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(8, "trial-2", "trial-step-2"));
            manager.recordEvent(trialEnded(9, "trial-2", "trial-step-2"));
            manager.recordEvent(stepCompleted(10, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordSuspension(EXEC_ID, PLAN_ID, "Pause 2");

            RecoveryResult r2 = manager.recover(EXEC_ID, plan);
            assertThat(r2.wasCleanShutdown()).isTrue();
            assertThat(r2.completedTrialIds())
                .containsExactlyInAnyOrder("trial-1", "trial-2");

            // Cycle 3: Resume, do trial-3, complete normally
            manager.recordEvent(stepStarted(12, "trial-step-3", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(13, "trial-3", "trial-step-3"));
            manager.recordEvent(trialEnded(14, "trial-3", "trial-step-3"));
            manager.recordEvent(stepCompleted(15, "trial-step-3", AtomicStep.StepType.TRIAL_STEP));

            RecoveryResult r3 = manager.recover(EXEC_ID, plan);
            assertThat(r3.wasCleanShutdown()).isFalse(); // no suspension at end
            assertThat(r3.completedTrialIds())
                .containsExactlyInAnyOrder("trial-1", "trial-2", "trial-3");
            assertThat(r3.inFlightStepIds()).isEmpty();
        }

        ///
        /// Verifies that trial results persisted via {@link ExecutionStateManager#saveTrialResult}
        /// survive across recovery cycles (suspend → resume → verify results still present).
        ///
        @Test
        void trialResultsSurviveAcrossRecoveryCycles() {
            ExecutionPlan plan = planWithMultipleTrials();

            // Phase 1: Execute trial-1, save its result, then suspend
            manager.recordEvent(executionStarted(1));
            manager.recordEvent(stepStarted(2, "trial-step-1", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(3, "trial-1", "trial-step-1"));
            manager.recordEvent(trialEnded(4, "trial-1", "trial-step-1"));
            manager.recordEvent(stepCompleted(5, "trial-step-1", AtomicStep.StepType.TRIAL_STEP));

            TrialResult result1 = createTrialResult("trial-1", TrialStatus.COMPLETED);
            manager.saveTrialResult(EXEC_ID, result1);

            manager.recordSuspension(EXEC_ID, PLAN_ID, "Pause after trial-1");

            // Verify result survives after suspend
            assertThat(manager.getTrialResult("trial-1")).isPresent();
            assertThat(manager.getTrialResults(EXEC_ID)).hasSize(1);

            // Phase 2: Resume, execute trial-2, save its result
            manager.recordEvent(stepStarted(7, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));
            manager.recordEvent(trialStarting(8, "trial-2", "trial-step-2"));
            manager.recordEvent(trialEnded(9, "trial-2", "trial-step-2"));
            manager.recordEvent(stepCompleted(10, "trial-step-2", AtomicStep.StepType.TRIAL_STEP));

            TrialResult result2 = createTrialResult("trial-2", TrialStatus.COMPLETED);
            manager.saveTrialResult(EXEC_ID, result2);

            // Both results should be present
            assertThat(manager.getTrialResults(EXEC_ID)).hasSize(2);
            assertThat(manager.getTrialResult("trial-1")).isPresent();
            assertThat(manager.getTrialResult("trial-2")).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // Helper: Execution plan factories
    // -----------------------------------------------------------------------

    private ExecutionPlan planWithDeployAndTrialSteps() {
        return createExecutionPlan(PLAN_ID, List.of(
            new AtomicStep.DeployElement("step-1", "elem-A", 0, Map.of(),
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of()),
            new AtomicStep.TrialStep("step-2", "trial-1", Map.of(),
                List.of("step-1"), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of())
        ));
    }

    private ExecutionPlan planWithBarrierStep() {
        return createExecutionPlan(PLAN_ID, List.of(
            new AtomicStep.BarrierSync("barrier-1", "sync-point",
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of())
        ));
    }

    private ExecutionPlan planWithMultipleSteps() {
        return createExecutionPlan(PLAN_ID, List.of(
            new AtomicStep.DeployElement("step-1", "elem-A", 0, Map.of(),
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of()),
            new AtomicStep.TrialStep("step-2", "trial-1", Map.of(),
                List.of("step-1"), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of()),
            new AtomicStep.TeardownElement("step-3", "elem-A", 0, false,
                List.of("step-2"), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of())
        ));
    }

    private ExecutionPlan planWithFullLifecycle() {
        return createExecutionPlan(PLAN_ID, List.of(
            new AtomicStep.DeployElement("deploy-A", "elem-A", 0, Map.of(),
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of()),
            new AtomicStep.DeployElement("deploy-B", "elem-B", 0, Map.of(),
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of()),
            new AtomicStep.TrialStep("trial-1", "trial-1", Map.of(),
                List.of("deploy-A", "deploy-B"), Optional.empty(),
                AtomicStep.ResourceRequirements.minimal(), Optional.empty(), Map.of()),
            new AtomicStep.TeardownElement("teardown-A", "elem-A", 0, false,
                List.of("trial-1"), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of())
        ));
    }

    private ExecutionPlan planWithMultipleTrials() {
        return createExecutionPlan(PLAN_ID, List.of(
            new AtomicStep.TrialStep("trial-step-1", "trial-1", Map.of(),
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of()),
            new AtomicStep.TrialStep("trial-step-2", "trial-2", Map.of(),
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of()),
            new AtomicStep.TrialStep("trial-step-3", "trial-3", Map.of(),
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of()),
            new AtomicStep.TrialStep("trial-step-4", "trial-4", Map.of(),
                List.of(), Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(), Map.of())
        ));
    }

    // -----------------------------------------------------------------------
    // Helper: Event factories
    // -----------------------------------------------------------------------

    private static JournalEvent executionStarted(long seq) {
        return new JournalEvent.ExecutionStarted(
            seq, EXEC_ID, PLAN_ID, Instant.now(), Optional.empty(), Map.of());
    }

    private static JournalEvent phaseTransition(long seq,
            Executor.ExecutionPhase from, Executor.ExecutionPhase to) {
        return new JournalEvent.PhaseTransition(
            seq, EXEC_ID, PLAN_ID, Instant.now(), from, to);
    }

    private static JournalEvent stepStarted(long seq, String stepId,
            AtomicStep.StepType stepType) {
        return new JournalEvent.StepStarted(
            seq, EXEC_ID, PLAN_ID, Instant.now(), stepId, stepType, Optional.empty());
    }

    private static JournalEvent stepCompleted(long seq, String stepId,
            AtomicStep.StepType stepType) {
        return new JournalEvent.StepCompleted(
            seq, EXEC_ID, PLAN_ID, Instant.now(), stepId, stepType,
            Duration.ofMillis(100), Map.of());
    }

    private static JournalEvent stepFailed(long seq, String stepId,
            AtomicStep.StepType stepType) {
        return new JournalEvent.StepFailed(
            seq, EXEC_ID, PLAN_ID, Instant.now(), stepId, stepType,
            "TestError", "Test failure", false, 1);
    }

    private static JournalEvent stepSkipped(long seq, String stepId) {
        return new JournalEvent.StepSkipped(
            seq, EXEC_ID, PLAN_ID, Instant.now(), stepId, "Test skip reason");
    }

    private static JournalEvent elementStateChanged(long seq, String elementName,
            Element.OperationalState from, Element.OperationalState to) {
        return new JournalEvent.ElementStateChanged(
            seq, EXEC_ID, PLAN_ID, Instant.now(), elementName, from, to,
            from + " → " + to);
    }

    private static JournalEvent trialStarting(long seq, String trialId, String stepId) {
        return new JournalEvent.TrialStarting(
            seq, EXEC_ID, PLAN_ID, Instant.now(), trialId, stepId);
    }

    private static JournalEvent trialEnded(long seq, String trialId, String stepId) {
        return new JournalEvent.TrialEnded(
            seq, EXEC_ID, PLAN_ID, Instant.now(), trialId, stepId, TrialStatus.COMPLETED);
    }

    private static JournalEvent checkpointCreated(long seq, String checkpointId) {
        return new JournalEvent.CheckpointCreated(
            seq, EXEC_ID, PLAN_ID, Instant.now(), checkpointId);
    }
}
