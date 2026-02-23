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

///
/// Runtime status of an {@link ExecutionGraph.Edge} within a {@link LiveExecutionGraph}.
///
/// Edge statuses are derived from the {@link StepStatus} of the source and target
/// steps. They describe the current state of the dependency relationship.
///
/// ## Derivation Rules
///
/// ```
/// Edge Status Derivation:
///   source FAILED/SKIPPED/UNREACHABLE → FAILED
///   source COMPLETED and target IN_PROGRESS → ACTIVE
///   source COMPLETED → SATISFIED
///   otherwise → PENDING
/// ```
///
public enum EdgeStatus {

    /// The dependency is satisfied — source step completed successfully.
    SATISFIED,

    /// The dependency is satisfied and the target step is currently executing.
    ACTIVE,

    /// The dependency is not yet satisfied — source step has not completed.
    PENDING,

    /// The dependency can never be satisfied because the source step
    /// failed, was skipped, or is unreachable.
    FAILED
}
