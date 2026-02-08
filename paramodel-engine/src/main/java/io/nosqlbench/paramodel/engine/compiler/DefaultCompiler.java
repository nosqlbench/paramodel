package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.*;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    private final List<CompilationStage> stages;
    private final CompilationContext.Factory contextFactory;

    public DefaultCompiler(List<CompilationStage> stages, CompilationContext.Factory contextFactory) {
        this.stages = new ArrayList<>(Objects.requireNonNull(stages));
        this.contextFactory = Objects.requireNonNull(contextFactory);
    }

    @Override
    public ExecutionPlan compile(TestPlan testPlan) {
        log.info("Starting compilation of TestPlan: {}", testPlan.metadata().fingerprint());

        CompilationContext context = contextFactory.create(testPlan);

        for (CompilationStage stage : stages) {
            log.debug("Executing stage: {}", stage.name());

            long startTime = System.nanoTime();
            context = stage.execute(context);
            long duration = System.nanoTime() - startTime;

            log.debug("Stage {} completed in {}ms", stage.name(), duration / 1_000_000);

            if (!context.isValid()) {
                log.error("Compilation failed at stage {}: {}", stage.name(), context.errors());
                throw new CompilationException("Compilation failed at stage: " + stage.name(), context.errors());
            }
        }

        ExecutionPlan plan = context.getExecutionPlan();
        log.info("Compilation completed. Estimated trials: {}", plan.estimatedTrialCount());

        return plan;
    }

    @Override
    public CompilationContext compile(TestPlan testPlan, CompilationContext initialContext) {
        CompilationContext context = initialContext;

        for (CompilationStage stage : stages) {
            context = stage.execute(context);
            if (!context.isValid()) {
                return context;
            }
        }

        return context;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<CompilationStage> stages = new ArrayList<>();
        private CompilationContext.Factory contextFactory = DefaultCompilationContext::new;

        public Builder stage(CompilationStage stage) {
            this.stages.add(stage);
            return this;
        }

        public Builder contextFactory(CompilationContext.Factory factory) {
            this.contextFactory = factory;
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
            return new DefaultCompiler(stages, contextFactory);
        }
    }

    /**
     * Exception thrown when compilation fails.
     */
    public static class CompilationException extends RuntimeException {
        private final List<String> errors;

        public CompilationException(String message, List<String> errors) {
            super(message);
            this.errors = new ArrayList<>(errors);
        }

        public List<String> getErrors() {
            return List.copyOf(errors);
        }
    }
}
