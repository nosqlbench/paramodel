package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.Value;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

///
/// Stage 5: Step Generation
///
/// Converts trials and element instances into a flat, ordered list of {@link AtomicStep}
/// records. Ported from the PlanComposer algorithm in hyperplane-study.
///
/// ## Three-Phase Algorithm
///
/// 1. **PER_RUN elements**: Single deploy at the start
/// 2. **Per-trial steps**: For each trial, compare Value fingerprints against previous trial.
///    If element config changed, emit teardown-then-deploy. Emit ExecuteTrial binding.
///    Emit BarrierSync at trial boundaries.
/// 3. **Final teardown**: Reverse topological order
///
public class StepGenerationStage implements CompilationStage {
    public StepGenerationStage() {}

    @Override
    public String name() {
        return "StepGeneration";
    }

    @Override
    public void execute(CompilationContext context) {
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

        // Topological sort elements
        List<Element> sortedElements = topologicalSort(elements);

        List<AtomicStep> steps = new ArrayList<>();
        List<Barrier> barriers = new ArrayList<>();

        // Track last step ID per element (for dependency chaining)
        Map<String, String> lastStepForElement = new HashMap<>();
        // Track current fingerprint per element (for change detection)
        Map<String, String> currentFingerprintForElement = new HashMap<>();
        // Track per-element monotonic instance counter (never resets)
        Map<String, Integer> nextInstanceNumber = new HashMap<>();
        // Track currently active instance number per element
        Map<String, Integer> currentInstanceNumber = new HashMap<>();

        int stepIndex = 0;

        // Phase 1: Deploy PER_RUN elements
        for (Element element : sortedElements) {
            if (isGlobalScope(element, allInstances)) {
                List<String> deps = computeDependencies(element, lastStepForElement);
                Map<String, Object> config = buildConfiguration(element, trials.getFirst(), allInstances);

                int instNum = nextInstanceNumber.getOrDefault(element.name(), 0);
                nextInstanceNumber.put(element.name(), instNum + 1);
                currentInstanceNumber.put(element.name(), instNum);

                String stepId = "deploy_" + element.name() + "_" + stepIndex++;
                AtomicStep.DeployElement deployStep = new AtomicStep.DeployElement(
                    stepId,
                    element.name(),
                    instNum,
                    config,
                    List.of(),
                    deps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.minimal(),
                    Optional.empty(),
                    Map.of("scope", "PER_RUN", "phase", "setup")
                );
                steps.add(deployStep);
                lastStepForElement.put(element.name(), stepId);
            }
        }

        // Phase 2: Per-trial steps
        for (int trialIdx = 0; trialIdx < trials.size(); trialIdx++) {
            Trial trial = trials.get(trialIdx);
            List<String> trialDeployStepIds = new ArrayList<>();

            for (Element element : sortedElements) {
                if (isGlobalScope(element, allInstances)) {
                    continue; // Already deployed in phase 1
                }

                // Compute fingerprint for this element in this trial
                String currentFingerprint = computeElementFingerprint(element, trial, plan);
                String previousFingerprint = currentFingerprintForElement.get(element.name());

                boolean needsDeploy = (previousFingerprint == null)
                    || !currentFingerprint.equals(previousFingerprint);

                if (needsDeploy) {
                    // Teardown previous if replacing
                    if (previousFingerprint != null) {
                        List<String> teardownDeps = List.of(lastStepForElement.getOrDefault(element.name(), ""));
                        teardownDeps = teardownDeps.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());

                        int teardownInstNum = currentInstanceNumber.get(element.name());
                        String teardownId = "teardown_" + element.name() + "_" + stepIndex++;
                        AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                            teardownId,
                            element.name(),
                            teardownInstNum,
                            false,
                            teardownDeps,
                            Optional.empty(),
                            AtomicStep.ResourceRequirements.none(),
                            Optional.empty(),
                            Map.of("reason", "parameter_change", "trial_index", trialIdx)
                        );
                        steps.add(teardown);
                        lastStepForElement.put(element.name(), teardownId);
                    }

                    // Deploy with new config
                    int instNum = nextInstanceNumber.getOrDefault(element.name(), 0);
                    nextInstanceNumber.put(element.name(), instNum + 1);
                    currentInstanceNumber.put(element.name(), instNum);

                    List<String> deployDeps = computeDependencies(element, lastStepForElement);
                    Map<String, Object> config = buildConfiguration(element, trial, allInstances);

                    String deployId = "deploy_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                    AtomicStep.DeployElement deploy = new AtomicStep.DeployElement(
                        deployId,
                        element.name(),
                        instNum,
                        config,
                        List.of(),
                        deployDeps,
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.minimal(),
                        Optional.empty(),
                        Map.of("scope", "PER_TRIAL", "trial_index", trialIdx)
                    );
                    steps.add(deploy);
                    lastStepForElement.put(element.name(), deployId);
                    trialDeployStepIds.add(deployId);

                    currentFingerprintForElement.put(element.name(), currentFingerprint);
                } else {
                    // Reuse: no new steps needed for this element
                    String existingId = lastStepForElement.get(element.name());
                    if (existingId != null) {
                        trialDeployStepIds.add(existingId);
                    }
                }
            }

            // Emit ExecuteTrial step
            Map<String, String> elementBindings = new HashMap<>();
            for (Element element : elements) {
                Optional<CompilationContext.ElementInstance> inst = findInstanceForTrial(element, trial, allInstances);
                inst.ifPresent(i -> elementBindings.put(element.name(), i.instanceId()));
            }

            // ExecuteTrial depends on all deploy steps for this trial
            List<String> execDeps = new ArrayList<>();
            for (Element element : elements) {
                String lastStep = lastStepForElement.get(element.name());
                if (lastStep != null) {
                    execDeps.add(lastStep);
                }
            }

            String execId = "exec_trial_" + trialIdx + "_" + stepIndex++;
            AtomicStep.ExecuteTrial executeTrial = new AtomicStep.ExecuteTrial(
                execId,
                trial.id(),
                elementBindings,
                execDeps,
                Optional.empty(),
                AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(),
                Map.of("trial_index", trialIdx)
            );
            steps.add(executeTrial);
            lastStepForElement.put("__trial_" + trialIdx, execId);

            // Emit BarrierSync at trial boundary
            String barrierId = "barrier_trial_" + trialIdx;
            String barrierStepId = "barrier_step_" + trialIdx + "_" + stepIndex++;
            AtomicStep.BarrierSync barrierStep = new AtomicStep.BarrierSync(
                barrierStepId,
                barrierId,
                List.of(execId),
                Optional.of(Duration.ZERO),
                AtomicStep.ResourceRequirements.none(),
                Optional.empty(),
                Map.of("trial_index", trialIdx)
            );
            steps.add(barrierStep);

            DefaultBarrier barrier = new DefaultBarrier(
                barrierId,
                Barrier.BarrierType.TRIAL_BATCH,
                "Trial " + trialIdx + " completion barrier",
                List.of(execId),
                List.of(),
                null,
                Barrier.TimeoutAction.FAIL_FAST,
                Map.of("trial_index", trialIdx)
            );
            barriers.add(barrier);
        }

        // Phase 3: Final teardown in reverse topological order
        List<Element> reversedElements = new ArrayList<>(sortedElements);
        Collections.reverse(reversedElements);

        for (Element element : reversedElements) {
            String lastStep = lastStepForElement.get(element.name());
            if (lastStep == null) {
                continue;
            }

            // Collect all execution step dependencies
            List<String> teardownDeps = new ArrayList<>();
            teardownDeps.add(lastStep);

            // Also depend on all trial execution steps that used this element
            for (int i = 0; i < trials.size(); i++) {
                String trialExecStep = lastStepForElement.get("__trial_" + i);
                if (trialExecStep != null) {
                    teardownDeps.add(trialExecStep);
                }
            }

            int finalInstNum = currentInstanceNumber.getOrDefault(element.name(), 0);
            String teardownId = "teardown_final_" + element.name() + "_" + stepIndex++;
            AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                teardownId,
                element.name(),
                finalInstNum,
                true,
                teardownDeps,
                Optional.empty(),
                AtomicStep.ResourceRequirements.none(),
                Optional.empty(),
                Map.of("phase", "cleanup")
            );
            steps.add(teardown);
        }

        context.setSteps(steps);
        context.setBarriers(barriers);
        context.recordMetric("steps_generated", steps.size());
        context.recordMetric("barriers_generated", barriers.size());
    }

    private boolean isGlobalScope(Element element, List<CompilationContext.ElementInstance> allInstances) {
        // An element is global-scope if it has a single instance covering all trials
        // (i.e., its scope description is "global")
        return allInstances.stream()
            .filter(inst -> inst.element().name().equals(element.name()))
            .anyMatch(inst -> inst.scopeDescription().equals("global"));
    }

    private String computeElementFingerprint(Element element, Trial trial, TestPlan plan) {
        // Concatenate sorted fingerprints of all parameter values for this element
        TreeMap<String, String> sortedFingerprints = new TreeMap<>();

        // Check formal parameters (existing)
        for (var param : element.parameters()) {
            trial.assignment(param.name()).ifPresent(value ->
                sortedFingerprints.put(param.name(), value.fingerprint())
            );
        }

        // Check axes targeting this element (via targetElement tag)
        for (var axis : plan.axes()) {
            if (axis.targetElement().map(t -> t.equals(element.name())).orElse(false)) {
                trial.assignment(axis.name()).ifPresent(value ->
                    sortedFingerprints.put(axis.name(), value.fingerprint()));
            }
        }

        if (sortedFingerprints.isEmpty()) {
            // No varying parameters for this element.
            // If the element has PER_TRIAL scope, it must be redeployed for
            // every trial even without parameter changes — use the trial id
            // to produce a unique fingerprint per trial.
            if (element.instancingScope()
                    .map(s -> s == Element.InstancingScope.PER_TRIAL)
                    .orElse(false)) {
                return "per_trial:" + element.name() + ":" + trial.id();
            }
            return "static:" + element.name();
        }
        return String.join("|", sortedFingerprints.values());
    }

    private List<String> computeDependencies(Element element, Map<String, String> lastStepForElement) {
        List<String> deps = new ArrayList<>();
        for (Element dep : element.dependencies()) {
            String lastStep = lastStepForElement.get(dep.name());
            if (lastStep != null) {
                deps.add(lastStep);
            }
        }
        return deps;
    }

    private Map<String, Object> buildConfiguration(Element element, Trial trial, List<CompilationContext.ElementInstance> allInstances) {
        Map<String, Object> config = new HashMap<>();
        for (var param : element.parameters()) {
            trial.assignment(param.name()).ifPresent(value ->
                config.put(param.name(), value.value())
            );
        }
        return config;
    }

    private Optional<CompilationContext.ElementInstance> findInstanceForTrial(
        Element element, Trial trial, List<CompilationContext.ElementInstance> allInstances
    ) {
        return allInstances.stream()
            .filter(inst -> inst.element().name().equals(element.name()))
            .filter(inst -> inst.trials().contains(trial))
            .findFirst();
    }

    private List<Element> topologicalSort(List<Element> elements) {
        Map<String, Element> byName = new LinkedHashMap<>();
        for (Element e : elements) byName.put(e.name(), e);

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (Element e : elements) {
            inDegree.putIfAbsent(e.name(), 0);
            for (Element dep : e.dependencies()) {
                adj.computeIfAbsent(dep.name(), k -> new ArrayList<>()).add(e.name());
                inDegree.merge(e.name(), 1, Integer::sum);
                inDegree.putIfAbsent(dep.name(), 0);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<Element> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            Element e = byName.get(name);
            if (e != null) result.add(e);

            for (String dependent : adj.getOrDefault(name, List.of())) {
                inDegree.put(dependent, inDegree.get(dependent) - 1);
                if (inDegree.get(dependent) == 0) queue.add(dependent);
            }
        }
        return result;
    }
}
