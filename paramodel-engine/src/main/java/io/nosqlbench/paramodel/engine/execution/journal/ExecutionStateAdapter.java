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
import io.nosqlbench.paramodel.plan.ExecutionState;

import java.util.Map;
import java.util.Set;

///
/// Adapter that bridges an engine-level {@link ExecutionSnapshot} to the
/// API-level {@link ExecutionState} interface.
///
/// This is a thin delegation adapter — no data is copied. Changes to the
/// underlying snapshot are reflected through this adapter.
///
public class ExecutionStateAdapter implements ExecutionState {

    private final ExecutionSnapshot snapshot;

    private ExecutionStateAdapter(ExecutionSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /// Creates an {@link ExecutionState} view over an {@link ExecutionSnapshot}.
    ///
    /// @param snapshot the engine-level snapshot
    /// @return an API-level execution state
    public static ExecutionState from(ExecutionSnapshot snapshot) {
        return new ExecutionStateAdapter(snapshot);
    }

    @Override
    public Set<String> completedStepIds() {
        return snapshot.completedStepIds();
    }

    @Override
    public Set<String> failedStepIds() {
        return snapshot.failedStepIds();
    }

    @Override
    public Set<String> skippedStepIds() {
        return snapshot.skippedStepIds();
    }

    @Override
    public Set<String> inFlightStepIds() {
        return snapshot.inFlightStepIds();
    }

    @Override
    public Set<String> completedTrialIds() {
        return snapshot.completedTrialIds();
    }

    @Override
    public Set<String> inFlightTrialIds() {
        return snapshot.inFlightTrialIds();
    }

    @Override
    public Map<String, Element.OperationalState> elementStates() {
        return snapshot.elementStates();
    }
}
