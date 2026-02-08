package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;

import java.util.*;

/**
 * Default implementation of CompilationContext.
 *
 * Tracks state through the compilation pipeline including:
 * - Source TestPlan
 * - Intermediate artifacts
 * - Errors and warnings
 * - Final ExecutionPlan
 */
public class DefaultCompilationContext implements CompilationContext {
    private final TestPlan testPlan;
    private final Map<String, Object> artifacts;
    private final List<String> errors;
    private final List<String> warnings;
    private ExecutionPlan executionPlan;

    public DefaultCompilationContext(TestPlan testPlan) {
        this.testPlan = Objects.requireNonNull(testPlan);
        this.artifacts = new HashMap<>();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    private DefaultCompilationContext(TestPlan testPlan, Map<String, Object> artifacts,
                                     List<String> errors, List<String> warnings,
                                     ExecutionPlan executionPlan) {
        this.testPlan = testPlan;
        this.artifacts = new HashMap<>(artifacts);
        this.errors = new ArrayList<>(errors);
        this.warnings = new ArrayList<>(warnings);
        this.executionPlan = executionPlan;
    }

    @Override
    public TestPlan testPlan() {
        return testPlan;
    }

    @Override
    public <T> Optional<T> getArtifact(String key, Class<T> type) {
        Object value = artifacts.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    @Override
    public CompilationContext withArtifact(String key, Object artifact) {
        Map<String, Object> newArtifacts = new HashMap<>(artifacts);
        newArtifacts.put(key, artifact);
        return new DefaultCompilationContext(testPlan, newArtifacts, errors, warnings, executionPlan);
    }

    @Override
    public CompilationContext withError(String error) {
        List<String> newErrors = new ArrayList<>(errors);
        newErrors.add(error);
        return new DefaultCompilationContext(testPlan, artifacts, newErrors, warnings, executionPlan);
    }

    @Override
    public CompilationContext withWarning(String warning) {
        List<String> newWarnings = new ArrayList<>(warnings);
        newWarnings.add(warning);
        return new DefaultCompilationContext(testPlan, artifacts, errors, newWarnings, executionPlan);
    }

    @Override
    public CompilationContext withExecutionPlan(ExecutionPlan plan) {
        return new DefaultCompilationContext(testPlan, artifacts, errors, warnings, plan);
    }

    @Override
    public boolean isValid() {
        return errors.isEmpty();
    }

    @Override
    public List<String> errors() {
        return List.copyOf(errors);
    }

    @Override
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    @Override
    public ExecutionPlan getExecutionPlan() {
        if (executionPlan == null) {
            throw new IllegalStateException("ExecutionPlan not yet created");
        }
        return executionPlan;
    }

    @Override
    public boolean hasExecutionPlan() {
        return executionPlan != null;
    }

    @Override
    public Map<String, Object> allArtifacts() {
        return Map.copyOf(artifacts);
    }
}
