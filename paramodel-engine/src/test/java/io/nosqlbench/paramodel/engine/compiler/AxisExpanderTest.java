package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.parameters.SamplingStrategy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the AxisExpander utility.
class AxisExpanderTest {

    @Test
    void gridReturnsAllValues() {
        List<String> values = List.of("a", "b", "c");
        List<?> result = AxisExpander.applyStrategy(values, SamplingStrategy.grid());
        assertThat(result).isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void randomReturnsCorrectCount() {
        List<Integer> values = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<?> result = AxisExpander.sampleRandom(values, 3, 42L);
        assertThat(result).hasSize(3);
    }

    @Test
    void randomIsDeterministic() {
        List<Integer> values = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<?> result1 = AxisExpander.sampleRandom(values, 3, 42L);
        List<?> result2 = AxisExpander.sampleRandom(values, 3, 42L);
        assertThat(result1).isEqualTo(result2);
    }

    @Test
    void randomWithKGreaterOrEqualSizeReturnsAll() {
        List<Integer> values = List.of(1, 2, 3);
        assertThat(AxisExpander.sampleRandom(values, 3, 42L)).isEqualTo(List.of(1, 2, 3));
        assertThat(AxisExpander.sampleRandom(values, 10, 42L)).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void linspaceIntegerInterpolation() {
        List<Integer> values = List.of(0, 25, 50, 75, 100);
        List<?> result = AxisExpander.generateLinspace(values, 3);
        assertThat(result).isEqualTo(List.of(0, 50, 100));
    }

    @Test
    void linspaceLongTypePreservation() {
        List<Long> values = List.of(0L, 250L, 500L, 750L, 1000L);
        List<?> result = AxisExpander.generateLinspace(values, 3);
        assertThat(result).isEqualTo(List.of(0L, 500L, 1000L));
        assertThat(result.getFirst()).isInstanceOf(Long.class);
    }

    @Test
    void linspaceDoubleTypePreservation() {
        List<Double> values = List.of(0.0, 0.25, 0.5, 0.75, 1.0);
        List<?> result = AxisExpander.generateLinspace(values, 3);
        assertThat(result).isEqualTo(List.of(0.0, 0.5, 1.0));
        assertThat(result.getFirst()).isInstanceOf(Double.class);
    }

    @Test
    void linspaceNonNumericFallsBackToEvenIndexSampling() {
        List<String> values = List.of("a", "b", "c", "d", "e");
        List<?> result = AxisExpander.generateLinspace(values, 3);
        assertThat(result).isEqualTo(List.of("a", "c", "e"));
    }

    @Test
    void linspaceCount1ReturnsFirstValue() {
        List<Integer> values = List.of(10, 20, 30, 40, 50);
        List<?> result = AxisExpander.generateLinspace(values, 1);
        assertThat(result).isEqualTo(List.of(10));
    }

    @Test
    void linspaceNumericCountEqualSizeInterpolates() {
        List<Integer> values = List.of(1, 2, 3);
        assertThat(AxisExpander.generateLinspace(values, 3)).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void linspaceNumericCountGreaterThanSizeInterpolates() {
        List<Integer> values = List.of(0, 100);
        List<?> result = AxisExpander.generateLinspace(values, 5);
        assertThat(result).isEqualTo(List.of(0, 25, 50, 75, 100));
    }

    @Test
    void linspaceNonNumericCountGreaterOrEqualSizeReturnsAll() {
        List<String> values = List.of("a", "b", "c");
        assertThat(AxisExpander.generateLinspace(values, 3)).isEqualTo(List.of("a", "b", "c"));
        assertThat(AxisExpander.generateLinspace(values, 10)).isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void applyStrategyDispatchesGrid() {
        List<Integer> values = List.of(1, 2, 3);
        List<?> result = AxisExpander.applyStrategy(values, SamplingStrategy.grid());
        assertThat(result).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void applyStrategyDispatchesRandom() {
        List<Integer> values = List.of(1, 2, 3, 4, 5);
        List<?> result = AxisExpander.applyStrategy(values, SamplingStrategy.random(2, 99L));
        assertThat(result).hasSize(2);
    }

    @Test
    void applyStrategyDispatchesLinspace() {
        List<Integer> values = List.of(0, 25, 50, 75, 100);
        List<?> result = AxisExpander.applyStrategy(values, SamplingStrategy.linspace(3));
        assertThat(result).isEqualTo(List.of(0, 50, 100));
    }
}
