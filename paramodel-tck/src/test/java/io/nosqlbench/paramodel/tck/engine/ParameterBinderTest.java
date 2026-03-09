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
import io.nosqlbench.paramodel.parameters.*;
import io.nosqlbench.paramodel.parameters.types.*;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for {@link DefaultParameterBinder} and the parameter binding workflow.
///
class ParameterBinderTest {

    // ── Basic binding ────────────────────────────────────────────────

    @Test
    void bindWithAllValuesProvided() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64),
            DoubleParameter.range("temp", 0.0, 1.0),
            BooleanParameter.of("verbose")
        );

        ParameterBinding binding = binder.bind(params, Map.of(
            "threads", 8,
            "temp", 0.5,
            "verbose", true
        ));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("threads", 8);
        assertThat(binding.toValueMap()).containsEntry("temp", 0.5);
        assertThat(binding.toValueMap()).containsEntry("verbose", true);
    }

    @Test
    void bindWithDefaultApplied() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64).withDefault(4),
            StringParameter.of("model").withDefault("default-model")
        );

        ParameterBinding binding = binder.bind(params, Map.of());

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("threads", 4);
        assertThat(binding.toValueMap()).containsEntry("model", "default-model");
    }

    @Test
    void bindWithMissingRequiredValue() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of());

        assertThat(binding.validationResult().isFailed()).isTrue();
        assertThat(binding.validationResult().violations()).isNotEmpty();
    }

    @Test
    void bindWithPartialInputsAndDefaults() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64).withDefault(4),
            StringParameter.of("host")
        );

        ParameterBinding binding = binder.bind(params, Map.of("host", "localhost"));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("threads", 4);
        assertThat(binding.toValueMap()).containsEntry("host", "localhost");
    }

    // ── Type coercion ────────────────────────────────────────────────

    @Test
    void coerceStringToInteger() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of("threads", "16"));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.getValue("threads", Integer.class)).isEqualTo(16);
    }

    @Test
    void coerceStringToDouble() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            DoubleParameter.range("temp", 0.0, 1.0)
        );

        ParameterBinding binding = binder.bind(params, Map.of("temp", "0.75"));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.getValue("temp", Double.class)).isEqualTo(0.75);
    }

    @Test
    void coerceStringToBoolean() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            BooleanParameter.of("verbose")
        );

        ParameterBinding binding = binder.bind(params, Map.of("verbose", "true"));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.getValue("verbose", Boolean.class)).isTrue();
    }

    @Test
    void coerceStringToSelectionList() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            SelectionParameter.of("region", Set.of("us-east-1", "us-west-2"))
        );

        ParameterBinding binding = binder.bind(params, Map.of("region", "us-east-1"));

        assertThat(binding.validationResult().isPassed()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> region = (List<String>) binding.toValueMap().get("region");
        assertThat(region).containsExactly("us-east-1");
    }

    @Test
    void coerceNumberToInteger() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of("threads", 16L));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.getValue("threads", Integer.class)).isEqualTo(16);
    }

    @Test
    void coerceNumberToDouble() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            DoubleParameter.range("temp", 0.0, 1.0)
        );

        ParameterBinding binding = binder.bind(params, Map.of("temp", 0.5f));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.getValue("temp", Double.class)).isCloseTo(0.5, within(0.01));
    }

    @Test
    void invalidCoercionFails() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of("threads", "not-a-number"));

        assertThat(binding.validationResult().isFailed()).isTrue();
    }

    // ── Derived parameters ───────────────────────────────────────────

    @Test
    void derivedParameterEvaluation() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);

        DerivedParameter<Integer> batchSize = new DerivedParameter<>() {
            @Override
            public Integer compute(Map<String, Object> boundValues) {
                return (int) boundValues.get("threads") * 2;
            }

            @Override
            public String expression() {
                return "threads * 2";
            }

            @Override
            public String name() {
                return "batch_size";
            }

            @Override
            public String type() {
                return "integer";
            }

            @Override
            public Domain<Integer> domain() {
                return IntegerParameter.range("batch_size", 1, 256).domain();
            }

            @Override
            public Integer generate() {
                return 8;
            }

            @Override
            public Integer generateBoundary() {
                return 1;
            }

            @Override
            public Integer generateRandom() {
                return 8;
            }

            @Override
            public ValidationResult validate(Integer value) {
                if (value == null) return new ValidationResult.Failed("null", List.of("null"));
                if (value < 1 || value > 256) return new ValidationResult.Failed(
                    "out of range", List.of("value " + value + " out of [1, 256]"));
                return new ValidationResult.Passed();
            }

            @Override
            public boolean satisfies(Constraint<Integer> constraint) {
                return constraint.test(8);
            }
        };

        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64),
            batchSize
        );

        ParameterBinding binding = binder.bind(params, Map.of("threads", 4));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.getValue("threads", Integer.class)).isEqualTo(4);
        assertThat(binding.getValue("batch_size", Integer.class)).isEqualTo(8);
    }

    // ── Passthrough handling ─────────────────────────────────────────

    @Test
    void passthroughLenientMode() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64).withDefault(4)
        );

        ParameterBinding binding = binder.bind(params, Map.of(
            "CUSTOM_VAR", "some-value",
            "OTHER_VAR", 42
        ));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.toValueMap()).containsEntry("threads", 4);
        assertThat(binding.toValueMap()).doesNotContainKey("CUSTOM_VAR");
        assertThat(binding.passthroughValues()).containsEntry("CUSTOM_VAR", "some-value");
        assertThat(binding.passthroughValues()).containsEntry("OTHER_VAR", 42);
    }

    @Test
    void passthroughStrictModeRejects() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64).withDefault(4)
        );

        ParameterBinding binding = binder.bind(params, Map.of("UNKNOWN", "value"));

        assertThat(binding.validationResult().isFailed()).isTrue();
    }

    // ── toValueMap excludes passthroughs ─────────────────────────────

    @Test
    void toValueMapExcludesPassthroughs() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            StringParameter.of("host").withDefault("localhost")
        );

        ParameterBinding binding = binder.bind(params, Map.of("EXTRA", "extra-val"));

        assertThat(binding.toValueMap()).containsKey("host");
        assertThat(binding.toValueMap()).doesNotContainKey("EXTRA");
        assertThat(binding.passthroughValues()).containsKey("EXTRA");
    }

    // ── Deterministic ordering ───────────────────────────────────────

    @Test
    void deterministicOrdering() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("alpha", 1, 10).withDefault(1),
            StringParameter.of("beta").withDefault("b"),
            BooleanParameter.of("gamma").withDefault(true),
            DoubleParameter.range("delta", 0.0, 1.0).withDefault(0.5)
        );

        ParameterBinding binding = binder.bind(params, Map.of());

        List<String> keys = new ArrayList<>(binding.toValueMap().keySet());
        assertThat(keys).containsExactly("alpha", "beta", "gamma", "delta");
    }

    // ── Value provenance ─────────────────────────────────────────────

    @Test
    void getValueReturnsTypedValue() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of("threads", 8));

        assertThat(binding.getValue("threads", Integer.class)).isEqualTo(8);
    }

    @Test
    void getValueThrowsForUnboundParameter() {
        var binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> binding.getValue("threads", Integer.class));
    }

    @Test
    void getReturnsOptionalValue() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of("threads", 8));

        assertThat(binding.get("threads")).isPresent();
        assertThat(binding.get("threads").get().value()).isEqualTo(8);
        assertThat(binding.get("nonexistent")).isEmpty();
    }

    @Test
    void parametersListPreserved() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        var p1 = IntegerParameter.range("a", 1, 10).withDefault(1);
        var p2 = StringParameter.of("b").withDefault("x");
        List<Parameter<?>> params = List.of(p1, p2);

        ParameterBinding binding = binder.bind(params, Map.of());

        assertThat(binding.parameters()).hasSize(2);
        assertThat(binding.parameters().get(0).name()).isEqualTo("a");
        assertThat(binding.parameters().get(1).name()).isEqualTo("b");
    }

    // ── Value metadata ───────────────────────────────────────────────

    @Test
    void userProvidedValueHasMetadata() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of("threads", 8));

        Value<?> v = binding.get("threads").orElseThrow();
        assertThat(v.parameterName()).isEqualTo("threads");
        assertThat(v.generatedAt()).isNotNull();
        assertThat(v.generatorMetadata()).isPresent();
        assertThat(v.generatorMetadata().get()).contains("user-provided");
    }

    @Test
    void defaultValueHasMetadata() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64).withDefault(4)
        );

        ParameterBinding binding = binder.bind(params, Map.of());

        Value<?> v = binding.get("threads").orElseThrow();
        assertThat(v.generatorMetadata()).isPresent();
        assertThat(v.generatorMetadata().get()).contains("default");
    }

    // ── Domain validation during binding ─────────────────────────────

    @Test
    void outOfDomainValueFails() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64)
        );

        ParameterBinding binding = binder.bind(params, Map.of("threads", 100));

        assertThat(binding.validationResult().isFailed()).isTrue();
    }

    // ── String parameter binding ─────────────────────────────────────

    @Test
    void stringParameterBinding() {
        var binder = new DefaultParameterBinder(BindingPolicy.STRICT);
        List<Parameter<?>> params = List.of(
            StringParameter.of("host")
        );

        ParameterBinding binding = binder.bind(params, Map.of("host", "localhost"));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.getValue("host", String.class)).isEqualTo("localhost");
    }

    // ── No-arg constructor uses LENIENT ──────────────────────────────

    @Test
    void noArgConstructorUsesLenient() {
        var binder = new DefaultParameterBinder();
        List<Parameter<?>> params = List.of(
            IntegerParameter.range("threads", 1, 64).withDefault(4)
        );

        ParameterBinding binding = binder.bind(params, Map.of("EXTRA", "extra"));

        assertThat(binding.validationResult().isPassed()).isTrue();
        assertThat(binding.passthroughValues()).containsKey("EXTRA");
    }
}
