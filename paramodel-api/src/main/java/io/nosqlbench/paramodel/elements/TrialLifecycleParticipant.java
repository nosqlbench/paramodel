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
package io.nosqlbench.paramodel.elements;

///
/// Contract for receiving trial boundary notifications.
///
/// Elements that participate in study execution are notified when trial
/// timeframes begin and end, regardless of whether the element itself is
/// being started or stopped for that trial. This allows long-lived or
/// shared elements to correlate their observations (metrics, logs,
/// artifacts) to specific trial windows.
///
/// ## Notification Ordering
///
/// Notifications are delivered in dependency/scope order, nesting cleanly
/// across shared and upstream boundaries:
///
/// - **{@link #onTrialStarting}**: outermost (longest-lived) elements
///   are notified first, innermost (shortest-lived) last.
/// - **{@link #onTrialEnding}**: innermost elements are notified first,
///   outermost last.
///
/// This mirrors constructor/destructor ordering and ensures that an outer
/// element's observation window fully encloses any inner element's activity:
///
/// ```
/// node.onTrialStarting(ctx)        ─┐
///   process.onTrialStarting(ctx)    │  trial timeframe
///   ...process runs and completes...│
///   process.onTrialEnding(ctx)      │
/// node.onTrialEnding(ctx)          ─┘
/// ```
///
/// ## Active vs Passive Elements
///
/// **Passive elements** (services, infrastructure) typically use these
/// notifications to bracket metric collection windows. They remain running
/// across trial boundaries.
///
/// **Active elements** (commands, benchmarks) complete on their own. When
/// the innermost active element completes, the executor initiates the
/// ending notification cascade outward through the trial stack.
///
/// ## Default Behavior
///
/// Both methods default to no-ops. Elements are not required to act on
/// trial boundary notifications, but they always receive them.
///
/// @see TrialContext
/// @see Element
/// @since 0.1.0
///
public interface TrialLifecycleParticipant {

    ///
    /// Called when a trial timeframe is beginning around this element.
    ///
    /// The executor calls this on each element in the trial stack, ordered
    /// from outermost (longest-lived / started first) to innermost
    /// (shortest-lived / started last). An element may use this to open
    /// metric collection windows, start timers, or prepare trial-scoped
    /// resources.
    ///
    /// ## Contract
    ///
    /// - MUST NOT throw exceptions (log and continue on failure)
    /// - MUST be idempotent if called multiple times for the same trial
    /// - SHOULD complete quickly; expensive work should be asynchronous
    ///
    /// @param context the trial context with trial identity and timestamp
    ///
    default void onTrialStarting(TrialContext context) {}

    ///
    /// Called when a trial timeframe is ending around this element.
    ///
    /// The executor calls this on each element in the trial stack, ordered
    /// from innermost (shortest-lived / started last) to outermost
    /// (longest-lived / started first). An element may use this to close
    /// metric collection windows, flush buffered data, or finalize
    /// trial-scoped artifacts.
    ///
    /// For active elements that complete on their own, the executor calls
    /// this after detecting completion. For passive elements, the executor
    /// calls this when the trial's active element has completed and
    /// the ending cascade reaches them.
    ///
    /// ## Contract
    ///
    /// - MUST NOT throw exceptions (log and continue on failure)
    /// - MUST be idempotent if called multiple times for the same trial
    /// - SHOULD complete quickly; expensive work should be asynchronous
    ///
    /// @param context the trial context with trial identity and timestamp
    ///
    default void onTrialEnding(TrialContext context) {}
}
