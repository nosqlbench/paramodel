/*
 * Copyright (c) nosqlbench
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nosqlbench.paramodel.tck.api;

import io.nosqlbench.paramodel.parameters.Constraint;
import io.nosqlbench.paramodel.parameters.Domain;
import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.types.BooleanParameter;
import io.nosqlbench.paramodel.parameters.types.DoubleParameter;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.SelectionParameter;
import io.nosqlbench.paramodel.parameters.types.SelectionResolver;
import io.nosqlbench.paramodel.parameters.types.StringParameter;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for the built-in concrete parameter types in the API module.
///
class BuiltInParameterTypesTest {

    // ── IntegerParameter.range ──────────────────────────────────────

    @Test
    void integerRangeHasCorrectName() {
        var p = IntegerParameter.range("threads", 1, 64);
        assertThat(p.name()).isEqualTo("threads");
    }

    @Test
    void integerRangeHasCorrectTags() {
        var p = IntegerParameter.range("threads", 1, 64);
        assertThat(p.tags()).containsEntry("name", "threads");
        assertThat(p.tags()).containsEntry("type", "integer");
    }

    @Test
    void integerRangeDomainContainsValues() {
        var p = IntegerParameter.range("x", 1, 10);
        Domain<Integer> d = p.domain();
        assertThat(d.contains(1)).isTrue();
        assertThat(d.contains(5)).isTrue();
        assertThat(d.contains(10)).isTrue();
        assertThat(d.contains(0)).isFalse();
        assertThat(d.contains(11)).isFalse();
    }

    @Test
    void integerRangeDomainCardinality() {
        var p = IntegerParameter.range("x", 1, 10);
        assertThat(p.domain().cardinality()).hasValue(10L);
    }

    @Test
    void integerRangeDomainBoundaryValues() {
        var p = IntegerParameter.range("x", 1, 10);
        assertThat(p.domain().boundaryValues()).containsExactlyInAnyOrder(1, 10);
    }

    @Test
    void integerRangeDomainSingleValueBoundary() {
        var p = IntegerParameter.range("x", 5, 5);
        assertThat(p.domain().boundaryValues()).containsExactly(5);
        assertThat(p.domain().cardinality()).hasValue(1L);
    }

    @Test
    void integerRangeDomainSample() {
        var p = IntegerParameter.range("x", 1, 10);
        for (int i = 0; i < 50; i++) {
            Integer sample = p.domain().sample(new java.util.Random(i));
            assertThat(sample).isBetween(1, 10);
        }
    }

    @Test
    void integerRangeDomainEnumerate() {
        var p = IntegerParameter.range("x", 1, 5);
        Iterator<Integer> iter = p.domain().enumerate();
        assertThat(iter.hasNext()).isTrue();
        assertThat(iter.next()).isEqualTo(1);
        assertThat(iter.next()).isEqualTo(2);
        assertThat(iter.next()).isEqualTo(3);
        assertThat(iter.next()).isEqualTo(4);
        assertThat(iter.next()).isEqualTo(5);
        assertThat(iter.hasNext()).isFalse();
    }

    @Test
    void integerRangeGenerate() {
        var p = IntegerParameter.range("x", 1, 10);
        Integer val = p.generate();
        assertThat(val).isBetween(1, 10);
    }

    @Test
    void integerRangeGenerateBoundary() {
        var p = IntegerParameter.range("x", 1, 10);
        Integer val = p.generateBoundary();
        assertThat(val).isIn(1, 10);
    }

    @Test
    void integerRangeGenerateRandom() {
        var p = IntegerParameter.range("x", 1, 10);
        Integer val = p.generateRandom();
        assertThat(val).isBetween(1, 10);
    }

    @Test
    void integerRangeValidateAcceptsValid() {
        var p = IntegerParameter.range("x", 1, 10);
        assertThat(p.validate(5).isPassed()).isTrue();
    }

    @Test
    void integerRangeValidateRejectsNull() {
        var p = IntegerParameter.range("x", 1, 10);
        assertThat(p.validate(null).isFailed()).isTrue();
    }

    @Test
    void integerRangeValidateRejectsOutOfRange() {
        var p = IntegerParameter.range("x", 1, 10);
        assertThat(p.validate(11).isFailed()).isTrue();
        assertThat(p.validate(0).isFailed()).isTrue();
    }

    @Test
    void integerRangeWithConstraint() {
        var p = IntegerParameter.range("x", 1, 10)
            .withConstraint((Constraint<Integer>) v -> v % 2 == 0);
        assertThat(p.validate(4).isPassed()).isTrue();
        assertThat(p.validate(5).isFailed()).isTrue();
    }

    @Test
    void integerRangeSatisfiesConstraint() {
        var p = IntegerParameter.range("x", 1, 10);
        Constraint<Integer> positive = v -> v > 0;
        assertThat(p.satisfies(positive)).isTrue();
    }

    @Test
    void integerRangeInvalidRange() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> IntegerParameter.range("x", 10, 1));
    }

    // ── IntegerParameter.of (discrete) ──────────────────────────────

    @Test
    void integerDiscreteContainsValues() {
        var p = IntegerParameter.of("batch", Set.of(32, 64, 128));
        assertThat(p.domain().contains(32)).isTrue();
        assertThat(p.domain().contains(64)).isTrue();
        assertThat(p.domain().contains(50)).isFalse();
    }

    @Test
    void integerDiscreteCardinality() {
        var p = IntegerParameter.of("batch", Set.of(32, 64, 128));
        assertThat(p.domain().cardinality()).hasValue(3L);
    }

    @Test
    void integerDiscreteBoundaryValues() {
        var p = IntegerParameter.of("batch", Set.of(32, 64, 128));
        assertThat(p.domain().boundaryValues()).containsExactlyInAnyOrder(32, 128);
    }

    @Test
    void integerDiscreteSingleBoundary() {
        var p = IntegerParameter.of("batch", Set.of(42));
        assertThat(p.domain().boundaryValues()).containsExactly(42);
    }

    @Test
    void integerDiscreteSample() {
        var p = IntegerParameter.of("batch", Set.of(32, 64, 128));
        Integer sample = p.domain().sample(new java.util.Random(42));
        assertThat(sample).isIn(32, 64, 128);
    }

    @Test
    void integerDiscreteEnumerate() {
        var p = IntegerParameter.of("batch", Set.of(32, 64, 128));
        Iterator<Integer> iter = p.domain().enumerate();
        var values = new java.util.ArrayList<Integer>();
        iter.forEachRemaining(values::add);
        assertThat(values).containsExactlyInAnyOrder(32, 64, 128);
    }

    @Test
    void integerDiscreteValidate() {
        var p = IntegerParameter.of("batch", Set.of(32, 64));
        assertThat(p.validate(32).isPassed()).isTrue();
        assertThat(p.validate(50).isFailed()).isTrue();
        assertThat(p.validate(null).isFailed()).isTrue();
    }

    @Test
    void integerDiscreteRejectsEmptySet() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> IntegerParameter.of("x", Set.of()));
    }

    @Test
    void integerDiscreteRejectsNull() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> IntegerParameter.of("x", null));
    }

    // ── DoubleParameter ─────────────────────────────────────────────

    @Test
    void doubleRangeBasics() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        assertThat(p.name()).isEqualTo("temp");
        assertThat(p.tags()).containsEntry("type", "double");
    }

    @Test
    void doubleRangeDomainContains() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        assertThat(p.domain().contains(0.5)).isTrue();
        assertThat(p.domain().contains(0.0)).isTrue();
        assertThat(p.domain().contains(1.0)).isTrue();
        assertThat(p.domain().contains(-0.1)).isFalse();
        assertThat(p.domain().contains(1.1)).isFalse();
        assertThat(p.domain().contains(Double.NaN)).isFalse();
    }

    @Test
    void doubleRangeDomainCardinality() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        assertThat(p.domain().cardinality()).isEmpty();
    }

    @Test
    void doubleRangeDomainBoundaryValues() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        assertThat(p.domain().boundaryValues()).containsExactlyInAnyOrder(0.0, 1.0);
    }

    @Test
    void doubleRangeDomainSingleValueBoundary() {
        var p = DoubleParameter.range("temp", 0.5, 0.5);
        assertThat(p.domain().boundaryValues()).containsExactly(0.5);
    }

    @Test
    void doubleRangeEnumerateThrows() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        assertThatThrownBy(() -> p.domain().enumerate())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void doubleRangeSample() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        Double sample = p.domain().sample(new java.util.Random(42));
        assertThat(sample).isBetween(0.0, 1.0);
    }

    @Test
    void doubleRangeGenerate() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        assertThat(p.generate()).isBetween(0.0, 1.0);
        assertThat(p.generateBoundary()).isIn(0.0, 1.0);
        assertThat(p.generateRandom()).isBetween(0.0, 1.0);
    }

    @Test
    void doubleRangeValidate() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        assertThat(p.validate(0.5).isPassed()).isTrue();
        assertThat(p.validate(null).isFailed()).isTrue();
        assertThat(p.validate(1.5).isFailed()).isTrue();
    }

    @Test
    void doubleRangeWithConstraint() {
        var p = DoubleParameter.range("temp", 0.0, 1.0)
            .withConstraint((Constraint<Double>) v -> v > 0.1);
        assertThat(p.validate(0.5).isPassed()).isTrue();
        assertThat(p.validate(0.05).isFailed()).isTrue();
    }

    @Test
    void doubleRangeSatisfies() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        Constraint<Double> positive = v -> v >= 0.0;
        assertThat(p.satisfies(positive)).isTrue();
    }

    @Test
    void doubleRangeRejectsNaN() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DoubleParameter.range("x", Double.NaN, 1.0));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DoubleParameter.range("x", 0.0, Double.NaN));
    }

    @Test
    void doubleRangeRejectsInvertedRange() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DoubleParameter.range("x", 1.0, 0.0));
    }

    // ── BooleanParameter ────────────────────────────────────────────

    @Test
    void booleanBasics() {
        var p = BooleanParameter.of("enable_cache");
        assertThat(p.name()).isEqualTo("enable_cache");
        assertThat(p.tags()).containsEntry("type", "boolean");
    }

    @Test
    void booleanDomainContains() {
        var p = BooleanParameter.of("flag");
        assertThat(p.domain().contains(true)).isTrue();
        assertThat(p.domain().contains(false)).isTrue();
        assertThat(p.domain().contains(null)).isFalse();
    }

    @Test
    void booleanDomainCardinality() {
        var p = BooleanParameter.of("flag");
        assertThat(p.domain().cardinality()).hasValue(2L);
    }

    @Test
    void booleanDomainBoundaryValues() {
        var p = BooleanParameter.of("flag");
        assertThat(p.domain().boundaryValues()).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void booleanDomainEnumerate() {
        var p = BooleanParameter.of("flag");
        Iterator<Boolean> iter = p.domain().enumerate();
        assertThat(iter.next()).isEqualTo(false);
        assertThat(iter.next()).isEqualTo(true);
        assertThat(iter.hasNext()).isFalse();
    }

    @Test
    void booleanDomainSample() {
        var p = BooleanParameter.of("flag");
        Boolean sample = p.domain().sample(new java.util.Random(42));
        assertThat(sample).isNotNull();
    }

    @Test
    void booleanGenerate() {
        var p = BooleanParameter.of("flag");
        assertThat(p.generate()).isNotNull();
        assertThat(p.generateBoundary()).isNotNull();
        assertThat(p.generateRandom()).isNotNull();
    }

    @Test
    void booleanValidate() {
        var p = BooleanParameter.of("flag");
        assertThat(p.validate(true).isPassed()).isTrue();
        assertThat(p.validate(false).isPassed()).isTrue();
        assertThat(p.validate(null).isFailed()).isTrue();
    }

    @Test
    void booleanWithConstraint() {
        var p = BooleanParameter.of("flag")
            .withConstraint((Constraint<Boolean>) v -> v);
        assertThat(p.validate(true).isPassed()).isTrue();
        assertThat(p.validate(false).isFailed()).isTrue();
    }

    @Test
    void booleanSatisfies() {
        var p = BooleanParameter.of("flag");
        Constraint<Boolean> trueOnly = v -> v;
        assertThat(p.satisfies(trueOnly)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void booleanDomainIsDiscrete() {
        var p = BooleanParameter.of("flag");
        assertThat(p.domain()).isInstanceOf(Domain.Discrete.class);
        var discrete = (Domain.Discrete<Boolean>) p.domain();
        assertThat(discrete.values()).containsExactlyInAnyOrder(true, false);
    }

    // ── SelectionParameter (built-in) ───────────────────────────────

    @Test
    void selectionBasics() {
        var p = SelectionParameter.of("region", Set.of("us-east-1", "us-west-2", "eu-west-1"));
        assertThat(p.name()).isEqualTo("region");
        assertThat(p.tags()).containsEntry("type", "selection");
        assertThat(p.tags()).containsEntry("maxSelections", "1");
    }

    @Test
    void selectionGenerate() {
        var p = SelectionParameter.of("region", Set.of("us-east-1", "us-west-2"));
        List<String> val = p.generate();
        assertThat(val).isNotEmpty();
        assertThat(val).allSatisfy(v -> assertThat(v).isIn("us-east-1", "us-west-2"));
    }

    @Test
    void selectionGenerateBoundary() {
        var p = SelectionParameter.of("region", Set.of("a", "b", "c"));
        List<String> val = p.generateBoundary();
        assertThat(val).hasSize(1);
    }

    @Test
    void selectionGenerateRandom() {
        var p = SelectionParameter.of("region", Set.of("a", "b"));
        List<String> val = p.generateRandom();
        assertThat(val).isNotEmpty();
    }

    @Test
    void selectionValidateAcceptsSingle() {
        var p = SelectionParameter.of("region", Set.of("us-east-1", "us-west-2"));
        assertThat(p.validate(List.of("us-east-1")).isPassed()).isTrue();
    }

    @Test
    void selectionValidateRejectsInvalid() {
        var p = SelectionParameter.of("region", Set.of("us-east-1", "us-west-2"));
        assertThat(p.validate(List.of("invalid")).isFailed()).isTrue();
    }

    @Test
    void selectionValidateRejectsNull() {
        var p = SelectionParameter.of("region", Set.of("us-east-1"));
        assertThat(p.validate(null).isFailed()).isTrue();
    }

    @Test
    void selectionValidateRejectsExcessSelections() {
        var p = SelectionParameter.of("region", Set.of("a", "b", "c"));
        assertThat(p.validate(List.of("a", "b")).isFailed()).isTrue();
    }

    @Test
    void selectionMaxSelections() {
        var p = SelectionParameter.of("tags", Set.of("fast", "accurate", "cheap"))
            .maxSelections(2);
        assertThat(p.tags()).containsEntry("maxSelections", "2");
        assertThat(p.validate(List.of("fast", "accurate")).isPassed()).isTrue();
        assertThat(p.validate(List.of("fast", "accurate", "cheap")).isFailed()).isTrue();
    }

    @Test
    void selectionMaxSelectionsRejectsInvalid() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SelectionParameter.of("x", Set.of("a")).maxSelections(0));
    }

    @Test
    void selectionDomainContains() {
        var p = SelectionParameter.of("x", Set.of("a", "b"));
        assertThat(p.domain().contains(List.of("a"))).isTrue();
        assertThat(p.domain().contains(List.of("c"))).isFalse();
        assertThat(p.domain().contains(List.of("a", "b"))).isFalse();
    }

    @Test
    void selectionDomainCardinalitySingleSelect() {
        var p = SelectionParameter.of("x", Set.of("a", "b", "c"));
        assertThat(p.domain().cardinality()).hasValue(3L);
    }

    @Test
    void selectionDomainCardinalityMultiSelect() {
        var p = SelectionParameter.of("x", Set.of("a", "b", "c")).maxSelections(2);
        assertThat(p.domain().cardinality()).isEmpty();
    }

    @Test
    void selectionDomainBoundaryValues() {
        var p = SelectionParameter.of("x", Set.of("a", "b", "c"));
        Set<List<String>> boundaries = p.domain().boundaryValues();
        assertThat(boundaries).isNotEmpty();
        for (List<String> boundary : boundaries) {
            assertThat(boundary).hasSize(1);
        }
    }

    @Test
    void selectionDomainEnumerateSingleSelect() {
        var p = SelectionParameter.of("x", Set.of("a", "b"));
        Iterator<List<String>> iter = p.domain().enumerate();
        var values = new java.util.ArrayList<List<String>>();
        iter.forEachRemaining(values::add);
        assertThat(values).hasSize(2);
    }

    @Test
    void selectionDomainEnumerateMultiSelectThrows() {
        var p = SelectionParameter.of("x", Set.of("a", "b")).maxSelections(2);
        assertThatThrownBy(() -> p.domain().enumerate())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void selectionWithConstraint() {
        var p = SelectionParameter.of("x", Set.of("a", "b", "c"))
            .withConstraint((Constraint<List<String>>) v -> !v.contains("c"));
        assertThat(p.validate(List.of("a")).isPassed()).isTrue();
        assertThat(p.validate(List.of("c")).isFailed()).isTrue();
    }

    @Test
    void selectionSatisfies() {
        var p = SelectionParameter.of("x", Set.of("a", "b"));
        Constraint<List<String>> hasA = v -> v.contains("a");
        assertThat(p.satisfies(hasA)).isTrue();
    }

    @Test
    void selectionRejectsEmptyValues() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SelectionParameter.of("x", Set.of()));
    }

    @Test
    void selectionRejectsNullValues() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SelectionParameter.of("x", null));
    }

    // ── SelectionParameter (external) ───────────────────────────────

    @Test
    void selectionExternalBasics() {
        SelectionResolver resolver = new SelectionResolver() {
            @Override public Set<String> validValues() { return Set.of("model-a", "model-b"); }
            @Override public boolean isValid(String value) { return validValues().contains(value); }
        };
        var p = SelectionParameter.external("model", resolver);
        assertThat(p.name()).isEqualTo("model");
        assertThat(p.validate(List.of("model-a")).isPassed()).isTrue();
        assertThat(p.validate(List.of("unknown")).isFailed()).isTrue();
    }

    @Test
    void selectionExternalGenerate() {
        SelectionResolver resolver = new SelectionResolver() {
            @Override public Set<String> validValues() { return Set.of("a", "b"); }
            @Override public boolean isValid(String value) { return validValues().contains(value); }
        };
        var p = SelectionParameter.external("x", resolver);
        List<String> val = p.generate();
        assertThat(val).isNotEmpty();
        assertThat(val.getFirst()).isIn("a", "b");
    }

    @Test
    void selectionExternalRejectsNullResolver() {
        assertThatNullPointerException()
            .isThrownBy(() -> SelectionParameter.external("x", null));
    }

    // ── Validation result details ───────────────────────────────────

    @Test
    void integerRangeValidationResultHasViolations() {
        var p = IntegerParameter.range("x", 1, 10)
            .withConstraint((Constraint<Integer>) v -> v > 5);
        ValidationResult result = p.validate(3);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.violations()).isNotEmpty();
        assertThat(result.message()).isPresent();
    }

    // ── IntegerParameter.withDefault ─────────────────────────────────

    @Test
    void integerRangeWithDefault() {
        var p = IntegerParameter.range("threads", 1, 64).withDefault(4);
        assertThat(p.defaultValue()).hasValue(4);
    }

    @Test
    void integerRangeWithDefaultRejectsOutOfDomain() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> IntegerParameter.range("threads", 1, 64).withDefault(100));
    }

    @Test
    void integerRangeDefaultValueEmptyByDefault() {
        var p = IntegerParameter.range("threads", 1, 64);
        assertThat(p.defaultValue()).isEmpty();
    }

    @Test
    void integerDiscreteWithDefault() {
        var p = IntegerParameter.of("batch", Set.of(32, 64, 128)).withDefault(64);
        assertThat(p.defaultValue()).hasValue(64);
    }

    @Test
    void integerDiscreteWithDefaultRejectsOutOfDomain() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> IntegerParameter.of("batch", Set.of(32, 64, 128)).withDefault(50));
    }

    // ── DoubleParameter.withDefault ──────────────────────────────────

    @Test
    void doubleRangeWithDefault() {
        var p = DoubleParameter.range("temp", 0.0, 1.0).withDefault(0.5);
        assertThat(p.defaultValue()).hasValue(0.5);
    }

    @Test
    void doubleRangeWithDefaultRejectsOutOfDomain() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DoubleParameter.range("temp", 0.0, 1.0).withDefault(2.0));
    }

    @Test
    void doubleRangeDefaultValueEmptyByDefault() {
        var p = DoubleParameter.range("temp", 0.0, 1.0);
        assertThat(p.defaultValue()).isEmpty();
    }

    // ── BooleanParameter.withDefault ─────────────────────────────────

    @Test
    void booleanWithDefault() {
        var p = BooleanParameter.of("verbose").withDefault(false);
        assertThat(p.defaultValue()).hasValue(false);
    }

    @Test
    void booleanWithDefaultTrue() {
        var p = BooleanParameter.of("flag").withDefault(true);
        assertThat(p.defaultValue()).hasValue(true);
    }

    @Test
    void booleanDefaultValueEmptyByDefault() {
        var p = BooleanParameter.of("flag");
        assertThat(p.defaultValue()).isEmpty();
    }

    // ── SelectionParameter.withDefault ───────────────────────────────

    @Test
    void selectionWithDefault() {
        var p = SelectionParameter.of("region", Set.of("us-east-1", "us-west-2"))
            .withDefault(List.of("us-east-1"));
        assertThat(p.defaultValue()).hasValue(List.of("us-east-1"));
    }

    @Test
    void selectionWithDefaultRejectsInvalidSelection() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SelectionParameter.of("region", Set.of("us-east-1", "us-west-2"))
                .withDefault(List.of("invalid")));
    }

    @Test
    void selectionWithDefaultRejectsExcessSelections() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SelectionParameter.of("region", Set.of("a", "b", "c"))
                .withDefault(List.of("a", "b")));
    }

    @Test
    void selectionDefaultValueEmptyByDefault() {
        var p = SelectionParameter.of("region", Set.of("us-east-1"));
        assertThat(p.defaultValue()).isEmpty();
    }

    // ── StringParameter ──────────────────────────────────────────────

    @Test
    void stringParameterBasics() {
        var p = StringParameter.of("host");
        assertThat(p.name()).isEqualTo("host");
        assertThat(p.tags()).containsEntry("name", "host");
        assertThat(p.tags()).containsEntry("type", "string");
    }

    @Test
    void stringParameterDomainAcceptsAnyString() {
        var p = StringParameter.of("host");
        assertThat(p.domain().contains("localhost")).isTrue();
        assertThat(p.domain().contains("")).isTrue();
        assertThat(p.domain().contains(null)).isFalse();
    }

    @Test
    void stringParameterDomainCardinality() {
        var p = StringParameter.of("host");
        assertThat(p.domain().cardinality()).isEmpty();
    }

    @Test
    void stringParameterDomainEnumerateThrows() {
        var p = StringParameter.of("host");
        assertThatThrownBy(() -> p.domain().enumerate())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stringParameterGenerate() {
        var p = StringParameter.of("host");
        assertThat(p.generate()).isNotNull().isNotEmpty();
        assertThat(p.generateBoundary()).isNotNull();
        assertThat(p.generateRandom()).isNotNull().isNotEmpty();
    }

    @Test
    void stringParameterValidate() {
        var p = StringParameter.of("host");
        assertThat(p.validate("localhost").isPassed()).isTrue();
        assertThat(p.validate("").isPassed()).isTrue();
        assertThat(p.validate(null).isFailed()).isTrue();
    }

    @Test
    void stringParameterWithDefault() {
        var p = StringParameter.of("host").withDefault("localhost");
        assertThat(p.defaultValue()).hasValue("localhost");
    }

    @Test
    void stringParameterDefaultValueEmptyByDefault() {
        var p = StringParameter.of("host");
        assertThat(p.defaultValue()).isEmpty();
    }

    @Test
    void stringParameterWithConstraint() {
        var p = StringParameter.of("host")
            .withConstraint((Constraint<String>) v -> v.length() > 0);
        assertThat(p.validate("localhost").isPassed()).isTrue();
        assertThat(p.validate("").isFailed()).isTrue();
    }

    @Test
    void stringParameterSatisfies() {
        var p = StringParameter.of("host");
        Constraint<String> nonEmpty = v -> !v.isEmpty();
        assertThat(p.satisfies(nonEmpty)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void stringParameterDomainIsCustom() {
        var p = StringParameter.of("host");
        assertThat(p.domain()).isInstanceOf(Domain.Custom.class);
    }
}
