package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.SamplingStrategy;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class TrialEnumerationStageTest {

    @Test
    @DisplayName("Grid sampling produces full Cartesian product (backward compat)")
    void gridProducesFullCartesianProduct() {
        Axis<String> axis1 = MockAxis.of("color", "red", "blue");
        Axis<Integer> axis2 = MockAxis.of("size", 10, 20, 30);

        TestPlan plan = MockTestPlan.builder()
            .name("grid-test")
            .axis(axis1)
            .axis(axis2)
            .element(MockElement.of("svc"))
            .build();

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());
        new TrialEnumerationStage().execute(context);

        assertThat(context.trials()).isPresent();
        List<Trial> trials = context.trials().get();
        assertThat(trials).hasSize(6); // 2 * 3
    }

    @Test
    @DisplayName("Random(k) produces exactly k values per axis")
    void randomProducesExactlyKPerAxis() {
        Axis<String> axis = MockAxis.of("letter", "a", "b", "c", "d", "e");

        TestPlan plan = MockTestPlan.builder()
            .name("random-test")
            .axis(axis)
            .element(MockElement.of("svc"))
            .build();

        SamplingConfig config = new SamplingConfig(
            Map.of("letter", SamplingStrategy.random(2, 42L)),
            Map.of(),
            Map.of()
        );

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());
        context.put("samplingConfig", config);
        new TrialEnumerationStage().execute(context);

        assertThat(context.trials()).isPresent();
        assertThat(context.trials().get()).hasSize(2);
    }

    @Test
    @DisplayName("Random sampling is deterministic with same seed")
    void randomIsDeterministicWithSameSeed() {
        Axis<String> axis = MockAxis.of("letter", "a", "b", "c", "d", "e", "f", "g");

        TestPlan plan = MockTestPlan.builder()
            .name("seed-test")
            .axis(axis)
            .element(MockElement.of("svc"))
            .build();

        SamplingConfig config = new SamplingConfig(
            Map.of("letter", SamplingStrategy.random(3, 123L)),
            Map.of(),
            Map.of()
        );

        // Run twice
        DefaultCompilationContext ctx1 = new DefaultCompilationContext(plan, defaultOptions());
        ctx1.put("samplingConfig", config);
        new TrialEnumerationStage().execute(ctx1);

        // Need a fresh plan since commit() may have been called
        TestPlan plan2 = MockTestPlan.builder()
            .name("seed-test")
            .axis(axis)
            .element(MockElement.of("svc"))
            .build();

        DefaultCompilationContext ctx2 = new DefaultCompilationContext(plan2, defaultOptions());
        ctx2.put("samplingConfig", config);
        new TrialEnumerationStage().execute(ctx2);

        // MockAxis targets "mock" element by default
        List<Object> values1 = ctx1.trials().get().stream()
            .map(t -> t.assignment("mock", "letter").get().value())
            .collect(Collectors.toList());
        List<Object> values2 = ctx2.trials().get().stream()
            .map(t -> t.assignment("mock", "letter").get().value())
            .collect(Collectors.toList());

        assertThat(values1).isEqualTo(values2);
    }

    @Test
    @DisplayName("Linspace on Integer range produces evenly-spaced integers")
    void linspaceOnIntegerRange() {
        Axis<Integer> axis = MockAxis.of("count", 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);

        TestPlan plan = MockTestPlan.builder()
            .name("linspace-int-test")
            .axis(axis)
            .element(MockElement.of("svc"))
            .build();

        SamplingConfig config = new SamplingConfig(
            Map.of("count", SamplingStrategy.linspace(3)),
            Map.of(),
            Map.of()
        );

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());
        context.put("samplingConfig", config);
        new TrialEnumerationStage().execute(context);

        assertThat(context.trials()).isPresent();
        List<Trial> trials = context.trials().get();
        assertThat(trials).hasSize(3);

        List<Object> values = trials.stream()
            .map(t -> t.assignment("mock", "count").get().value())
            .collect(Collectors.toList());
        // Should be 0, 50, 100 (evenly spaced across min=0 to max=100)
        assertThat(values).containsExactly(0, 50, 100);
    }

    @Test
    @DisplayName("Linspace on Double range preserves double type")
    void linspaceOnDoubleRange() {
        Axis<Double> axis = MockAxis.of("rate", 1.0, 2.0, 3.0, 4.0, 5.0);

        TestPlan plan = MockTestPlan.builder()
            .name("linspace-double-test")
            .axis(axis)
            .element(MockElement.of("svc"))
            .build();

        SamplingConfig config = new SamplingConfig(
            Map.of("rate", SamplingStrategy.linspace(3)),
            Map.of(),
            Map.of()
        );

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());
        context.put("samplingConfig", config);
        new TrialEnumerationStage().execute(context);

        List<Trial> trials = context.trials().get();
        assertThat(trials).hasSize(3);

        List<Object> values = trials.stream()
            .map(t -> t.assignment("mock", "rate").get().value())
            .collect(Collectors.toList());
        // 1.0, 3.0, 5.0
        assertThat(values).containsExactly(1.0, 3.0, 5.0);
    }

    @Test
    @DisplayName("Nesting order affects combination ordering")
    void nestingOrderAffectsCombinationOrdering() {
        Axis<String> axis1 = MockAxis.of("color", "R", "G");
        Axis<Integer> axis2 = MockAxis.of("size", 1, 2);

        // axis2 (size) is outermost (nesting 0), axis1 (color) is innermost (nesting 1)
        SamplingConfig config = new SamplingConfig(
            Map.of(),
            Map.of("size", 0, "color", 1),
            Map.of()
        );

        TestPlan plan = MockTestPlan.builder()
            .name("nesting-test")
            .axis(axis1)
            .axis(axis2)
            .element(MockElement.of("svc"))
            .build();

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());
        context.put("samplingConfig", config);
        new TrialEnumerationStage().execute(context);

        List<Trial> trials = context.trials().get();
        assertThat(trials).hasSize(4);

        // With size outermost: (1,R), (1,G), (2,R), (2,G)
        List<Object> sizes = trials.stream()
            .map(t -> t.assignment("mock", "size").get().value())
            .collect(Collectors.toList());
        assertThat(sizes).containsExactly(1, 1, 2, 2);
    }

    @Test
    @DisplayName("Repetitions multiply trial count")
    void repetitionsMultiplyTrialCount() {
        Axis<String> axis = MockAxis.of("mode", "fast", "slow");

        SamplingConfig config = new SamplingConfig(
            Map.of(),
            Map.of(),
            Map.of("mode", 3) // 3 repetitions
        );

        TestPlan plan = MockTestPlan.builder()
            .name("rep-test")
            .axis(axis)
            .element(MockElement.of("svc"))
            .build();

        DefaultCompilationContext context = new DefaultCompilationContext(plan, defaultOptions());
        context.put("samplingConfig", config);
        new TrialEnumerationStage().execute(context);

        assertThat(context.trials()).isPresent();
        assertThat(context.trials().get()).hasSize(6); // 2 values * 3 repetitions
    }

    private Compiler.CompilerOptions defaultOptions() {
        return new Compiler.CompilerOptions() {
            @Override public Compiler.CompilationStrategy strategy() { return Compiler.CompilationStrategy.BALANCED; }
            @Override public Compiler.OptimizationLevel optimizationLevel() { return Compiler.OptimizationLevel.STANDARD; }
            @Override public long maxTrialSpaceSize() { return 1_000_000; }
            @Override public boolean parallelCompilation() { return false; }
            @Override public boolean dryRun() { return false; }
            @Override public Map<String, Object> customOptions() { return Map.of(); }
        };
    }
}
