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

/// Unit tests for {@link BindingStateComputer}.
class BindingStateComputerTest {

    @Test
    @DisplayName("Run-scoped element: binding level 0, no varying parameters")
    void runScopedElement() {
        Element db = MockElement.of("db");

        var enumerator = new MixedRadixEnumerator(
            new int[]{1}, List.of("__single"), List.of("__single"));

        BindingStateComputer bsc = BindingStateComputer.compute(List.of(db), enumerator);

        var binding = bsc.binding("db");
        assertThat(binding).isNotNull();
        assertThat(binding.isRunScoped()).isTrue();
        assertThat(binding.bindingLevel()).isEqualTo(0);
        assertThat(binding.parameterCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Single axis element: bound at level 1")
    void singleAxisElement() {
        var paramT = IntegerParameter.range("threads", 1, 4);
        Element svc = MockElement.builder("svc")
            .parameter(paramT)
            .build();

        var enumerator = new MixedRadixEnumerator(
            new int[]{4}, List.of("threads"), List.of("svc"));

        BindingStateComputer bsc = BindingStateComputer.compute(List.of(svc), enumerator);

        var binding = bsc.binding("svc");
        assertThat(binding.isRunScoped()).isFalse();
        assertThat(binding.bindingLevel()).isEqualTo(1);
        assertThat(binding.firstRank()).isEqualTo(0);
        assertThat(binding.parameterCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Two-param element: bound at level 2 (both params determined)")
    void twoParamElement() {
        var paramX = IntegerParameter.range("x", 1, 3);
        var paramY = IntegerParameter.range("y", 10, 20);
        Element a = MockElement.builder("a")
            .parameter(paramX)
            .parameter(paramY)
            .build();

        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2}, List.of("x", "y"), List.of("a", "a"));

        BindingStateComputer bsc = BindingStateComputer.compute(List.of(a), enumerator);

        var binding = bsc.binding("a");
        assertThat(binding.bindingLevel()).isEqualTo(2);
        assertThat(binding.firstRank()).isEqualTo(0);
        assertThat(binding.parameterCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Worked example: a at level 2, b at level 3 (per-trial)")
    void workedExample() {
        var paramX = IntegerParameter.range("x", 1, 3);
        var paramY = IntegerParameter.range("y", 10, 20);
        Element a = MockElement.builder("a")
            .parameter(paramX)
            .parameter(paramY)
            .build();

        var paramU = StringParameter.of("u");
        Element b = MockElement.builder("b")
            .parameter(paramU)
            .dependency(a)
            .build();

        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2, 3},
            List.of("x", "y", "u"),
            List.of("a", "a", "b"));

        BindingStateComputer bsc = BindingStateComputer.compute(List.of(a, b), enumerator);

        var bindingA = bsc.binding("a");
        assertThat(bindingA.bindingLevel()).isEqualTo(2);
        assertThat(bindingA.isTrialScoped(3)).isFalse();

        var bindingB = bsc.binding("b");
        assertThat(bindingB.bindingLevel()).isEqualTo(3);
        assertThat(bindingB.isTrialScoped(3)).isTrue();
    }

    @Test
    @DisplayName("sameGroupForElement: run-scoped always returns true")
    void sameGroupForRunScoped() {
        Element db = MockElement.of("db");

        var enumerator = new MixedRadixEnumerator(
            new int[]{3}, List.of("x"), List.of("svc"));

        BindingStateComputer bsc = BindingStateComputer.compute(List.of(db), enumerator);

        assertThat(bsc.sameGroupForElement("db", enumerator, 0, 1)).isTrue();
        assertThat(bsc.sameGroupForElement("db", enumerator, 0, 2)).isTrue();
    }

    @Test
    @DisplayName("groupIndexForElement: returns correct group for varying element")
    void groupIndexForElement() {
        var paramX = IntegerParameter.range("x", 1, 3);
        Element a = MockElement.builder("a")
            .parameter(paramX)
            .build();

        var enumerator = new MixedRadixEnumerator(
            new int[]{3, 2},
            List.of("x", "u"),
            List.of("a", "b"));

        BindingStateComputer bsc = BindingStateComputer.compute(List.of(a), enumerator);

        // a is bound at level 1 (rank 0, count 1)
        // T0=(0,0), T1=(0,1): both in group 0
        assertThat(bsc.groupIndexForElement("a", enumerator, 0)).isEqualTo(0);
        assertThat(bsc.groupIndexForElement("a", enumerator, 1)).isEqualTo(0);
        // T2=(1,0): group 1
        assertThat(bsc.groupIndexForElement("a", enumerator, 2)).isEqualTo(1);
        // T4=(2,0): group 2
        assertThat(bsc.groupIndexForElement("a", enumerator, 4)).isEqualTo(2);
    }
}
