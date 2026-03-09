package io.nosqlbench.paramodel.tck.elements;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.OperationalStateObservable;
import io.nosqlbench.paramodel.elements.TrialContext;
import io.nosqlbench.paramodel.elements.TrialLifecycleParticipant;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for Element contract.
///
/// Validates that implementations correctly:
/// - Provide non-null, non-empty names
/// - Return traits containing at minimum a "name" entry
/// - Return non-null parameter, dependency, health check, and scope values
/// - Support element construction with dependencies and health checks
/// - Implement {@link TrialLifecycleParticipant} with safe default behavior
/// - Support {@link OperationalStateObservable} with registration-as-catchup semantics
///
/// @see Element
/// @see TrialLifecycleParticipant
/// @see OperationalStateObservable
/// @since 0.1.0
///
public abstract class ElementTCK {
    protected ElementTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testElementHasName() {
        Element element = getProvider().createElement("database");

        assertThat(element.name()).isNotNull();
        assertThat(element.name()).isNotEmpty();
        assertThat(element.name()).isEqualTo("database");
    }

    @Test
    public void testElementHasLabels() {
        Element element = getProvider().createElement("cache");

        assertThat(element.labels()).isNotNull();
        assertThat(element.labels()).containsKey("name");
        assertThat(element.labels().get("name")).isEqualTo("cache");
    }

    @Test
    public void testElementHasParameters() {
        Element element = getProvider().createElement("service");

        assertThat(element.parameters()).isNotNull();
    }

    @Test
    public void testElementHasResultParameters() {
        Element element = getProvider().createElement("service");

        assertThat(element.resultParameters()).isNotNull();
    }

    @Test
    public void testElementHasDependencies() {
        Element element = getProvider().createElement("app-server");

        assertThat(element.dependencies()).isNotNull();
    }

    @Test
    public void testElementHealthCheck() {
        Element element = getProvider().createElement("api");

        // healthCheck() should return a non-null Optional
        assertThat(element.healthCheck()).isNotNull();
    }

    @Test
    public void testElementWithType() {
        Element element = getProvider().createTypedElement("postgres", "service");

        assertThat(element.name()).isEqualTo("postgres");
        assertThat(element.labels()).containsKey("type");
        assertThat(element.label("type")).isEqualTo("service");
    }

    @Test
    public void testElementWithDependencies() {
        Element dep = getProvider().createElement("storage");
        Element element = getProvider().createElementWithDependencies("database",
            java.util.List.of(dep));

        assertThat(element.dependencies()).isNotEmpty();
        assertThat(element.dependencies()).hasSize(1);
    }

    @Test
    public void testElementWithHealthCheck() {
        Element.HealthCheckSpec healthCheck = getProvider().createHealthCheckSpec(
            java.time.Duration.ofSeconds(30));
        Element element = getProvider().createElementWithHealthCheck("service", healthCheck);

        assertThat(element.healthCheck()).isPresent();
        assertThat(element.healthCheck().get().timeout()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(element.healthCheck().get().maxRetries()).isGreaterThanOrEqualTo(0);
        assertThat(element.healthCheck().get().retryInterval()).isNotNull();
    }

    @Test
    public void testElementStatusCheck() {
        Element element = getProvider().createElement("worker");

        Element.LiveStatusSummary status = element.statusCheck();
        assertThat(status).isNotNull();
        assertThat(status.state()).isNotNull();
        assertThat(status.summary()).isNotNull();
        assertThat(status.summary()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // Trial lifecycle participation
    // -----------------------------------------------------------------------

    @Test
    public void testElementIsTrialLifecycleParticipant() {
        Element element = getProvider().createElement("worker");

        assertThat(element).isInstanceOf(TrialLifecycleParticipant.class);
    }

    @Test
    public void testDefaultOnTrialStartingDoesNotThrow() {
        Element element = getProvider().createElement("worker");
        Trial trial = getProvider().createTrial("trial-1");
        TrialContext context = TrialContext.now(trial);

        assertThatCode(() -> element.onTrialStarting(context)).doesNotThrowAnyException();
    }

    @Test
    public void testDefaultOnTrialEndingDoesNotThrow() {
        Element element = getProvider().createElement("worker");
        Trial trial = getProvider().createTrial("trial-1");
        TrialContext context = TrialContext.now(trial);

        assertThatCode(() -> element.onTrialEnding(context)).doesNotThrowAnyException();
    }

    @Test
    public void testTrialLifecycleIdempotent() {
        Element element = getProvider().createElement("worker");
        Trial trial = getProvider().createTrial("trial-1");
        TrialContext context = TrialContext.now(trial);

        // Calling twice for the same trial must not throw
        assertThatCode(() -> {
            element.onTrialStarting(context);
            element.onTrialStarting(context);
            element.onTrialEnding(context);
            element.onTrialEnding(context);
        }).doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Operational state observation
    // -----------------------------------------------------------------------

    @Test
    public void testElementIsOperationalStateObservable() {
        Element element = getProvider().createElement("worker");

        assertThat(element).isInstanceOf(OperationalStateObservable.class);
    }

    @Test
    public void testObserveStateDeliversImmediateTransition() {
        Element element = getProvider().createElement("worker");
        AtomicReference<OperationalStateObservable.StateTransition> received =
            new AtomicReference<>();

        element.observeState(received::set);

        assertThat(received.get()).isNotNull();
        assertThat(received.get().from()).isEqualTo(Element.OperationalState.UNKNOWN);
        assertThat(received.get().to()).isNotNull();
        assertThat(received.get().summary()).isNotNull();
        assertThat(received.get().summary()).isNotEmpty();
        assertThat(received.get().timestamp()).isNotNull();
    }

    @Test
    public void testObserveStateReturnsNonNullHandle() {
        Element element = getProvider().createElement("worker");

        OperationalStateObservable.StateObservation observation =
            element.observeState(transition -> {});

        assertThat(observation).isNotNull();
    }

    @Test
    public void testObserveStateCancelIsIdempotent() {
        Element element = getProvider().createElement("worker");

        OperationalStateObservable.StateObservation observation =
            element.observeState(transition -> {});

        // Cancel twice — second call must not throw
        assertThatCode(() -> {
            observation.cancel();
            observation.cancel();
        }).doesNotThrowAnyException();
    }

    @Test
    public void testObserveStateRejectsNullListener() {
        Element element = getProvider().createElement("worker");

        assertThatThrownBy(() -> element.observeState(null))
            .isInstanceOf(NullPointerException.class);
    }
}
