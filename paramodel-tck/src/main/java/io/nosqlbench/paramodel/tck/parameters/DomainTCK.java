package io.nosqlbench.paramodel.tck.parameters;

import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Technology Compatibility Kit tests for Domain contract.
 *
 * Validates that implementations correctly:
 * - Define value spaces
 * - Sample values appropriately
 * - Check membership
 * - Compute cardinality
 */
public abstract class DomainTCK {
    protected DomainTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testDomainContainsValue() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("a", "b", "c"));

        assertThat(domain.contains("a")).isTrue();
        assertThat(domain.contains("b")).isTrue();
        assertThat(domain.contains("c")).isTrue();
        assertThat(domain.contains("d")).isFalse();
    }

    @Test
    public void testDomainSamplesFromValueSpace() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(10, 20, 30));

        Random rng = new Random();
        Set<Integer> samples = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            Integer sample = domain.sample(rng);
            assertThat(sample).isIn(10, 20, 30);
            samples.add(sample);
        }

        // Should see multiple values over 100 samples
        assertThat(samples.size()).isGreaterThan(1);
    }

    @Test
    public void testDomainCardinalityForDiscrete() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("x", "y", "z", "w"));

        assertThat(domain.cardinality()).isPresent();
        assertThat(domain.cardinality().orElseThrow()).isEqualTo(4L);
    }

    @Test
    public void testDomainIsFinite() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3));

        assertThat(domain.cardinality()).isPresent();
    }

    @Test
    public void testDomainSamplesConsistentlyWithSameSeed() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3, 4, 5));

        Random rng1 = new Random(9999);
        Random rng2 = new Random(9999);

        Integer sample1 = domain.sample(rng1);
        Integer sample2 = domain.sample(rng2);

        assertThat(sample1).isEqualTo(sample2);
    }

    @Test
    public void testEmptyDomainHasZeroCardinality() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of());

        assertThat(domain.cardinality()).isPresent();
        assertThat(domain.cardinality().orElseThrow()).isEqualTo(0L);
    }

    @Test
    public void testSingleElementDomain() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("only"));

        assertThat(domain.cardinality()).isPresent();
        assertThat(domain.cardinality().orElseThrow()).isEqualTo(1L);
        assertThat(domain.contains("only")).isTrue();

        Random rng = new Random();
        assertThat(domain.sample(rng)).isEqualTo("only");
    }

    @Test
    public void testDomainEquality() {
        Domain<Integer> domain1 = getProvider().createDiscreteDomain(List.of(1, 2, 3));
        Domain<Integer> domain2 = getProvider().createDiscreteDomain(List.of(1, 2, 3));

        // Domains with same values should be equal (or at least behave identically)
        assertThat(domain1.cardinality()).isEqualTo(domain2.cardinality());
        assertThat(domain1.contains(1)).isEqualTo(domain2.contains(1));
        assertThat(domain1.contains(2)).isEqualTo(domain2.contains(2));
        assertThat(domain1.contains(3)).isEqualTo(domain2.contains(3));
    }

    @Test
    public void testDomainEnumerate() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("a", "b", "c"));

        Iterator<String> iter = domain.enumerate();
        assertThat(iter).isNotNull();

        Set<String> enumerated = new HashSet<>();
        while (iter.hasNext()) {
            enumerated.add(iter.next());
        }

        assertThat(enumerated).hasSize(domain.cardinality().orElseThrow().intValue());
    }

    @Test
    public void testDomainBoundaryValues() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(10, 20, 30));

        Set<Integer> boundaries = domain.boundaryValues();

        assertThat(boundaries).isNotEmpty();
        for (Integer boundary : boundaries) {
            assertThat(domain.contains(boundary)).isTrue();
        }
    }

    @Test
    public void testDomainEnumerateIsConsistentWithContains() {
        Domain<String> domain = getProvider().createDiscreteDomain(List.of("x", "y", "z"));

        Iterator<String> iter = domain.enumerate();
        while (iter.hasNext()) {
            String value = iter.next();
            assertThat(domain.contains(value))
                .as("Enumerated value '%s' should be contained in domain", value)
                .isTrue();
        }
    }

    @Test
    public void testDomainBoundaryValuesAreContained() {
        Domain<Integer> domain = getProvider().createDiscreteDomain(List.of(1, 2, 3, 4, 5));

        Set<Integer> boundaries = domain.boundaryValues();
        for (Integer boundary : boundaries) {
            assertThat(domain.contains(boundary))
                .as("Boundary value %d should be contained in domain", boundary)
                .isTrue();
        }
    }
}
