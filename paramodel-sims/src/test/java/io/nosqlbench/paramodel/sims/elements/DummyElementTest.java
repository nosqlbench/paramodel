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
import io.nosqlbench.paramodel.elements.ElementProvider;
import io.nosqlbench.paramodel.elements.ElementTypeDescriptor;
import io.nosqlbench.paramodel.elements.ElementTypeDescriptorProvider;
import io.nosqlbench.paramodel.elements.OperationalStateObservable;
import io.nosqlbench.paramodel.elements.OperationalStateObservable.StateObservation;
import io.nosqlbench.paramodel.elements.OperationalStateObservable.StateTransition;
import io.nosqlbench.paramodel.elements.TrialContext;
import io.nosqlbench.paramodel.mock.sequence.MockTrial;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.sequence.Trial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for {@link DummyElement} and the SPI providers in paramodel-sims.
///
class DummyElementTest {

    // -----------------------------------------------------------------------
    // Template structure tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Template Structure")
    class TemplateStructure {

        @Test
        @DisplayName("template has correct name, type tag, and expected parameter count")
        void testTemplateStructure() {
            Element template = DummyElement.template();

            assertThat(template.name()).isEqualTo("dummy");
            assertThat(template.tags()).containsEntry("name", "dummy");
            assertThat(template.tags()).containsEntry("type", "dummy");
            assertThat(template.parameters()).hasSize(6);
        }

        @Test
        @DisplayName("template has no dependencies")
        void testNoDependencies() {
            Element template = DummyElement.template();

            assertThat(template.dependencies()).isEmpty();
        }

        @Test
        @DisplayName("template has no instancing scope")
        void testNoInstancingScope() {
            Element template = DummyElement.template();

            assertThat(template.instancingScope()).isEmpty();
        }

        @Test
        @DisplayName("status check returns inactive at model level")
        void testStatusCheckInactive() {
            Element template = DummyElement.template();

            Element.LiveStatusSummary status = template.statusCheck();
            assertThat(status.state()).isEqualTo(Element.OperationalState.INACTIVE);
            assertThat(status.summary()).isEqualTo("Not started");
        }
    }

    // -----------------------------------------------------------------------
    // Parameter tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Parameters")
    class Parameters {

        @Test
        @DisplayName("all parameters are present and uniquely named")
        void testParameterNamesUnique() {
            Element template = DummyElement.template();
            List<String> names = template.parameters().stream()
                    .map(Parameter::name)
                    .toList();

            assertThat(names).containsExactly(
                    "behavior",
                    "startup_delay_ms",
                    "duration_ms",
                    "health_check_mode",
                    "health_check_delay_ms",
                    "failure_rate"
            );

            Set<String> uniqueNames = Set.copyOf(names);
            assertThat(uniqueNames).hasSameSizeAs(names);
        }

        @Test
        @DisplayName("behavior parameter has correct domain and default")
        void testBehaviorParameter() {
            Parameter<?> behavior = findParameter("behavior");

            assertThat(behavior.defaultValue()).isPresent();
            assertThat(behavior.defaultValue().get()).isEqualTo(List.of("service"));

            // generate should produce a valid value
            Object generated = behavior.generate();
            assertThat(generated).isNotNull();
        }

        @Test
        @DisplayName("startup_delay_ms parameter has correct domain and default")
        void testStartupDelayParameter() {
            Parameter<?> param = findParameter("startup_delay_ms");

            assertThat(param.defaultValue()).isPresent();
            assertThat(param.defaultValue().get()).isEqualTo(0);

            Object generated = param.generate();
            assertThat(generated).isInstanceOf(Integer.class);
        }

        @Test
        @DisplayName("duration_ms parameter has correct domain and default")
        void testDurationParameter() {
            Parameter<?> param = findParameter("duration_ms");

            assertThat(param.defaultValue()).isPresent();
            assertThat(param.defaultValue().get()).isEqualTo(5000);

            Object generated = param.generate();
            assertThat(generated).isInstanceOf(Integer.class);
        }

        @Test
        @DisplayName("health_check_mode parameter has correct domain and default")
        void testHealthCheckModeParameter() {
            Parameter<?> param = findParameter("health_check_mode");

            assertThat(param.defaultValue()).isPresent();
            assertThat(param.defaultValue().get()).isEqualTo(List.of("immediate"));

            Object generated = param.generate();
            assertThat(generated).isNotNull();
        }

        @Test
        @DisplayName("health_check_delay_ms parameter has correct domain and default")
        void testHealthCheckDelayParameter() {
            Parameter<?> param = findParameter("health_check_delay_ms");

            assertThat(param.defaultValue()).isPresent();
            assertThat(param.defaultValue().get()).isEqualTo(0);

            Object generated = param.generate();
            assertThat(generated).isInstanceOf(Integer.class);
        }

