/*
 * Copyright (c) 2026 nosqlbench contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.nosqlbench.paramodel.engine.plan;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.compiler.DefaultCompiler;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.OptimizationStrategy;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.plan.policies.ExecutionPolicies;
import io.nosqlbench.paramodel.sequence.Sequence;
import io.nosqlbench.paramodel.sequence.Trial;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// A production implementation of {@link TestPlan} and {@link Sequence} with a
/// builder, compiler delegation, and re-entrancy guard.
///
/// This class replaces hyperplane's {@code Study} by providing the same
/// dual-interface implementation ({@code TestPlan} + {@code Sequence}) with
/// builder construction, mutable pre-commit state, and delegation to
/// {@link DefaultCompiler} for execution plan generation.
///
/// ## Re-entrancy Guard
///
/// The {@link DefaultCompiler} calls {@code commit()} on the test plan after
/// its pipeline completes to mark the plan as frozen. Since this class
/// delegates {@code commit()} back to the compiler, a volatile
/// {@code compiling} flag prevents infinite recursion: when set, the
/// re-entrant {@code commit()} returns the plan that the pipeline produced.
///
/// ## Usage
///
/// ```java
/// DefaultTestPlan plan = DefaultTestPlan.builder()
///     .name("perf-sweep")
///     .description("Performance parameter sweep")
///     .element(dbElement)
///     .element(benchElement)
///     .axis(threadAxis)
///     .axis(batchAxis)
///     .build();
///
/// ExecutionPlan execPlan = plan.commit();
/// ```
public class DefaultTestPlan implements TestPlan, Sequence {

    private final String name;
    private final String description;
    private final Map<String, Element> elements;
    private final List<Axis<?>> axes;
    private final ExecutionPolicies policies;
    private final OptimizationStrategy optimizationStrategy;
    private final TestPlanMetadata metadata;
    private final Compiler compiler;
    private final List<Trial> trials;
    private volatile boolean compiling;
    private volatile ExecutionPlan compiledPlan;

    private DefaultTestPlan(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.description = builder.description;
        this.elements = new LinkedHashMap<>(builder.elements);
        this.axes = Collections.unmodifiableList(new ArrayList<>(builder.axes));
        this.policies = builder.policies;
        this.optimizationStrategy = builder.optimizationStrategy;
        this.metadata = builder.metadata != null ? builder.metadata : new DefaultMetadata(description);
        this.compiler = builder.compiler;
        this.trials = new ArrayList<>(builder.trials);
    }

    // ── TestPlan interface ─────────────────────────────────────────────

    @Override
    public String name() {
        return name;
    }

    /// Returns the description of this test plan.
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    public List<Axis<?>> axes() {
        return axes;
    }

    @Override
    public List<Element> elements() {
        return List.copyOf(elements.values());
    }

    @Override
    public ExecutionPolicies policies() {
        return policies;
    }

    @Override
    public OptimizationStrategy optimizationStrategy() {
        return optimizationStrategy;
    }

    @Override
    public long trialSpaceSize() {
        if (axes.isEmpty()) return 0;
        long product = 1;
        for (Axis<?> axis : axes) {
            product *= axis.cardinality();
        }
        return product;
    }

    @Override
    public boolean isCommitted() {
        return compiledPlan != null;
    }

    @Override
    public ValidationResult validate() {
        for (Trial trial : trials) {
            ValidationResult result = trial.validate();
            if (result.isFailed()) {
                return result;
            }
        }
        return new ValidationResult.Passed();
    }

    @Override
    public TestPlan reorderAxes(List<String> axisNames) {
        throw new UnsupportedOperationException(
                "Axis reordering is not supported; control axis order in the plan definition");
    }

    /// Compiles this test plan into a paramodel {@link ExecutionPlan} using
    /// the configured compiler (defaults to {@link DefaultCompiler}'s standard
    /// 8-stage pipeline).
    ///
    /// The compiler calls {@code commit()} on the test plan after running
    /// its pipeline to mark the plan as frozen. Because this class delegates
    /// {@code commit()} back to the compiler, a re-entrancy guard prevents
    /// infinite recursion: the second (re-entrant) call detects that
    /// compilation is already in progress and returns the plan that
    /// the pipeline produced.
    @Override
    public ExecutionPlan commit() {
        if (compiledPlan != null) {
            return compiledPlan;
        }

        if (compiling) {
            return compiledPlan;
        }

        compiling = true;
        try {
            Compiler effectiveCompiler = this.compiler != null
                    ? this.compiler
                    : DefaultCompiler.builder().standardPipeline().build();
            Compiler.CompilationResult result = effectiveCompiler.compile(this);
            if (!result.isSuccess()) {
                String errors = result.errors().stream()
                        .map(Compiler.CompilationError::message)
                        .collect(Collectors.joining("\n"));
                throw new IllegalStateException("Compilation failed:\n" + errors);
            }
            compiledPlan = result.executionPlan().orElseThrow(
                    () -> new IllegalStateException("Compilation produced no execution plan"));
            return compiledPlan;
        } finally {
            compiling = false;
        }
    }

    @Override
    public TestPlanMetadata metadata() {
        return metadata;
    }

    // ── Sequence interface ─────────────────────────────────────────────

    @Override
    public List<Trial> trials() {
        return Collections.unmodifiableList(trials);
    }

    @Override
    public int size() {
        return trials.size();
    }

    @Override
    public boolean isEmpty() {
        return trials.isEmpty();
    }

    @Override
    public Iterator<Trial> iterator() {
        return trials().iterator();
    }

    // ── Mutable methods (before commit) ────────────────────────────────

    /// Replaces an element in the plan. Called during composition to apply bindings.
    ///
    /// @param element the element to replace (matched by name)
    public void replaceElement(Element element) {
        elements.put(element.name(), element);
    }

    /// Adds a trial to the plan. Called during composition by trial generators.
    ///
    /// @param trial the trial to add
    public void addTrial(Trial trial) {
        this.trials.add(trial);
    }

    /// Returns the paramodel execution plan, if generated via {@link #commit()}.
    ///
    /// @return the compiled execution plan, or empty if not yet committed
    public Optional<ExecutionPlan> getExecutionPlan() {
        return Optional.ofNullable(compiledPlan);
    }

    /// Returns all elements in this plan as an unmodifiable map.
    ///
    /// @return element map keyed by element name
    public Map<String, Element> getElementMap() {
        return Collections.unmodifiableMap(elements);
    }

    // ── Builder ────────────────────────────────────────────────────────

    /// Creates a new builder for constructing DefaultTestPlan instances.
    ///
    /// @return a new builder
    public static Builder builder() {
        return new Builder();
    }

    /// Builder for creating {@link DefaultTestPlan} instances.
    public static class Builder {
        private String name;
        private String description;
        private final Map<String, Element> elements = new LinkedHashMap<>();
        private final List<Axis<?>> axes = new ArrayList<>();
        private final List<Trial> trials = new ArrayList<>();
        private ExecutionPolicies policies = new NoOpExecutionPolicies();
        private OptimizationStrategy optimizationStrategy = OptimizationStrategy.NONE;
        private TestPlanMetadata metadata;
        private Compiler compiler;

        private Builder() {}

        /// Sets the plan name.
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /// Sets the plan description.
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /// Adds an element to the plan.
        public Builder element(Element element) {
            this.elements.put(element.name(), element);
            return this;
        }

        /// Adds an axis to the plan.
        public Builder axis(Axis<?> axis) {
            this.axes.add(axis);
            return this;
        }

        /// Adds multiple axes to the plan.
        public Builder axes(List<? extends Axis<?>> axes) {
            this.axes.addAll(axes);
            return this;
        }

        /// Adds a trial to the plan.
        public Builder trial(Trial trial) {
            this.trials.add(trial);
            return this;
        }

        /// Adds multiple trials to the plan.
        public Builder trials(List<Trial> trials) {
            this.trials.addAll(trials);
            return this;
        }

        /// Sets the execution policies.
        public Builder policies(ExecutionPolicies policies) {
            this.policies = policies;
            return this;
        }

        /// Sets the optimization strategy.
        public Builder optimizationStrategy(OptimizationStrategy strategy) {
            this.optimizationStrategy = strategy;
            return this;
        }

        /// Sets the test plan metadata.
        public Builder metadata(TestPlanMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        /// Sets the compiler for commit(). If not set, defaults to
        /// DefaultCompiler with standardPipeline.
        public Builder compiler(Compiler compiler) {
            this.compiler = compiler;
            return this;
        }

        /// Builds the test plan.
        public DefaultTestPlan build() {
            return new DefaultTestPlan(this);
        }
    }

    /// Minimal execution policies matching the default behavior where retries
    /// and timeouts are not modeled at the plan level.
    private static class NoOpExecutionPolicies implements ExecutionPolicies {
        private static final RetryPolicy NO_RETRY = new RetryPolicy() {
            @Override public int maxAttempts() { return 1; }
            @Override public BackoffStrategy backoff() { return attempt -> Duration.ZERO; }
            @Override public Set<String> retryableErrors() { return Set.of(); }
        };

        @Override public RetryPolicy trialRetryPolicy() { return NO_RETRY; }
        @Override public RetryPolicy elementDeploymentRetryPolicy() { return NO_RETRY; }
        @Override public Optional<Duration> trialTimeout() { return Optional.empty(); }
        @Override public Optional<Duration> elementStartTimeout() { return Optional.empty(); }
        @Override public InterventionMode interventionMode() { return InterventionMode.AFTER_ACTIVE_TRIALS; }
        @Override public PartialRunBehavior partialRunBehavior() { return PartialRunBehavior.RETAIN_RESULTS; }
    }

    /// Default metadata implementation.
    private static class DefaultMetadata implements TestPlanMetadata {
        private final Instant createdAt = Instant.now();
        private final String description;

        DefaultMetadata(String description) {
            this.description = description;
        }

        @Override public Instant createdAt() { return createdAt; }
        @Override public Optional<String> createdBy() { return Optional.empty(); }
        @Override public Optional<String> description() { return Optional.ofNullable(description); }
        @Override public Map<String, String> tags() { return Map.of(); }
        @Override public Optional<String> version() { return Optional.empty(); }
    }

    @Override
    public String toString() {
        return "DefaultTestPlan{" +
                "name='" + name + '\'' +
                ", elements=" + elements.size() +
                ", axes=" + axes.size() +
                ", trials=" + trials.size() +
                '}';
    }
}
