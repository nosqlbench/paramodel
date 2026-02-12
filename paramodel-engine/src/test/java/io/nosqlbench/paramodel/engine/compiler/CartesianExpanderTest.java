package io.nosqlbench.paramodel.engine.compiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CartesianExpanderTest {

    @Test
    @DisplayName("expandAxis single axis produces Cartesian product")
    void expandAxisSingleAxis() {
        List<Map<String, Object>> seed = List.of(new LinkedHashMap<>());
        List<Map<String, Object>> result = CartesianExpander.expandAxis(seed, "color", List.of("red", "blue", "green"));

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).containsEntry("color", "red");
        assertThat(result.get(1)).containsEntry("color", "blue");
        assertThat(result.get(2)).containsEntry("color", "green");
    }

    @Test
    @DisplayName("expandAxis two axes produces full Cartesian product")
    void expandAxisTwoAxes() {
        List<Map<String, Object>> seed = List.of(new LinkedHashMap<>());
        List<Map<String, Object>> afterFirst = CartesianExpander.expandAxis(seed, "color", List.of("red", "blue"));
        List<Map<String, Object>> result = CartesianExpander.expandAxis(afterFirst, "size", List.of(10, 20, 30));

        assertThat(result).hasSize(6); // 2 * 3
        // Verify all combinations present
        assertThat(result).anySatisfy(m -> {
            assertThat(m).containsEntry("color", "red").containsEntry("size", 10);
        });
        assertThat(result).anySatisfy(m -> {
            assertThat(m).containsEntry("color", "blue").containsEntry("size", 30);
        });
    }

    @Test
    @DisplayName("expandAxis preserves seed entries (fixed bindings)")
    void expandAxisPreservesSeedEntries() {
        Map<String, Object> fixed = new LinkedHashMap<>();
        fixed.put("host", "localhost");
        fixed.put("port", 8080);
        List<Map<String, Object>> seed = List.of(fixed);

        List<Map<String, Object>> result = CartesianExpander.expandAxis(seed, "threads", List.of(1, 2, 4));

        assertThat(result).hasSize(3);
        for (Map<String, Object> combo : result) {
            assertThat(combo).containsEntry("host", "localhost");
            assertThat(combo).containsEntry("port", 8080);
            assertThat(combo).containsKey("threads");
        }
    }

    @Test
    @DisplayName("applyRepetitions with maxReps=1 returns input unchanged")
    void applyRepetitionsNoOp() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("x", 1);
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(entry);

        List<Map<String, Object>> result = CartesianExpander.applyRepetitions(input, 1);

        assertThat(result).isSameAs(input);
    }

    @Test
    @DisplayName("applyRepetitions with maxReps=3 triples the list with markers")
    void applyRepetitionsTriples() {
        List<Map<String, Object>> input = new ArrayList<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("color", "red");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("color", "blue");
        input.add(a);
        input.add(b);

        List<Map<String, Object>> result = CartesianExpander.applyRepetitions(input, 3);

        assertThat(result).hasSize(6); // 2 * 3
        // Each entry should have the repetition marker
        for (Map<String, Object> combo : result) {
            assertThat(combo).containsKey(CartesianExpander.REPETITION_KEY);
        }
        // Repetition indices should cycle 0,1,2 for each base combo
        assertThat(result.get(0)).containsEntry(CartesianExpander.REPETITION_KEY, 0).containsEntry("color", "red");
        assertThat(result.get(1)).containsEntry(CartesianExpander.REPETITION_KEY, 1).containsEntry("color", "red");
        assertThat(result.get(2)).containsEntry(CartesianExpander.REPETITION_KEY, 2).containsEntry("color", "red");
        assertThat(result.get(3)).containsEntry(CartesianExpander.REPETITION_KEY, 0).containsEntry("color", "blue");
    }

    @Test
    @DisplayName("REPETITION_KEY constant matches \"__repetition__\"")
    void repetitionKeyConstant() {
        assertThat(CartesianExpander.REPETITION_KEY).isEqualTo("__repetition__");
    }
}
