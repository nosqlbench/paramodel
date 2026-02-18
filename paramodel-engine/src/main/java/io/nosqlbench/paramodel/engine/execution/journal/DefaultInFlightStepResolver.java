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
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;

import java.time.Instant;
import java.util.*;

///
/// Default implementation of {@link InFlightStepResolver}.
///
/// Applies the following resolution logic to each in-flight step:
///
/// 1. **Deadline passed** → {@link ResolutionAction#TIMED_OUT}
/// 2. **Clean shutdown** (ExecutionSuspended was last event) → {@link ResolutionAction#RESUME}
/// 3. **Idempotent step** (DEPLOY_ELEMENT or TRIAL_STEP) with retries remaining
///    → {@link ResolutionAction#RETRY}
/// 4. **Otherwise** → {@link ResolutionAction#FAIL}
///
/// @see InFlightStepResolver
/// @see ExecutionSnapshot
/// @since 0.1.0
///
public class DefaultInFlightStepResolver implements InFlightStepResolver {

    private static final Set<AtomicStep.StepType> IDEMPOTENT_STEP_TYPES = Set.of(
        AtomicStep.StepType.DEPLOY_ELEMENT,
        AtomicStep.StepType.TRIAL_STEP
    );

    /// Creates a new resolver.
    public DefaultInFlightStepResolver() {}

    @Override
    public Map<String, StepResolution> resolve(ExecutionSnapshot snapshot, ExecutionPlan plan) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(plan, "plan must not be null");

        Map<String, StepResolution> resolutions = new LinkedHashMap<>();
        Instant now = Instant.now();

        Map<String, AtomicStep> stepLookup = buildStepLookup(plan);

        for (Map.Entry<String, JournalEvent.StepStarted> entry :
                snapshot.inFlightStepDetails().entrySet()) {
            String stepId = entry.getKey();
            JournalEvent.StepStarted started = entry.getValue();

            StepResolution resolution = resolveStep(
                stepId, started, snapshot, stepLookup, now);
            resolutions.put(stepId, resolution);
        }

        return Collections.unmodifiableMap(resolutions);
    }

    private StepResolution resolveStep(
            String stepId,
            JournalEvent.StepStarted started,
            ExecutionSnapshot snapshot,
            Map<String, AtomicStep> stepLookup,
            Instant now) {

        // 1. Check deadline
        if (started.deadline().isPresent() && now.isAfter(started.deadline().get())) {
            return new StepResolution(stepId, ResolutionAction.TIMED_OUT,
                "Step deadline passed at " + started.deadline().get());
        }

        // 2. Check clean shutdown
        if (snapshot.wasCleanShutdown()) {
            return new StepResolution(stepId, ResolutionAction.RESUME,
                "Clean shutdown detected; step can be continued");
        }

        // 3. Check idempotent + retries remaining
        if (IDEMPOTENT_STEP_TYPES.contains(started.stepType())) {
            AtomicStep step = stepLookup.get(stepId);
            if (step != null && step.retryPolicy().isPresent()) {
                int maxAttempts = step.retryPolicy().get().maxAttempts();
                if (maxAttempts > 1) {
                    return new StepResolution(stepId, ResolutionAction.RETRY,
                        "Idempotent step type " + started.stepType()
                            + " with retry policy (max " + maxAttempts + " attempts)");
                }
            }
            // Idempotent but no retry policy — still safe to retry once
            return new StepResolution(stepId, ResolutionAction.RETRY,
                "Idempotent step type " + started.stepType() + "; safe to retry");
        }

        // 4. Otherwise fail
        return new StepResolution(stepId, ResolutionAction.FAIL,
            "Non-idempotent step type " + started.stepType()
                + " interrupted without clean shutdown");
    }

    private Map<String, AtomicStep> buildStepLookup(ExecutionPlan plan) {
        Map<String, AtomicStep> lookup = new HashMap<>();
        for (AtomicStep step : plan.steps()) {
            lookup.put(step.id(), step);
        }
        return lookup;
    }
}
