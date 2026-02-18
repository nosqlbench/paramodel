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
package io.nosqlbench.paramodel.engine;

import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.ElementTypeDescriptorProvider;
import io.nosqlbench.paramodel.engine.compiler.DefaultValidationResult;
import io.nosqlbench.paramodel.engine.compiler.ValidationStage;
import io.nosqlbench.paramodel.engine.definition.TestPlanComposer;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinitionParser;
import io.nosqlbench.paramodel.engine.plan.DefaultTestPlan;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.ExecutionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// High-level API facade for the test plan system.
///
/// Provides a simple interface for:
/// - Parsing and validating test plan definitions
/// - Composing plans with trials and execution plans
/// - Previewing plan execution (cost estimation)
public class TestPlanService {
    private static final Logger logger = LoggerFactory.getLogger(TestPlanService.class);

    private final TestPlanDefinitionParser parser;
    private final ValidationStage validationStage;
    private final TestPlanComposer composer;

    public TestPlanService() {
        this(ElementTypeDescriptorProvider.open());
    }

    /// Creates a service with the given element type descriptor provider.
    ///
    /// @param typeProvider supplies element type descriptors for validation
    public TestPlanService(ElementTypeDescriptorProvider typeProvider) {
        this.parser = new TestPlanDefinitionParser(typeProvider);
        this.validationStage = new ValidationStage(typeProvider);
        this.composer = new TestPlanComposer(typeProvider);
    }

    /// Parses a test plan definition from a YAML file.
    ///
    /// @param yamlPath path to the YAML definition
    /// @return the parsed definition
    /// @throws IOException if reading fails
    public TestPlanDefinition parseDefinition(Path yamlPath) throws IOException {
        logger.info("Parsing test plan definition from: {}", yamlPath);
        return parser.parse(yamlPath);
    }

    /// Parses a test plan definition from an input stream.
    ///
    /// @param inputStream the input stream containing YAML
    /// @return the parsed definition
    /// @throws IOException if reading fails
    public TestPlanDefinition parseDefinition(InputStream inputStream) throws IOException {
        return parser.parse(inputStream);
    }

    /// Parses a test plan definition from a YAML string.
    ///
    /// @param yaml the YAML content
    /// @return the parsed definition
    /// @throws IOException if parsing fails
    public TestPlanDefinition parseDefinition(String yaml) throws IOException {
        return parser.parseString(yaml);
    }

    /// Validates a test plan definition.
    ///
    /// @param definition the definition to validate
    /// @return validation result with any errors or warnings
    public Compiler.ValidationResult validate(TestPlanDefinition definition) {
        logger.debug("Validating test plan definition: {}", definition.name());
        return validationStage.validate(definition);
    }

    /// Validates a test plan definition in draft mode.
    ///
    /// Draft mode is lenient — empty element lists produce an informational
    /// note rather than an error, allowing incremental plan construction.
    ///
    /// @param definition the definition to validate
    /// @return validation result with downgraded empty-element errors
    public Compiler.ValidationResult validateDraft(TestPlanDefinition definition) {
        logger.debug("Validating test plan definition (draft mode): {}", definition.name());
        return validationStage.validateDraft(definition);
    }

    /// Composes a plan from a definition, returning a {@link DefaultTestPlan}.
    ///
    /// This performs full composition including:
    /// - Validation
    /// - Scope derivation (via paramodel's NormalizationStage)
    /// - Trial generation (via paramodel's TrialEnumerationStage)
    /// - Execution plan generation (via paramodel's compiler pipeline)
    ///
    /// @param definition the test plan definition
    /// @return the composed plan with trials and execution plan
    /// @throws IllegalArgumentException if the definition is invalid
    public DefaultTestPlan compose(TestPlanDefinition definition) {
        // Validate first
        Compiler.ValidationResult report = validationStage.validate(definition);
        if (!report.isValid()) {
            throw new IllegalArgumentException(
                    "Invalid test plan definition:\n" + report);
        }

        // Log any warnings
        if (report.hasWarnings()) {
            logger.warn("Test plan definition has warnings:\n{}", report);
        }

        // Compose the plan
        return composer.compose(definition);
    }

    /// Parses and composes a plan from a YAML file in one step.
    ///
    /// @param yamlPath path to the YAML definition
    /// @return the composed plan
    /// @throws IOException if reading fails
    /// @throws IllegalArgumentException if the definition is invalid
    public DefaultTestPlan loadAndCompose(Path yamlPath) throws IOException {
        TestPlanDefinition definition = parseDefinition(yamlPath);
        return compose(definition);
    }

    /// Parses and composes a plan from a YAML string in one step.
    ///
    /// @param yaml the YAML content
    /// @return the composed plan
    /// @throws IOException if parsing fails
    /// @throws IllegalArgumentException if the definition is invalid
    public DefaultTestPlan loadAndCompose(String yaml) throws IOException {
        TestPlanDefinition definition = parseDefinition(yaml);
        return compose(definition);
    }

    /// Gets the execution plan for a composed plan.
    ///
    /// @param plan the composed test plan
    /// @return the execution plan, or empty if not yet composed
    public Optional<ExecutionPlan> getExecutionPlan(DefaultTestPlan plan) {
        return plan.getExecutionPlan();
    }