        @Test
        @DisplayName("failure_rate parameter has correct domain and default")
        void testFailureRateParameter() {
            Parameter<?> param = findParameter("failure_rate");

            assertThat(param.defaultValue()).isPresent();
            assertThat(param.defaultValue().get()).isEqualTo(0.0);

            Object generated = param.generate();
            assertThat(generated).isInstanceOf(Double.class);
        }

        @Test
        @DisplayName("all parameters generate valid values")
        void testAllParametersGenerateValidValues() {
            Element template = DummyElement.template();

            for (Parameter<?> param : template.parameters()) {
                Object value = param.generate();
                assertThat(value)
                        .as("Generated value for %s", param.name())
                        .isNotNull();

                Object boundary = param.generateBoundary();
                assertThat(boundary)
                        .as("Boundary value for %s", param.name())
                        .isNotNull();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Health check tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Health Check")
    class HealthCheck {

        @Test
        @DisplayName("health check spec is present")
        void testHealthCheckPresent() {
            Element template = DummyElement.template();

            assertThat(template.healthCheck()).isPresent();
        }

        @Test
        @DisplayName("health check has reasonable defaults")
        void testHealthCheckDefaults() {
            Element.HealthCheckSpec hc = DummyElement.template().healthCheck().orElseThrow();

            assertThat(hc.timeout()).isNotNull();
            assertThat(hc.timeout().toSeconds()).isGreaterThan(0);
            assertThat(hc.maxRetries()).isGreaterThanOrEqualTo(0);
            assertThat(hc.retryInterval()).isNotNull();
            assertThat(hc.retryInterval().toSeconds()).isGreaterThan(0);
        }
    }

    // -----------------------------------------------------------------------
    // Builder tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder creates element with overridden name")
        void testBuilderOverridesName() {
            DummyElement custom = DummyElement.builder("custom-dummy").build();

            assertThat(custom.name()).isEqualTo("custom-dummy");
            assertThat(custom.tags()).containsEntry("name", "custom-dummy");
            assertThat(custom.tags()).containsEntry("type", "dummy");
        }

        @Test
        @DisplayName("builder-created element has same parameters as template")
        void testBuilderSameParameters() {
            DummyElement custom = DummyElement.builder("other").build();
            Element template = DummyElement.template();

            List<String> customNames = custom.parameters().stream()
                    .map(Parameter::name).toList();
            List<String> templateNames = template.parameters().stream()
                    .map(Parameter::name).toList();

            assertThat(customNames).isEqualTo(templateNames);
        }
    }

    // -----------------------------------------------------------------------
    // ParameterView tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ParameterView")
    class ParameterViewTests {

        @Test
        @DisplayName("parameter view is static (not dynamic)")
        void testParameterViewIsStatic() {
            Element template = DummyElement.template();

            assertThat(template.parameterView().isDynamic()).isFalse();
            assertThat(template.parameterView().requiredParameters())
                    .isEqualTo(template.parameters());
        }
    }

    // -----------------------------------------------------------------------
    // SPI discovery tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SPI Discovery")
    class SPIDiscovery {

        @Test
        @DisplayName("SimElementProvider is discoverable via ServiceLoader")
        void testElementProviderSPI() {
            List<ElementProvider> providers = ServiceLoader.load(ElementProvider.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();

            assertThat(providers).isNotEmpty();

            boolean foundSim = providers.stream()
                    .anyMatch(p -> p instanceof SimElementProvider);
            assertThat(foundSim)
                    .as("SimElementProvider should be discoverable")
                    .isTrue();
        }

        @Test
        @DisplayName("SimElementProvider returns dummy element template")
        void testElementProviderReturnsDummy() {
            SimElementProvider provider = new SimElementProvider();
            List<Element> elements = provider.elements();

            assertThat(elements).hasSize(1);
            assertThat(elements.get(0).name()).isEqualTo("dummy");
            assertThat(elements.get(0).tags()).containsEntry("type", "dummy");
        }

        @Test
        @DisplayName("SimElementTypeDescriptorProvider is discoverable via ServiceLoader")
        void testTypeDescriptorProviderSPI() {
            List<ElementTypeDescriptorProvider> providers =
                    ServiceLoader.load(ElementTypeDescriptorProvider.class)
                            .stream()
                            .map(ServiceLoader.Provider::get)
                            .toList();

            assertThat(providers).isNotEmpty();

            boolean foundSim = providers.stream()
                    .anyMatch(p -> p instanceof SimElementTypeDescriptorProvider);
            assertThat(foundSim)
                    .as("SimElementTypeDescriptorProvider should be discoverable")
                    .isTrue();
        }

        @Test
        @DisplayName("SimElementTypeDescriptorProvider declares dummy type")
        void testTypeDescriptorDeclaresDummy() {
            SimElementTypeDescriptorProvider provider = new SimElementTypeDescriptorProvider();
            List<ElementTypeDescriptor> descriptors = provider.descriptors();

            assertThat(descriptors).hasSize(1);
            assertThat(descriptors.get(0).typeId()).isEqualTo("dummy");
        }
    }

    // -----------------------------------------------------------------------
    // Trial lifecycle tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Trial Lifecycle")
    class TrialLifecycle {

        @Test
        @DisplayName("onTrialStarting records a STARTING event")
        void testOnTrialStartingRecordsEvent() {
            DummyElement dummy = DummyElement.template();
            Trial trial = MockTrial.builder().id("t-1").build();
            TrialContext ctx = TrialContext.now(trial);

            dummy.onTrialStarting(ctx);

            assertThat(dummy.trialEvents()).hasSize(1);
            assertThat(dummy.trialEvents().get(0).kind())
                    .isEqualTo(DummyElement.TrialEvent.Kind.STARTING);
            assertThat(dummy.trialEvents().get(0).context()).isSameAs(ctx);
        }

        @Test
        @DisplayName("onTrialEnding records an ENDING event")
        void testOnTrialEndingRecordsEvent() {
            DummyElement dummy = DummyElement.template();
            Trial trial = MockTrial.builder().id("t-1").build();
            TrialContext ctx = TrialContext.now(trial);

            dummy.onTrialEnding(ctx);

            assertThat(dummy.trialEvents()).hasSize(1);
            assertThat(dummy.trialEvents().get(0).kind())
                    .isEqualTo(DummyElement.TrialEvent.Kind.ENDING);
            assertThat(dummy.trialEvents().get(0).context()).isSameAs(ctx);
        }

        @Test
        @DisplayName("events accumulate in chronological order across trials")
        void testEventsAccumulateInOrder() {
            DummyElement dummy = DummyElement.template();
            Trial t1 = MockTrial.builder().id("t-1").build();
            Trial t2 = MockTrial.builder().id("t-2").build();

            TrialContext ctx1Start = TrialContext.now(t1);
            dummy.onTrialStarting(ctx1Start);

            TrialContext ctx1End = TrialContext.now(t1);
            dummy.onTrialEnding(ctx1End);

            TrialContext ctx2Start = TrialContext.now(t2);
            dummy.onTrialStarting(ctx2Start);

            TrialContext ctx2End = TrialContext.now(t2);
            dummy.onTrialEnding(ctx2End);

            assertThat(dummy.trialEvents()).hasSize(4);
            assertThat(dummy.trialEvents().get(0).kind())
                    .isEqualTo(DummyElement.TrialEvent.Kind.STARTING);
            assertThat(dummy.trialEvents().get(0).context().trial().id()).isEqualTo("t-1");
            assertThat(dummy.trialEvents().get(1).kind())
                    .isEqualTo(DummyElement.TrialEvent.Kind.ENDING);
            assertThat(dummy.trialEvents().get(1).context().trial().id()).isEqualTo("t-1");
            assertThat(dummy.trialEvents().get(2).kind())
                    .isEqualTo(DummyElement.TrialEvent.Kind.STARTING);
            assertThat(dummy.trialEvents().get(2).context().trial().id()).isEqualTo("t-2");
            assertThat(dummy.trialEvents().get(3).kind())
                    .isEqualTo(DummyElement.TrialEvent.Kind.ENDING);
            assertThat(dummy.trialEvents().get(3).context().trial().id()).isEqualTo("t-2");
        }

        @Test
        @DisplayName("trialEvents returns unmodifiable list")
        void testTrialEventsUnmodifiable() {
            DummyElement dummy = DummyElement.template();

            assertThatThrownBy(() -> dummy.trialEvents().add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("each DummyElement instance has independent event logs")
        void testIndependentEventLogs() {
            DummyElement a = DummyElement.template();
            DummyElement b = DummyElement.builder("other").build();
            Trial trial = MockTrial.builder().id("t-1").build();

            a.onTrialStarting(TrialContext.now(trial));

            assertThat(a.trialEvents()).hasSize(1);
            assertThat(b.trialEvents()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Operational state observation tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("State Observation")
    class StateObservationTests {

        @Test
        @DisplayName("observeState delivers immediate UNKNOWN → INACTIVE transition")
        void testImmediateTransitionOnRegistration() {
            DummyElement dummy = DummyElement.template();
            AtomicReference<StateTransition> received = new AtomicReference<>();

            dummy.observeState(received::set);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().from()).isEqualTo(Element.OperationalState.UNKNOWN);
            assertThat(received.get().to()).isEqualTo(Element.OperationalState.INACTIVE);
            assertThat(received.get().summary()).isEqualTo("Not started");
            assertThat(received.get().timestamp()).isNotNull();
        }

        @Test
        @DisplayName("transitionTo notifies registered observers")
        void testTransitionNotifiesObservers() {
            DummyElement dummy = DummyElement.template();
            List<StateTransition> transitions = new ArrayList<>();

            dummy.observeState(transitions::add);
            dummy.transitionTo(Element.OperationalState.RUNNING, "Simulated start");

            assertThat(transitions).hasSize(2);
            // First: registration-as-catchup
            assertThat(transitions.get(0).from()).isEqualTo(Element.OperationalState.UNKNOWN);
            assertThat(transitions.get(0).to()).isEqualTo(Element.OperationalState.INACTIVE);
            // Second: real transition
            assertThat(transitions.get(1).from()).isEqualTo(Element.OperationalState.INACTIVE);
            assertThat(transitions.get(1).to()).isEqualTo(Element.OperationalState.RUNNING);
            assertThat(transitions.get(1).summary()).isEqualTo("Simulated start");
        }

        @Test
        @DisplayName("statusCheck reflects current state after transition")
        void testStatusCheckReflectsTransition() {
            DummyElement dummy = DummyElement.template();

            assertThat(dummy.statusCheck().state()).isEqualTo(Element.OperationalState.INACTIVE);

            dummy.transitionTo(Element.OperationalState.RUNNING, "Started");
            assertThat(dummy.statusCheck().state()).isEqualTo(Element.OperationalState.RUNNING);
            assertThat(dummy.statusCheck().summary()).isEqualTo("Started");
        }

        @Test
        @DisplayName("cancel stops delivery of subsequent transitions")
        void testCancelStopsDelivery() {
            DummyElement dummy = DummyElement.template();
            List<StateTransition> transitions = new ArrayList<>();

            StateObservation obs = dummy.observeState(transitions::add);
            obs.cancel();

            dummy.transitionTo(Element.OperationalState.RUNNING, "Started");

            // Only the initial registration transition should be present
            assertThat(transitions).hasSize(1);
        }

        @Test
        @DisplayName("multiple observers receive independent notifications")
        void testMultipleObservers() {
            DummyElement dummy = DummyElement.template();
            List<StateTransition> listenerA = new ArrayList<>();
            List<StateTransition> listenerB = new ArrayList<>();

            dummy.observeState(listenerA::add);
            dummy.observeState(listenerB::add);

            dummy.transitionTo(Element.OperationalState.RUNNING, "Go");

            assertThat(listenerA).hasSize(2);
            assertThat(listenerB).hasSize(2);
        }

        @Test
        @DisplayName("re-registration after transition delivers current state")
        void testReRegistrationDeliversCurrentState() {
            DummyElement dummy = DummyElement.template();
            dummy.transitionTo(Element.OperationalState.RUNNING, "Active");

            // Register AFTER the transition
            AtomicReference<StateTransition> received = new AtomicReference<>();
            dummy.observeState(received::set);

            assertThat(received.get().from()).isEqualTo(Element.OperationalState.UNKNOWN);
            assertThat(received.get().to()).isEqualTo(Element.OperationalState.RUNNING);
            assertThat(received.get().summary()).isEqualTo("Active");
        }

        @Test
        @DisplayName("observeState rejects null listener")
        void testRejectsNullListener() {
            DummyElement dummy = DummyElement.template();

            assertThatThrownBy(() -> dummy.observeState(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("transitionTo rejects null state")
        void testTransitionToRejectsNullState() {
            DummyElement dummy = DummyElement.template();

            assertThatThrownBy(() -> dummy.transitionTo(null, "summary"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("transitionTo rejects null summary")
        void testTransitionToRejectsNullSummary() {
            DummyElement dummy = DummyElement.template();

            assertThatThrownBy(() -> dummy.transitionTo(Element.OperationalState.RUNNING, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("each DummyElement instance has independent state")
        void testIndependentState() {
            DummyElement a = DummyElement.template();
            DummyElement b = DummyElement.builder("other").build();

            a.transitionTo(Element.OperationalState.RUNNING, "A running");

            assertThat(a.statusCheck().state()).isEqualTo(Element.OperationalState.RUNNING);
            assertThat(b.statusCheck().state()).isEqualTo(Element.OperationalState.INACTIVE);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Parameter<?> findParameter(String name) {
        return DummyElement.template().parameters().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Parameter not found: " + name));
    }
}
