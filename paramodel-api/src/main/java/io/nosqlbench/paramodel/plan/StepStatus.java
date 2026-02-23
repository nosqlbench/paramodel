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
/// Runtime status of an {@link AtomicStep} within a {@link LiveExecutionGraph}.
///
/// Step statuses are derived by combining the static {@link ExecutionGraph}
/// structure with runtime {@link ExecutionState}. Terminal statuses indicate
/// that no further transitions will occur for the step; active statuses
/// indicate the step is currently eligible for or undergoing execution.
///
/// ## Status Derivation
///
/// ```
/// Derivation Priority (checked in order):
///   1. completedStepIds  → COMPLETED
///   2. failedStepIds     → FAILED
///   3. skippedStepIds    → SKIPPED
///   4. inFlightStepIds   → IN_PROGRESS
///   5. any dependency FAILED/SKIPPED/UNREACHABLE → UNREACHABLE
///   6. all dependencies COMPLETED → READY
///   7. otherwise → BLOCKED
/// ```
///
public enum StepStatus {

    /// Step completed successfully.
    COMPLETED,

    /// Step failed permanently after exhausting retries.
    FAILED,

    /// Step was skipped (e.g. due to policy or conditions).
    SKIPPED,

    /// Step is currently executing.
    IN_PROGRESS,

    /// Step is eligible for execution (all dependencies satisfied).
    READY,

    /// Step is waiting for one or more dependencies to complete.
    BLOCKED,

    /// Step can never execute because a dependency failed, was skipped,
    /// or is itself unreachable.
    UNREACHABLE;

    /// Returns {@code true} if this status is terminal — no further
    /// transitions will occur for the step.
    ///
    /// @return true for COMPLETED, FAILED, SKIPPED, UNREACHABLE
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED || this == UNREACHABLE;
    }

    /// Returns {@code true} if this status indicates the step is
    /// currently active (executing or eligible for execution).
    ///
    /// @return true for IN_PROGRESS, READY
    public boolean isActive() {
        return this == IN_PROGRESS || this == READY;
    }
}
