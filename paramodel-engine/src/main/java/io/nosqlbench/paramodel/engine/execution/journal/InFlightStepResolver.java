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

import io.nosqlbench.paramodel.plan.ExecutionPlan;

import java.util.Map;

///
/// Resolves steps that were in-flight when execution was interrupted.
///
/// This is the key stuck-state prevention mechanism: **every in-flight
/// step gets a deterministic resolution**. No step can remain in an
/// unresolvable state after restart. The resolver examines each
/// in-flight step's type, deadline, and the shutdown context to decide
/// the appropriate action.
///
/// ## Resolution Logic
///
/// ```
/// In-flight step detected:
///   +-- Deadline passed? -> TIMED_OUT
///   +-- Clean shutdown (ExecutionSuspended present)? -> RESUME
///   +-- Idempotent step (DEPLOY_ELEMENT, TRIAL_STEP) -> RETRY
///   +-- Otherwise (BARRIER_SYNC, CHECKPOINT_STATE, TEARDOWN_ELEMENT) -> FAIL
/// ```
///
/// ## Idempotency Classification
///
/// Steps are classified as idempotent or non-idempotent by their
/// {@link io.nosqlbench.paramodel.plan.AtomicStep.StepType StepType}:
///
/// - **Idempotent** (`DEPLOY_ELEMENT`, `TRIAL_STEP`): safe to
///   re-execute from scratch because the operation either succeeds
///   with the same result or can detect prior partial completion.
///   Deploying an element that is already deployed is a no-op;
///   re-executing a trial produces a new independent result.
/// - **Non-idempotent** (`BARRIER_SYNC`, `CHECKPOINT_STATE`,
///   `TEARDOWN_ELEMENT`): re-executing could corrupt state. A
///   barrier that was partially signaled, a checkpoint that was
///   partially written, or a teardown that was partially completed
///   cannot safely be restarted from scratch.
///
/// ## Usage
///
/// The engine's default implementation is
/// {@link DefaultInFlightStepResolver}. Host systems may provide
/// custom resolvers if they have domain-specific knowledge about
/// step idempotency or wish to implement custom timeout policies.
///
/// @see ExecutionSnapshot
/// @see JournalStateReconstructor
/// @since 0.1.0
///
public interface InFlightStepResolver {

    /// Resolves all in-flight steps in the snapshot.
    ///
    /// @param snapshot the reconstructed execution state
    /// @param plan the execution plan providing step metadata
    /// @return a map from step ID to its resolution
    Map<String, StepResolution> resolve(ExecutionSnapshot snapshot, ExecutionPlan plan);

    /// Describes how an in-flight step should be resolved.
    ///
    /// @param stepId the step identifier
    /// @param action the resolution action
    /// @param reason human-readable explanation
    record StepResolution(String stepId, ResolutionAction action, String reason) {}

    /// Actions for resolving in-flight steps.
    enum ResolutionAction {
        /// Idempotent step with retries remaining.
        RETRY,
        /// Non-idempotent step or retries exhausted.
        FAIL,
        /// Step deadline has passed.
        TIMED_OUT,
        /// Clean shutdown; step can be continued.
        RESUME
    }
}
