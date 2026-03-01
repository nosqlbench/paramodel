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
package io.nosqlbench.paramodel.tck.mock;

import io.nosqlbench.paramodel.execution.ExecutionStateManager;
import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.mock.execution.MockExecutionStateManager;
import io.nosqlbench.paramodel.mock.plan.MockExecutionPlan;
import io.nosqlbench.paramodel.mock.sequence.MockTrial;
import io.nosqlbench.paramodel.mock.sequence.MockTrialResult;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.TrialResult;
import io.nosqlbench.paramodel.sequence.TrialStatus;
import io.nosqlbench.paramodel.tck.execution.ExecutionStateManagerTCK;

import java.time.Instant;
import java.util.List;
import java.util.Map;

///
/// Runs {@link ExecutionStateManagerTCK} tests against the
/// {@link MockExecutionStateManager}.
///
/// @since 0.1.0
///
class MockExecutionStateManagerTest extends ExecutionStateManagerTCK {

    @Override
    protected ExecutionStateManager createExecutionStateManager() {
        return new MockExecutionStateManager();
    }

    @Override
    protected ExecutionPlan createExecutionPlan(String planId, List<AtomicStep> steps) {
        MockExecutionPlan.Builder builder = MockExecutionPlan.builder(planId, "fp-" + planId);
        for (AtomicStep step : steps) {
            builder.step(step);
        }
        return builder.build();
    }

    @Override
    protected TrialResult createTrialResult(String trialId, TrialStatus status) {
        MockTrial trial = MockTrial.builder().id(trialId).build();
        return MockTrialResult.builder(trial).status(status).build();
    }

    @Override
    protected Executor.Checkpoint createCheckpoint(
            String checkpointId, String executionPlanId,
            List<String> completedStepIds, List<String> completedTrialIds) {
        return new MockCheckpoint(checkpointId, executionPlanId,
            Instant.now(), completedTrialIds, completedStepIds, Map.of());
    }

    private record MockCheckpoint(
        String checkpointId,
        String executionPlanId,
        Instant createdAt,
        List<String> completedTrialIds,
        List<String> completedStepIds,
        Map<String, Object> state
    ) implements Executor.Checkpoint {}
}
