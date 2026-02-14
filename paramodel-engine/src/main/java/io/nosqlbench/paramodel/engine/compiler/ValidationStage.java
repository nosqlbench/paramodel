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

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.Element.InstancingScope;
import io.nosqlbench.paramodel.elements.ElementTypeDescriptorProvider;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.AxisDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.BindingDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.DependencyDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.ElementDefinition;
import io.nosqlbench.paramodel.engine.plan.DefaultTestPlan;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Stage 1: Validation
///
/// Validates test plan definitions before composition. Supports both
/// definition-level validation (operating on {@link TestPlanDefinition}) and
/// pipeline validation (operating on {@link TestPlan} via the compilation context).
///
/// Validation checks include:
/// - Required fields present
/// - Element IDs unique
/// - Dependencies reference existing elements
/// - No dependency cycles
/// - Scope constraints satisfied
/// - Axis parameters reference existing elements
/// - CONCURRENT mode only on innermost axes
/// - MUTUALLY_EXCLUSIVE high-cost warnings
/// - INSTANCED_PER edge warnings
/// - Node sufficiency warnings
public class ValidationStage implements CompilationStage {
    private static final Logger logger = LoggerFactory.getLogger(ValidationStage.class);

    /// Error codes for validation issues.
    public static final String ERR_MISSING_NAME = "MISSING_NAME";
    public static final String ERR_NO_ELEMENTS = "NO_ELEMENTS";
    public static final String ERR_DUPLICATE_ELEMENT_ID = "DUPLICATE_ELEMENT_ID";
    public static final String ERR_MISSING_ELEMENT_ID = "MISSING_ELEMENT_ID";
    public static final String ERR_MISSING_ELEMENT_TYPE = "MISSING_ELEMENT_TYPE";
    @Deprecated public static final String ERR_MISSING_IMAGE = "MISSING_IMAGE";
    public static final String ERR_UNKNOWN_DEPENDENCY = "UNKNOWN_DEPENDENCY";
    public static final String ERR_DEPENDENCY_CYCLE = "DEPENDENCY_CYCLE";
    public static final String ERR_SELF_DEPENDENCY = "SELF_DEPENDENCY";
    public static final String ERR_SCOPE_VIOLATION = "SCOPE_VIOLATION";
    public static final String ERR_UNKNOWN_AXIS_ELEMENT = "UNKNOWN_AXIS_ELEMENT";
    public static final String ERR_EMPTY_AXIS_VALUES = "EMPTY_AXIS_VALUES";
    public static final String ERR_CONCURRENT_NOT_INNERMOST = "CONCURRENT_NOT_INNERMOST";
    public static final String ERR_AXIS_LOCALITY = "AXIS_LOCALITY";
    public static final String ERR_INVALID_RETRY_COUNT = "INVALID_RETRY_COUNT";
    public static final String ERR_REQUIRED_FIELD = "REQUIRED_FIELD";
    public static final String ERR_FORBIDDEN_FIELD = "FORBIDDEN_FIELD";
    public static final String WARN_FIELD_ADVISORY = "FIELD_ADVISORY";
    public static final String WARN_UNSTABLE_REUSE = "UNSTABLE_REUSE";
    public static final String ERR_MISSING_AXIS_PARAMETER = "MISSING_AXIS_PARAMETER";
    public static final String ERR_MISSING_BINDING_FIELD = "MISSING_BINDING_FIELD";
    public static final String WARN_INSTANCED_PER_WITHOUT_CONCURRENT = "INSTANCED_PER_WITHOUT_CONCURRENT";
    public static final String WARN_INSUFFICIENT_NODES = "INSUFFICIENT_NODES";
    public static final String WARN_MUTUALLY_EXCLUSIVE_HIGH_COST = "MUTUALLY_EXCLUSIVE_HIGH_COST";
    public static final String WARN_LARGE_TRIAL_COUNT = "LARGE_TRIAL_COUNT";

    private final ElementTypeDescriptorProvider typeProvider;

    /// Creates a validation stage with no type constraints (accepts any type).
    public ValidationStage() {
        this(ElementTypeDescriptorProvider.open());
    }

    /// Creates a validation stage using the given type descriptor provider.
    ///
    /// @param typeProvider supplies element type descriptors for field validation
    public ValidationStage(ElementTypeDescriptorProvider typeProvider) {
        this.typeProvider = typeProvider;
    }

