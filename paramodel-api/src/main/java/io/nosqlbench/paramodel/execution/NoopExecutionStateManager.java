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
package io.nosqlbench.paramodel.execution;

import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.util.List;
import java.util.Optional;

///
/// No-op implementation of {@link ExecutionStateManager} for testing
/// and dry-run scenarios.
///
/// All write operations are silently discarded. Recovery always returns
/// empty state. Idempotency checks always return {@code false}.
///
/// This class is package-private; obtain an instance via
/// {@link ExecutionStateManager#noop()}.
///
/// @since 0.1.0
///
final class NoopExecutionStateManager implements ExecutionStateManager {

    static final NoopExecutionStateManager INSTANCE = new NoopExecutionStateManager();

    private NoopExecutionStateManager() {}

    @Override
    public void recordEvent(JournalEvent event) {
        // no-op
    }

    @Override
    public void checkpoint(Executor.Checkpoint checkpoint) {
        // no-op
    }

    @Override
    public RecoveryResult recover(String executionId, ExecutionPlan plan) {
        return RecoveryResult.empty();
    }

    @Override
    public boolean isStepCompleted(String executionId, String stepId) {
        return false;
    }

    @Override
    public void recordSuspension(String executionId, String executionPlanId, String reason) {
        // no-op
    }

    @Override
    public void saveTrialResult(String executionId, TrialResult result) {
        // no-op
    }

    @Override
    public Optional<TrialResult> getTrialResult(String trialId) {
        return Optional.empty();
    }

    @Override
    public List<TrialResult> getTrialResults(String executionId) {
        return List.of();
    }

    @Override
    public void cleanup(String executionId) {
        // no-op
    }
}
