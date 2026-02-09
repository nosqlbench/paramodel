package io.nosqlbench.paramodel.tck.compilation;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for CompilationStage contract.
///
/// Validates that implementations correctly:
/// - Provide name and description
/// - Execute against a compilation context
/// - Declare dependencies
/// - Support conditional skipping
///
/// @see CompilationStage
/// @since 0.1.0
///
public abstract class CompilationStageTCK {
    protected CompilationStageTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testStageHasName() {
        CompilationStage stage = getProvider().createCompilationStage("test-stage");

        assertThat(stage.name()).isNotNull();
        assertThat(stage.name()).isNotEmpty();
    }

    @Test
    public void testStageHasDescription() {
        CompilationStage stage = getProvider().createCompilationStage("test-stage");

        assertThat(stage.description()).isNotNull();
        assertThat(stage.description()).isNotEmpty();
    }

    @Test
    public void testStageExecutes() {
        CompilationStage stage = getProvider().createCompilationStage("test-stage");
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        assertThatCode(() -> stage.execute(context)).doesNotThrowAnyException();
    }

    @Test
    public void testStageDependencies() {
        CompilationStage stage = getProvider().createCompilationStage("test-stage");

        assertThat(stage.dependencies()).isNotNull();
    }

    @Test
    public void testStageCanSkip() {
        CompilationStage stage = getProvider().createCompilationStage("test-stage");
        var testPlan = getProvider().createTestPlan();
        CompilationContext context = getProvider().createCompilationContext(testPlan);

        // canSkip should return a boolean without throwing
        boolean canSkip = stage.canSkip(context);
        assertThat(canSkip).isIn(true, false);
    }
}
