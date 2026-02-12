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
package io.nosqlbench.paramodel.tck.engine;

import io.nosqlbench.paramodel.engine.binding.DefaultParameterBinder;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.parameters.types.BooleanParameter;
import io.nosqlbench.paramodel.parameters.types.IntegerParameter;
import io.nosqlbench.paramodel.parameters.types.StringParameter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

///
/// Tests for dynamic parameter binding through {@link ParameterBinder}
/// and {@link DefaultParameterBinder}.
///
class DynamicParameterBindingTest {

    // ── Static view matches existing behavior ───────────────────────

    @Test
    void bindingWithStaticViewMatchesExistingBehavior() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        ParameterView staticView = ParameterView.of(List.of(
            IntegerParameter.range("threads", 1, 64),
            StringParameter.of("host")
        ));

        ParameterBinding binding = binder.bind(staticView, Map.of(
            "threads", 8,
            "host", "localhost"
        ));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("threads", 8);
        assertThat(binding.toValueMap()).containsEntry("host", "localhost");
    }

    // ── Dynamic view resolves and binds dynamic params ──────────────

    @Test
    void bindingWithDynamicViewResolvesAndBindsDynamicParams() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);

        DynamicParameterResolver resolver = bindings -> {
            String type = (String) bindings.get("command_type");
            if ("read".equals(type)) {
                return List.of(
                    BooleanParameter.of("allow_filtering").withDefault(false)
                );
            }
            return List.of();
        };

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("command_type")),
            resolver
        );

        ParameterBinding binding = binder.bind(view, Map.of(
            "command_type", "read",
            "allow_filtering", true
        ));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("command_type", "read");
        assertThat(binding.toValueMap()).containsEntry("allow_filtering", true);
    }

    @Test
    void dynamicParamsUseDefaults() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);

        DynamicParameterResolver resolver = bindings ->
            List.of(IntegerParameter.range("ttl", 0, 86400).withDefault(3600));

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("type")),
            resolver
        );

        ParameterBinding binding = binder.bind(view, Map.of("type", "write"));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("type", "write");
        assertThat(binding.toValueMap()).containsEntry("ttl", 3600);
    }

    // ── Dynamic params validated against their domains ──────────────

    @Test
    void dynamicParamsValidatedAgainstDomains() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);

        DynamicParameterResolver resolver = bindings ->
            List.of(IntegerParameter.range("port", 1024, 65535));

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("type")),
            resolver
        );

        ParameterBinding binding = binder.bind(view, Map.of(
            "type", "service",
            "port", 99  // out of range
        ));

        assertThat(binding.validationResult().isFailed()).isTrue();
    }

    // ── Missing required params prevent dynamic resolution ──────────

    @Test
    void missingRequiredParamsResultInFailedBinding() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);

        DynamicParameterResolver resolver = bindings ->
            List.of(BooleanParameter.of("extra").withDefault(true));

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("command_type")),  // no default, not in inputs
            resolver
        );

        ParameterBinding binding = binder.bind(view, Map.of());

        // Required binding fails because command_type has no input and no default
        assertThat(binding.validationResult().isFailed()).isTrue();
    }

    // ── Passthrough works across both phases ────────────────────────

    @Test
    void passthroughWorksAcrossBothPhases() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);

        DynamicParameterResolver resolver = bindings ->
            List.of(BooleanParameter.of("verbose").withDefault(false));

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("mode")),
            resolver
        );

        ParameterBinding binding = binder.bind(view, Map.of(
            "mode", "debug",
            "CUSTOM_VAR", "some-value"
        ));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.passthroughValues()).containsEntry("CUSTOM_VAR", "some-value");
    }

    // ── Type coercion works for dynamic params ──────────────────────

    @Test
    void typeCoercionWorksForDynamicParams() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);

        DynamicParameterResolver resolver = bindings ->
            List.of(IntegerParameter.range("batch_size", 1, 1000));

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("mode")),
            resolver
        );

        ParameterBinding binding = binder.bind(view, Map.of(
            "mode", "batch",
            "batch_size", "256"  // string that should be coerced to Integer
        ));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.getValue("batch_size", Integer.class)).isEqualTo(256);
    }

    // ── Element integration ─────────────────────────────────────────

    @Test
    void bindElementWithDynamicView() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);

        MockElement element = MockElement.builder("test-element")
            .type("service")
            .requiredParameter(StringParameter.of("command_type"))
            .dynamicResolver(bindings -> {
                String type = (String) bindings.get("command_type");
                if ("read".equals(type)) {
                    return List.of(BooleanParameter.of("allow_filtering").withDefault(false));
                }
                return List.of(IntegerParameter.range("ttl", 0, 86400).withDefault(3600));
            })
            .build();

        assertThat(element.parameterView().isDynamic()).isTrue();

        ParameterBinding binding = binder.bind(element, Map.of(
            "command_type", "read",
            "allow_filtering", true
        ));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("command_type", "read");
        assertThat(binding.toValueMap()).containsEntry("allow_filtering", true);
    }

    @Test
    void bindElementWithStaticView() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);

        MockElement element = MockElement.builder("simple")
            .parameter(IntegerParameter.range("threads", 1, 64))
            .build();

        assertThat(element.parameterView().isDynamic()).isFalse();

        ParameterBinding binding = binder.bind(element, Map.of("threads", 8));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("threads", 8);
    }

    // ── Merged binding parameters include both phases ───────────────

    @Test
    void mergedBindingContainsAllParameters() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);

        DynamicParameterResolver resolver = bindings ->
            List.of(BooleanParameter.of("extra").withDefault(true));

        ParameterView view = ParameterView.dynamic(
            List.of(StringParameter.of("mode").withDefault("default")),
            resolver
        );

        ParameterBinding binding = binder.bind(view, Map.of());

        assertThat(binding.parameters()).hasSize(2);
        assertThat(binding.parameters().get(0).name()).isEqualTo("mode");
        assertThat(binding.parameters().get(1).name()).isEqualTo("extra");
    }
}
