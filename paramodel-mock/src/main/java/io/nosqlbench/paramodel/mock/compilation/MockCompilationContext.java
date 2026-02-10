package io.nosqlbench.paramodel.mock.compilation;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

///
/// Simple in-memory compilation context for testing.
///
/// Stores trials, steps, barriers, metrics, errors, and warnings
/// in memory. Provides a mock CompilerOptions that returns sensible defaults.
///
/// @see CompilationContext
/// @since 0.1.0
///
public class MockCompilationContext implements CompilationContext {
    private final TestPlan testPlan;
    private final Compiler.CompilerOptions options;
    private final Map<String, Object> environment = new HashMap<>();
    private final Map<String, Object> contextData = new HashMap<>();

    private List<Trial> trials;
    private final Map<String, List<ElementInstance>> elementInstances = new HashMap<>();
    private List<AtomicStep> steps;
    private List<Barrier> barriers;

    private final List<Compiler.CompilationError> errors = new ArrayList<>();
    private final List<Compiler.CompilationWarning> warnings = new ArrayList<>();

    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final Map<String, Instant> timerStarts = new HashMap<>();
    private final Map<String, Duration> timings = new LinkedHashMap<>();

    ///
    /// Creates a mock compilation context for the given test plan.
    ///
    /// @param testPlan the test plan to compile
    ///
    public MockCompilationContext(TestPlan testPlan) {
        this.testPlan = Objects.requireNonNull(testPlan);
        this.options = new MockCompilerOptions();
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
    public Optional<List<ElementInstance>> elementInstances() {
        List<ElementInstance> all = new ArrayList<>();
        elementInstances.values().forEach(all::addAll);
        return all.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(all));
    }

    @Override
    public String planInstance(Element element, List<Trial> trials, String scopeDescription) {
        return planInstance(element, trials, scopeDescription, Set.of());
    }

    @Override
    public String planInstance(Element element, List<Trial> trials, String scopeDescription, Set<String> dependsOn) {
        String instanceId = element.name() + "_" + scopeDescription;
        ElementInstance instance = new ElementInstance(
            instanceId, element, List.copyOf(trials), scopeDescription, dependsOn);
        elementInstances.computeIfAbsent(element.name(), k -> new ArrayList<>()).add(instance);
        return instanceId;
    }

    @Override
    public Optional<ElementInstance> getInstanceForTrial(String elementName, Trial trial) {
        return getInstancesForElement(elementName).stream()
            .filter(ei -> ei.trials().contains(trial))
            .findFirst();
    }

    @Override
    public List<ElementInstance> getInstancesForElement(String elementName) {
        return elementInstances.getOrDefault(elementName, List.of());
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
    public void addError(Compiler.ErrorSeverity severity, String message,
                         String location, String suggestion) {
        errors.add(new MockCompilationError(severity, message, location, suggestion));
    }

    @Override
    public void addWarning(String message, String suggestion) {
        warnings.add(new MockCompilationWarning(message, null, suggestion));
    }

    @Override
    public void addInfo(String message) {
        // Info messages are recorded as metrics
        recordMetric("info_count", counters.getOrDefault("info_count", 0L) + 1);
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

    private record MockCompilationError(
        Compiler.ErrorSeverity severity,
        String message,
        String loc,
        String sug
    ) implements Compiler.CompilationError {
        @Override
        public Optional<String> location() {
            return Optional.ofNullable(loc);
        }

        @Override
        public Optional<String> suggestion() {
            return Optional.ofNullable(sug);
        }
    }

    private record MockCompilationWarning(
        String message,
        String loc,
        String sug
    ) implements Compiler.CompilationWarning {
        @Override
        public Optional<String> location() {
            return Optional.ofNullable(loc);
        }

        @Override
        public Optional<String> suggestion() {
            return Optional.ofNullable(sug);
        }
    }

    private static class MockCompilerOptions implements Compiler.CompilerOptions {
        @Override
        public Compiler.CompilationStrategy strategy() {
            return Compiler.CompilationStrategy.FAST_COMPILE;
        }

        @Override
        public Compiler.OptimizationLevel optimizationLevel() {
            return Compiler.OptimizationLevel.BASIC;
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
