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
package io.nosqlbench.paramodel.engine.definition;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.ElementTypeDescriptorProvider;
import io.nosqlbench.paramodel.engine.compiler.DefaultValidationResult;
import io.nosqlbench.paramodel.engine.compiler.ExportResolver;
import io.nosqlbench.paramodel.engine.compiler.ValidationStage;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.AxisDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.BindingDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.DependencyDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.ElementDefinition;
import io.nosqlbench.paramodel.engine.plan.DefaultAxis;
import io.nosqlbench.paramodel.engine.plan.DefaultElement;
import io.nosqlbench.paramodel.engine.plan.DefaultTestPlan;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nosqlbench.paramodel.engine.CompactId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The main composition pipeline that transforms a {@link TestPlanDefinition} into a
/// {@link DefaultTestPlan} with trials and an execution plan.
///
/// The composition pipeline:
/// 1. Convert definition to domain model ({@link DefaultElement}, {@link DefaultAxis})
/// 2. Apply cross-element bindings
/// 3. Validate export references
/// 4. Compile via paramodel's DefaultCompiler (which handles scope derivation,
///    trial generation, step generation, etc.)
/// 5. Post-composition validation
public class TestPlanComposer {
    private static final Logger logger = LoggerFactory.getLogger(TestPlanComposer.class);

    private final ExportResolver exportResolver;
    private final ValidationStage validationStage;

    public TestPlanComposer() {
        this(ElementTypeDescriptorProvider.open());
    }

    /// Creates a composer with the given element type descriptor provider.
    ///
    /// @param typeProvider supplies element type descriptors for validation
    public TestPlanComposer(ElementTypeDescriptorProvider typeProvider) {
        this.exportResolver = new ExportResolver();
        this.validationStage = new ValidationStage(typeProvider);
    }

    /// Composes a complete {@link DefaultTestPlan} from a {@link TestPlanDefinition}.
    ///
    /// Scope derivation and trial generation are now handled by the paramodel
    /// compiler pipeline (NormalizationStage and TrialEnumerationStage respectively).
    /// All type-specific metadata flows generically through element tags.
    ///
    /// @param definition the parsed test plan definition
    /// @return the composed plan with trials and execution plan
    public DefaultTestPlan compose(TestPlanDefinition definition) {
        DefaultTestPlan plan = buildPlan(definition);

        // Compile the execution plan via paramodel's DefaultCompiler.
        // plan.commit() delegates to DefaultCompiler's 8-stage pipeline.
        ExecutionPlan execPlan = plan.commit();

        // Post-composition validation
        DefaultValidationResult composedReport = validationStage.validateComposed(plan);
        if (composedReport.hasWarnings()) {
            composedReport.warnings().forEach(w ->
                    logger.warn("Post-composition warning [{}]: {}",
                            w.code().orElse(""), w.message()));
        }

        logger.info("Plan composition complete: {} elements, {} axes, {} trials, {} steps",
                plan.elements().size(),
                plan.axes().size(),
                plan.size(),
                execPlan.steps().size());

        return plan;
    }

    /// Builds a {@link DefaultTestPlan} from a definition without calling commit().
    ///
    /// This is useful for standalone validation — call `buildPlan()` to get an
    /// uncommitted plan, then use {@link ValidationStage#validate} to validate it.
    ///
    /// @param definition the parsed test plan definition
    /// @return the uncommitted plan
    public DefaultTestPlan buildPlan(TestPlanDefinition definition) {
        String planId = generatePlanId(definition.name());
        logger.info("Building plan: {} ({})", definition.name(), planId);

        // Convert elements (two-pass for dependency resolution)
        Map<String, DefaultElement> elementMap = convertElements(definition.elements());

        // Convert axes
        List<Axis<?>> axes = convertAxes(definition.axes());

        // Build the test plan
        DefaultTestPlan.Builder planBuilder = DefaultTestPlan.builder()
                .name(definition.name())
                .description(definition.description())
                .metadata(new PlanMetadata(planId, definition.description()));

        elementMap.values().forEach(planBuilder::element);
        axes.forEach(planBuilder::axis);

        DefaultTestPlan plan = planBuilder.build();

        // Apply cross-element bindings (spec section 10 — bindings section)
        if (definition.bindings() != null && !definition.bindings().isEmpty()) {
            applyBindings(plan, definition.bindings(), elementMap);
        }

        // Validate export references
        var exportIssues = exportResolver.validateExportReferences(plan);
        if (!exportIssues.isEmpty()) {
            logger.warn("Export reference issues: {}", exportIssues);
        }

        // Validate output_of references
        var outputOfIssues = exportResolver.validateOutputOfReferences(plan);
        if (!outputOfIssues.isEmpty()) {
            logger.warn("output_of reference issues: {}", outputOfIssues);
        }

        return plan;
    }

