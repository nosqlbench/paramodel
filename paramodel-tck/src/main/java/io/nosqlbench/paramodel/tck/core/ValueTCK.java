package io.nosqlbench.paramodel.tck.core;

import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for Value contract.
 *
 * Validates that implementations correctly:
 * - Store parameter values
 * - Track metadata (timestamps, generators)
 * - Provide fingerprints
 * - Support comparison
 */
public abstract class ValueTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testValueHoldsData() {
        Value<String> value = getProvider().createValue("test-data", "param1");

        assertThat(value.value()).isEqualTo("test-data");
    }

    @Test
    public void testValueHasParameterName() {
        Value<Integer> value = getProvider().createValue(42, "param1");

        assertThat(value.parameterName()).isEqualTo("param1");
    }

    @Test
    public void testValueHasGenerationTimestamp() {
        Value<String> value = getProvider().createValue("test", "param1");

        assertThat(value.generatedAt()).isNotNull();
    }

    @Test
    public void testValueHasFingerprint() {
        Value<String> value = getProvider().createValue("test", "param1");

        assertThat(value.fingerprint()).isNotNull();
        assertThat(value.fingerprint()).isNotEmpty();
    }

    @Test
    public void testValueSupportsNullValue() {
        Value<String> value = getProvider().createValue(null, "param1");

        assertThat(value.value()).isNull();
        assertThat(value.parameterName()).isEqualTo("param1");
    }

    @Test
    public void testValueEquality() {
        Value<Integer> value1 = getProvider().createValue(100, "param1");
        Value<Integer> value2 = getProvider().createValue(100, "param1");

        // Same parameter name and value should produce consistent behavior
        assertThat(value1.value()).isEqualTo(value2.value());
        assertThat(value1.parameterName()).isEqualTo(value2.parameterName());
    }

    @Test
    public void testValueWithDifferentParameters() {
        Value<Integer> value1 = getProvider().createValue(100, "param1");
        Value<Integer> value2 = getProvider().createValue(100, "param2");

        assertThat(value1.parameterName()).isNotEqualTo(value2.parameterName());
    }

    @Test
    public void testValueGeneratorMetadata() {
        Value<String> value = getProvider().createValue("test", "param1");

        assertThat(value.generatorMetadata()).isNotNull();
        // Metadata may be empty but should never be null
    }

    @Test
    public void testValueSupportsComplexTypes() {
        record ComplexType(String name, int id) {}
        ComplexType complex = new ComplexType("test", 123);

        Value<ComplexType> value = getProvider().createValue(complex, "complex-param");

        assertThat(value.value()).isEqualTo(complex);
        assertThat(value.value().name()).isEqualTo("test");
        assertThat(value.value().id()).isEqualTo(123);
    }
}
