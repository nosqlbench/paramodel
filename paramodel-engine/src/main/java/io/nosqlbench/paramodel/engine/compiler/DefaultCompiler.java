package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.*;
import io.nosqlbench.paramodel.engine.plan.DefaultTestPlan;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Default compiler implementation using the 8-stage compilation pipeline.
 *
 * Pipeline stages:
 * 1. Validation - Verify TestPlan correctness
 * 2. Normalization - Canonicalize representation
 * 3. Trial Enumeration - Expand parameter space
 * 4. Instantiation - Create concrete values
 * 5. Step Generation - Build atomic steps
 * 6. Dependency Analysis - Compute execution graph
 * 7. Optimization - Apply transformations
 * 8. Code Generation - Produce ExecutionPlan
 */
public class DefaultCompiler implements Compiler {
    private static final Logger log = LoggerFactory.getLogger(DefaultCompiler.class);
    private static final String VERSION = "0.1.0";

    private final List<CompilationStage> stages;
    private final CompilerOptions compilerOptions;

    public DefaultCompiler(List<CompilationStage> stages, CompilerOptions options) {
        this.stages = new ArrayList<>(Objects.requireNonNull(stages));
        this.compilerOptions = Objects.requireNonNull(options);
    }

    @Override
    public ValidationResult validate(TestPlan testPlan) {
        io.nosqlbench.paramodel.parameters.ValidationResult planValidation = testPlan.validate();

        List<CompilationError> errors = new ArrayList<>();
        List<CompilationWarning> warnings = new ArrayList<>();

        if (planValidation.isFailed()) {
            for (String violation : planValidation.violations()) {
                errors.add(new DefaultCompilationError(ErrorSeverity.ERROR, violation, null, null));
            }
        }

        return new DefaultValidationResult(errors.isEmpty(), errors, warnings);
    }

    @Override
    public CompilationResult compile(TestPlan testPlan) {
        Instant startTime = Instant.now();

        CompilationContext context = new DefaultCompilationContext(testPlan, compilerOptions);
        context.startTimer("total");

        for (CompilationStage stage : stages) {
            log.debug("Executing stage: {}", stage.name());

            context.startTimer(stage.name());
            stage.execute(context);
            context.stopTimer(stage.name());

            if (context.hasErrors()) {
                log.error("Compilation failed at stage {}", stage.name());
                Duration duration = Duration.between(startTime, Instant.now());
                return new DefaultCompilationResult(
                    false,
                    Optional.empty(),
                    context.errors(),
                    context.warnings(),
                    duration,
                    context
                );
            }
        }

        context.stopTimer("total");
        Duration duration = Duration.between(startTime, Instant.now());

        // Push generated trials back to the plan so adopters that query
        // plan.trials() / plan.size() see the compiler-generated trials.
        context.trials().ifPresent(trials -> {
            if (testPlan instanceof DefaultTestPlan dtp) {
                for (var trial : trials) {
                    dtp.addTrial(trial);
                }
            }
        });

        // Commit the test plan to mark it as frozen.
        // TestPlan implementations that delegate commit() to a compiler
        // must guard against re-entrancy (see Study.commit()).
        ExecutionPlan committedPlan = testPlan.commit();

        // Prefer the compiled plan from the pipeline; fall back to the
        // plan returned by commit() for simple TestPlan implementations.
        ExecutionPlan plan = context.get("executionPlan")
            .filter(ExecutionPlan.class::isInstance)
            .map(ExecutionPlan.class::cast)
            .orElse(committedPlan);

        log.info("Compilation completed in {}ms", duration.toMillis());

        return new DefaultCompilationResult(
            true,
            Optional.of(plan),
            context.errors(),
            context.warnings(),
            duration,
            context
        );
    }

    @Override
    public CompilationResult compileIncremental(TestPlan modified, ExecutionPlan previous) {
        // For now, just do full compilation
        // Full incremental compilation would require change detection
        log.warn("Incremental compilation not yet implemented, performing full compilation");
        return compile(modified);
    }

    @Override
    public CompilerOptions options() {
        return compilerOptions;
    }

