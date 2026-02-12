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

import io.nosqlbench.paramodel.parameters.DynamicParameterResolver;
import io.nosqlbench.paramodel.parameters.Parameter;
import io.nosqlbench.paramodel.parameters.ParameterView;
import io.nosqlbench.paramodel.parameters.types.BooleanParameter;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.StringParameter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests for {@link ParameterView} static and dynamic views.
///
class ParameterViewTest {

    // ── Static view ─────────────────────────────────────────────────

    @Test
    void staticViewReturnsAllParamsAsRequired() {
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64),
            StringParameter.of("host")
        );

        ParameterView view = ParameterView.of(params);

        assertThat(view.requiredParameters()).hasSize(2);
        assertThat(view.requiredParameters().get(0).name()).isEqualTo("threads");
        assertThat(view.requiredParameters().get(1).name()).isEqualTo("host");
    }

    @Test
    void staticViewHasNoDynamicParams() {
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterView view = ParameterView.of(params);

        assertThat(view.dynamicParameters(Map.of("threads", 8))).isEmpty();
    }

    @Test
    void staticViewIsNotDynamic() {
        ParameterView view = ParameterView.of(List.of(
            StringParameter.of("name")
        ));

        assertThat(view.isDynamic()).isFalse();
    }

    @Test
    void staticViewActiveParametersEqualsRequired() {
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64),
            BooleanParameter.of("verbose")
        );

        ParameterView view = ParameterView.of(params);

        List<Parameter<?>> active = view.activeParameters(Map.of());
        assertThat(active).hasSize(2);
        assertThat(active.get(0).name()).isEqualTo("threads");
        assertThat(active.get(1).name()).isEqualTo("verbose");
    }

    // ── Dynamic view ────────────────────────────────────────────────

    @Test
    void dynamicViewIsDynamic() {
        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("type")),
            bindings -> List.of()
        );

        assertThat(view.isDynamic()).isTrue();
    }

    @Test
    void dynamicViewCallsResolverWithBindings() {
        DynamicParameterResolver resolver = bindings -> {
            String type = (String) bindings.get("command_type");
            if ("read".equals(type)) {
                return List.of(BooleanParameter.of("allow_filtering"));
            }
            return List.of();
        };

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("command_type")),
            resolver
        );

        List<Parameter<?>> dynamicParams =
            view.dynamicParameters(Map.of("command_type", "read"));
        assertThat(dynamicParams).hasSize(1);
        assertThat(dynamicParams.get(0).name()).isEqualTo("allow_filtering");
    }

    @Test
    void activeParametersCombinesRequiredAndDynamic() {
        DynamicParameterResolver resolver = bindings ->
            List.of(IntegerParameter.range("ttl", 0, 86400));

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("command_type")),
            resolver
        );

        List<Parameter<?>> active = view.activeParameters(Map.of("command_type", "write"));
        assertThat(active).hasSize(2);
        assertThat(active.get(0).name()).isEqualTo("command_type");
        assertThat(active.get(1).name()).isEqualTo("ttl");
    }

    @Test
    void resolverReceivesCorrectRequiredBindings() {
        DynamicParameterResolver resolver = bindings -> {
            assertThat(bindings).containsEntry("mode", "batch");
            assertThat(bindings).containsEntry("size", 100);
            return List.of(BooleanParameter.of("parallel"));
        };

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("mode"), IntegerParameter.range("size", 1, 1000)),
            resolver
        );

        List<Parameter<?>> dynamic = view.dynamicParameters(
            Map.of("mode", "batch", "size", 100)
        );
        assertThat(dynamic).hasSize(1);
    }

    @Test
    void dynamicParamsChangeWhenRequiredBindingsChange() {
        DynamicParameterResolver resolver = bindings -> {
            String type = (String) bindings.get("type");
            return switch (type) {
                case "a" -> List.of(IntegerParameter.range("param_a", 1, 10));
                case "b" -> List.of(
                    StringParameter.of("param_b1"),
                    StringParameter.of("param_b2")
                );
                default -> List.of();
            };
        };

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("type")),
            resolver
        );

        List<Parameter<?>> dynamicA = view.dynamicParameters(Map.of("type", "a"));
        assertThat(dynamicA).hasSize(1);
        assertThat(dynamicA.get(0).name()).isEqualTo("param_a");

        List<Parameter<?>> dynamicB = view.dynamicParameters(Map.of("type", "b"));
        assertThat(dynamicB).hasSize(2);
        assertThat(dynamicB.get(0).name()).isEqualTo("param_b1");
        assertThat(dynamicB.get(1).name()).isEqualTo("param_b2");
    }

    @Test
    void emptyStaticView() {
        ParameterView view = ParameterView.of(List.of());

        assertThat(view.requiredParameters()).isEmpty();
        assertThat(view.dynamicParameters(Map.of())).isEmpty();
        assertThat(view.activeParameters(Map.of())).isEmpty();
        assertThat(view.isDynamic()).isFalse();
    }

    @Test
    void dynamicViewWithEmptyResolverResult() {
        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("type")),
            bindings -> List.of()
        );

        List<Parameter<?>> active = view.activeParameters(Map.of("type", "none"));
        assertThat(active).hasSize(1);
        assertThat(active.get(0).name()).isEqualTo("type");
    }
}
