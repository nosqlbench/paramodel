package io.nosqlbench.paramodel.tck.plan;

import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for Axis contract.
///
/// Validates that implementations correctly:
/// - Store ordered discrete values
/// - Compute cardinality as values().size()
/// - Identify boundary values (first and last)
/// - Support containment and index lookup
/// - Provide optional description and tags
///
/// @see Axis
/// @since 0.1.0
///
public abstract class AxisTCK {
    protected AxisTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testAxisHasName() {
        Axis<String> axis = getProvider().createTypedAxis("model",
            List.of("gpt-4", "claude-3"));

        assertThat(axis.name()).isNotNull();
        assertThat(axis.name()).isEqualTo("model");
    }

    @Test
    public void testAxisHasTags() {
        Axis<String> axis = getProvider().createTypedAxis("model",
            List.of("gpt-4", "claude-3"));

        assertThat(axis.tags()).isNotNull();
        assertThat(axis.tags()).containsKey("name");
        assertThat(axis.tags().get("name")).isEqualTo("model");
    }

    @Test
    public void testAxisHasValues() {
        Axis<Integer> axis = getProvider().createTypedAxis("batch_size",
            List.of(16, 32, 64));

        assertThat(axis.values()).isNotNull();
        assertThat(axis.values()).isNotEmpty();
        assertThat(axis.values()).containsExactly(16, 32, 64);
    }

    @Test
    public void testAxisCardinality() {
        Axis<String> axis = getProvider().createTypedAxis("optimizer",
            List.of("adam", "sgd", "rmsprop"));

        assertThat(axis.cardinality()).isEqualTo(3);
        assertThat(axis.cardinality()).isEqualTo(axis.values().size());
    }

    @Test
    public void testAxisBoundaryValues() {
        Axis<Integer> axis = getProvider().createTypedAxis("temperature",
            List.of(0, 25, 50, 75, 100));

        List<Integer> boundaries = axis.boundaryValues();
        assertThat(boundaries).isNotNull();
        assertThat(boundaries).contains(0, 100);
    }

    @Test
    public void testAxisDescription() {
        Axis<String> axis = getProvider().createTypedAxis("model",
            List.of("gpt-4", "claude-3"));

        // description() should return non-null Optional (may be empty)
        assertThat(axis.description()).isNotNull();
    }

    @Test
    public void testAxisContains() {
        Axis<String> axis = getProvider().createTypedAxis("model",
            List.of("gpt-4", "claude-3"));

        assertThat(axis.contains("gpt-4")).isTrue();
        assertThat(axis.contains("claude-3")).isTrue();
        assertThat(axis.contains("nonexistent")).isFalse();
    }

    @Test
    public void testAxisIndexOf() {
        Axis<String> axis = getProvider().createTypedAxis("model",
            List.of("gpt-4", "claude-3", "gemini-pro"));

        assertThat(axis.indexOf("gpt-4")).isEqualTo(0);
        assertThat(axis.indexOf("claude-3")).isEqualTo(1);
        assertThat(axis.indexOf("gemini-pro")).isEqualTo(2);
        assertThat(axis.indexOf("nonexistent")).isEqualTo(-1);
    }
}
