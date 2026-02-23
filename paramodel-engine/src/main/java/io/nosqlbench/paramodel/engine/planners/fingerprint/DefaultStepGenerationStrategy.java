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
package io.nosqlbench.paramodel.engine.planners.fingerprint;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.compiler.AxisBindingSet;
import io.nosqlbench.paramodel.engine.compiler.DefaultBarrier;
import io.nosqlbench.paramodel.engine.compiler.NormalizationStage;
import io.nosqlbench.paramodel.engine.compiler.StepGenerationStage;
import io.nosqlbench.paramodel.engine.planners.StepGenerationStrategy;
import io.nosqlbench.paramodel.engine.planners.StepGenerationUtils;
import io.nosqlbench.paramodel.engine.plan.DefaultElement;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

import static io.nosqlbench.paramodel.engine.planners.StepGenerationUtils.*;

///
/// Default step generation strategy implementing the unified fingerprint-based algorithm.
///
/// This strategy implements the full step generation logic including fingerprint-based
/// grouping, teardown ordering, barrier emission, and notification scoping.  It is the
/// original algorithm extracted from {@link StepGenerationStage}.
///
/// ## Design Rules (in order of precedence)
///
/// 1. **Trial element identity**: Trial elements are the innermost leaf
///    nodes in the dependency graph.
/// 2. **Notification scope containment**: The full lifecycle of every trial
///    element falls within NotifyTrialStart / NotifyTrialEnd brackets.
/// 3. **Non-trial elements as notification receivers**: Non-trial elements
///    deploy before NotifyTrialStart so they can observe trial lifecycle events.
///
/// @see StepGenerationStrategy
/// @see StepGenerationUtils
///
public class DefaultStepGenerationStrategy implements StepGenerationStrategy {

    @Override
    public String strategyName() {
        return "fingerprint";
    }

