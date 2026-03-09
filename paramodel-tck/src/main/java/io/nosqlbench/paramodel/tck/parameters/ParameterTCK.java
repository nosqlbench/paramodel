package io.nosqlbench.paramodel.tck.parameters;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for Parameter contract.
 *
 * Validates that implementations correctly:
 * - Generate values from their domain
 * - Respect domain constraints
 * - Provide traits
 * - Handle validation
 */
public abstract class ParameterTCK {
    protected ParameterTCK() {}

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
    public void testParameterGeneratesFromDomain() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("x", "y", "z"));
        Parameter<String> param = getProvider().createParameter("letters", domain);

        String value = param.generate();

        assertThat(value).isIn("x", "y", "z");
    }

    @Test
    public void testParameterValidatesCorrectValue() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("valid", "also-valid"));
        Parameter<String> param = getProvider().createParameter("test", domain);

        assertThat(param.validate("valid").isPassed()).isTrue();
    }

    @Test
    public void testParameterRejectsInvalidValue() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("valid", "also-valid"));
        Parameter<String> param = getProvider().createParameter("test", domain);

        assertThat(param.validate("invalid").isPassed()).isFalse();
    }

    @Test
    public void testParameterHasNameAndType() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3));
        Parameter<Integer> param = getProvider().createParameter("test", domain);

        assertThat(param.name()).isNotNull();
        assertThat(param.name()).isEqualTo("test");
        assertThat(param.type()).isNotNull();
    }

    @Test
    public void testParameterGeneratesConsistently() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        Parameter<Integer> param = getProvider().createParameter("test", domain);

        // Generate should always produce values within domain
        for (int i = 0; i < 50; i++) {
            Integer value = param.generate();
            assertThat(value).isBetween(1, 10);
        }
    }

    @Test
    public void testParameterGeneratesBoundaryValues() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3, 4, 5));
        Parameter<Integer> param = getProvider().createParameter("boundary", domain);

        Integer boundary = param.generateBoundary();

        assertThat(boundary).isNotNull();
        assertThat(domain.contains(boundary)).isTrue();
    }

    @Test
    public void testParameterGeneratesRandomValues() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3, 4, 5));
        Parameter<Integer> param = getProvider().createParameter("random", domain);

        Integer random = param.generateRandom();

        assertThat(random).isNotNull();
        assertThat(domain.contains(random)).isTrue();
    }

    @Test
    public void testParameterSatisfiesConstraint() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3, 4, 5));
        Parameter<Integer> param = getProvider().createParameter("constrained", domain);

        Constraint<Integer> positive = n -> n > 0;
        Constraint<Integer> impossible = n -> n > 100;

        assertThat(param.satisfies(positive)).isTrue();
        assertThat(param.satisfies(impossible)).isFalse();
    }
}
