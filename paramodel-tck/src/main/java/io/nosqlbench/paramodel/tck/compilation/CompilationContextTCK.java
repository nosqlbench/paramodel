package io.nosqlbench.paramodel.tck.compilation;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for CompilationContext contract.
///
/// Validates that implementations correctly:
/// - Provide access to the test plan and options
/// - Manage trials, steps, and barriers state
/// - Track errors, warnings, and info messages
/// - Record metrics and timings
///
/// @see CompilationContext
/// @since 0.1.0
///
public abstract class CompilationContextTCK {
    protected CompilationContextTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testContextHasTestPlan() {
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThat(context.testPlan()).isNotNull();
        assertThat(context.testPlan()).isSameAs(testPlan);
    }

    @Test
    public void testContextManagesTrials() {
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThat(context.trials()).isEmpty();

        Trial trial1 = getProvider().createTrial("t1");
        Trial trial2 = getProvider().createTrial("t2");
        context.setTrials(List.of(trial1, trial2));

        assertThat(context.trials()).isPresent();
        assertThat(context.trials().get()).hasSize(2);
    }

    @Test
    public void testContextManagesSteps() {
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThat(context.steps()).isEmpty();

        Trial trial = getProvider().createTrial("t1");
        var step = getProvider().createAtomicStep("step1", trial);
        context.setSteps(List.of(step));

        assertThat(context.steps()).isPresent();
        assertThat(context.steps().get()).hasSize(1);
    }

    @Test
    public void testContextManagesBarriers() {
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThat(context.barriers()).isEmpty();

        Barrier barrier = getProvider().createBarrier("b1");
        context.setBarriers(List.of(barrier));

        assertThat(context.barriers()).isPresent();
        assertThat(context.barriers().get()).hasSize(1);
    }

    @Test
    public void testContextTracksErrors() {
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThat(context.hasErrors()).isFalse();
        assertThat(context.errors()).isEmpty();

        context.addError(Compiler.ErrorSeverity.ERROR, "test error", "location", "suggestion");

        assertThat(context.hasErrors()).isTrue();
        assertThat(context.errors()).hasSize(1);
        assertThat(context.errors().get(0).message()).isEqualTo("test error");
        assertThat(context.errors().get(0).severity()).isEqualTo(Compiler.ErrorSeverity.ERROR);
    }

    @Test
    public void testContextTracksWarnings() {
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThat(context.warnings()).isEmpty();

        context.addWarning("test warning", "suggestion");

        assertThat(context.warnings()).hasSize(1);
        assertThat(context.warnings().get(0).message()).isEqualTo("test warning");
    }

    @Test
    public void testContextRecordsMetrics() {
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThat(context.counters()).isEmpty();

        context.recordMetric("trials_generated", 100L);

        assertThat(context.counters()).containsKey("trials_generated");
        assertThat(context.counters().get("trials_generated")).isEqualTo(100L);
    }

    @Test
    public void testContextTimers() {
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThat(context.timings()).isEmpty();

        context.startTimer("validation");
        context.stopTimer("validation");

        assertThat(context.timings()).containsKey("validation");
        assertThat(context.timings().get("validation")).isNotNull();
    }
}