    @Override
    public String name() {
        return "Validation";
    }

    @Override
    public void execute(CompilationContext context) {
        TestPlan plan = context.testPlan();

        // Validate TestPlan itself
        io.nosqlbench.paramodel.parameters.ValidationResult planValidation = plan.validate();
        if (planValidation.isFailed()) {
            planValidation.message().ifPresent(msg ->
                context.addError(Compiler.ErrorSeverity.ERROR, "TestPlan validation failed: " + msg, null, null)
            );
            for (String violation : planValidation.violations()) {
                context.addError(Compiler.ErrorSeverity.ERROR, violation, null, null);
            }
            return;
        }

        // Validate axes exist
        if (plan.axes().isEmpty()) {
            context.addWarning("TestPlan has no axes", "Add at least one axis to define parameter space");
        }

        // Validate elements exist
        if (plan.elements().isEmpty()) {
            context.addWarning("TestPlan has no elements", "Add at least one element to test");
        }

        // Record validation metrics
        context.recordMetric("axes_count", plan.axes().size());
        context.recordMetric("elements_count", plan.elements().size());
        context.recordMetric("trial_space_size", plan.trialSpaceSize());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Standalone definition-level validation
    // ═══════════════════════════════════════════════════════════════════════

    /// Validates a definition in draft mode.
    ///
    /// Draft mode is lenient — several error categories are downgraded to
    /// informational notes so that users can build plans incrementally:
    /// - Empty element lists (no elements added yet)
    /// - Unknown dependency targets (dependency target not yet defined)
    /// - Unknown axis element references (axis targets an element not yet added)
    ///
    /// @param definition the definition to validate
    /// @return a validation result with downgraded draft-phase errors
    public DefaultValidationResult validateDraft(TestPlanDefinition definition) {
        DefaultValidationResult result = validate(definition);
        result.downgradeError(ERR_NO_ELEMENTS);
        result.downgradeError(ERR_UNKNOWN_DEPENDENCY);
        result.downgradeError(ERR_UNKNOWN_AXIS_ELEMENT);
        return result;
    }

    /// Validates a definition and returns a validation result.
    public DefaultValidationResult validate(TestPlanDefinition definition) {
        DefaultValidationResult report = new DefaultValidationResult();

        // Basic structure validation
        validateBasicStructure(definition, report);

        if (report.hasErrors()) {
            return report;
        }

        // Element validation
        Map<String, ElementDefinition> elementMap = validateElements(definition, report);

        // Dependency validation
        validateDependencies(definition, elementMap, report);

        // Cycle detection
        detectCycles(definition, elementMap, report);

        // Scope validation
        validateScopes(definition, elementMap, report);

        // Axis validation
        validateAxes(definition, elementMap, report);

        // Settings validation
        validateSettings(definition, report);

        // Parameter completeness
        validateParameterCompleteness(definition, elementMap, report);

        // Cost warnings
        validateCosts(definition, elementMap, report);

        // INSTANCED_PER edge warnings
        validateInstancedPerEdges(definition, elementMap, report);

        // Node sufficiency
        validateNodeSufficiency(definition, elementMap, report);

        return report;
    }

    /// Post-composition validation on a composed plan with trials.
    ///
    /// This validation runs after trial generation to detect issues that depend on
    /// actual binding values (not just the definition structure).
    ///
    /// @param plan the composed plan with trials
    /// @return a validation report with any warnings found
    public DefaultValidationResult validateComposed(DefaultTestPlan plan) {
        DefaultValidationResult report = new DefaultValidationResult();
        validateUnstableReuse(plan, report);
        return report;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Validation checks
    // ═══════════════════════════════════════════════════════════════════════

    /// Validates basic structure.
    private void validateBasicStructure(TestPlanDefinition definition, DefaultValidationResult report) {
        if (definition.name() == null || definition.name().isBlank()) {
            report.addError(ERR_MISSING_NAME, "Study must have a name",
                    "Study names are used to identify studies in logs, results storage, and the UI. " +
                    "A descriptive name helps distinguish between different experiments and their outcomes.");
        }

        if (definition.elements() == null || definition.elements().isEmpty()) {
            report.addError(ERR_NO_ELEMENTS, "Study must have at least one element",
                    "A study describes a coordinated experiment using deployable elements. " +
                    "Without elements, there is nothing to deploy or measure. " +
                    "Add at least one NODE, SERVICE, or COMMAND element to define what the study will run.");
        }
    }

    /// Validates elements and builds element map.
    private Map<String, ElementDefinition> validateElements(
            TestPlanDefinition definition, DefaultValidationResult report) {

        Map<String, ElementDefinition> elementMap = new HashMap<>();
        Set<String> seenIds = new HashSet<>();

        for (int i = 0; i < definition.elements().size(); i++) {
            ElementDefinition elem = definition.elements().get(i);
            String location = "elements[" + i + "]";

            if (elem.id() == null || elem.id().isBlank()) {
                report.addError(ERR_MISSING_ELEMENT_ID, "Element must have an id", location,
                        "Element IDs uniquely identify each element in the study. They are used in " +
                        "dependencies, axis references, instance naming, and result correlation. " +
                        "Choose a short, descriptive ID like 'jvector' or 'cassandra-server'.",
                        List.of("Add an 'id' field to the element definition"));
                continue;
            }

            if (seenIds.contains(elem.id())) {
                report.addError(ERR_DUPLICATE_ELEMENT_ID,
                        "Duplicate element id: " + elem.id(), location,
                        "Each element in a study must have a unique ID so that dependencies, axes, " +
                        "and results can unambiguously reference it. When multiple instances of the " +
                        "same element type are needed, use distinct IDs like 'jvector-primary' and 'jvector-secondary'.",
                        List.of("Rename this element to have a unique ID",
                                "Remove the duplicate element if it was added by mistake"));
                continue;
            }

            seenIds.add(elem.id());
            elementMap.put(elem.id(), elem);

            // Descriptor-driven type validation
            validateElementFields(elem, location, report);
        }

        return elementMap;
    }

    /// Validates element fields against the registered type descriptor.
    ///
    /// Checks required fields, forbidden fields, and advisory warnings
    /// based on the descriptor for the element's type. All field checks use
    /// the generic `properties` map on {@link ElementDefinition} — no
    /// concrete element types are referenced.
    ///
    /// If no descriptor is registered (open provider), no field validation
    /// is performed.
    private void validateElementFields(ElementDefinition elem, String location,
                                        DefaultValidationResult report) {
        var descriptor = typeProvider.descriptor(elem.type());
        if (descriptor.isEmpty()) return;

        var desc = descriptor.get();

        // Required fields
        for (String field : desc.requiredFields()) {
            if (!elem.hasProperty(field)) {
                report.addError(ERR_REQUIRED_FIELD,
                        elem.type().toUpperCase() + " element '" + elem.id()
                                + "' must specify '" + field + "'",
                        location,
                        "The '" + field + "' field is required for elements of type '"
                                + elem.type() + "' but was not provided.",
                        List.of("Add a '" + field + "' field to the element definition"));
            }
        }

        // Forbidden fields
        for (var entry : desc.forbiddenFields().entrySet()) {
            if (elem.hasProperty(entry.getKey())) {
                report.addError(ERR_FORBIDDEN_FIELD,
                        elem.type().toUpperCase() + " element '" + elem.id()
                                + "' cannot have '" + entry.getKey() + "'",
                        location,
                        entry.getValue(),
                        List.of("Remove '" + entry.getKey() + "' from the element"));
            }
        }

        // Advisory warnings
        for (var entry : desc.fieldWarnings().entrySet()) {
            if (elem.hasProperty(entry.getKey())) {
                report.addWarning(WARN_FIELD_ADVISORY,
                        elem.type().toUpperCase() + " element '" + elem.id()
                                + "' has '" + entry.getKey() + "': " + entry.getValue(),
                        location,
                        entry.getValue(),
                        List.of("Remove '" + entry.getKey() + "' if not intentional"));
            }
        }
    }

    /// Validates dependencies reference existing elements.
    private void validateDependencies(
            TestPlanDefinition definition,
            Map<String, ElementDefinition> elementMap,
            DefaultValidationResult report) {

        for (ElementDefinition elem : definition.elements()) {
            if (elem.dependsOn() == null) continue;

            for (DependencyDefinition dep : elem.dependsOn()) {
                if (dep.element().equals(elem.id())) {
                    report.addError(ERR_SELF_DEPENDENCY,
                            "Element cannot depend on itself: " + elem.id(),
                            "element:" + elem.id(),
                            "Self-dependencies create an impossible ordering constraint where an element " +
                            "must be running before it can start. This is logically inconsistent and " +
                            "prevents the execution planner from generating a valid plan.",
                            List.of("Remove the self-referential dependency",
                                    "If you need ordering between instances, use separate element definitions"));
                } else if (!elementMap.containsKey(dep.element())) {
                    report.addError(ERR_UNKNOWN_DEPENDENCY,
                            "Unknown dependency '" + dep.element() + "' in element '" + elem.id() + "'",
                            "element:" + elem.id(),
                            "The dependency references an element ID that doesn't exist in this study. " +
                            "This could be a typo, or the referenced element may have been removed. " +
                            "Dependencies must reference elements defined in the same study.",
                            List.of("Check the spelling of '" + dep.element() + "'",
                                    "Add an element with id '" + dep.element() + "' to the study",
                                    "Remove the invalid dependency"));
                }
            }
        }
    }

    /// Detects cycles in the dependency graph.
    private void detectCycles(
            TestPlanDefinition definition,
            Map<String, ElementDefinition> elementMap,
            DefaultValidationResult report) {

        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String elementId : elementMap.keySet()) {
            if (!visited.contains(elementId)) {
                if (hasCycle(elementId, elementMap, visited, visiting)) {
                    report.addError(ERR_DEPENDENCY_CYCLE,
                            "Dependency cycle detected involving element: " + elementId,
                            "A dependency cycle occurs when elements form a circular chain of dependencies " +
                            "(e.g., A depends on B, B depends on C, C depends on A). This creates an " +
                            "impossible ordering where each element must start before another, preventing " +
                            "any valid execution plan. Break the cycle by removing or reorienting one dependency.");
                }
            }
        }
    }

    /// DFS cycle detection.
    private boolean hasCycle(
            String elementId,
            Map<String, ElementDefinition> elementMap,
            Set<String> visited,
            Set<String> visiting) {

        if (visiting.contains(elementId)) {
            return true;
        }
        if (visited.contains(elementId)) {
            return false;
        }

        visiting.add(elementId);

        ElementDefinition elem = elementMap.get(elementId);
        if (elem != null && elem.dependsOn() != null) {
            for (DependencyDefinition dep : elem.dependsOn()) {
                if (elementMap.containsKey(dep.element())) {
                    if (hasCycle(dep.element(), elementMap, visited, visiting)) {
                        return true;
                    }
                }
            }
        }

        visiting.remove(elementId);
        visited.add(elementId);
        return false;
    }

    /// Validates scope constraints.
    private void validateScopes(
            TestPlanDefinition definition,
            Map<String, ElementDefinition> elementMap,
            DefaultValidationResult report) {

        Map<String, InstancingScope> scopeMap = new HashMap<>();
        for (ElementDefinition elem : definition.elements()) {
            InstancingScope scope = elem.scope();
            if (scope == null) {
                scope = InstancingScope.PER_TRIAL;
            }
            scopeMap.put(elem.id(), scope);
        }

        for (ElementDefinition elem : definition.elements()) {
            if (elem.dependsOn() == null) continue;

            InstancingScope downstreamScope = scopeMap.get(elem.id());
            for (DependencyDefinition dep : elem.dependsOn()) {
                InstancingScope upstreamScope = scopeMap.get(dep.element());
                if (upstreamScope == null) continue;

                if (upstreamScope.ordinal() < downstreamScope.ordinal()) {
                    report.addError(ERR_SCOPE_VIOLATION,
                            String.format("Element '%s' (%s scope) cannot depend on '%s' (%s scope)",
                                    elem.id(), downstreamScope, dep.element(), upstreamScope),
                            "element:" + elem.id(),
                            "Scope determines element lifetime: PER_RUN (whole run) > PER_GROUP (group of trials) > PER_TRIAL (single trial). " +
                            "An element cannot depend on something with a shorter lifetime because the " +
                            "dependency might not exist when needed. For example, a PER_RUN-scoped element " +
                            "cannot depend on a PER_GROUP-scoped element since the group element may be " +
                            "torn down at a group boundary while the PER_RUN element still runs.",
                            List.of("Promote '" + dep.element() + "' to " + downstreamScope + " scope",
                                    "Demote '" + elem.id() + "' to " + upstreamScope + " scope",
                                    "Restructure the dependency relationship"));
                }
            }
        }
    }

    /// Validates axis definitions.
    private void validateAxes(
            TestPlanDefinition definition,
            Map<String, ElementDefinition> elementMap,
            DefaultValidationResult report) {

        if (definition.axes() == null) return;

        Map<String, Integer> maxNesting = new HashMap<>();

        for (int i = 0; i < definition.axes().size(); i++) {
            AxisDefinition axis = definition.axes().get(i);
            String location = "axes[" + i + "]";

            if (!elementMap.containsKey(axis.element())) {
                report.addError(ERR_UNKNOWN_AXIS_ELEMENT,
                        "Axis references unknown element: " + axis.element(),
                        location,
                        "Each axis must reference a valid element to specify which element's parameter " +
                        "will be varied. The referenced element '" + axis.element() + "' is not defined " +
                        "in this study. Axes create variations by sweeping parameter values across trials.",
                        List.of("Check the spelling of the element reference",
                                "Add an element with id '" + axis.element() + "'",
                                "Reference an existing element from this study"));
                continue;
            }

            ElementDefinition referencedElement = elementMap.get(axis.element());

            if (referencedElement.scope() == InstancingScope.PER_RUN) {
                report.addError(ERR_AXIS_LOCALITY,
                        "PER_RUN-scoped element '" + axis.element() + "' cannot have varied parameters",
                        location,
                        "An element with explicit PER_RUN scope is deployed once at study start and torn down " +
                        "at study end. Varying its parameters would require redeployment, conflicting with " +
                        "the PER_RUN scope guarantee. Either remove the scope override to allow automatic " +
                        "scope derivation, or move the parameter to a PER_TRIAL-scoped element.",
                        List.of("Remove the 'scope: PER_RUN' override from element '" + axis.element() + "'",
                                "Remove this axis",
                                "Move the parameter to a different element"));
            }

            if ((axis.values() == null || axis.values().isEmpty()) &&
                axis.min() == null && axis.max() == null) {
                report.addError(ERR_EMPTY_AXIS_VALUES,
                        "Axis must have values or min/max range",
                        location,
                        "An axis without values produces no parameter variation. Define either an " +
                        "explicit list of values (e.g., [1, 2, 4, 8]) or a numeric range with min/max. " +
                        "The number of axis values determines how many trials will be generated for this dimension.",
                        List.of("Add a 'values' list with explicit parameter values",
                                "Add 'min' and 'max' fields to define a numeric range",
                                "Remove this axis if no variation is needed"));
            }

            int nesting = axis.nesting() != null ? axis.nesting() : i;
            maxNesting.merge(axis.element(), nesting, Math::max);
        }

        // Validate CONCURRENT only on innermost axis
        for (int i = 0; i < definition.axes().size(); i++) {
            AxisDefinition axis = definition.axes().get(i);
            if ("concurrent".equals(axis.mode())) {
                int nesting = axis.nesting() != null ? axis.nesting() : i;
                int max = maxNesting.getOrDefault(axis.element(), 0);

                if (nesting < max) {
                    report.addError(ERR_CONCURRENT_NOT_INNERMOST,
                            "CONCURRENT mode is only valid for innermost axis within an element",
                            "axes[" + i + "]",
                            "CONCURRENT mode runs multiple parameter values in parallel rather than sequentially. " +
                            "This is only allowed on the innermost (deepest nested) axis because outer axes " +
                            "determine the sequential trial structure. Running outer axes concurrently would " +
                            "create ambiguous execution order and prevent proper lifecycle management.",
                            List.of("Change this axis to SERIAL mode",
                                    "Increase this axis's nesting level to be innermost",
                                    "Reduce the nesting level of other axes on the same element"));
                }
            }
        }
    }

    /// Validates settings.
    private void validateSettings(TestPlanDefinition definition, DefaultValidationResult report) {
        if (definition.settings() == null) return;

        var settings = definition.settings();
        if ("retry".equals(settings.onFailure())) {
            int retryCount = settings.retryCount();
            if (retryCount < 1) {
                report.addError(ERR_INVALID_RETRY_COUNT,
                        "RETRY failure policy requires a retry count of at least 1",
                        "settings.on_failure",
                        "When on_failure is set to RETRY, a positive retry count must be specified. " +
                        "Use the format 'retry(n)' where n is the number of retries per failed trial. " +
                        "For example, 'retry(3)' will retry each failed trial up to 3 times.",
                        List.of("Set on_failure to 'retry(3)' for 3 retries",
                                "Change on_failure to 'skip' to continue without retries",
                                "Change on_failure to 'stop' to halt on first failure"));
            }
        }
    }

    /// Validates and warns about high-cost configurations.
    private void validateCosts(
            TestPlanDefinition definition,
            Map<String, ElementDefinition> elementMap,
            DefaultValidationResult report) {

        int estimatedTrials = estimateTrialCount(definition);

        if (estimatedTrials > 100) {
            report.addWarning(WARN_LARGE_TRIAL_COUNT,
                    String.format("Study will generate ~%d trials, which may take significant time",
                            estimatedTrials),
                    "Large studies can take hours or days to complete. Each trial involves deployment, " +
                    "execution, result capture, and potentially teardown. Consider starting with fewer " +
                    "parameter values to validate your study design before running the full sweep. " +
                    "You can also use sampling strategies to reduce the trial count while maintaining coverage.");
        }

        for (ElementDefinition elem : definition.elements()) {
            if (elem.dependsOn() == null) continue;

            for (DependencyDefinition dep : elem.dependsOn()) {
                if (dep.relationship() == RelationshipType.MUTUALLY_EXCLUSIVE) {
                    int downstreamRuns = countElementInvocations(definition, elem.id());
                    if (downstreamRuns > 10) {
                        report.addWarning(WARN_MUTUALLY_EXCLUSIVE_HIGH_COST,
                                String.format("MUTUALLY_EXCLUSIVE on '%s' → '%s' will cause %d redeploys",
                                        dep.element(), elem.id(), downstreamRuns),
                                "element:" + elem.id(),
                                "MUTUALLY_EXCLUSIVE tears down and redeploys the upstream element before each " +
                                "invocation of the downstream element. With " + downstreamRuns + " invocations, " +
                                "this adds significant deployment overhead. Consider using SHARED relationship " +
                                "if the upstream element can be reused across trials, or reduce the number " +
                                "of parameter values to minimize redeploys.",
                                List.of("Change relationship to SHARED if state reuse is acceptable",
                                        "Reduce axis values to decrease invocation count",
                                        "Accept the overhead if fresh state is required for correctness"));
                    }
                }
            }
        }
    }

    /// Estimates the total number of trials.
    private int estimateTrialCount(TestPlanDefinition definition) {
        if (definition.axes() == null || definition.axes().isEmpty()) {
            return 1;
        }

        int total = 1;
        for (AxisDefinition axis : definition.axes()) {
            int axisSize = 1;
            if (axis.values() != null) {
                axisSize = axis.values().size();
            } else if (axis.min() != null && axis.max() != null &&
                       axis.sampling() != null && axis.sampling().count() != null) {
                axisSize = axis.sampling().count();
            }

            int reps = axis.repetitions() != null ? axis.repetitions() : 1;
            total *= axisSize * reps;
        }

        return total;
    }

    /// Counts how many times an element will be invoked.
    private int countElementInvocations(TestPlanDefinition definition, String elementId) {
        int count = 1;
        if (definition.axes() != null) {
            for (AxisDefinition axis : definition.axes()) {
                if (axis.element().equals(elementId) && axis.values() != null) {
                    count *= axis.values().size();
                }
            }
        }
        return count;
    }

    /// Validates parameter completeness.
    private void validateParameterCompleteness(
            TestPlanDefinition definition,
            Map<String, ElementDefinition> elementMap,
            DefaultValidationResult report) {

        if (definition.axes() != null) {
            for (int i = 0; i < definition.axes().size(); i++) {
                AxisDefinition axis = definition.axes().get(i);
                if (axis.parameter() == null || axis.parameter().isBlank()) {
                    report.addError(ERR_MISSING_AXIS_PARAMETER,
                            "Axis at index " + i + " has no parameter name",
                            "axes[" + i + "]",
                            "Each axis must specify which parameter to vary. The 'parameter' field names " +
                            "a parameter from the target element's parameter space. Without it, the axis " +
                            "has no dimension to sweep.",
                            List.of("Add a 'parameter' field to the axis definition",
                                    "Remove the axis if no variation is needed"));
                }
            }
        }

        if (definition.bindings() != null) {
            for (int i = 0; i < definition.bindings().size(); i++) {
                BindingDefinition binding = definition.bindings().get(i);
                String location = "bindings[" + i + "]";

                if (binding.parameter() == null || binding.parameter().isBlank()) {
                    report.addError(ERR_MISSING_BINDING_FIELD,
                            "Binding at index " + i + " has no parameter name",
                            location,
                            "Each binding must specify which parameter to set. The 'parameter' field " +
                            "names the target parameter on the element.",
                            List.of("Add a 'parameter' field to the binding"));
                }
                if (binding.element() == null || binding.element().isBlank()) {
                    report.addError(ERR_MISSING_BINDING_FIELD,
                            "Binding at index " + i + " has no target element",
                            location,
                            "Each binding must specify which element receives the parameter value. " +
                            "The 'element' field must reference a valid element in the study.",
                            List.of("Add an 'element' field to the binding"));
                } else if (!elementMap.containsKey(binding.element())) {
                    report.addError(ERR_MISSING_BINDING_FIELD,
                            "Binding at index " + i + " references unknown element '" + binding.element() + "'",
                            location,
                            "The binding targets element '" + binding.element() + "' which is not defined " +
                            "in this study. Bindings must reference elements in the same study definition.",
                            List.of("Check the spelling of the element reference",
                                    "Add an element with id '" + binding.element() + "'"));
                }
                if (binding.value() == null) {
                    report.addError(ERR_MISSING_BINDING_FIELD,
                            "Binding at index " + i + " has no value",
                            location,
                            "Each binding must provide a value. The value may be a literal or contain " +
                            "${element.export} references that are resolved at runtime.",
                            List.of("Add a 'value' field to the binding"));
                }
            }
        }
    }

    /// Validates node sufficiency for INSTANCED_PER configurations.
    private void validateNodeSufficiency(
            TestPlanDefinition definition,
            Map<String, ElementDefinition> elementMap,
            DefaultValidationResult report) {

        if (definition.axes() == null) return;

        Map<String, Integer> concurrentCounts = new HashMap<>();
        for (AxisDefinition axis : definition.axes()) {
            if ("concurrent".equals(axis.mode()) && axis.values() != null) {
                concurrentCounts.merge(axis.element(), axis.values().size(), (a, b) -> a * b);
            }
        }

        for (ElementDefinition elem : definition.elements()) {
            if (elem.dependsOn() == null) continue;

            Integer concurrentCount = concurrentCounts.get(elem.id());
            if (concurrentCount == null || concurrentCount <= 1) continue;

            for (DependencyDefinition dep : elem.dependsOn()) {
                if (dep.relationship() == RelationshipType.INSTANCED_PER) {
                    boolean hasInfraProvider = typeProvider.hasInfrastructureType()
                            && definition.elements().stream()
                                    .anyMatch(e -> typeProvider.descriptor(e.type())
                                            .map(d -> d.providesInfrastructure())
                                            .orElse(false));

                    if (!hasInfraProvider) {
                        report.addWarning(WARN_INSUFFICIENT_NODES,
                                String.format("INSTANCED_PER on '%s' requires %d concurrent instances but " +
                                        "no infrastructure-providing element is defined",
                                        elem.id(), concurrentCount),
                                "element:" + elem.id(),
                                "INSTANCED_PER with CONCURRENT axes deploys " + concurrentCount +
                                " parallel instances of '" + elem.id() + "'. Each instance may need " +
                                "separate infrastructure (nodes, ports). Without an infrastructure-providing " +
                                "element, these instances would all run on the controller, which may not " +
                                "have sufficient resources.",
                                List.of("Add an infrastructure element to provision " + concurrentCount + " nodes",
                                        "Reduce the CONCURRENT axis value count",
                                        "Change the axis mode to SERIAL"));
                    } else {
                        report.addWarning(WARN_INSUFFICIENT_NODES,
                                String.format("INSTANCED_PER on '%s' requires %d concurrent instances; " +
                                        "verify infrastructure has sufficient capacity",
                                        elem.id(), concurrentCount),
                                "element:" + elem.id(),
                                "INSTANCED_PER with CONCURRENT axes deploys " + concurrentCount +
                                " parallel instances of '" + elem.id() + "'. Verify that the " +
                                "infrastructure element provisions at least " + concurrentCount +
                                " nodes with the required role. Insufficient capacity will cause " +
                                "scheduling failures at runtime.",
                                List.of("Verify infrastructure has at least " + concurrentCount + " nodes",
                                        "Reduce the CONCURRENT axis value count if capacity is limited",
                                        "Change the axis mode to SERIAL for sequential execution"));
                    }
                    break;
                }
            }
        }
    }

    /// Validates INSTANCED_PER dependency edges.
    private void validateInstancedPerEdges(
            TestPlanDefinition definition,
            Map<String, ElementDefinition> elementMap,
            DefaultValidationResult report) {

        Set<String> elementsWithConcurrentAxis = new HashSet<>();
        if (definition.axes() != null) {
            for (AxisDefinition axis : definition.axes()) {
                if ("concurrent".equals(axis.mode())) {
                    elementsWithConcurrentAxis.add(axis.element());
                }
            }
        }

        for (ElementDefinition elem : definition.elements()) {
            if (elem.dependsOn() == null) continue;

            for (DependencyDefinition dep : elem.dependsOn()) {
                if (dep.relationship() == RelationshipType.INSTANCED_PER
                        && !elementsWithConcurrentAxis.contains(elem.id())) {
                    report.addWarning(WARN_INSTANCED_PER_WITHOUT_CONCURRENT,
                            String.format("INSTANCED_PER dependency '%s' → '%s' without CONCURRENT axis on '%s'",
                                    dep.element(), elem.id(), elem.id()),
                            "element:" + elem.id(),
                            "INSTANCED_PER relationship is designed for concurrent downstream execution. " +
                            "Without a CONCURRENT axis on '" + elem.id() + "', the INSTANCED_PER behavior " +
                            "is equivalent to SHARED — downstream instances run one at a time. " +
                            "Add a CONCURRENT axis to enable parallel execution.",
                            List.of("Add a CONCURRENT axis to element '" + elem.id() + "'",
                                    "Change the dependency relationship to SHARED if sequential execution is intended"));
                }
            }
        }
    }

    /// Validates that SHARED dependency edges are not silently invalidated by
    /// upstream binding changes between adjacent trials.
    private void validateUnstableReuse(DefaultTestPlan plan, DefaultValidationResult report) {
        List<Trial> trials = plan.trials();
        if (trials.size() < 2) {
            return;
        }

        for (Element downstream : plan.elements()) {
            for (Element upstreamDep : downstream.dependencies()) {
                var relOpt = plan.relationshipBetween(upstreamDep, downstream);
                if (relOpt.isEmpty() || relOpt.get() != RelationshipType.SHARED) {
                    continue;
                }

                String upstreamId = upstreamDep.name();

                for (int i = 1; i < trials.size(); i++) {
                    Trial prev = trials.get(i - 1);
                    Trial curr = trials.get(i);

                    Map<String, Object> prevBindings = extractElementAssignments(prev, upstreamId);
                    Map<String, Object> currBindings = extractElementAssignments(curr, upstreamId);

                    if (!prevBindings.isEmpty() && !currBindings.isEmpty()
                            && !prevBindings.equals(currBindings)) {
                        report.addWarning(WARN_UNSTABLE_REUSE,
                                String.format("SHARED edge '%s' → '%s' overridden: upstream bindings change " +
                                        "between trial %d and trial %d, forcing redeploy",
                                        upstreamId, downstream.name(), i - 1, i),
                                "element:" + downstream.name(),
                                "The SHARED relationship on the dependency from '" + downstream.name() +
                                "' to '" + upstreamId + "' intends to keep the upstream running across " +
                                "trials. However, '" + upstreamId + "' has a varied parameter that " +
                                "changes between these trials, requiring a redeploy. The SHARED " +
                                "optimization is silently overridden for this trial pair.",
                                List.of("Accept the overhead — this is inherent when the upstream has varied parameters",
                                        "Remove the axis on '" + upstreamId + "' if it shouldn't vary",
                                        "Change the dependency relationship to MUTUALLY_EXCLUSIVE to make the intent explicit"));
                        break;
                    }
                }
            }
        }
    }

    /// Extracts assignment values for a specific element from a trial.
    private Map<String, Object> extractElementAssignments(Trial trial, String elementId) {
        String prefix = elementId + ".";
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : trial.assignments().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey().substring(prefix.length()),
                        entry.getValue().value());
            }
        }
        return result;
    }
}