    @Override
    public String description() {
        return "Unified fingerprint-based step generation with grouping, teardown ordering, "
            + "barrier emission, and notification scoping";
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

        // Resolve effective bindings from context (populated by NormalizationStage)
        @SuppressWarnings("unchecked")
        Map<String, AxisBindingSet> effectiveBindings = context.get(NormalizationStage.EFFECTIVE_BINDINGS_KEY)
            .filter(v -> v instanceof Map)
            .map(v -> (Map<String, AxisBindingSet>) v)
            .orElse(Map.of());
        List<Element> elements = plan.elements();

        // Build reverse DEDICATED dependency map for fingerprint computation
        Map<String, List<Element>> dedicatedDependents = new HashMap<>();
        for (Element element : elements) {
            for (Element.Dependency dep : element.dependencies()) {
                if (dep.type() == RelationshipType.DEDICATED) {
                    dedicatedDependents.computeIfAbsent(dep.target().name(), k -> new ArrayList<>())
                        .add(element);
                }
            }
        }

        // Build set of elements that have ANY non-DEDICATED dependents
        Set<String> hasSharedDependents = new HashSet<>();
        for (Element element : elements) {
            for (Element.Dependency dep : element.dependencies()) {
                if (dep.type() != RelationshipType.DEDICATED) {
                    hasSharedDependents.add(dep.target().name());
                }
            }
        }

        // Topological sort elements
        List<Element> sortedElements = topologicalSort(elements);

        // Identify the trial elements (most interior leaves)
        List<String> trialElements = identifyTrialElements(sortedElements, effectiveBindings);

        // Elements that allow parallel instances across trials
        Set<String> parallelElements = new HashSet<>();
        for (Element element : elements) {
            boolean isDedicatedTarget = dedicatedDependents.containsKey(element.name())
                && !hasSharedDependents.contains(element.name());
            boolean isTrialLeaf = trialElements.contains(element.name());
            if ((isDedicatedTarget || isTrialLeaf) && getMaxConcurrency(element) <= 0) {
                parallelElements.add(element.name());
            }
        }
        Map<String, Set<String>> lifelineClusters = computeLifelineClusters(sortedElements);

        boolean explicitTransitiveDeps = Boolean.TRUE.equals(
            context.options().customOptions().get(StepGenerationStage.OPTION_EXPLICIT_TRANSITIVE_DEPS));

        List<AtomicStep> steps = new ArrayList<>();
        List<Barrier> barriers = new ArrayList<>();

        Map<String, String> lastStepForElement = new HashMap<>();
        Map<String, String> currentFingerprintForElement = new HashMap<>();
        Map<String, Integer> nextInstanceNumber = new HashMap<>();
        Map<String, Integer> currentInstanceNumber = new HashMap<>();
        Map<String, Deque<String>> concurrencyWindows = new HashMap<>();

        List<Element> allElementsByDepth = new ArrayList<>(sortedElements);
        allElementsByDepth.sort(Comparator.comparingInt(e ->
            resolveBinding(e, effectiveBindings).depth()));

        List<String> allElementNames = elements.stream().map(Element::name).toList();

        // --- Precompute nesting paths for graph visualization ---
        List<? extends io.nosqlbench.paramodel.plan.Axis<?>> planAxes = plan.axes();

        Map<Integer, List<String>> trialFullPaths = new HashMap<>();
        for (int i = 0; i < trials.size(); i++) {
            Trial t = trials.get(i);
            trialFullPaths.put(i, computeTrialAxisPath(t, planAxes));
        }

        Map<String, Map<Integer, List<String>>> groupNestingPaths = new HashMap<>();
        for (Element element : allElementsByDepth) {
            Map<Integer, List<String>> nestingByTrial = new HashMap<>();
            int groupStart = 0;
            String groupFingerprint = computeElementFingerprint(element, trials.getFirst(), plan, dedicatedDependents);

            for (int i = 1; i <= trials.size(); i++) {
                String fp = (i < trials.size())
                    ? computeElementFingerprint(element, trials.get(i), plan, dedicatedDependents)
                    : null;

                if (fp == null || !fp.equals(groupFingerprint)) {
                    List<String> sharedPrefix = new ArrayList<>(trialFullPaths.get(groupStart));
                    for (int j = groupStart + 1; j < i; j++) {
                        List<String> other = trialFullPaths.get(j);
                        int prefixLen = 0;
                        while (prefixLen < sharedPrefix.size() && prefixLen < other.size()
                               && sharedPrefix.get(prefixLen).equals(other.get(prefixLen))) {
                            prefixLen++;
                        }
                        sharedPrefix = sharedPrefix.subList(0, prefixLen);
                    }
                    for (int j = groupStart; j < i; j++) {
                        nestingByTrial.put(j, new ArrayList<>(sharedPrefix));
                    }
                    groupStart = i;
                    groupFingerprint = fp;
                }
            }
            groupNestingPaths.put(element.name(), nestingByTrial);
        }

        int stepIndex = 0;

        // Per-trial steps
        String lastSequentialExecId = null;
        List<String> latestStepPerTrial = new ArrayList<>();

        for (int trialIdx = 0; trialIdx < trials.size(); trialIdx++) {
            Trial trial = trials.get(trialIdx);

            // === Fingerprint check for all elements ===
            List<Element> toTeardown = new ArrayList<>();
            List<Element> toDeploy = new ArrayList<>();

            for (Element element : allElementsByDepth) {
                String currentFingerprint = computeElementFingerprint(element, trial, plan, dedicatedDependents);
                String previousFingerprint = currentFingerprintForElement.get(element.name());

                boolean needsDeploy = (previousFingerprint == null)
                    || !currentFingerprint.equals(previousFingerprint);

                if (needsDeploy) {
                    if (previousFingerprint != null) {
                        toTeardown.add(element);
                    }
                    toDeploy.add(element);
                    currentFingerprintForElement.put(element.name(), currentFingerprint);
                }
            }

            // Filter out lifeline-subsumed elements
            toTeardown.removeIf(StepGenerationUtils::isLifelineSubsumed);

            // Skip teardown for COMMAND trial elements (they self-terminate)
            toTeardown.removeIf(e -> e.shutdownSemantics() == Element.ShutdownSemantics.COMMAND
                && trialElements.contains(e.name()));

            // Teardown at group boundaries using reverse dependency ordering
            List<Element> teardownReversed = new ArrayList<>(toTeardown);
            Collections.reverse(teardownReversed);
            Map<String, String> boundaryTeardownStepForElement = new HashMap<>();
            for (Element element : teardownReversed) {
                int teardownInstNum = currentInstanceNumber.get(element.name());
                List<String> teardownDeps = new ArrayList<>();
                if (lastSequentialExecId != null) {
                    teardownDeps.add(lastSequentialExecId);
                } else {
                    String lastStep = lastStepForElement.get(element.name());
                    if (lastStep != null) teardownDeps.add(lastStep);
                }
                for (Element other : toTeardown) {
                    for (Element.Dependency dep : other.dependencies()) {
                        if (dep.target().name().equals(element.name())) {
                            String otherTeardown = boundaryTeardownStepForElement.get(other.name());
                            if (otherTeardown != null && !teardownDeps.contains(otherTeardown)) {
                                teardownDeps.add(otherTeardown);
                            }
                        }
                    }
                }
                String teardownId = "teardown_" + element.name() + "_" + stepIndex++;
                int prevTeardownTrialIdx = trialIdx > 0 ? trialIdx - 1 : 0;
                List<String> teardownNesting = groupNestingPaths
                        .getOrDefault(element.name(), Map.of())
                        .getOrDefault(prevTeardownTrialIdx, List.of());
                AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                    teardownId,
                    element.name(),
                    teardownInstNum,
                    false,
                    teardownDeps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    buildBoundaryTeardownMeta(element, trialIdx, trial.id(), teardownNesting, effectiveBindings)
                );
                steps.add(teardown);
                if (!parallelElements.contains(element.name())) {
                    lastStepForElement.put(element.name(), teardownId);
                }
                boundaryTeardownStepForElement.put(element.name(), teardownId);
            }

            // Split deploy list into non-trial and trial elements
            List<Element> toDeployNonTrial = new ArrayList<>();
            List<Element> toDeployTrial = new ArrayList<>();
            for (Element element : toDeploy) {
                if (trialElements.contains(element.name())) {
                    toDeployTrial.add(element);
                } else {
                    toDeployNonTrial.add(element);
                }
            }

            // Deploy NON-TRIAL bound elements BEFORE NotifyTrialStart
            List<String> nonTrialDeployIds = new ArrayList<>();
            for (Element element : toDeployNonTrial) {
                stepIndex = deployBoundElement(element, trialIdx, trial, plan, allInstances,
                        lastStepForElement, nextInstanceNumber, currentInstanceNumber,
                        concurrencyWindows, groupNestingPaths, steps, barriers, stepIndex,
                        explicitTransitiveDeps, parallelElements, List.of(), trialElements,
                        effectiveBindings, dedicatedDependents);
                nonTrialDeployIds.add(lastStepForElement.get(element.name()));
            }

            // === 2b: Trial notification scope ===
            Map<String, String> elementBindings = new HashMap<>();
            for (Element element : elements) {
                Optional<CompilationContext.ElementInstance> inst = findInstanceForTrial(element, trial, allInstances);
                inst.ifPresent(i -> elementBindings.put(element.name(), i.instanceId()));
            }

            List<String> notifyStartDeps = new ArrayList<>(nonTrialDeployIds);
            for (Element element : elements) {
                if (trialElements.contains(element.name())) continue;
                if (toDeployNonTrial.contains(element)) continue;
                String lastStep = lastStepForElement.get(element.name());
                if (lastStep != null && !notifyStartDeps.contains(lastStep)) {
                    notifyStartDeps.add(lastStep);
                }
            }
            if (!explicitTransitiveDeps) {
                notifyStartDeps = minimalDeps(elements, lastStepForElement, Map.of(), notifyStartDeps);
            }

            String notifyStartId = null;
            if (!trialElements.isEmpty()) {
                notifyStartId = "notify_trial_start_" + trialIdx + "_" + stepIndex++;
                List<String> trialNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
                AtomicStep.NotifyTrialStart notifyStart = new AtomicStep.NotifyTrialStart(
                    notifyStartId, trial.id(), trialIdx, Optional.empty(), allElementNames, notifyStartDeps,
                    Optional.empty(), AtomicStep.ResourceRequirements.none(), Optional.empty(),
                    meta("trial_index", trialIdx, "trial_id", trial.id(), "nesting_path", trialNesting)
                );
                steps.add(notifyStart);
            }

            // Deploy TRIAL elements AFTER NotifyTrialStart
            List<String> trialDeployExtraDeps = notifyStartId != null
                    ? List.of(notifyStartId) : List.of();
            for (Element element : toDeployTrial) {
                stepIndex = deployBoundElement(element, trialIdx, trial, plan, allInstances,
                        lastStepForElement, nextInstanceNumber, currentInstanceNumber,
                        concurrencyWindows, groupNestingPaths, steps, barriers, stepIndex,
                        explicitTransitiveDeps, parallelElements, trialDeployExtraDeps, trialElements,
                        effectiveBindings, dedicatedDependents);
            }

            // Emit operative steps for all trial elements in topological order
            List<String> currentExecDeps = notifyStartId != null ? List.of(notifyStartId) : List.of();
            List<String> trialOperativeStepIds = new ArrayList<>();
            Map<String, String> elementToOperativeStep = new HashMap<>();

            for (Element element : sortedElements) {
                if (!trialElements.contains(element.name())) continue;

                List<String> operativeDeps = new ArrayList<>();

                String ownDeploy = lastStepForElement.get(element.name());
                if (ownDeploy != null) {
                    operativeDeps.add(ownDeploy);
                } else {
                    operativeDeps.addAll(currentExecDeps);
                }

                for (Element.Dependency dep : element.dependencies()) {
                    String depOperativeStep = elementToOperativeStep.get(dep.target().name());
                    if (depOperativeStep != null && !operativeDeps.contains(depOperativeStep)) {
                        operativeDeps.add(depOperativeStep);
                    }
                }

                List<String> execNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
                Map<String, Object> execMeta = meta("trial_index", trialIdx,
                         "trial_id", trial.id(), "nesting_path", execNesting,
                         "trial_element", element.name());

                String opId;
                if (element.shutdownSemantics() == Element.ShutdownSemantics.COMMAND) {
                    int awaitInstNum = currentInstanceNumber.getOrDefault(element.name(), 0);
                    opId = "await_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                    AtomicStep.AwaitElement awaitStep = new AtomicStep.AwaitElement(
                        opId, element.name(), awaitInstNum, trial.id(),
                        elementBindings, operativeDeps,
                        Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                        Optional.empty(), execMeta
                    );
                    steps.add(awaitStep);
                } else {
                    opId = "trial_step_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                    AtomicStep.TrialStep trialStep = new AtomicStep.TrialStep(
                        opId,
                        trial.id(),
                        elementBindings,
                        operativeDeps,
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.minimal(),
                        Optional.empty(),
                        execMeta
                    );
                    steps.add(trialStep);
                }
                elementToOperativeStep.put(element.name(), opId);
                trialOperativeStepIds.add(opId);
                lastStepForElement.put("__trial_op_" + element.name() + "_t" + trialIdx, opId);
            }

            // Phase 2c: Predictive eager teardown
            String notifyTrialEndId = null;
            Map<String, String> eagerTeardownStepForElement = new HashMap<>();
            Set<String> eagerTeardownDependedUpon = new HashSet<>();

            if (!trialElements.isEmpty()) {
                notifyTrialEndId = "notify_trial_end_" + trialIdx + "_" + stepIndex++;
                List<String> trialNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
                AtomicStep.NotifyTrialEnd notifyEnd = new AtomicStep.NotifyTrialEnd(
                    notifyTrialEndId, trial.id(), trialIdx, Optional.empty(), allElementNames,
                    AtomicStep.ShutdownReason.NORMAL,
                    trialOperativeStepIds,
                    Optional.empty(), AtomicStep.ResourceRequirements.none(), Optional.empty(),
                    meta("trial_index", trialIdx, "trial_id", trial.id(), "nesting_path", trialNesting)
                );
                steps.add(notifyEnd);
            }

            // Predictive eager teardown (Phase 2c)
            List<Element> boundReversed = new ArrayList<>(allElementsByDepth);
            Collections.reverse(boundReversed);

            for (Element element : boundReversed) {
                boolean isLastTrial = (trialIdx == trials.size() - 1);
                if (isLastTrial) continue;

                String nextFingerprint = computeElementFingerprint(element, trials.get(trialIdx + 1), plan, dedicatedDependents);
                String currentFingerprint = currentFingerprintForElement.get(element.name());
                boolean fingerprintWillChange = currentFingerprint != null
                    && !nextFingerprint.equals(currentFingerprint);

                if (!fingerprintWillChange) continue;

                if (element.shutdownSemantics() == Element.ShutdownSemantics.COMMAND && trialElements.contains(element.name())) {
                    if (!parallelElements.contains(element.name())) {
                        String opStep = elementToOperativeStep.get(element.name());
                        if (opStep != null) {
                            lastStepForElement.put(element.name(), opStep);
                        }
                    }
                    currentFingerprintForElement.remove(element.name());
                    continue;
                }

                if (isLifelineSubsumed(element)) continue;

                int teardownInstNum = currentInstanceNumber.get(element.name());
                List<String> teardownDeps = new ArrayList<>();
                String elementOpStep = elementToOperativeStep.get(element.name());
                if (elementOpStep != null) {
                    teardownDeps.add(elementOpStep);
                } else {
                    if (notifyTrialEndId != null) teardownDeps.add(notifyTrialEndId);
                    else teardownDeps.addAll(trialOperativeStepIds);
                }

                for (Element other : allElementsByDepth) {
                    for (Element.Dependency dep : other.dependencies()) {
                        if (dep.target().name().equals(element.name())) {
                            String otherTeardown = eagerTeardownStepForElement.get(other.name());
                            if (otherTeardown != null && !teardownDeps.contains(otherTeardown)) {
                                teardownDeps.add(otherTeardown);
                                eagerTeardownDependedUpon.add(otherTeardown);
                            }
                        }
                    }
                }

                List<String> teardownTrialNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
                String teardownId = "teardown_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                    teardownId,
                    element.name(),
                    teardownInstNum,
                    false,
                    teardownDeps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    meta("reason", "predictive_eager",
                         "binding_depth", resolveBinding(element, effectiveBindings).depth(),
                         "trial_index", trialIdx, "trial_id", trial.id(),
                         "nesting_path", teardownTrialNesting)
                );
                steps.add(teardown);
                eagerTeardownStepForElement.put(element.name(), teardownId);
                if (!parallelElements.contains(element.name())) {
                    lastStepForElement.put(element.name(), teardownId);
                }
                currentFingerprintForElement.remove(element.name());

                int maxConc = getMaxConcurrency(element);
                if (maxConc > 0) {
                    concurrencyWindows
                            .computeIfAbsent(element.name(), k -> new ArrayDeque<>())
                            .addLast(teardownId);
                }
            }

            if (notifyTrialEndId != null) {
                lastSequentialExecId = notifyTrialEndId;
            } else if (!trialOperativeStepIds.isEmpty()) {
                lastSequentialExecId = trialOperativeStepIds.getLast();
            }

            if (!eagerTeardownStepForElement.isEmpty()) {
                for (String tid : eagerTeardownStepForElement.values()) {
                    if (!eagerTeardownDependedUpon.contains(tid)) {
                        latestStepPerTrial.add(tid);
                    }
                }
            } else if (notifyTrialEndId != null) {
                latestStepPerTrial.add(notifyTrialEndId);
            } else if (!trialOperativeStepIds.isEmpty()) {
                latestStepPerTrial.add(trialOperativeStepIds.getLast());
            }
        }

        // Phase 3: Final teardown
        List<Element> reversedElements = new ArrayList<>(sortedElements);
        Collections.reverse(reversedElements);

        Map<String, String> finalTeardownStepForElement = new HashMap<>();
        for (Element element : reversedElements) {
            if (!currentFingerprintForElement.containsKey(element.name())) {
                continue;
            }
            if (isLifelineSubsumed(element)) continue;
            if (element.shutdownSemantics() == Element.ShutdownSemantics.COMMAND
                && trialElements.contains(element.name())) continue;

            String lastStep = lastStepForElement.get(element.name());
            if (lastStep == null) continue;

            int finalInstNum = currentInstanceNumber.getOrDefault(element.name(), 0);
            List<String> finalTeardownDeps = new ArrayList<>();
            boolean hasReverseDep = false;
            Set<String> clusterMembers = lifelineClusters.getOrDefault(element.name(), Set.of());
            for (Element other : sortedElements) {
                for (Element.Dependency dep : other.dependencies()) {
                    if (dep.target().name().equals(element.name())
                            || clusterMembers.contains(dep.target().name())) {
                        String otherTeardown = finalTeardownStepForElement.get(other.name());
                        if (otherTeardown != null && !finalTeardownDeps.contains(otherTeardown)) {
                            finalTeardownDeps.add(otherTeardown);
                            hasReverseDep = true;
                        }
                    }
                }
            }
            if (!hasReverseDep) {
                finalTeardownDeps.addAll(latestStepPerTrial);
            }

            String teardownId = "teardown_final_" + element.name() + "_" + stepIndex++;
            Map<String, Object> teardownMeta = buildBindingMeta(element, "cleanup", effectiveBindings);
            if (!clusterMembers.isEmpty()) {
                teardownMeta.put("lifeline_cluster", List.copyOf(clusterMembers));
            }
            AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                teardownId,
                element.name(),
                finalInstNum,
                true,
                finalTeardownDeps,
                Optional.empty(),
                AtomicStep.ResourceRequirements.none(),
                Optional.empty(),
                teardownMeta
            );
            steps.add(teardown);
            finalTeardownStepForElement.put(element.name(), teardownId);
        }

        context.setSteps(steps);
        context.setBarriers(barriers);
        context.put("trialElements", trialElements);
        context.recordMetric("steps_generated", steps.size());
        context.recordMetric("barriers_generated", barriers.size());
    }

    // --- Strategy-specific private helpers ---

    private int getMaxConcurrency(Element element) {
        if (element instanceof DefaultElement de) {
            return de.maxConcurrency().orElse(0);
        }
        String val = element.tags().get("max_concurrency");
        if (val == null || val.isBlank()) return 0;
        return Integer.parseInt(val);
    }

    private Map<String, Object> buildBindingMeta(Element element, String phase,
                                                  Map<String, AxisBindingSet> effectiveBindings) {
        AxisBindingSet binding = resolveBinding(element, effectiveBindings);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("binding_depth", binding.depth());
        m.put("bound_axes", List.copyOf(binding.boundAxes()));
        m.put("phase", phase);
        return m;
    }

    private Map<String, Object> buildDeployMeta(Element element, int trialIdx, String trialId,
                                                 List<String> nestingPath,
                                                 Map<String, AxisBindingSet> effectiveBindings) {
        AxisBindingSet binding = resolveBinding(element, effectiveBindings);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("binding_depth", binding.depth());
        m.put("bound_axes", List.copyOf(binding.boundAxes()));
        m.put("trial_index", trialIdx);
        m.put("trial_id", trialId);
        m.put("nesting_path", nestingPath);
        return m;
    }

    private Map<String, Object> buildBoundaryTeardownMeta(Element element, int trialIdx, String trialId,
                                                           List<String> nestingPath,
                                                           Map<String, AxisBindingSet> effectiveBindings) {
        AxisBindingSet binding = resolveBinding(element, effectiveBindings);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("binding_depth", binding.depth());
        m.put("bound_axes", List.copyOf(binding.boundAxes()));
        m.put("reason", "group_boundary");
        m.put("trial_index", trialIdx);
        m.put("trial_id", trialId);
        m.put("nesting_path", nestingPath);
        return m;
    }

    private Map<String, Object> buildConfiguration(Element element, Trial trial,
                                                    List<CompilationContext.ElementInstance> allInstances) {
        Map<String, Object> config = new HashMap<>(element.configuration());
        for (var param : element.parameters()) {
            trial.assignment(param.name()).ifPresent(value ->
                config.put(param.name(), value.value())
            );
        }
        return config;
    }

    private Optional<CompilationContext.ElementInstance> findInstanceForTrial(
            Element element, Trial trial, List<CompilationContext.ElementInstance> allInstances) {
        return allInstances.stream()
            .filter(inst -> inst.element().name().equals(element.name()))
            .filter(inst -> inst.trials().contains(trial))
            .findFirst();
    }

    /// Deploys a single element, updating step tracking maps and emitting
    /// health-check barriers as needed. Returns the updated step index.
    ///
    /// @param extraDeps additional dependencies to add to the deploy step (e.g. NOTIFY_TRIAL_START)
    /// @param trialElementNames names of trial elements, used for transitive dep pruning
    /// @param effectiveBindings resolved axis bindings
    /// @param dedicatedDependents reverse DEDICATED dependency map
    private int deployBoundElement(
            Element element, int trialIdx, Trial trial, TestPlan plan,
            List<CompilationContext.ElementInstance> allInstances,
            Map<String, String> lastStepForElement,
            Map<String, Integer> nextInstanceNumber,
            Map<String, Integer> currentInstanceNumber,
            Map<String, Deque<String>> concurrencyWindows,
            Map<String, Map<Integer, List<String>>> groupNestingPaths,
            List<AtomicStep> steps, List<Barrier> barriers,
            int stepIndex, boolean explicitTransitiveDeps,
            Set<String> parallelElements,
            List<String> extraDeps, List<String> trialElementNames,
            Map<String, AxisBindingSet> effectiveBindings,
            Map<String, List<Element>> dedicatedDependents) {

        int instNum = nextInstanceNumber.getOrDefault(element.name(), 0);
        nextInstanceNumber.put(element.name(), instNum + 1);
        currentInstanceNumber.put(element.name(), instNum);

        List<String> deployDeps = computeDependencies(element, lastStepForElement);

        if (!parallelElements.contains(element.name())) {
            String ownLastStep = lastStepForElement.get(element.name());
            if (ownLastStep != null && !deployDeps.contains(ownLastStep)) {
                deployDeps.add(ownLastStep);
            }
        }

        for (String extra : extraDeps) {
            if (!deployDeps.contains(extra)) {
                deployDeps.add(extra);
            }
        }

        if (!explicitTransitiveDeps && !extraDeps.isEmpty()) {
            Set<String> keep = new HashSet<>(extraDeps);
            if (!parallelElements.contains(element.name())) {
                String ownLast = lastStepForElement.get(element.name());
                if (ownLast != null) keep.add(ownLast);
            }
            Set<String> trialDepTargets = new HashSet<>();
            for (Element.Dependency dep : element.dependencies()) {
                if (trialElementNames.contains(dep.target().name())) {
                    String depStep = lastStepForElement.get(dep.target().name());
                    if (depStep != null) trialDepTargets.add(depStep);
                }
            }
            keep.addAll(trialDepTargets);
            deployDeps.removeIf(dep -> !keep.contains(dep));
        }

        int maxConc = getMaxConcurrency(element);
        if (maxConc > 0) {
            Deque<String> window = concurrencyWindows
                    .computeIfAbsent(element.name(), k -> new ArrayDeque<>());
            if (window.size() >= maxConc) {
                String oldestStep = window.pollFirst();
                if (!deployDeps.contains(oldestStep)) {
                    deployDeps.add(oldestStep);
                }
            }
        }

        Map<String, Object> config = buildConfiguration(element, trial, allInstances);

        List<String> deployNesting = groupNestingPaths
                .getOrDefault(element.name(), Map.of())
                .getOrDefault(trialIdx, List.of());
        String deployId = "deploy_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
        AtomicStep.DeployElement deploy = new AtomicStep.DeployElement(
            deployId,
            element.name(),
            instNum,
            config,
            deployDeps,
            Optional.empty(),
            AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(),
            buildDeployMeta(element, trialIdx, trial.id(), deployNesting, effectiveBindings)
        );
        steps.add(deploy);
        lastStepForElement.put(element.name(), deployId);

        if (element.healthCheck().isPresent()) {
            String readyBarrierId = "barrier_ready_" + element.name() + "_t" + trialIdx;
            String readyBarrierStepId = "barrier_ready_step_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
            AtomicStep.BarrierSync readyBarrierStep = new AtomicStep.BarrierSync(
                readyBarrierStepId,
                readyBarrierId,
                List.of(deployId),
                Optional.empty(),
                AtomicStep.ResourceRequirements.none(),
                Optional.empty(),
                buildDeployMeta(element, trialIdx, trial.id(), deployNesting, effectiveBindings)
            );
            steps.add(readyBarrierStep);
            lastStepForElement.put(element.name(), readyBarrierStepId);

            barriers.add(new DefaultBarrier(
                readyBarrierId,
                Barrier.BarrierType.ELEMENT_READY,
                element.name() + " ready after redeploy at trial " + trialIdx,
                List.of(deployId),
                List.of(),
                null,
                Barrier.TimeoutAction.FAIL_FAST,
                buildDeployMeta(element, trialIdx, trial.id(), deployNesting, effectiveBindings)
            ));
        }

        if (maxConc > 0) {
            concurrencyWindows
                    .computeIfAbsent(element.name(), k -> new ArrayDeque<>())
                    .addLast(deployId);
        }

        return stepIndex;
    }
}
