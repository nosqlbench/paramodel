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
package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.AxisDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.DependencyDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.ElementDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.SettingsDefinition;
import io.nosqlbench.paramodel.elements.ElementTypeDescriptor;
import io.nosqlbench.paramodel.elements.ElementTypeDescriptorProvider;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.plan.DefaultElement;
import io.nosqlbench.paramodel.engine.plan.DefaultTestPlan;
import io.nosqlbench.paramodel.engine.sequence.DefaultTrial;
import io.nosqlbench.paramodel.engine.sequence.DefaultValue;
import io.nosqlbench.paramodel.parameters.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for {@link ValidationStage}.
class ValidationStageTest {
    private ValidationStage validator;

    @BeforeEach
    void setUp() {
        validator = new ValidationStage();
    }

    @Test
    void testValidMinimalStudy() {
        TestPlanDefinition def = new TestPlanDefinition(
                "Test Study",
                "A valid study",
                List.of(serviceElement("server", "nginx:latest")),
                List.of(),
                List.of(),
                null
        );

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.isValid()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    @Test
    void testMissingName() {
        TestPlanDefinition def = new TestPlanDefinition(
                null,
                "Description",
                List.of(serviceElement("server", "nginx:latest")),
                List.of(),
                List.of(),
                null
        );

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_MISSING_NAME)));
    }

    @Test
    void testNoElements() {
        TestPlanDefinition def = new TestPlanDefinition(
                "Study",
                null,
                List.of(),
                List.of(),
                List.of(),
                null
        );

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_NO_ELEMENTS)));
    }

    @Test
    void testDuplicateElementId() {
        TestPlanDefinition def = new TestPlanDefinition(
                "Study",
                null,
                List.of(
                        serviceElement("server", "nginx:latest"),
                        serviceElement("server", "apache:latest")
                ),
                List.of(),
                List.of(),
                null
        );

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_DUPLICATE_ELEMENT_ID)));
    }

    @Test
    void testDescriptorDrivenRequiredFieldValidation() {
        // Create a provider that requires "image" for "service" type
        ElementTypeDescriptorProvider provider = new ElementTypeDescriptorProvider() {
            @Override
            public List<ElementTypeDescriptor> descriptors() {
                return List.of(new ElementTypeDescriptor(
                        "service", Set.of("image"), Map.of(), Map.of(), false));
            }

            @Override
            public Map<String, String> typeAliases() { return Map.of(); }
        };

        ValidationStage validatorWithProvider = new ValidationStage(provider);

        // Element missing the required "image" property
        ElementDefinition elem = new ElementDefinition(
                "server", "service", Map.of(), List.of(), Map.of(), Map.of());

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(elem), List.of(), List.of(), null);

        DefaultValidationResult report = validatorWithProvider.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_REQUIRED_FIELD)));
    }

    @Test
    void testDescriptorDrivenForbiddenFieldValidation() {
        // Create a provider that forbids "output" on "node" type
        ElementTypeDescriptorProvider provider = new ElementTypeDescriptorProvider() {
            @Override
            public List<ElementTypeDescriptor> descriptors() {
                return List.of(new ElementTypeDescriptor(
                        "node", Set.of(), Map.of("output", "Nodes do not produce output"),
                        Map.of(), true));
            }

            @Override
            public Map<String, String> typeAliases() { return Map.of(); }
        };

        ValidationStage validatorWithProvider = new ValidationStage(provider);

        // Node element with forbidden "output" property
        ElementDefinition elem = new ElementDefinition(
                "infra", "node", Map.of(), List.of(), Map.of(),
                Map.of("output", Map.of("volume", "/out")));

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(elem), List.of(), List.of(), null);

        DefaultValidationResult report = validatorWithProvider.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_FORBIDDEN_FIELD)));
    }

    @Test
    void testDescriptorDrivenAdvisoryWarning() {
        // Create a provider that warns when "output" appears on "service" type
        ElementTypeDescriptorProvider provider = new ElementTypeDescriptorProvider() {
            @Override
            public List<ElementTypeDescriptor> descriptors() {
                return List.of(new ElementTypeDescriptor(
                        "service", Set.of("image"), Map.of(),
                        Map.of("output", "Output is unusual on services"), false));
            }

            @Override
            public Map<String, String> typeAliases() { return Map.of(); }
        };

        ValidationStage validatorWithProvider = new ValidationStage(provider);

        // Service element with advisory "output" property
        ElementDefinition elem = new ElementDefinition(
                "server", "service", Map.of(), List.of(), Map.of(),
                Map.of("image", "nginx:latest", "output", Map.of("volume", "/out")));

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(elem), List.of(), List.of(), null);

        DefaultValidationResult report = validatorWithProvider.validate(def);

        assertThat(report.isValid()).isTrue();
        assertThat(report.hasWarnings()).isTrue();
        assertThat(report.warnings())
                .anyMatch(w -> w.code().equals(Optional.of(ValidationStage.WARN_FIELD_ADVISORY)));
    }

    @Test
    void testOpenProviderPerformsNoFieldValidation() {
        // With the default open provider, no field validation is performed —
        // an element without any properties is valid.
        ElementDefinition elem = new ElementDefinition(
                "server", "service", Map.of(), List.of(), Map.of(), Map.of());

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(elem), List.of(), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.isValid()).isTrue();
    }

    @Test
    void testUnknownDependency() {
        ElementDefinition server = new ElementDefinition(
                "server", "service", Map.of(),
                List.of(new DependencyDefinition("nonexistent", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "nginx:latest"));

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(server), List.of(), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_UNKNOWN_DEPENDENCY)));
    }

    @Test
    void testSelfDependency() {
        ElementDefinition server = new ElementDefinition(
                "server", "service", Map.of(),
                List.of(new DependencyDefinition("server", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "nginx:latest"));

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(server), List.of(), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_SELF_DEPENDENCY)));
    }

    @Test
    void testDependencyCycle() {
        ElementDefinition a = new ElementDefinition(
                "a", "service", Map.of(),
                List.of(new DependencyDefinition("b", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "image:a"));

        ElementDefinition b = new ElementDefinition(
                "b", "service", Map.of(),
                List.of(new DependencyDefinition("a", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "image:b"));

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(a, b), List.of(), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_DEPENDENCY_CYCLE)));
    }

    @Test
    void testValidDependencyChain() {
        ElementDefinition a = serviceElement("a", "image:a");
        ElementDefinition b = new ElementDefinition(
                "b", "service", Map.of(),
                List.of(new DependencyDefinition("a", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "image:b"));
        ElementDefinition c = new ElementDefinition(
                "c", "command", Map.of(),
                List.of(new DependencyDefinition("b", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "image:c"));

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(a, b, c), List.of(), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.isValid()).isTrue();
    }


    @Test
    void testUnknownAxisElement() {
        AxisDefinition axis = new AxisDefinition(
                "threads", "nonexistent", List.of(1, 2, 4),
                null, null, "serial", 0, null, null, 1);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(axis), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_UNKNOWN_AXIS_ELEMENT)));
    }

    @Test
    void testEmptyAxisValues() {
        AxisDefinition axis = new AxisDefinition(
                "threads", "server", List.of(),
                null, null, "serial", 0, null, null, 1);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(axis), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_EMPTY_AXIS_VALUES)));
    }

    @Test
    void testConcurrentNotInnermost() {
        AxisDefinition outer = new AxisDefinition(
                "threads", "server", List.of(1, 2),
                null, null, "concurrent", 0, null, null, 1);

        AxisDefinition inner = new AxisDefinition(
                "memory", "server", List.of("1g", "2g"),
                null, null, "serial", 1, null, null, 1);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(outer, inner), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_CONCURRENT_NOT_INNERMOST)));
    }

    @Test
    void testLargeTrialCountWarning() {
        // 10 x 10 x 10 = 1000 trials
        AxisDefinition a1 = new AxisDefinition(
                "p1", "server", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                null, null, "serial", 0, null, null, 1);
        AxisDefinition a2 = new AxisDefinition(
                "p2", "server", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                null, null, "serial", 1, null, null, 1);
        AxisDefinition a3 = new AxisDefinition(
                "p3", "server", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                null, null, "serial", 2, null, null, 1);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(a1, a2, a3), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        // Should pass validation but have warning
        assertThat(report.isValid()).isTrue();
        assertThat(report.hasWarnings()).isTrue();
        assertThat(report.warnings())
                .anyMatch(w -> w.code().equals(Optional.of(ValidationStage.WARN_LARGE_TRIAL_COUNT)));
    }

    @Test
    /// A node element without an explicit scope should be allowed to have
    /// varied parameters — nodes are elements like any other.
    void testAxisAllowedOnNodeElement() {
        ElementDefinition infra = new ElementDefinition(
                "infra", "node", Map.of(), List.of(), Map.of(), Map.of());

        AxisDefinition axis = new AxisDefinition(
                "instance_type", "infra", List.of("m5.xlarge", "m5.2xlarge"),
                null, null, "serial", 0, null, null, 1);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(infra), List.of(axis), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.errors())
                .noneMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_AXIS_LOCALITY)));
    }

    @Test
    void testRetryPolicyWithZeroCount() {
        SettingsDefinition settings = new SettingsDefinition(
                "retry", 0, 1, null, Map.of(), null, null);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(), List.of(), settings);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_INVALID_RETRY_COUNT)));
    }

    @Test
    void testValidOnFailureSkip() {
        SettingsDefinition settings = new SettingsDefinition(
                "skip", 0, 1, null, Map.of(), null, null);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(), List.of(), settings);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.isValid()).isTrue();
    }

    // --- Post-composition tests using DefaultTestPlan/DefaultElement/DefaultTrial ---

    @Test
    void testUnstableReuseWarningWhenUpstreamBindingsVary() {
        DefaultElement server = DefaultElement.builder("server")
                .tag("type", "service").tag("image", "server:latest").build();
        DefaultElement client = DefaultElement.builder("client")
                .tag("type", "command").tag("image", "client:latest")
                .dependency(server).build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(server).element(client)
                .trial(new DefaultTrial("trial-0", Map.of(
                        "server.threads", val("threads", 1)
                ), List.of(), null))
                .trial(new DefaultTrial("trial-1", Map.of(
                        "server.threads", val("threads", 2)
                ), List.of(), null))
                .build();

        DefaultValidationResult report = validator.validateComposed(plan);

        assertThat(report.hasWarnings()).isTrue();
        assertThat(report.warnings())
                .anyMatch(w -> w.code().equals(Optional.of(ValidationStage.WARN_UNSTABLE_REUSE)));
    }

    @Test
    void testNoUnstableReuseWarningWhenUpstreamBindingsFixed() {
        DefaultElement server = DefaultElement.builder("server")
                .tag("type", "service").tag("image", "server:latest").build();
        DefaultElement client = DefaultElement.builder("client")
                .tag("type", "command").tag("image", "client:latest")
                .dependency(server).build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(server).element(client)
                .trial(new DefaultTrial("trial-0", Map.of(
                        "server.threads", val("threads", 4),
                        "client.dataset", val("dataset", "sift")
                ), List.of(), null))
                .trial(new DefaultTrial("trial-1", Map.of(
                        "server.threads", val("threads", 4),
                        "client.dataset", val("dataset", "deep")
                ), List.of(), null))
                .build();

        DefaultValidationResult report = validator.validateComposed(plan);

        assertThat(report.hasWarnings()).isFalse();
    }

    @Test
    void testNoUnstableReuseWarningForExclusiveRelationship() {
        DefaultElement server = DefaultElement.builder("server")
                .tag("type", "service").tag("image", "server:latest").build();
        DefaultElement client = DefaultElement.builder("client")
                .tag("type", "command").tag("image", "client:latest")
                .dependency(server, RelationshipType.EXCLUSIVE).build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(server).element(client)
                .trial(new DefaultTrial("trial-0", Map.of(
                        "server.threads", val("threads", 1)
                ), List.of(), null))
                .trial(new DefaultTrial("trial-1", Map.of(
                        "server.threads", val("threads", 2)
                ), List.of(), null))
                .build();

        DefaultValidationResult report = validator.validateComposed(plan);

        assertThat(report.hasWarnings()).isFalse();
    }

    @Test
    void testNoUnstableReuseWarningSingleTrial() {
        DefaultElement server = DefaultElement.builder("server")
                .tag("type", "service").tag("image", "server:latest").build();
        DefaultElement client = DefaultElement.builder("client")
                .tag("type", "command").tag("image", "client:latest")
                .dependency(server).build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(server).element(client)
                .trial(new DefaultTrial("trial-0", Map.of(
                        "server.threads", val("threads", 1)
                ), List.of(), null))
                .build();

        DefaultValidationResult report = validator.validateComposed(plan);

        assertThat(report.hasWarnings()).isFalse();
    }

    @Test
    void testMissingAxisParameterName() {
        AxisDefinition axis = new AxisDefinition(
                null, "server", List.of(1, 2, 4),
                null, null, "serial", 0, null, null, 1);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(axis), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_MISSING_AXIS_PARAMETER)));
    }

    @Test
    void testBlankAxisParameterName() {
        AxisDefinition axis = new AxisDefinition(
                "   ", "server", List.of(1, 2, 4),
                null, null, "serial", 0, null, null, 1);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(axis), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_MISSING_AXIS_PARAMETER)));
    }

    @Test
    void testMissingBindingParameter() {
        TestPlanDefinition.BindingDefinition binding = new TestPlanDefinition.BindingDefinition(
                null, "server", "some-value");

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(), List.of(binding), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_MISSING_BINDING_FIELD)));
    }

    @Test
    void testMissingBindingElement() {
        TestPlanDefinition.BindingDefinition binding = new TestPlanDefinition.BindingDefinition(
                "threads", null, "4");

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(), List.of(binding), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_MISSING_BINDING_FIELD)));
    }

    @Test
    void testBindingReferencesUnknownElement() {
        TestPlanDefinition.BindingDefinition binding = new TestPlanDefinition.BindingDefinition(
                "threads", "nonexistent", "4");

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(), List.of(binding), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_MISSING_BINDING_FIELD)));
    }

    @Test
    void testMissingBindingValue() {
        TestPlanDefinition.BindingDefinition binding = new TestPlanDefinition.BindingDefinition(
                "threads", "server", null);

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(), List.of(binding), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_MISSING_BINDING_FIELD)));
    }

    @Test
    void testValidBindingNoErrors() {
        TestPlanDefinition.BindingDefinition binding = new TestPlanDefinition.BindingDefinition(
                "threads", "server", "4");

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null,
                List.of(serviceElement("server", "nginx:latest")),
                List.of(), List.of(binding), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.errors())
                .noneMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_MISSING_BINDING_FIELD)));
    }

    @Test
    void testThreeElementCycle() {
        ElementDefinition a = new ElementDefinition(
                "a", "service", Map.of(),
                List.of(new DependencyDefinition("b", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "image:a"));

        ElementDefinition b = new ElementDefinition(
                "b", "service", Map.of(),
                List.of(new DependencyDefinition("c", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "image:b"));

        ElementDefinition c = new ElementDefinition(
                "c", "service", Map.of(),
                List.of(new DependencyDefinition("a", RelationshipType.SHARED)),
                Map.of(), Map.of("image", "image:c"));

        TestPlanDefinition def = new TestPlanDefinition(
                "Study", null, List.of(a, b, c), List.of(), List.of(), null);

        DefaultValidationResult report = validator.validate(def);

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors())
                .anyMatch(e -> e.code().equals(Optional.of(ValidationStage.ERR_DEPENDENCY_CYCLE)));
    }


    // ── Helper methods ─────────────────────────────────────────────────

    /// Creates a simple service element definition for tests.
    private ElementDefinition serviceElement(String id, String image) {
        return new ElementDefinition(
                id, "service", Map.of(), List.of(), Map.of(),
                Map.of("image", image));
    }

    /// Creates a {@link Value} for use in test trial assignments.
    private static Value<?> val(String paramName, Object value) {
        return new DefaultValue<>(value, paramName, Instant.now(), Optional.empty());
    }
}
