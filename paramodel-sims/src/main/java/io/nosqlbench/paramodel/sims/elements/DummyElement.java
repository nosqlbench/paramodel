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
package io.nosqlbench.paramodel.sims.elements;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.TrialContext;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ParameterView;
import io.nosqlbench.paramodel.parameters.types.DoubleParameter;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.SelectionParameter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

///
/// A simulated element with configurable behavioral parameters for development and testing.
///
/// {@code DummyElement} mimics real infrastructure behavior without interacting with
/// actual systems. It supports configurable startup delays, health check modes,
/// command vs service semantics, and failure rates.
///
/// ## Parameters
///
/// | Name | Type | Domain | Default | Description |
/// |------|------|--------|---------|-------------|
/// | `behavior` | Selection | `"command"`, `"service"` | `"service"` | Command completes on its own; service runs until stopped |
/// | `startup_delay_ms` | Integer | [0, 60000] | 0 | Simulated startup time in ms |
/// | `duration_ms` | Integer | [0, 600000] | 5000 | Run duration for command behavior |
/// | `health_check_mode` | Selection | `"immediate"`, `"delayed"`, `"flaky"`, `"failing"` | `"immediate"` | How the health check behaves |
/// | `health_check_delay_ms` | Integer | [0, 30000] | 0 | Delay before health check passes (for "delayed" mode) |
/// | `failure_rate` | Double | [0.0, 1.0] | 0.0 | Probability of failure per status check (for "flaky" mode) |
///
/// ## Trial Lifecycle Participation
///
/// {@code DummyElement} records trial boundary notifications for observability.
/// Each call to {@link #onTrialStarting} or {@link #onTrialEnding} appends a
/// {@link TrialEvent} to the element's event log, which can be inspected via
/// {@link #trialEvents()}.
///
/// ```java
/// DummyElement dummy = DummyElement.template();
/// dummy.onTrialStarting(TrialContext.now(trial));
/// // ... trial runs ...
/// dummy.onTrialEnding(TrialContext.now(trial));
///
/// List<DummyElement.TrialEvent> events = dummy.trialEvents();
/// // [TrialEvent(STARTING, ctx1), TrialEvent(ENDING, ctx2)]
/// ```
///
/// ## Operational State Observation
///
/// {@code DummyElement} supports programmatic state transitions via
/// {@link #transitionTo(OperationalState, String)}. Registered observers
/// are notified of each transition, and new observers immediately receive
/// the current state on registration (registration-as-catchup semantics).
///
/// ```java
/// DummyElement dummy = DummyElement.template();
/// dummy.observeState(t -> System.out.println(t.from() + " → " + t.to()));
/// // prints: UNKNOWN → INACTIVE
///
/// dummy.transitionTo(OperationalState.RUNNING, "Simulated start");
/// // prints: INACTIVE → RUNNING
/// ```
///
/// ## Usage
///
/// ```java
/// // Get the canonical template with default values
/// Element dummy = DummyElement.template();
///
/// // Build a custom dummy element
/// Element custom = DummyElement.builder("my-dummy").build();
/// ```
///
/// @see SimElementProvider
/// @see io.nosqlbench.paramodel.elements.TrialLifecycleParticipant
/// @see io.nosqlbench.paramodel.elements.OperationalStateObservable
/// @since 0.1.0
///
public class DummyElement implements Element {

    /// Type tag value for all dummy elements.
    public static final String TYPE = "dummy";

    /// Default element name for the canonical template.
    public static final String DEFAULT_NAME = "dummy";

    private static final Parameter<?> BEHAVIOR = SelectionParameter
            .of("behavior", Set.of("command", "service"))
            .withDefault(List.of("service"));

    private static final Parameter<?> STARTUP_DELAY_MS = IntegerParameter
            .range("startup_delay_ms", 0, 60_000)
            .withDefault(0);

    private static final Parameter<?> DURATION_MS = IntegerParameter
            .range("duration_ms", 0, 600_000)
            .withDefault(5000);

    private static final Parameter<?> HEALTH_CHECK_MODE = SelectionParameter
            .of("health_check_mode", Set.of("immediate", "delayed", "flaky", "failing"))
            .withDefault(List.of("immediate"));

    private static final Parameter<?> HEALTH_CHECK_DELAY_MS = IntegerParameter
            .range("health_check_delay_ms", 0, 30_000)
            .withDefault(0);

    private static final Parameter<?> FAILURE_RATE = DoubleParameter
            .range("failure_rate", 0.0, 1.0)
            .withDefault(0.0);

    private static final List<Parameter<?>> ALL_PARAMETERS = List.of(
            BEHAVIOR,
            STARTUP_DELAY_MS,
            DURATION_MS,
            HEALTH_CHECK_MODE,
            HEALTH_CHECK_DELAY_MS,
            FAILURE_RATE
    );

