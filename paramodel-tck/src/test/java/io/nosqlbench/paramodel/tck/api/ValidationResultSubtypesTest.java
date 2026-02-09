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

import io.nosqlbench.paramodel.parameters.ValidationResult;
import io.nosqlbench.paramodel.parameters.ValidationResult.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for ValidationResult sealed subtypes: Passed, Failed, and Warning.
///
class ValidationResultSubtypesTest {

    // ── Passed ─────────────────────────────────────────────────────

    @Test
    void passedIsPassed() {
        var result = new Passed();
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void passedIsNotFailed() {
        var result = new Passed();
        assertThat(result.isFailed()).isFalse();
    }

    @Test
    void passedMessageIsEmpty() {
        var result = new Passed();
        assertThat(result.message()).isEmpty();
    }

    @Test
    void passedViolationsIsEmpty() {
        var result = new Passed();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void passedIsInstanceOfValidationResult() {
        assertThat((ValidationResult) new Passed()).isInstanceOf(ValidationResult.class);
    }

    // ── Failed ─────────────────────────────────────────────────────

    @Test
    void failedIsNotPassed() {
        var result = new Failed("error", List.of());
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void failedIsFailed() {
        var result = new Failed("error", List.of());
        assertThat(result.isFailed()).isTrue();
    }

    @Test
    void failedMessageIsPresent() {
        var result = new Failed("out of range", List.of());
        assertThat(result.message()).hasValue("out of range");
    }

    @Test
    void failedMsgAccessor() {
        var result = new Failed("bad value", List.of("v1", "v2"));
        assertThat(result.msg()).isEqualTo("bad value");
    }

    @Test
    void failedViolationsReturned() {
        var violations = List.of("too small", "not even");
        var result = new Failed("invalid", violations);
        assertThat(result.violations()).containsExactly("too small", "not even");
    }

    @Test
    void failedEmptyViolations() {
        var result = new Failed("just failed", List.of());
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void failedNullMessageThrows() {
        assertThatThrownBy(() -> new Failed(null, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void failedNullViolationsDefaultsToEmpty() {
        var result = new Failed("msg", null);
        assertThat(result.violations()).isEmpty();
    }

    // ── Warning ────────────────────────────────────────────────────

    @Test
    void warningWithPassedUnderlyingIsPassed() {
        var warning = new Warning("boundary value", new Passed());
        assertThat(warning.isPassed()).isTrue();
    }

    @Test
    void warningWithPassedUnderlyingIsNotFailed() {
        var warning = new Warning("boundary value", new Passed());
        assertThat(warning.isFailed()).isFalse();
    }

    @Test
    void warningWithFailedUnderlyingIsNotPassed() {
        var warning = new Warning("also broken",
            new Failed("hard fail", List.of("v1")));
        assertThat(warning.isPassed()).isFalse();
    }

    @Test
    void warningWithFailedUnderlyingIsFailed() {
        var warning = new Warning("also broken",
            new Failed("hard fail", List.of("v1")));
        assertThat(warning.isFailed()).isTrue();
    }

    @Test
    void warningMessageIsPresent() {
        var warning = new Warning("at maximum", new Passed());
        assertThat(warning.message()).hasValue("at maximum");
    }

    @Test
    void warningMsgAccessor() {
        var warning = new Warning("at boundary", new Passed());
        assertThat(warning.msg()).isEqualTo("at boundary");
    }

    @Test
    void warningViolationsFromPassedUnderlying() {
        var warning = new Warning("caution", new Passed());
        assertThat(warning.violations()).isEmpty();
    }

    @Test
    void warningViolationsFromFailedUnderlying() {
        var warning = new Warning("caution",
            new Failed("bad", List.of("v1", "v2")));
        assertThat(warning.violations()).containsExactly("v1", "v2");
    }

    @Test
    void warningUnderlyingAccessor() {
        var passed = new Passed();
        var warning = new Warning("note", passed);
        assertThat(warning.underlying()).isSameAs(passed);
    }

    @Test
    void warningNullMessageThrows() {
        assertThatThrownBy(() -> new Warning(null, new Passed()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void warningNullUnderlyingThrows() {
        assertThatThrownBy(() -> new Warning("msg", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }

    @Test
    void warningFlattensNestedWarning() {
        var inner = new Warning("inner warning", new Passed());
        var outer = new Warning("outer warning", inner);
        // Nested warnings are flattened — underlying should be the Passed, not the inner Warning
        assertThat(outer.underlying()).isInstanceOf(Passed.class);
    }

    @Test
    void warningDoublyNestedFlattens() {
        var deepest = new Warning("level 1", new Passed());
        var middle = new Warning("level 2", deepest);
        var outer = new Warning("level 3", middle);
        assertThat(outer.underlying()).isInstanceOf(Passed.class);
    }

    // ── Sealed hierarchy pattern matching ──────────────────────────

    @Test
    void sealedHierarchyPatternMatch() {
        ValidationResult passed = new Passed();
        ValidationResult failed = new Failed("err", List.of());
        ValidationResult warning = new Warning("warn", new Passed());

        // Verify exhaustive pattern matching compiles and works
        assertThat(describeResult(passed)).isEqualTo("passed");
        assertThat(describeResult(failed)).isEqualTo("failed");
        assertThat(describeResult(warning)).isEqualTo("warning");
    }

    @Test
    void sealedSubtypesAreCorrectInstances() {
        assertThat((ValidationResult) new Passed()).isInstanceOf(Passed.class);
        assertThat((ValidationResult) new Failed("x", List.of())).isInstanceOf(Failed.class);
        assertThat((ValidationResult) new Warning("x", new Passed())).isInstanceOf(Warning.class);
    }

    private static String describeResult(ValidationResult result) {
        return switch (result) {
            case Passed _ -> "passed";
            case Failed _ -> "failed";
            case Warning _ -> "warning";
        };
    }
}
