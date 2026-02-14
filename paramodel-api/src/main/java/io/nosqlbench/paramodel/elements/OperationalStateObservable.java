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

import java.time.Instant;
import java.util.Objects;

///
/// Contract for observing operational state transitions of an element.
///
/// ## Purpose
///
/// Elements progress through operational states (INACTIVE, STARTING, RUNNING,
/// STOPPED, etc.) during their lifecycle. This interface provides an event-driven
/// mechanism for external observers to be notified of these transitions without
/// polling.
///
/// ## Registration-as-Catchup Semantics
///
/// The core design property of this contract is **registration-as-catchup**:
/// when an observer registers via {@link #observeState}, the implementation
/// MUST immediately deliver a synthetic {@link StateTransition} from
/// {@link Element.OperationalState#UNKNOWN UNKNOWN} to the element's current
/// state. This ensures:
///
/// - **New observers** immediately learn the element's current state without
///   a separate query.
/// - **Restart recovery** is handled by re-registration alone. When a host
///   system restarts and re-registers observers, each observer immediately
///   receives the element's current state, re-establishing the signaling
///   chain without a separate recovery protocol.
///
/// ```
/// First registration (fresh start):
///   observer registers → receives UNKNOWN → INACTIVE
///   element starts     → receives INACTIVE → STARTING
///   element ready      → receives STARTING → READY
///
/// Re-registration (after restart):
///   host restarts, element is still RUNNING
///   observer re-registers → receives UNKNOWN → RUNNING
///   (signaling chain is now fully re-established)
/// ```
///
/// ## Command vs Service Symmetry
///
/// The same observation machinery supports both command (self-completing)
/// and service (externally managed) element semantics:
///
/// - **Command element** completes on its own. The element transitions to
///   STOPPED, and listeners are notified. The executor interprets this as
///   normal trial completion and initiates the trial-ending cascade.
///
/// - **Service element** that unexpectedly transitions to STOPPED or FAILED
///   is detected by the same listeners. The executor interprets this as
///   abnormal early exit and initiates error handling.
///
/// The machinery is identical; only the response handler differs. This
/// symmetry means implementations need not distinguish between command and
/// service in their observation support — the same {@link #observeState}
/// registration works for both.
///
/// ## Thread Safety
///
/// Implementations MUST be thread-safe:
/// - Multiple observers may be registered concurrently.
/// - State transitions may be delivered from any thread.
/// - The immediate delivery on registration may occur on the caller's
///   thread or on a delivery thread, but MUST happen before
///   {@link #observeState} returns.
///
/// ## Relationship to StatusCheck
///
/// {@link Element#statusCheck()} is a point-in-time poll of current state.
/// {@code observeState()} is a push-based subscription to state changes.
/// They are complementary: {@code statusCheck()} provides the seed value
/// for the initial synthetic transition at registration time, while
/// {@code observeState()} provides ongoing change notification.
///
/// ## Engine Rehydration
///
/// When the executor resumes from a checkpoint, it reconstructs element
/// models from persisted state, then re-registers state observers. Because
/// registration delivers the element's current state immediately, the
/// executor does not need a separate "catch up" protocol. The key
/// requirement is that the host system must be able to:
///
/// 1. Reconstruct element instances from checkpoint state, with their
///    current operational state already set (e.g. a service element that
///    was RUNNING before the restart is still RUNNING after).
/// 2. Re-register observers. The immediate delivery on registration
///    re-establishes the signaling chain from whatever state the element
///    is currently in.
///
/// @see Element
/// @see Element.OperationalState
/// @see Element.LiveStatusSummary
/// @since 0.1.0
///
public interface OperationalStateObservable {

    ///
    /// Registers a listener for operational state transitions.
    ///
    /// The listener is immediately called with a synthetic transition from
    /// {@link Element.OperationalState#UNKNOWN UNKNOWN} to the element's
    /// current state before this method returns. Subsequent real transitions
    /// are delivered as they occur.
    ///
    /// ## Contract
    ///
    /// - MUST immediately deliver current state as `UNKNOWN → current`
    ///   before returning.
    /// - MUST deliver subsequent transitions in order, without gaps.
    /// - MUST NOT deliver transitions after {@link StateObservation#cancel()}
    ///   has been called on the returned handle.
    /// - MUST be thread-safe: concurrent registrations and concurrent
    ///   transitions are both valid.
    ///
    /// ## Example
    ///
    /// ```java
    /// Element element = ...;
    ///
    /// StateObservation obs = element.observeState(transition -> {
    ///     System.out.printf("%s → %s: %s%n",
    ///         transition.from(), transition.to(), transition.summary());
    /// });
    /// // Immediately prints: UNKNOWN → INACTIVE: Not started
    ///
    /// // ... element starts, runs, stops — transitions delivered as they occur
    ///
    /// obs.cancel(); // stop receiving transitions
    /// ```
    ///
    /// @param listener the listener to receive state transitions, must not be null
    /// @return a handle for cancelling the observation, never null
    /// @throws NullPointerException if listener is null
    ///
    StateObservation observeState(StateTransitionListener listener);

    ///
    /// A state transition event describing a change in operational state.
    ///
    /// Each transition captures the previous state, new state, a human-readable
    /// summary, and the timestamp of the transition. The initial synthetic
    /// transition delivered at registration time uses
    /// {@link Element.OperationalState#UNKNOWN UNKNOWN} as the {@code from}
    /// state.
    ///
    /// @param from      the previous operational state
    /// @param to        the new operational state
    /// @param summary   human-readable description of the transition
    /// @param timestamp the instant the transition occurred
    ///
    record StateTransition(
        Element.OperationalState from,
        Element.OperationalState to,
        String summary,
        Instant timestamp
    ) {
        /// Validates that no fields are null.
        public StateTransition {
            Objects.requireNonNull(from, "from must not be null");
            Objects.requireNonNull(to, "to must not be null");
            Objects.requireNonNull(summary, "summary must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }
    }

    ///
    /// Listener for operational state transitions.
    ///
    /// Implementations should be lightweight and non-blocking. If a listener
    /// needs to perform expensive work in response to a transition, it should
    /// hand off to another thread.
    ///
    @FunctionalInterface
    interface StateTransitionListener {

        ///
        /// Called when the element transitions between operational states.
        ///
        /// This is called once immediately upon registration (with a synthetic
        /// {@code UNKNOWN → current} transition), and then once for each
        /// subsequent real state transition.
        ///
        /// @param transition the state transition event
        ///
        void onStateTransition(StateTransition transition);
    }

    ///
    /// Handle for an active state observation.
    ///
    /// Returned by {@link #observeState} and used to stop receiving
    /// transition notifications. Cancelling an already-cancelled observation
    /// is a no-op.
    ///
    interface StateObservation {

        ///
        /// Stops the observation. After this call, no further transitions
        /// will be delivered to the associated listener.
        ///
        /// Calling {@code cancel()} on an already-cancelled observation
        /// has no effect.
        ///
        void cancel();
    }
}
