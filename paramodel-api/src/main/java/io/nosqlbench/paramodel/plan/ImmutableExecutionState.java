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
package io.nosqlbench.paramodel.plan;

import io.nosqlbench.paramodel.elements.Element;

import java.util.Map;
import java.util.Set;

///
/// Immutable, defensive-copy implementation of {@link ExecutionState}.
///
/// All collections are copied at construction time via {@link Set#copyOf}
/// and {@link Map#copyOf}, making this record safe for concurrent access,
/// serialization, and use as a test fixture.
///
/// @param completedStepIds IDs of steps that completed successfully
/// @param failedStepIds IDs of steps that failed permanently
/// @param skippedStepIds IDs of steps that were skipped
/// @param inFlightStepIds IDs of steps currently executing
/// @param completedTrialIds IDs of trials that completed
/// @param inFlightTrialIds IDs of trials currently executing
/// @param elementStates operational state of each element
///
public record ImmutableExecutionState(
    Set<String> completedStepIds,
    Set<String> failedStepIds,
    Set<String> skippedStepIds,
    Set<String> inFlightStepIds,
    Set<String> completedTrialIds,
    Set<String> inFlightTrialIds,
    Map<String, Element.OperationalState> elementStates
) implements ExecutionState {

    /// Shared empty instance used by {@link ExecutionState#empty()}.
    static final ImmutableExecutionState EMPTY = new ImmutableExecutionState(
        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of()
    );

    /// Canonical constructor that defensively copies all collections.
    public ImmutableExecutionState {
        completedStepIds = Set.copyOf(completedStepIds);
        failedStepIds = Set.copyOf(failedStepIds);
        skippedStepIds = Set.copyOf(skippedStepIds);
        inFlightStepIds = Set.copyOf(inFlightStepIds);
        completedTrialIds = Set.copyOf(completedTrialIds);
        inFlightTrialIds = Set.copyOf(inFlightTrialIds);
        elementStates = Map.copyOf(elementStates);
    }
}