    /// Converts element definitions to {@link DefaultElement} instances.
    ///
    /// Uses two passes: first creates all elements without dependencies,
    /// then rebuilds elements that have dependencies with resolved references.
    private Map<String, DefaultElement> convertElements(List<ElementDefinition> definitions) {
        Map<String, DefaultElement> elementMap = new LinkedHashMap<>();

        // First pass: create all elements without resolved dependencies
        for (ElementDefinition def : definitions) {
            elementMap.put(def.id(), populateElementBuilder(def).build());
        }

        // Second pass: rebuild elements that have dependencies with resolved references
        for (ElementDefinition def : definitions) {
            if (def.dependsOn() != null && !def.dependsOn().isEmpty()) {
                DefaultElement.Builder builder = populateElementBuilder(def);
                for (DependencyDefinition dep : def.dependsOn()) {
                    DefaultElement upstream = elementMap.get(dep.element());
                    if (upstream != null) {
                        builder.dependency(upstream, dep.relationship());
                    }
                }
                elementMap.put(def.id(), builder.build());
            }
        }

        return elementMap;
    }

    /// Populates a {@link DefaultElement.Builder} from an {@link ElementDefinition}.
    ///
    /// All metadata flows through tags. The element type identifier and all
    /// type-specific properties from the definition are stored as tags so
    /// the engine remains agnostic to concrete element types.
    ///
    /// The element's {@code type} field also drives shutdown semantics:
    /// {@code "command"} type elements are self-terminating, so they receive
    /// {@link io.nosqlbench.paramodel.elements.Element.ShutdownSemantics#COMMAND COMMAND}
    /// semantics. All other types default to
    /// {@link io.nosqlbench.paramodel.elements.Element.ShutdownSemantics#SERVICE SERVICE}.
    @SuppressWarnings("unchecked")
    private DefaultElement.Builder populateElementBuilder(ElementDefinition def) {
        var builder = DefaultElement.builder(def.id())
                .tag("type", def.type());

        // Map element type to shutdown semantics.
        // "command" type elements are self-terminating — the scheduler awaits
        // natural completion instead of issuing a shutdown signal.
        if ("command".equalsIgnoreCase(def.type())) {
            builder.shutdownSemantics(Element.ShutdownSemantics.COMMAND);
        }

        // All type-specific properties flow through as tags generically
        if (def.properties() != null) {
            for (var entry : def.properties().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> mapValue) {
                    // Nested maps get flattened with dot-separated keys
                    for (var nested : ((Map<String, Object>) mapValue).entrySet()) {
                        if (nested.getValue() != null) {
                            builder.tag(entry.getKey() + "." + nested.getKey(),
                                    nested.getValue().toString());
                        }
                    }
                } else if (value != null) {
                    builder.tag(entry.getKey(), value.toString());
                }
            }
        }

        // Fixed bindings -> configuration map
        if (def.parameters() != null) {
            builder.configuration(def.parameters());
        }

        // Exports
        if (def.exports() != null) {
            builder.exports(def.exports());
        }

        // Relationship types are now carried on the Dependency edge directly,
        // no longer stored as tags.

