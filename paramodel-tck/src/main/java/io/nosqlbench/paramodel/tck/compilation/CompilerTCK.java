package io.nosqlbench.paramodel.tck.compilation;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for Compiler contract.
///
/// Validates that implementations correctly:
/// - Validate test plans
/// - Compile test plans into execution plans
/// - Provide version and options information
/// - Produce compilation results with expected metadata
///
/// @see Compiler
/// @since 0.1.0
///
public abstract class CompilerTCK {
    protected CompilerTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testCompilerValidatesPlan() {
        Compiler compiler = getProvider().createCompiler();
        var testPlan = getProvider().createTestPlan();

        Compiler.ValidationResult result = compiler.validate(testPlan);

        assertThat(result).isNotNull();
        assertThat(result.errors()).isNotNull();
        assertThat(result.warnings()).isNotNull();
    }

    @Test
    public void testCompilerCompilesValidPlan() {
        Compiler compiler = getProvider().createCompiler();
        var testPlan = getProvider().createTestPlan();

        Compiler.CompilationResult result = compiler.compile(testPlan);

        assertThat(result).isNotNull();
        assertThat(result.errors()).isNotNull();
        assertThat(result.warnings()).isNotNull();
    }

    @Test
    public void testCompilerHasVersion() {
        Compiler compiler = getProvider().createCompiler();

        assertThat(compiler.version()).isNotNull();
        assertThat(compiler.version()).isNotEmpty();
    }

    @Test
    public void testCompilerHasOptions() {
        Compiler compiler = getProvider().createCompiler();

        Compiler.CompilerOptions options = compiler.options();

        assertThat(options).isNotNull();
        assertThat(options.strategy()).isNotNull();
        assertThat(options.optimizationLevel()).isNotNull();
        assertThat(options.maxTrialSpaceSize()).isGreaterThan(0);
        assertThat(options.customOptions()).isNotNull();
    }

    @Test
    public void testCompilationResultHasExecutionPlan() {
        Compiler compiler = getProvider().createCompiler();
        var testPlan = getProvider().createTestPlan();

        Compiler.CompilationResult result = compiler.compile(testPlan);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.executionPlan()).isPresent();
    }

    @Test
    public void testCompilationResultHasStatistics() {
        Compiler compiler = getProvider().createCompiler();
        var testPlan = getProvider().createTestPlan();

        Compiler.CompilationResult result = compiler.compile(testPlan);

        assertThat(result.statistics()).isNotNull();
        assertThat(result.compilationDuration()).isNotNull();
    }
}
