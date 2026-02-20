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
package io.nosqlbench.paramodel.engine.planners.reducto;

///
/// Node types in the reducto execution graph.
///
/// Each type maps to a specific {@link io.nosqlbench.paramodel.plan.AtomicStep} subtype
/// during graph linearization.
///
public enum ReductoNodeType {

    /// Seed node representing a trial before lifecycle expansion (Rule 1).
    TRIAL_SEED,

    /// Deploys an element instance with specific parameter bindings.
    ACTIVATE,

    /// Tears down a service element instance.
    DEACTIVATE,

    /// Waits for a command element to complete naturally.
    AWAIT,

    /// Signals non-trial elements that a trial is about to begin.
    NOTIFY_TRIAL_START,

    /// Signals non-trial elements that a trial has ended.
    NOTIFY_TRIAL_END,

    /// Health check gate inserted after activation for elements with health checks.
    READINESS_GATE,

    /// Graph entry sentinel.
    START,

    /// Graph exit sentinel.
    END
}