        return builder;
    }

    /// Converts axis definitions to {@link DefaultAxis} instances.
    private List<Axis<?>> convertAxes(List<AxisDefinition> definitions) {
        List<Axis<?>> axes = new ArrayList<>();

        for (AxisDefinition def : definitions) {
            var builder = DefaultAxis.<Object>builder(def.parameter())
                    .values(def.values() != null ? def.values() : List.of())
                    .targetElement(def.element())
                    .sweepMode(def.mode() != null ? def.mode().toUpperCase() : "SERIAL")
                    .nesting(def.nesting() != null ? def.nesting() : axes.size())
                    .section(def.section())
                    .repetitions(def.repetitions() != null ? def.repetitions() : 1);
            if (def.sampling() != null) {
                builder.sampling(def.sampling().toSamplingStrategy());
            }
            DefaultAxis<Object> axis = builder.build();

            axes.add(axis);
        }

        return axes;
    }

    /// Applies cross-element bindings from the `bindings` section.
    ///
    /// Each binding sets a fixed parameter on a target element. The value may
    /// contain `${element.export}` references that will be resolved at runtime.
    private void applyBindings(
            DefaultTestPlan plan,
            List<BindingDefinition> bindings,
            Map<String, DefaultElement> elementMap) {

        Map<String, Map<String, Object>> bindingsByElement = new LinkedHashMap<>();
        for (BindingDefinition binding : bindings) {
            bindingsByElement
                    .computeIfAbsent(binding.element(), k -> new LinkedHashMap<>())
                    .put(binding.parameter(), binding.value());
        }

        for (Map.Entry<String, Map<String, Object>> entry : bindingsByElement.entrySet()) {
            String elementId = entry.getKey();
            Map<String, Object> newBindings = entry.getValue();

            DefaultElement original = elementMap.get(elementId);
            if (original == null) {
                continue;
            }

            // Merge bindings into element's configuration (bindings override)
            Map<String, Object> merged = new LinkedHashMap<>(original.configuration());
            merged.putAll(newBindings);

            // Rebuild element with merged configuration
            DefaultElement updated = rebuildWithConfiguration(original, merged);
            elementMap.put(elementId, updated);
            plan.replaceElement(updated);

            logger.debug("Applied {} bindings to element {}", newBindings.size(), elementId);
        }
    }

    /// Rebuilds a {@link DefaultElement} with a new configuration map, preserving all
    /// other fields.
    private DefaultElement rebuildWithConfiguration(DefaultElement original, Map<String, Object> configuration) {
        var builder = DefaultElement.builder(original.name());
        for (var tag : original.tags().entrySet()) {
            if (!"name".equals(tag.getKey())) {
                builder.tag(tag.getKey(), tag.getValue());
            }
        }
        builder.configuration(configuration);
        builder.exports(original.exports());
        builder.shutdownSemantics(original.shutdownSemantics());
        for (var p : original.parameters()) {
            builder.parameter(p);
        }
        for (var dep : original.dependencies()) {
            builder.dependency(dep);
        }
        return builder.build();
    }

    /// Generates a unique plan ID from the name.
    private String generatePlanId(String name) {
        String sanitized = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return sanitized + "-" + CompactId.next();
    }

    /// TestPlan metadata carrying plan context.
    ///
    /// Stores the generated planId in the tags map so downstream code
    /// (web services, execution tracking) can retrieve it.
    private static class PlanMetadata implements TestPlan.TestPlanMetadata {
        private final String planId;
        private final String description;
        private final Instant createdAt = Instant.now();

        PlanMetadata(String planId, String description) {
            this.planId = planId;
            this.description = description;
        }

        @Override public Instant createdAt() { return createdAt; }
        @Override public Optional<String> createdBy() { return Optional.empty(); }
        @Override public Optional<String> description() { return Optional.ofNullable(description); }
        @Override public Map<String, String> tags() { return Map.of("studyId", planId); }
        @Override public Optional<String> version() { return Optional.empty(); }
    }
}
