package io.nosqlbench.paramodel.mock.compilation;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.mock.plan.MockExecutionPlan;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// Simple compiler implementation for testing.
///
/// Validates that the test plan is non-null and produces stub
/// compilation results with an empty execution plan.
///
/// @see Compiler
/// @since 0.1.0
///
public class MockCompiler implements Compiler {
    private final CompilerOptions options;

    ///
    /// Creates a mock compiler with default options.
    ///
    public MockCompiler() {
        this.options = new MockCompilerOptions();
    }

    @Override
    public ValidationResult validate(TestPlan testPlan) {
        if (testPlan == null) {
            return new MockValidationResult(false,
                List.of(new MockCompilationError("TestPlan must not be null")),
                List.of());
        }
        return new MockValidationResult(true, List.of(), List.of());
    }

    @Override
    public CompilationResult compile(TestPlan testPlan) {
        ValidationResult validation = validate(testPlan);
        if (validation.hasErrors()) {
            return new MockCompilationResult(false, null,
                validation.errors(), validation.warnings());
        }
        ExecutionPlan plan = new MockExecutionPlan(
            java.util.UUID.randomUUID().toString(),
            java.util.UUID.randomUUID().toString());
        return new MockCompilationResult(true, plan, List.of(), List.of());
    }

    @Override
    public CompilationResult compileIncremental(TestPlan modified, ExecutionPlan previous) {
        return compile(modified);
    }

    @Override
    public CompilerOptions options() {
        return options;
    }

    @Override
    public String version() {
        return "mock-1.0.0";
    }

    private record MockCompilationResult(
        boolean isSuccess,
        ExecutionPlan plan,
        List<CompilationError> errors,
        List<CompilationWarning> warnings
    ) implements CompilationResult {
        @Override
        public Optional<ExecutionPlan> executionPlan() {
            return Optional.ofNullable(plan);
        }

        @Override
        public Duration compilationDuration() {
            return Duration.ofMillis(1);
        }

        @Override
        public Optional<OptimizationReport> optimizationReport() {
            return Optional.empty();
        }

        @Override
        public CompilationStatistics statistics() {
            return new CompilationStatistics(0, 0, 0, 0,
                Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        }
    }

    private record MockValidationResult(
        boolean isValid,
        List<CompilationError> errors,
        List<CompilationWarning> warnings
    ) implements ValidationResult {
        @Override
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        @Override
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    private record MockCompilationError(String message) implements CompilationError {
        @Override
        public ErrorSeverity severity() {
            return ErrorSeverity.ERROR;
        }

        @Override
        public Optional<String> location() {
            return Optional.empty();
        }

        @Override
        public Optional<String> suggestion() {
            return Optional.empty();
        }
    }

    private static class MockCompilerOptions implements CompilerOptions {
        @Override
        public CompilationStrategy strategy() {
            return CompilationStrategy.FAST_COMPILE;
        }

        @Override
        public OptimizationLevel optimizationLevel() {
            return OptimizationLevel.BASIC;
        }

        @Override
        public long maxTrialSpaceSize() {
            return 1_000_000;
        }

        @Override
        public boolean parallelCompilation() {
            return false;
        }

        @Override
        public boolean dryRun() {
            return false;
        }

        @Override
        public Map<String, Object> customOptions() {
            return Map.of();
        }
    }
}