    private static final ParameterView PARAMETER_VIEW = ParameterView.of(ALL_PARAMETERS);

    private final String name;
    private final List<TrialEvent> trialEvents = new CopyOnWriteArrayList<>();
    private final List<StateTransitionListener> stateListeners = new CopyOnWriteArrayList<>();
    private volatile OperationalState currentState = OperationalState.INACTIVE;
    private volatile String currentSummary = "Not started";

    private DummyElement(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    /// Returns the canonical template with default parameter values.
    ///
    /// @return a pre-built dummy element template
    public static DummyElement template() {
        return new DummyElement(DEFAULT_NAME);
    }

    /// Creates a builder for constructing a dummy element with a custom name.
    ///
    /// @param name the element name
    /// @return a new builder
    public static Builder builder(String name) {
        return new Builder(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> tags() {
        return Map.of("name", name, "type", TYPE);
    }

    @Override
    public List<Parameter<?>> parameters() {
        return ALL_PARAMETERS;
    }

    @Override
    public ParameterView parameterView() {
        return PARAMETER_VIEW;
    }

    @Override
    public List<Element> dependencies() {
        return List.of();
    }

    @Override
    public Optional<HealthCheckSpec> healthCheck() {
        return Optional.of(new DummyHealthCheckSpec());
    }

    @Override
    public LiveStatusSummary statusCheck() {
        return new LiveStatusSummary(currentState, currentSummary);
    }

    @Override
    public Optional<InstancingScope> instancingScope() {
        return Optional.empty();
    }

    // -----------------------------------------------------------------------
    // Operational state observation
    // -----------------------------------------------------------------------

    @Override
    public StateObservation observeState(StateTransitionListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        stateListeners.add(listener);
        listener.onStateTransition(new StateTransition(
            OperationalState.UNKNOWN,
            currentState,
            currentSummary,
            Instant.now()
        ));
        return () -> stateListeners.remove(listener);
    }

    /// Programmatically transitions this element to a new operational state.
    ///
    /// All registered observers are notified of the transition. This method
    /// is intended for testing and simulation — it allows test harnesses to
    /// drive state changes and observe the resulting notifications.
    ///
    /// @param newState the target state, must not be null
    /// @param summary  a human-readable description of the transition, must not be null
    /// @throws NullPointerException if newState or summary is null
    public void transitionTo(OperationalState newState, String summary) {
        Objects.requireNonNull(newState, "newState must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        OperationalState previousState = this.currentState;
        this.currentState = newState;
        this.currentSummary = summary;
        StateTransition transition = new StateTransition(
            previousState, newState, summary, Instant.now()
        );
        for (StateTransitionListener listener : stateListeners) {
            listener.onStateTransition(transition);
        }
    }

    // -----------------------------------------------------------------------
    // Trial lifecycle participation
    // -----------------------------------------------------------------------

    @Override
    public void onTrialStarting(TrialContext context) {
        trialEvents.add(new TrialEvent(TrialEvent.Kind.STARTING, context));
    }

    @Override
    public void onTrialEnding(TrialContext context) {
        trialEvents.add(new TrialEvent(TrialEvent.Kind.ENDING, context));
    }

    /// Returns the recorded trial lifecycle events in chronological order.
    ///
    /// @return unmodifiable list of trial events, never null
    public List<TrialEvent> trialEvents() {
        return Collections.unmodifiableList(trialEvents);
    }

    /// A recorded trial boundary notification.
    ///
    /// @param kind    whether this is a starting or ending notification
    /// @param context the trial context provided at the boundary
    public record TrialEvent(Kind kind, TrialContext context) {

        /// The kind of trial boundary event.
        public enum Kind {
            /// A trial timeframe is beginning.
            STARTING,
            /// A trial timeframe is ending.
            ENDING
        }
    }

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    /// Health check specification for dummy elements.
    ///
    /// The host system owns the health check mechanism — this spec only
    /// carries timing parameters for coordination. Uses reasonable defaults
    /// for simulated environments.
    private static final class DummyHealthCheckSpec implements HealthCheckSpec {

        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
        private static final int DEFAULT_MAX_RETRIES = 3;
        private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(5);

        @Override
        public Duration timeout() {
            return DEFAULT_TIMEOUT;
        }

        @Override
        public int maxRetries() {
            return DEFAULT_MAX_RETRIES;
        }

        @Override
        public Duration retryInterval() {
            return DEFAULT_RETRY_INTERVAL;
        }
    }

    /// Builder for constructing {@link DummyElement} instances with a custom name.
    public static final class Builder {
        private final String name;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        /// Builds the dummy element.
        ///
        /// @return the constructed dummy element
        public DummyElement build() {
            return new DummyElement(name);
        }
    }
}
