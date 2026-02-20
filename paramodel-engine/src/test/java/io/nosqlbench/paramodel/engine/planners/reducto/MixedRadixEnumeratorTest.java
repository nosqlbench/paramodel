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
package io.nosqlbench.paramodel.engine.planners.reducto;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.mock.plan.MockAxis;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.mock.plan.MockTestPlan;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.StringParameter;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/// Unit tests for {@link MixedRadixEnumerator}.
class MixedRadixEnumeratorTest {

    @Test
    @DisplayName("Single parameter: decompose and compose are inverse")
    void singleParameterRoundtrip() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{4}, List.of("threads"), List.of("server"));

        assertThat(enumerator.totalTrials()).isEqualTo(4);
        assertThat(enumerator.rankCount()).isEqualTo(1);

        for (int t = 0; t < 4; t++) {
            int[] offsets = enumerator.decompose(t);
            assertThat(offsets).hasSize(1);
            assertThat(offsets[0]).isEqualTo(t);
            assertThat(enumerator.compose(offsets)).isEqualTo(t);
        }
    }

    @Test
    @DisplayName("Two parameters: 3×2 = 6 trials, roundtrip all")
    void twoParameterRoundtrip() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2}, List.of("x", "y"), List.of("a", "a"));

        assertThat(enumerator.totalTrials()).isEqualTo(6);

        for (int t = 0; t < 6; t++) {
            int[] offsets = enumerator.decompose(t);
            assertThat(enumerator.compose(offsets)).isEqualTo(t);
        }

        // Verify specific decompositions: T0=(0,0), T1=(0,1), T2=(1,0), T3=(1,1), T4=(2,0), T5=(2,1)
        assertThat(enumerator.decompose(0)).containsExactly(0, 0);
        assertThat(enumerator.decompose(1)).containsExactly(0, 1);
        assertThat(enumerator.decompose(2)).containsExactly(1, 0);
        assertThat(enumerator.decompose(5)).containsExactly(2, 1);
    }

    @Test
    @DisplayName("Three parameters: 3×2×3 = 18 trials (worked example)")
    void threeParameterWorkedExample() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2, 3},
            List.of("param_x", "param_y", "param_u"),
            List.of("a", "a", "b"));

        assertThat(enumerator.totalTrials()).isEqualTo(18);

        // Roundtrip all 18 trials
        for (int t = 0; t < 18; t++) {
            int[] offsets = enumerator.decompose(t);
            assertThat(enumerator.compose(offsets)).isEqualTo(t);
        }
    }

    @Test
    @DisplayName("sameGroup: trials in same group at level 1")
    void sameGroupLevel1() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2, 3},
            List.of("x", "y", "u"),
            List.of("a", "a", "b"));

        // T0=(0,0,0) and T5=(0,1,2): same x=0 → same group at level 1
        assertThat(enumerator.sameGroup(0, 5, 1)).isTrue();

        // T0=(0,0,0) and T6=(1,0,0): different x → different group at level 1
        assertThat(enumerator.sameGroup(0, 6, 1)).isFalse();
    }

    @Test
    @DisplayName("sameGroup: trials in same group at level 2")
    void sameGroupLevel2() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2, 3},
            List.of("x", "y", "u"),
            List.of("a", "a", "b"));

        // T0=(0,0,0) and T2=(0,0,2): same x=0,y=0 → same group at level 2
        assertThat(enumerator.sameGroup(0, 2, 2)).isTrue();

        // T0=(0,0,0) and T3=(0,1,0): different y → different group at level 2
        assertThat(enumerator.sameGroup(0, 3, 2)).isFalse();
    }

    @Test
    @DisplayName("groupIndex computation at various levels")
    void groupIndex() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2, 3},
            List.of("x", "y", "u"),
            List.of("a", "a", "b"));

        // Level 0: all trials in group 0
        assertThat(enumerator.groupIndex(0, 0)).isEqualTo(0);
        assertThat(enumerator.groupIndex(17, 0)).isEqualTo(0);

        // Level 1: 3 groups (by x)
        assertThat(enumerator.groupIndex(0, 1)).isEqualTo(0);   // x=0
        assertThat(enumerator.groupIndex(6, 1)).isEqualTo(1);   // x=1
        assertThat(enumerator.groupIndex(12, 1)).isEqualTo(2);  // x=2

        // Level 2: 6 groups (by x×y)
        assertThat(enumerator.groupIndex(0, 2)).isEqualTo(0);   // x=0,y=0
        assertThat(enumerator.groupIndex(3, 2)).isEqualTo(1);   // x=0,y=1
        assertThat(enumerator.groupIndex(6, 2)).isEqualTo(2);   // x=1,y=0
    }

    @Test
    @DisplayName("groupCount at various levels")
    void groupCount() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2, 3},
            List.of("x", "y", "u"),
            List.of("a", "a", "b"));

        assertThat(enumerator.groupCount(0)).isEqualTo(1);
        assertThat(enumerator.groupCount(1)).isEqualTo(3);
        assertThat(enumerator.groupCount(2)).isEqualTo(6);
        assertThat(enumerator.groupCount(3)).isEqualTo(18);
    }

    @Test
    @DisplayName("trialsPerGroup at various levels")
    void trialsPerGroup() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2, 3},
            List.of("x", "y", "u"),
            List.of("a", "a", "b"));

        assertThat(enumerator.trialsPerGroup(0)).isEqualTo(18);
        assertThat(enumerator.trialsPerGroup(1)).isEqualTo(6);
        assertThat(enumerator.trialsPerGroup(2)).isEqualTo(3);
        assertThat(enumerator.trialsPerGroup(3)).isEqualTo(1);
    }

    @Test
    @DisplayName("Boundary: out-of-range trial number throws")
    void outOfRangeThrows() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{3}, List.of("x"), List.of("a"));

        assertThatThrownBy(() -> enumerator.decompose(-1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> enumerator.decompose(3))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromElements: derives cardinalities from axes targeting elements")
    void fromElementsWithAxes() {
        var paramX = IntegerParameter.range("param_x", 1, 3);
        Element a = MockElement.builder("a")
            .parameter(paramX)
            .build();
        Element b = MockElement.of("b");

        var axisX = MockAxis.of("param_x", 1, 2, 3);

        TestPlan plan = MockTestPlan.builder()
            .name("test")
            .axis(axisX)
            .element(a)
            .element(b)
            .build();

        MixedRadixEnumerator enumerator = MixedRadixEnumerator.fromElements(
            List.of(a, b), plan);

        assertThat(enumerator.totalTrials()).isEqualTo(3);
        assertThat(enumerator.rankCount()).isEqualTo(1);
        assertThat(enumerator.parameterName(0)).isEqualTo("param_x");
        assertThat(enumerator.elementName(0)).isEqualTo("a");
    }

    @Test
    @DisplayName("fromElements: multi-param element derives cardinalities")
    void fromElementsMultiParam() {
        var paramX = IntegerParameter.range("param_x", 1, 3);
        var paramY = IntegerParameter.range("param_y", 10, 20);
        Element a = MockElement.builder("a")
            .parameter(paramX)
            .parameter(paramY)
            .build();

        var paramU = StringParameter.of("param_u");
        Element b = MockElement.builder("b")
            .parameter(paramU)
            .dependency(a)
            .build();

        var axisX = MockAxis.of("param_x", 1, 2, 3);
        var axisY = MockAxis.of("param_y", 10, 20);
        var axisU = MockAxis.of("param_u", "asm", "dra", "ghi");

        TestPlan plan = MockTestPlan.builder()
            .name("test")
            .axis(axisX)
            .axis(axisY)
            .axis(axisU)
            .element(a)
            .element(b)
            .build();

        MixedRadixEnumerator enumerator = MixedRadixEnumerator.fromElements(
            List.of(a, b), plan);

        assertThat(enumerator.totalTrials()).isEqualTo(18);
        assertThat(enumerator.rankCount()).isEqualTo(3);
        assertThat(enumerator.elementName(0)).isEqualTo("a");
        assertThat(enumerator.elementName(1)).isEqualTo("a");
        assertThat(enumerator.elementName(2)).isEqualTo("b");
    }

    @Test
    @DisplayName("trialCode: narrow mode (all cardinalities ≤ 16)")
    void trialCodeNarrowMode() {
        // axes: a[3], b[3] → max cardinality = 3 ≤ 16 → 1 hex digit per axis
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 3}, List.of("a", "b"), List.of("e1", "e2"));

        // Trial 0: offsets (0,0) → "0x00"
        assertThat(enumerator.trialCode(0)).isEqualTo("0x00");

        // Trial 4: offsets (1,1) → "0x11"
        assertThat(enumerator.trialCode(4)).isEqualTo("0x11");

        // Trial 8: offsets (2,2) → "0x22"
        assertThat(enumerator.trialCode(8)).isEqualTo("0x22");
    }

    @Test
    @DisplayName("trialCode: spec example — 3×3 with trial 4")
    void trialCodeSpecExample() {
        // From reducto.md: axes a=[1,2,3] b=[asm,dra,ghi]
        // Trial 4 (id=4): a=2(offset=1), b=dra(offset=1) → "0x11"
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 3}, List.of("a", "b"), List.of("e1", "e2"));

        assertThat(enumerator.trialCode(4)).isEqualTo("0x11");
    }

    @Test
    @DisplayName("trialCode: spec example — 3×2×4 with trial 10")
    void trialCodeSpecExample2() {
        // From reducto.md: axes v1=[a,b,c], v2=[u,v], v3=[w,x,y,z]
        // Trial 10: stride=[8,4,1], offsets=(10/8%3=1, 10/4%2=0, 10/1%4=2) → "0x102"
        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2, 4}, List.of("v1", "v2", "v3"), List.of("e1", "e2", "e3"));

        assertThat(enumerator.trialCode(10)).isEqualTo("0x102");
    }

    @Test
    @DisplayName("trialCode: wide mode (cardinality > 16)")
    void trialCodeWideMode() {
        // axes: a[17], b[2] → max cardinality = 17 > 16 → 2 hex digits per axis
        var enumerator = new MixedRadixEnumerator(
            new int[]{17, 2}, List.of("a", "b"), List.of("e1", "e2"));

        // Trial 0: offsets (0,0) → "0x0000"
        assertThat(enumerator.trialCode(0)).isEqualTo("0x0000");

        // Trial 5: offsets (5/2%17=2, 5%2=1) → "0x0201"
        assertThat(enumerator.trialCode(5)).isEqualTo("0x0201");

        // Trial 33: offsets (33/2%17=16, 33%2=1) → "0x1001"
        assertThat(enumerator.trialCode(33)).isEqualTo("0x1001");
    }

    @Test
    @DisplayName("trialCode: single parameter")
    void trialCodeSingleParam() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{8}, List.of("threads"), List.of("svc"));

        assertThat(enumerator.trialCode(0)).isEqualTo("0x0");
        assertThat(enumerator.trialCode(7)).isEqualTo("0x7");
    }

    @Test
    @DisplayName("trialCode: hex digits a-f for offsets 10-15")
    void trialCodeHexDigits() {
        var enumerator = new MixedRadixEnumerator(
            new int[]{16}, List.of("val"), List.of("elem"));

        assertThat(enumerator.trialCode(10)).isEqualTo("0xa");
        assertThat(enumerator.trialCode(15)).isEqualTo("0xf");
    }

    @Test
    @DisplayName("Degenerate: no varying parameters produces single trial")
    void noVaryingParamsSingleTrial() {
        Element a = MockElement.of("a");

        TestPlan plan = MockTestPlan.builder()
            .name("test")
            .axis(MockAxis.of("mode", "default"))
            .element(a)
            .build();

        MixedRadixEnumerator enumerator = MixedRadixEnumerator.fromElements(
            List.of(a), plan);

        assertThat(enumerator.totalTrials()).isEqualTo(1);
    }
}
