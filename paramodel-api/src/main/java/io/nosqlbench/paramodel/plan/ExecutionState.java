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
/// API-level abstraction for runtime execution state, decoupled from
/// the engine's internal snapshot representation.
///
/// An {@code ExecutionState} captures a point-in-time view of which steps
/// have completed, failed, been skipped, or are currently in-flight. It
/// serves as one of the two inputs to {@link LiveExecutionGraph} (the other
/// being the static {@link ExecutionGraph}).
///
/// ## Usage
///
/// ```java
/// ExecutionState state = ExecutionState.empty();
/// LiveExecutionGraph live = LiveExecutionGraph.create(graph, state);
/// // All root steps will be READY, all others BLOCKED
/// ```
///
/// @see LiveExecutionGraph
/// @see ImmutableExecutionState
///
public interface ExecutionState {

    /// Returns IDs of steps that completed successfully.
    ///
    /// @return completed step IDs (unmodifiable)
    Set<String> completedStepIds();

    /// Returns IDs of steps that failed permanently.
    ///
    /// @return failed step IDs (unmodifiable)
    Set<String> failedStepIds();

    /// Returns IDs of steps that were skipped.
    ///
    /// @return skipped step IDs (unmodifiable)
    Set<String> skippedStepIds();

    /// Returns IDs of steps that are currently executing.
    ///
    /// @return in-flight step IDs (unmodifiable)
    Set<String> inFlightStepIds();

    /// Returns IDs of trials that have completed.
    ///
    /// @return completed trial IDs (unmodifiable)
    Set<String> completedTrialIds();

    /// Returns IDs of trials that are currently executing.
    ///
    /// @return in-flight trial IDs (unmodifiable)
    Set<String> inFlightTrialIds();

    /// Returns the operational state of each element.
    ///
    /// @return element name to operational state mapping (unmodifiable)
    Map<String, Element.OperationalState> elementStates();

    /// Returns an empty execution state representing the pre-execution
    /// state where no steps have been started.
    ///
    /// @return empty execution state
    static ExecutionState empty() {
        return ImmutableExecutionState.EMPTY;
    }
}
