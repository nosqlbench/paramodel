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

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.compiler.AxisBindingSet;
import io.nosqlbench.paramodel.engine.compiler.NormalizationStage;
import io.nosqlbench.paramodel.engine.planners.StepGenerationStrategy;
import io.nosqlbench.paramodel.engine.planners.StepGenerationUtils;
import io.nosqlbench.paramodel.engine.planners.reducto.rules.*;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

///
/// Graph-reduction-based step generation strategy.
///
/// Constructs a mutable DAG of trial lifecycle operations and applies eight
/// named transformation rules in sequence to produce the final execution plan.
/// Each transformation is independently testable and the algorithm is more
/// transparent than the imperative fingerprint-based approach.
///
/// ## Pipeline
///
/// 1. **Stage One** — Mixed-radix enumeration of the parameter space
/// 2. **Stage Two** — Seed graph with one TRIAL_SEED node per trial
/// 3. **Stage Three** — Apply rules 1–8 in order
/// 4. **Linearization** — Topological sort and map to AtomicStep records
///
/// @see io.nosqlbench.paramodel.engine.planners.reducto.rules.Rule
///
public class ReductoStepGenerationStrategy implements StepGenerationStrategy {

    @Override
    public String strategyName() {
        return "reducto";
    }

    @Override
    public String description() {
        return "Graph-reduction-based step generation with explicit DAG transformations "
            + "and named rules for transparent, testable step planning";
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
        TestPlan plan = context.testPlan();
        List<Element> elements = plan.elements();

        @SuppressWarnings("unchecked")
        Map<String, AxisBindingSet> effectiveBindings = context.get(NormalizationStage.EFFECTIVE_BINDINGS_KEY)
            .filter(v -> v instanceof Map)
            .map(v -> (Map<String, AxisBindingSet>) v)
            .orElse(Map.of());

        List<Element> sortedElements = StepGenerationUtils.topologicalSort(elements);
        List<String> trialElementNames = StepGenerationUtils.identifyTrialElements(sortedElements, effectiveBindings);
        Map<String, Set<String>> lifelineClusters = StepGenerationUtils.computeLifelineClusters(sortedElements);

        Map<String, List<Element>> dedicatedDependents = new HashMap<>();
        for (Element element : elements) {
            for (Element.Dependency dep : element.dependencies()) {
                if (dep.type() == RelationshipType.DEDICATED) {
                    dedicatedDependents.computeIfAbsent(dep.target().name(), k -> new ArrayList<>())
                        .add(element);
                }
            }
        }

        MixedRadixEnumerator enumerator = MixedRadixEnumerator.fromElements(sortedElements, plan);
        BindingStateComputer bindingState = BindingStateComputer.compute(sortedElements, enumerator);

        RuleContext ruleContext = new RuleContext(
            sortedElements, bindingState, enumerator,
            trialElementNames, lifelineClusters, dedicatedDependents);

        ReductoGraph graph = new ReductoGraph();
        GraphSeeder.seed(graph, enumerator.totalTrials());

        List<Rule> rules = List.of(
            new Rule1_LifecycleExpansion(),
            new Rule2_DependencyEdges(),
            new Rule3_GroupCoalescing(),
            new Rule4_TrialNotifications(),
            new Rule5_HealthCheckGates(),
            new Rule6_ConcurrencyAnnotation(),
            new Rule7_StartEndMaterialization(),
            new Rule8_TransitiveReduction()
        );

        for (Rule rule : rules) {
            rule.apply(graph, ruleContext);
        }

        validateLifecycleInvariant(graph, sortedElements);

        // Stamp trial codes onto nodes for metadata propagation to AtomicSteps
        for (ReductoNode node : graph.nodes()) {
            if (node.trialIndex() >= 0 && node.trialIndex() < enumerator.totalTrials()) {
                node.putMetadata("trial_code", enumerator.trialCode(node.trialIndex()));
            }
        }

        Map<String, int[]> instanceTracker = new LinkedHashMap<>();
        GraphLinearizer.Result result = GraphLinearizer.linearize(
            graph, trials, sortedElements, trialElementNames, instanceTracker);

        context.setSteps(result.steps());
        context.setBarriers(result.barriers());
        context.recordMetric("steps_generated", result.steps().size());
        context.recordMetric("barriers_generated", result.barriers().size());
    }

    /// Validates the instance lifecycle invariant: every element must have an equal
    /// number of ACTIVATE and DEACTIVATE/AWAIT nodes, unless the element has a
    /// LIFELINE dependency (in which case its deactivation is subsumed by the
    /// lifeline target and the deactivation count is zero).
    private void validateLifecycleInvariant(ReductoGraph graph, List<Element> sortedElements) {
        Set<String> lifelineSubsumed = new HashSet<>();
        for (Element elem : sortedElements) {
            for (Element.Dependency dep : elem.dependencies()) {
                if (dep.type() == RelationshipType.LIFELINE) {
                    lifelineSubsumed.add(elem.name());
                }
            }
        }

        Map<String, Integer> activateCount = new HashMap<>();
        Map<String, Integer> terminateCount = new HashMap<>();

        for (ReductoNode node : graph.nodes()) {
            String eName = node.elementName();
            if (eName == null) continue;

            switch (node.type()) {
                case ACTIVATE -> activateCount.merge(eName, 1, Integer::sum);
                case DEACTIVATE, AWAIT -> terminateCount.merge(eName, 1, Integer::sum);
                default -> {}
            }
        }

        List<String> violations = new ArrayList<>();
        for (Element elem : sortedElements) {
            String eName = elem.name();
            int activations = activateCount.getOrDefault(eName, 0);
            int terminations = terminateCount.getOrDefault(eName, 0);

            if (lifelineSubsumed.contains(eName)) {
                if (terminations != 0) {
                    violations.add("Element '" + eName + "' has a LIFELINE dependency but "
                        + terminations + " deactivation node(s) remain in the graph");
                }
            } else {
                if (activations != terminations) {
                    violations.add("Element '" + eName + "' has " + activations
                        + " activation(s) but " + terminations + " deactivation(s)");
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "Instance lifecycle invariant violated after rule application:\n  "
                    + String.join("\n  ", violations));
        }
    }
}
