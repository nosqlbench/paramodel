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
package io.nosqlbench.paramodel.engine.planners.simple;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.planners.StepGenerationStrategy;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

import static io.nosqlbench.paramodel.engine.planners.StepGenerationUtils.*;

///
/// Naive baseline step generation strategy with no grouping.
///
/// For every trial this strategy deploys all elements, emits a
/// {@link AtomicStep.TrialStep}, and tears them all down.  No element
/// reuse across trials and no notification scoping.
///
/// This strategy exists as a minimal reference implementation for writing
/// new strategies.  It is not intended for production use.
///
/// @see StepGenerationStrategy
///
public class SimpleStepGenerationStrategy implements StepGenerationStrategy {

    @Override
    public String strategyName() {
        return "simple";
    }

    @Override
    public String description() {
        return "Naive per-trial deploy/teardown with no grouping — "
            + "baseline reference strategy";
    }

    @Override
    public void generateSteps(CompilationContext context) {
        Optional<List<Trial>> trialsOpt = context.trials();
        Optional<List<CompilationContext.ElementInstance>> instancesOpt = context.elementInstances();

        if (trialsOpt.isEmpty() || instancesOpt.isEmpty()) {
            context.recordMetric("steps_generated", 0);
            return;
        }

        List<Trial> trials = trialsOpt.get();
        List<CompilationContext.ElementInstance> allInstances = instancesOpt.get();
        TestPlan plan = context.testPlan();
        List<Element> elements = plan.elements();

        List<Element> sortedElements = topologicalSort(elements);
        List<AtomicStep> steps = new ArrayList<>();
        List<Barrier> barriers = new ArrayList<>();

        int stepIndex = 0;
        int instanceCounter = 0;

        for (int trialIdx = 0; trialIdx < trials.size(); trialIdx++) {
            Trial trial = trials.get(trialIdx);

            // Deploy all elements in dependency order
            Map<String, String> deployStepForElement = new HashMap<>();
            for (Element element : sortedElements) {
                List<String> deployDeps = computeDependencies(element, deployStepForElement);

                Map<String, Object> config = new HashMap<>(element.configuration());
                for (var param : element.parameters()) {
                    trial.assignment(param.name()).ifPresent(value ->
                        config.put(param.name(), value.value())
                    );
                }

                String deployId = "deploy_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                int instNum = instanceCounter++;
                AtomicStep.DeployElement deploy = new AtomicStep.DeployElement(
                    deployId, element.name(), instNum, config, deployDeps,
                    Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                    Optional.empty(),
                    meta("trial_index", trialIdx, "trial_id", trial.id(), "strategy", "simple")
                );
                steps.add(deploy);
                deployStepForElement.put(element.name(), deployId);
            }

            // Emit a TrialStep depending on all deploys
            Map<String, String> elementBindings = new HashMap<>();
            for (Element element : elements) {
                allInstances.stream()
                    .filter(inst -> inst.element().name().equals(element.name()))
                    .filter(inst -> inst.trials().contains(trial))
                    .findFirst()
                    .ifPresent(inst -> elementBindings.put(element.name(), inst.instanceId()));
            }

            String trialStepId = "trial_step_t" + trialIdx + "_" + stepIndex++;
            AtomicStep.TrialStep trialStep = new AtomicStep.TrialStep(
                trialStepId, trial.id(), elementBindings,
                new ArrayList<>(deployStepForElement.values()),
                Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(),
                meta("trial_index", trialIdx, "trial_id", trial.id(), "strategy", "simple")
            );
            steps.add(trialStep);

            // Teardown all elements in reverse dependency order
            List<Element> reversed = new ArrayList<>(sortedElements);
            Collections.reverse(reversed);
            Map<String, String> teardownStepForElement = new HashMap<>();
            for (Element element : reversed) {
                List<String> teardownDeps = new ArrayList<>();
                teardownDeps.add(trialStepId);
                // Depend on teardowns of elements that depend on this one
                for (Element other : sortedElements) {
                    for (Element.Dependency dep : other.dependencies()) {
                        if (dep.target().name().equals(element.name())) {
                            String otherTeardown = teardownStepForElement.get(other.name());
                            if (otherTeardown != null && !teardownDeps.contains(otherTeardown)) {
                                teardownDeps.add(otherTeardown);
                            }
                        }
                    }
                }

                int instNum = deployStepForElement.containsKey(element.name())
                    ? instanceCounter - sortedElements.size() + sortedElements.indexOf(element)
                    : 0;
                String teardownId = "teardown_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                boolean isFinal = (trialIdx == trials.size() - 1);
                AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                    teardownId, element.name(), instNum, isFinal, teardownDeps,
                    Optional.empty(), AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    meta("trial_index", trialIdx, "trial_id", trial.id(), "strategy", "simple")
                );
                steps.add(teardown);
                teardownStepForElement.put(element.name(), teardownId);
            }
        }

        context.setSteps(steps);
        context.setBarriers(barriers);
        context.recordMetric("steps_generated", steps.size());
        context.recordMetric("barriers_generated", barriers.size());
    }
}