    @Override
    public String version() {
        return VERSION;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder() {}

        private final List<CompilationStage> stages = new ArrayList<>();
        private CompilerOptions options = new DefaultCompilerOptions();

        public Builder stage(CompilationStage stage) {
            this.stages.add(stage);
            return this;
        }

        public Builder options(CompilerOptions options) {
            this.options = options;
            return this;
        }

        public Builder standardPipeline() {
            // Add 8 standard stages
            return this
                .stage(new ValidationStage())
                .stage(new NormalizationStage())
                .stage(new TrialEnumerationStage())
                .stage(new InstantiationStage())
                .stage(new StepGenerationStage())
                .stage(new DependencyAnalysisStage())
                .stage(new OptimizationStage())
                .stage(new CodeGenerationStage());
        }

        public DefaultCompiler build() {
            if (stages.isEmpty()) {
                throw new IllegalStateException("Compiler must have at least one stage");
            }
            return new DefaultCompiler(stages, options);
        }
    }

    // Implementation classes

    private static class DefaultCompilerOptions implements CompilerOptions {
        @Override
        public CompilationStrategy strategy() {
            return CompilationStrategy.BALANCED;
        }

        @Override
        public OptimizationLevel optimizationLevel() {
            return OptimizationLevel.STANDARD;
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

    private static class DefaultValidationResult implements ValidationResult {
        private final boolean valid;
        private final List<CompilationError> errors;
        private final List<CompilationWarning> warnings;

        public DefaultValidationResult(boolean valid, List<CompilationError> errors,
                                     List<CompilationWarning> warnings) {
            this.valid = valid;
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }

        @Override
        public boolean isValid() { return valid; }

        @Override
        public boolean hasErrors() { return !errors.isEmpty(); }

        @Override
        public boolean hasWarnings() { return !warnings.isEmpty(); }

        @Override
        public List<CompilationError> errors() { return errors; }

        @Override
        public List<CompilationWarning> warnings() { return warnings; }
    }

    private static class DefaultCompilationResult implements CompilationResult {
        private final boolean success;
        private final Optional<ExecutionPlan> plan;
        private final List<CompilationError> errors;
        private final List<CompilationWarning> warnings;
        private final Duration duration;
        private final CompilationContext context;

        public DefaultCompilationResult(boolean success, Optional<ExecutionPlan> plan,
                                      List<CompilationError> errors, List<CompilationWarning> warnings,
                                      Duration duration, CompilationContext context) {
            this.success = success;
            this.plan = plan;
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
            this.duration = duration;
            this.context = context;
        }

        @Override
        public boolean isSuccess() { return success; }

        @Override
        public Optional<ExecutionPlan> executionPlan() { return plan; }

        @Override
        public List<CompilationError> errors() { return errors; }

        @Override
        public List<CompilationWarning> warnings() { return warnings; }

        @Override
        public Duration compilationDuration() { return duration; }

        @Override
        public Optional<OptimizationReport> optimizationReport() {
            return Optional.empty(); // TODO: implement optimization reporting
        }

        @Override
        public CompilationStatistics statistics() {
            Map<String, Duration> timings = context.timings();
            Map<String, Long> counters = context.counters();

            return new CompilationStatistics(
                counters.getOrDefault("trials", 0L).intValue(),
                counters.getOrDefault("steps", 0L).intValue(),
                counters.getOrDefault("barriers", 0L).intValue(),
                counters.getOrDefault("optimizations", 0L).intValue(),
                timings.getOrDefault("ValidationStage", Duration.ZERO),
                timings.getOrDefault("TrialEnumerationStage", Duration.ZERO),
                timings.getOrDefault("OptimizationStage", Duration.ZERO),
                timings.getOrDefault("CodeGenerationStage", Duration.ZERO)
            );
        }
    }

    private static class DefaultCompilationError implements CompilationError {
        private final ErrorSeverity severity;
        private final String message;
        private final String location;
        private final String suggestion;

        public DefaultCompilationError(ErrorSeverity severity, String message,
                                      String location, String suggestion) {
            this.severity = severity;
            this.message = message;
            this.location = location;
            this.suggestion = suggestion;
        }

        @Override
        public ErrorSeverity severity() { return severity; }

        @Override
        public String message() { return message; }

        @Override
        public Optional<String> location() { return Optional.ofNullable(location); }

        @Override
        public Optional<String> suggestion() { return Optional.ofNullable(suggestion); }
    }
}
