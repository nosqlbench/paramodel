package io.nosqlbench.paramodel.tck.core;

import io.nosqlbench.paramodel.core.Domain;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for Parameter contract.
 *
 * Validates that implementations correctly:
 * - Generate values from their domain
 * - Respect domain constraints
 * - Provide metadata
 * - Handle validation
 */
public abstract class ParameterTCK {

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testParameterHasName() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("a", "b", "c"));
        Parameter<String> param = getProvider().createParameter("test", domain);

        assertThat(param.name()).isEqualTo("test");
    }

    @Test
    public void testParameterHasDomain() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("a", "b", "c"));
        Parameter<String> param = getProvider().createParameter("test", domain);

        assertThat(param.domain()).isEqualTo(domain);
    }

    @Test
    public void testParameterGeneratesValues() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3, 4, 5));
        Parameter<Integer> param = getProvider().createParameter("numbers", domain);

        // Generate multiple values
        Set<Integer> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Integer value = param.generate();
            assertThat(value).isNotNull();
            assertThat(value).isIn(1, 2, 3, 4, 5);
            generated.add(value);
        }

        // Should eventually generate multiple different values
        assertThat(generated.size()).isGreaterThan(1);
    }

    @Test
    public void testParameterGeneratesWithRandom() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("x", "y", "z"));
        Parameter<String> param = getProvider().createParameter("letters", domain);

        Random rng = new Random(42);
        String value = param.generate(rng);

        assertThat(value).isIn("x", "y", "z");
    }

    @Test
    public void testParameterValidatesCorrectValue() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("valid", "also-valid"));
        Parameter<String> param = getProvider().createParameter("test", domain);

        Value<String> value = getProvider().createValue("valid", "test");
        assertThat(param.validate(value).isValid()).isTrue();
    }

    @Test
    public void testParameterRejectsInvalidValue() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("valid", "also-valid"));
        Parameter<String> param = getProvider().createParameter("test", domain);

        Value<String> value = getProvider().createValue("invalid", "test");
        assertThat(param.validate(value).isValid()).isFalse();
    }

    @Test
    public void testParameterHasMetadata() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3));
        Parameter<Integer> param = getProvider().createParameter("test", domain);

        assertThat(param.metadata()).isNotNull();
        assertThat(param.metadata().tags()).isNotNull();
        assertThat(param.metadata().description()).isNotNull();
    }

    @Test
    public void testParameterGeneratesConsistentlyWithSameSeed() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        Parameter<Integer> param = getProvider().createParameter("test", domain);

        Random rng1 = new Random(12345);
        Random rng2 = new Random(12345);

        Integer value1 = param.generate(rng1);
        Integer value2 = param.generate(rng2);

        assertThat(value1).isEqualTo(value2);
    }
}