    /// Returns a preview of the plan execution without actually running it.
    /// Useful for dry-run validation and cost estimation.
    ///
    /// @param plan the composed test plan
    /// @return a detailed summary of what would be executed
    public PlanPreview preview(DefaultTestPlan plan) {
        ExecutionPlan execPlan = plan.getExecutionPlan()
                .orElseThrow(() -> new IllegalStateException("Plan has no execution plan"));

        int deploySteps = 0;
        int teardownSteps = 0;
        int trialSteps = 0;
        int barrierSteps = 0;
        int checkpointSteps = 0;

        // Per-element deploy counts
        Map<String, Integer> deploysPerElement = new LinkedHashMap<>();

        for (AtomicStep step : execPlan.steps()) {
            switch (step) {
                case AtomicStep.DeployElement deploy -> {
                    deploySteps++;
                    deploysPerElement.merge(deploy.elementId(), 1, Integer::sum);
                }
                case AtomicStep.TeardownElement ignored -> teardownSteps++;
                case AtomicStep.TrialStep ignored -> trialSteps++;
                case AtomicStep.AwaitElement ignored -> trialSteps++;
                case AtomicStep.BarrierSync ignored -> barrierSteps++;
                case AtomicStep.CheckpointState ignored -> checkpointSteps++;
                case AtomicStep.NotifyTrialStart ignored -> {} // lifecycle notification
                case AtomicStep.NotifyTrialEnd ignored -> {} // lifecycle notification
            }
        }

        // Node utilization: count how many deploys target each node role
        Map<String, Integer> nodeRoleUsage = new LinkedHashMap<>();
        for (Element element : plan.elements()) {
            String nodeRole = element.tags().get("node_role");
            if (nodeRole != null) {
                int deploys = deploysPerElement.getOrDefault(element.name(), 0);
                nodeRoleUsage.merge(nodeRole, deploys, Integer::sum);
            }
        }

        // Estimate SHARED savings: compare actual deploys vs worst-case deploys.
        // Worst case: every element deploys once per trial (no fingerprint reuse).
        int worstCaseDeploys = plan.elements().size() * Math.max(1, plan.size());
        int sharedSavings = Math.max(0, worstCaseDeploys - deploySteps);

        return new PlanPreview(
                plan.name(),
                plan.elements().size(),
                plan.axes().size(),
                plan.size(),
                execPlan.steps().size(),
                deploySteps,
                teardownSteps,
                trialSteps,
                barrierSteps,
                checkpointSteps,
                deploysPerElement,
                nodeRoleUsage,
                sharedSavings
        );
    }

    /// Detailed preview of a plan execution.
    ///
    /// Includes per-element deploy counts, node utilization, and optimization savings.
    /// Step counts map to paramodel {@link AtomicStep} types:
    /// - `deploySteps`: {@link AtomicStep.DeployElement} (element deployment)
    /// - `teardownSteps`: {@link AtomicStep.TeardownElement} (element teardown)
    /// - `trialSteps`: {@link AtomicStep.TrialStep} (trial execution)
    /// - `barrierSteps`: {@link AtomicStep.BarrierSync} (synchronization)
    /// - `checkpointSteps`: {@link AtomicStep.CheckpointState} (state persistence)
    ///
    /// @param name the plan name
    /// @param elementCount number of elements
    /// @param axisCount number of axes
    /// @param trialCount number of trials
    /// @param totalSteps total steps in the execution plan
    /// @param deploySteps element deployment steps
    /// @param teardownSteps element teardown steps
    /// @param trialSteps trial execution steps
    /// @param barrierSteps barrier synchronization steps
    /// @param checkpointSteps checkpoint persistence steps
    /// @param deploysPerElement deploys per element ID
    /// @param nodeRoleUsage deploy counts per node role
    /// @param sharedSavings estimated deploys saved by SHARED reuse optimization
    public record PlanPreview(
            String name,
            int elementCount,
            int axisCount,
            int trialCount,
            int totalSteps,
            int deploySteps,
            int teardownSteps,
            int trialSteps,
            int barrierSteps,
            int checkpointSteps,
            Map<String, Integer> deploysPerElement,
            Map<String, Integer> nodeRoleUsage,
            int sharedSavings
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Study: %s%n", name));
            sb.append(String.format("  Elements: %d%n", elementCount));
            sb.append(String.format("  Axes: %d%n", axisCount));
            sb.append(String.format("  Trials: %d%n", trialCount));
            sb.append(String.format("  Total Steps: %d%n", totalSteps));
            sb.append(String.format("    Deploy: %d%n", deploySteps));
            sb.append(String.format("    Teardown: %d%n", teardownSteps));
            sb.append(String.format("    Execute Trial: %d%n", trialSteps));
            sb.append(String.format("    Barrier: %d%n", barrierSteps));
            sb.append(String.format("    Checkpoint: %d%n", checkpointSteps));

            if (!deploysPerElement.isEmpty()) {
                sb.append("  Deploys per element:%n".formatted());
                deploysPerElement.forEach((id, count) ->
                        sb.append(String.format("    %s: %d%n", id, count)));
            }

            if (!nodeRoleUsage.isEmpty()) {
                sb.append("  Node role utilization:%n".formatted());
                nodeRoleUsage.forEach((role, count) ->
                        sb.append(String.format("    %s: %d deploys%n", role, count)));
            }

            if (sharedSavings > 0) {
                sb.append(String.format("  Optimization: %d deploys saved via SHARED reuse%n", sharedSavings));
            }

            return sb.toString();
        }
    }
}
