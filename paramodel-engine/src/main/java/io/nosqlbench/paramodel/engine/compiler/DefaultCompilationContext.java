package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.CompactId;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Default implementation of CompilationContext.
 *
 * Tracks state through the compilation pipeline including:
 * - Source TestPlan
 * - Intermediate artifacts
 * - Errors and warnings
 * - Compilation metrics
 */
public class DefaultCompilationContext implements CompilationContext {
    private final TestPlan testPlan;
    private final Compiler.CompilerOptions options;
    private final Map<String, Object> environment;
    private final Map<String, Object> contextData;

    // Compilation artifacts
    private List<Trial> trials;
    private final Map<String, List<CompilationContext.ElementInstance>> elementInstances;
    private List<AtomicStep> steps;
    private List<Barrier> barriers;

    // Diagnostics
    private final List<Compiler.CompilationError> errors;
    private final List<Compiler.CompilationWarning> warnings;

    // Metrics
    private final Map<String, Long> counters;
    private final Map<String, Instant> timerStarts;
    private final Map<String, Duration> timings;

    public DefaultCompilationContext(TestPlan testPlan, Compiler.CompilerOptions options) {
        this.testPlan = Objects.requireNonNull(testPlan);
        this.options = Objects.requireNonNull(options);
        this.environment = new HashMap<>();
        this.contextData = new HashMap<>();
        this.elementInstances = new HashMap<>();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.counters = new HashMap<>();
        this.timerStarts = new HashMap<>();
        this.timings = new HashMap<>();
    }

    @Override
    public TestPlan testPlan() {
        return testPlan;
    }

    @Override
    public Compiler.CompilerOptions options() {
        return options;
    }

    @Override
    public Map<String, Object> environment() {
        return Collections.unmodifiableMap(environment);
    }

    @Override
    public Optional<List<Trial>> trials() {
        return Optional.ofNullable(trials);
    }

    @Override
    public void setTrials(List<Trial> trials) {
        this.trials = new ArrayList<>(trials);
    }

    @Override
    public Optional<List<CompilationContext.ElementInstance>> elementInstances() {
        List<CompilationContext.ElementInstance> allInstances = new ArrayList<>();
        elementInstances.values().forEach(allInstances::addAll);
        return allInstances.isEmpty() ? Optional.empty() : Optional.of(allInstances);
    }

    @Override
    public String planInstance(Element element, List<Trial> trials, String scopeDescription) {
        return planInstance(element, trials, scopeDescription, Set.of());
    }

    @Override
    public String planInstance(Element element, List<Trial> trials, String scopeDescription, Set<String> dependsOn) {
        String instanceId = element.name() + "_" + CompactId.next();
        CompilationContext.ElementInstance instance =
            new CompilationContext.ElementInstance(instanceId, element, trials, scopeDescription, dependsOn);
        elementInstances.computeIfAbsent(element.name(), k -> new ArrayList<>()).add(instance);
        return instanceId;
    }

    @Override
    public Optional<CompilationContext.ElementInstance> getInstanceForTrial(String elementName, Trial trial) {
        return elementInstances.getOrDefault(elementName, List.of()).stream()
            .filter(inst -> inst.trials().contains(trial))
            .findFirst();
    }

    @Override
    public List<CompilationContext.ElementInstance> getInstancesForElement(String elementName) {
        return Collections.unmodifiableList(elementInstances.getOrDefault(elementName, List.of()));
    }

    @Override
    public Optional<List<AtomicStep>> steps() {
        return Optional.ofNullable(steps);
    }

    @Override
    public void setSteps(List<AtomicStep> steps) {
        this.steps = new ArrayList<>(steps);
    }

    @Override
    public Optional<List<Barrier>> barriers() {
        return Optional.ofNullable(barriers);
    }

    @Override
    public void setBarriers(List<Barrier> barriers) {
        this.barriers = new ArrayList<>(barriers);
    }

    @Override
    public void addError(Compiler.ErrorSeverity severity, String message, String location, String suggestion) {
        errors.add(new DefaultCompilationError(severity, message, location, suggestion));
    }

    @Override
    public void addWarning(String message, String suggestion) {
        warnings.add(new DefaultCompilationWarning(message, suggestion));
    }

    @Override
    public void addInfo(String message) {
        // Could store info messages if needed
    }

    @Override
    public List<Compiler.CompilationError> errors() {
        return Collections.unmodifiableList(errors);
    }

    @Override
    public List<Compiler.CompilationWarning> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    @Override
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public void recordMetric(String name, long value) {
        counters.put(name, value);
    }

    @Override
    public void recordMetric(String name, double value) {
        counters.put(name, (long) value);
    }

    @Override
    public void startTimer(String name) {
        timerStarts.put(name, Instant.now());
    }

    @Override
    public void stopTimer(String name) {
        Instant start = timerStarts.get(name);
        if (start != null) {
            timings.put(name, Duration.between(start, Instant.now()));
        }
    }

    @Override
    public Map<String, Duration> timings() {
        return Collections.unmodifiableMap(timings);
    }

    @Override
    public Map<String, Long> counters() {
        return Collections.unmodifiableMap(counters);
    }

    @Override
    public void put(String key, Object value) {
        contextData.put(key, value);
    }

    @Override
    public Optional<Object> get(String key) {
        return Optional.ofNullable(contextData.get(key));
    }

    // Inner classes for compilation diagnostics

    private static class DefaultCompilationError implements Compiler.CompilationError {
        private final Compiler.ErrorSeverity severity;
        private final String message;
        private final String location;
        private final String suggestion;

        public DefaultCompilationError(Compiler.ErrorSeverity severity, String message,
                                      String location, String suggestion) {
            this.severity = severity;
            this.message = message;
            this.location = location;
            this.suggestion = suggestion;
        }

        @Override
        public Compiler.ErrorSeverity severity() { return severity; }

        @Override
        public String message() { return message; }

        @Override
        public Optional<String> location() { return Optional.ofNullable(location); }

        @Override
        public Optional<String> suggestion() { return Optional.ofNullable(suggestion); }
    }

    private static class DefaultCompilationWarning implements Compiler.CompilationWarning {
        private final String message;
        private final String location;
        private final String suggestion;

        public DefaultCompilationWarning(String message, String suggestion) {
            this(message, null, suggestion);
        }

        public DefaultCompilationWarning(String message, String location, String suggestion) {
            this.message = message;
            this.location = location;
            this.suggestion = suggestion;
        }

        @Override
        public String message() { return message; }

        @Override
        public Optional<String> location() { return Optional.ofNullable(location); }

        @Override
        public Optional<String> suggestion() { return Optional.ofNullable(suggestion); }
    }
}
