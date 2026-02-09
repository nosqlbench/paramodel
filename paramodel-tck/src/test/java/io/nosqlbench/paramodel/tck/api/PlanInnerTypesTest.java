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

import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.Barrier.*;
import io.nosqlbench.paramodel.plan.TestPlanMetadata;
import io.nosqlbench.paramodel.plan.TestPlanMetadata.*;
import io.nosqlbench.paramodel.plan.TrialOrdering;
import io.nosqlbench.paramodel.plan.TrialOrdering.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for inner types in TrialOrdering, TestPlanMetadata, and Barrier.
///
class PlanInnerTypesTest {

    // ── TrialOrdering Constants ────────────────────────────────────

    @Test
    void sequentialOrderingDescription() {
        assertThat(TrialOrdering.SEQUENTIAL.description())
            .isNotNull()
            .containsIgnoringCase("sequential");
    }

    @Test
    void sequentialOrderingThrowsOnOrder() {
        assertThatThrownBy(() -> TrialOrdering.SEQUENTIAL.order(List.of()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void edgeFirstOrderingDescription() {
        assertThat(TrialOrdering.EDGE_FIRST.description())
            .isNotNull()
            .containsIgnoringCase("edge");
    }

    @Test
    void edgeFirstOrderingThrowsOnOrder() {
        assertThatThrownBy(() -> TrialOrdering.EDGE_FIRST.order(List.of()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dependencyOptimizedOrderingDescription() {
        assertThat(TrialOrdering.DEPENDENCY_OPTIMIZED.description())
            .isNotNull()
            .containsIgnoringCase("dependency");
    }

    @Test
    void dependencyOptimizedOrderingThrowsOnOrder() {
        assertThatThrownBy(() -> TrialOrdering.DEPENDENCY_OPTIMIZED.order(List.of()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void costOptimizedOrderingDescription() {
        assertThat(TrialOrdering.COST_OPTIMIZED.description())
            .isNotNull()
            .containsIgnoringCase("cost");
    }

    @Test
    void costOptimizedOrderingThrowsOnOrder() {
        assertThatThrownBy(() -> TrialOrdering.COST_OPTIMIZED.order(List.of()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── TrialOrdering inner classes ────────────────────────────────

    @Test
    void sequentialOrderingIsInstantiable() {
        var ordering = new SequentialOrdering();
        assertThat(ordering).isInstanceOf(TrialOrdering.class);
    }

    @Test
    void edgeFirstOrderingIsInstantiable() {
        var ordering = new EdgeFirstOrdering();
        assertThat(ordering).isInstanceOf(TrialOrdering.class);
    }

    @Test
    void dependencyOptimizedOrderingIsInstantiable() {
        var ordering = new DependencyOptimizedOrdering();
        assertThat(ordering).isInstanceOf(TrialOrdering.class);
    }

    @Test
    void costOptimizedOrderingIsInstantiable() {
        var ordering = new CostOptimizedOrdering();
        assertThat(ordering).isInstanceOf(TrialOrdering.class);
    }

    // ── ShuffledOrdering ───────────────────────────────────────────

    @Test
    void shuffledOrderingWithSeed() {
        var ordering = TrialOrdering.shuffled(42L);
        assertThat(ordering).isInstanceOf(ShuffledOrdering.class);
        assertThat(((ShuffledOrdering) ordering).seed()).isEqualTo(42L);
    }

    @Test
    void shuffledOrderingDescription() {
        var ordering = TrialOrdering.shuffled(99L);
        assertThat(ordering.description())
            .isNotNull()
            .contains("99");
    }

    @Test
    void shuffledOrderingNoArgFactory() {
        var ordering = TrialOrdering.shuffled();
        assertThat(ordering).isInstanceOf(ShuffledOrdering.class);
        assertThat(ordering.description()).containsIgnoringCase("shuffled");
    }

    @Test
    void shuffledOrderingThrowsOnOrder() {
        var ordering = TrialOrdering.shuffled(1L);
        assertThatThrownBy(() -> ordering.order(List.of()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── CustomOrdering ─────────────────────────────────────────────

    @Test
    void customOrderingWithComparator() {
        Comparator<io.nosqlbench.paramodel.sequence.Trial> comparator =
            Comparator.comparing(io.nosqlbench.paramodel.sequence.Trial::id);
        var ordering = TrialOrdering.custom(comparator);
        assertThat(ordering).isInstanceOf(CustomOrdering.class);
        assertThat(ordering.description()).isEqualTo("Custom ordering");
    }

    @Test
    void customOrderingWithDescription() {
        Comparator<io.nosqlbench.paramodel.sequence.Trial> comparator =
            Comparator.comparing(io.nosqlbench.paramodel.sequence.Trial::id);
        var ordering = TrialOrdering.custom(comparator, "by trial ID");
        assertThat(ordering.description()).isEqualTo("by trial ID");
    }

    @Test
    void customOrderingComparatorAccessor() {
        Comparator<io.nosqlbench.paramodel.sequence.Trial> comparator =
            Comparator.comparing(io.nosqlbench.paramodel.sequence.Trial::id);
        var ordering = (CustomOrdering) TrialOrdering.custom(comparator);
        assertThat(ordering.comparator()).isSameAs(comparator);
    }

    // ── SemanticVersion ────────────────────────────────────────────

    @Test
    void semanticVersionAccessors() {
        var v = new SemanticVersion(1, 2, 3);
        assertThat(v.major()).isEqualTo(1);
        assertThat(v.minor()).isEqualTo(2);
        assertThat(v.patch()).isEqualTo(3);
    }

    @Test
    void semanticVersionParse() {
        var v = SemanticVersion.parse("4.5.6");
        assertThat(v.major()).isEqualTo(4);
        assertThat(v.minor()).isEqualTo(5);
        assertThat(v.patch()).isEqualTo(6);
    }

    @Test
    void semanticVersionParseZeros() {
        var v = SemanticVersion.parse("0.0.0");
        assertThat(v).isEqualTo(new SemanticVersion(0, 0, 0));
    }

    @Test
    void semanticVersionParseInvalidTwoParts() {
        assertThatThrownBy(() -> SemanticVersion.parse("1.2"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MAJOR.MINOR.PATCH");
    }

    @Test
    void semanticVersionParseInvalidFourParts() {
        assertThatThrownBy(() -> SemanticVersion.parse("1.2.3.4"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void semanticVersionParseNonNumeric() {
        assertThatThrownBy(() -> SemanticVersion.parse("a.b.c"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-numeric");
    }

    @Test
    void semanticVersionCompareMajor() {
        var v1 = new SemanticVersion(1, 0, 0);
        var v2 = new SemanticVersion(2, 0, 0);
        assertThat(v1.compareTo(v2)).isNegative();
        assertThat(v2.compareTo(v1)).isPositive();
    }

    @Test
    void semanticVersionCompareMinor() {
        var v1 = new SemanticVersion(1, 1, 0);
        var v2 = new SemanticVersion(1, 2, 0);
        assertThat(v1.compareTo(v2)).isNegative();
    }

    @Test
    void semanticVersionComparePatch() {
        var v1 = new SemanticVersion(1, 2, 3);
        var v2 = new SemanticVersion(1, 2, 4);
        assertThat(v1.compareTo(v2)).isNegative();
    }

    @Test
    void semanticVersionCompareEqual() {
        var v = new SemanticVersion(1, 2, 3);
        assertThat(v.compareTo(new SemanticVersion(1, 2, 3))).isZero();
    }

    @Test
    void semanticVersionToString() {
        var v = new SemanticVersion(3, 14, 159);
        assertThat(v.toString()).isEqualTo("3.14.159");
    }

    @Test
    void semanticVersionIsCompatibleWith() {
        var v1 = new SemanticVersion(1, 2, 0);
        var v2 = new SemanticVersion(1, 2, 5);
        assertThat(v1.isCompatibleWith(v2)).isTrue();
    }

    @Test
    void semanticVersionIsNotCompatibleDifferentMinor() {
        var v1 = new SemanticVersion(1, 2, 0);
        var v2 = new SemanticVersion(1, 3, 0);
        assertThat(v1.isCompatibleWith(v2)).isFalse();
    }

    @Test
    void semanticVersionIsNotCompatibleDifferentMajor() {
        var v1 = new SemanticVersion(1, 2, 3);
        var v2 = new SemanticVersion(2, 2, 3);
        assertThat(v1.isCompatibleWith(v2)).isFalse();
    }

    @Test
    void semanticVersionParseRoundTrip() {
        var original = new SemanticVersion(7, 8, 9);
        var parsed = SemanticVersion.parse(original.toString());
        assertThat(parsed).isEqualTo(original);
    }

    // ── LifecycleState ─────────────────────────────────────────────

    @Test
    void lifecycleStateValues() {
        assertThat(LifecycleState.values()).containsExactlyInAnyOrder(
            LifecycleState.DRAFT,
            LifecycleState.VALIDATED,
            LifecycleState.COMMITTED,
            LifecycleState.EXECUTING,
            LifecycleState.COMPLETED,
            LifecycleState.FAILED,
            LifecycleState.CANCELLED,
            LifecycleState.ARCHIVED);
    }

    @ParameterizedTest
    @EnumSource(value = LifecycleState.class,
        names = {"COMPLETED", "FAILED", "CANCELLED", "ARCHIVED"})
    void lifecycleStateTerminal(LifecycleState state) {
        assertThat(state.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = LifecycleState.class,
        names = {"DRAFT", "VALIDATED", "COMMITTED", "EXECUTING"})
    void lifecycleStateNonTerminal(LifecycleState state) {
        assertThat(state.isTerminal()).isFalse();
    }

    @Test
    void lifecycleStateOnlyCompletedIsSuccess() {
        assertThat(LifecycleState.COMPLETED.isSuccess()).isTrue();
        for (var state : LifecycleState.values()) {
            if (state != LifecycleState.COMPLETED) {
                assertThat(state.isSuccess()).isFalse();
            }
        }
    }

    @Test
    void lifecycleStateFailedAndCancelledAreFailure() {
        assertThat(LifecycleState.FAILED.isFailure()).isTrue();
        assertThat(LifecycleState.CANCELLED.isFailure()).isTrue();
    }

    @Test
    void lifecycleStateNonFailureStates() {
        assertThat(LifecycleState.DRAFT.isFailure()).isFalse();
        assertThat(LifecycleState.VALIDATED.isFailure()).isFalse();
        assertThat(LifecycleState.COMMITTED.isFailure()).isFalse();
        assertThat(LifecycleState.EXECUTING.isFailure()).isFalse();
        assertThat(LifecycleState.COMPLETED.isFailure()).isFalse();
        assertThat(LifecycleState.ARCHIVED.isFailure()).isFalse();
    }

    // ── BarrierType enum ───────────────────────────────────────────

    @Test
    void barrierTypeValues() {
        assertThat(BarrierType.values()).containsExactlyInAnyOrder(
            BarrierType.ELEMENT_READY,
            BarrierType.ELEMENT_SCOPE_END,
            BarrierType.TRIAL_BATCH,
            BarrierType.CHECKPOINT_BOUNDARY,
            BarrierType.CUSTOM);
    }

    // ── BarrierState enum ──────────────────────────────────────────

    @Test
    void barrierStateValues() {
        assertThat(BarrierState.values()).containsExactlyInAnyOrder(
            BarrierState.PENDING,
            BarrierState.SATISFIED,
            BarrierState.FAILED,
            BarrierState.TIMEOUT);
    }

    // ── TimeoutAction enum ─────────────────────────────────────────

    @Test
    void timeoutActionValues() {
        assertThat(TimeoutAction.values()).containsExactlyInAnyOrder(
            TimeoutAction.FAIL_FAST,
            TimeoutAction.SKIP_DEPENDENT,
            TimeoutAction.WAIT_FOREVER,
            TimeoutAction.RETRY);
    }

    // ── BarrierException ───────────────────────────────────────────

    @Test
    void barrierExceptionBasic() {
        var ex = new BarrierException("b-1", "timed out", BarrierState.TIMEOUT);
        assertThat(ex.barrierId()).isEqualTo("b-1");
        assertThat(ex.getMessage()).isEqualTo("timed out");
        assertThat(ex.finalState()).isEqualTo(BarrierState.TIMEOUT);
    }

    @Test
    void barrierExceptionWithCause() {
        var cause = new RuntimeException("network error");
        var ex = new BarrierException("b-2", "dependency failed", cause, BarrierState.FAILED);
        assertThat(ex.barrierId()).isEqualTo("b-2");
        assertThat(ex.getMessage()).isEqualTo("dependency failed");
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.finalState()).isEqualTo(BarrierState.FAILED);
    }

    @Test
    void barrierExceptionIsCheckedException() {
        assertThat(new BarrierException("b", "msg", BarrierState.FAILED))
            .isInstanceOf(Exception.class)
            .isNotInstanceOf(RuntimeException.class);
    }
}
