package io.nosqlbench.paramodel.tck.compilation;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.OptimizationPass;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for OptimizationPass contract.
///
/// Validates that implementations correctly:
/// - Provide name and description
/// - Determine applicability via shouldApply
/// - Apply without throwing exceptions
/// - Report optimization category
///
/// @see OptimizationPass
/// @since 0.1.0
///
public abstract class OptimizationPassTCK {
    protected OptimizationPassTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testPassHasName() {
        OptimizationPass pass = getProvider().createOptimizationPass("test-pass");

        assertThat(pass.name()).isNotNull();
        assertThat(pass.name()).isNotEmpty();
    }

    @Test
    public void testPassHasDescription() {
        OptimizationPass pass = getProvider().createOptimizationPass("test-pass");

        assertThat(pass.description()).isNotNull();
        assertThat(pass.description()).isNotEmpty();
    }

    @Test
    public void testPassShouldApply() {
        OptimizationPass pass = getProvider().createOptimizationPass("test-pass");
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        // shouldApply should return a boolean without throwing
        boolean shouldApply = pass.shouldApply(context);
        assertThat(shouldApply).isIn(true, false);
    }

    @Test
    public void testPassApply() {
        OptimizationPass pass = getProvider().createOptimizationPass("test-pass");
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThatCode(() -> pass.apply(context)).doesNotThrowAnyException();
    }

    @Test
    public void testPassCategory() {
        OptimizationPass pass = getProvider().createOptimizationPass("test-pass");

        assertThat(pass.category()).isNotNull();
    }
}
