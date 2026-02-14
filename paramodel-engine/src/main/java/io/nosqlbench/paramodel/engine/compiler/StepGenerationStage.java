package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.plan.DefaultElement;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.Barrier;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.time.Duration;
import java.util.*;

///
/// Stage 5: Step Generation
///
/// Converts trials and element instances into a flat, ordered list of {@link AtomicStep}
/// records.
///
/// ## Three-Phase Algorithm
///
/// 1. **PER_RUN elements** (outermost group): Single deploy at the start, with
///    an {@code ELEMENT_READY} barrier after each deploy for downstream coordination.
/// 2. **Per-trial steps**: Non-global elements are classified into two categories:
///    - **PER_GROUP**: Elements that vary by axis. A group is a contiguous block
///      of trials with constant configuration (same fingerprint). The element
///      deploys at group start, persists across all trials in the group, and
///      tears down at the group boundary when the fingerprint changes. Group
///      boundaries produce {@code ELEMENT_SCOPE_END} barriers; redeploys produce
///      {@code ELEMENT_READY} barriers.
///    - **PER_TRIAL (independent)**: Elements with explicit {@code PER_TRIAL}
///      instancing scope get a fresh instance per trial. Trials are independent
///      (no cross-trial dependencies unless {@code max_concurrency} is set) and
///      instances are eagerly torn down in LIFO order after each trial's execution.
/// 3. **Final teardown**: Reverse topological order for PER_RUN and PER_GROUP
///    elements only (PER_TRIAL elements are already torn down in phase 2).
///    An {@code ELEMENT_SCOPE_END} barrier is emitted before each final teardown.
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

        // Track last step ID per element (for dependency chaining — PER_RUN and recycling only)
        Map<String, String> lastStepForElement = new HashMap<>();
        // Track current fingerprint per element (for change detection)
        Map<String, String> currentFingerprintForElement = new HashMap<>();
        // Track per-element monotonic instance counter (never resets)
        Map<String, Integer> nextInstanceNumber = new HashMap<>();
        // Track currently active instance number per element
        Map<String, Integer> currentInstanceNumber = new HashMap<>();

        // Sliding window for per-element max concurrency enforcement
        Map<String, Deque<String>> concurrencyWindows = new HashMap<>();

        // Classify non-global elements into PER_TRIAL (independent) vs PER_GROUP
        List<Element> perTrialElements = new ArrayList<>();
        List<Element> perGroupElements = new ArrayList<>();
        for (Element element : sortedElements) {
            if (isGlobalScope(element, allInstances)) continue;
            if (isPerTrialScope(element)) {
                perTrialElements.add(element);
            } else {
                perGroupElements.add(element);
            }
        }
        // Reverse topo order for LIFO teardowns
        List<Element> perTrialReversed = new ArrayList<>(perTrialElements);
        Collections.reverse(perTrialReversed);

        int stepIndex = 0;

        // Phase 1: Deploy PER_RUN elements (outermost group)
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
                    deps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.minimal(),
                    Optional.empty(),
                    Map.of("scope", "PER_RUN", "phase", "setup")
                );
                steps.add(deployStep);
                lastStepForElement.put(element.name(), stepId);

                // ELEMENT_READY barrier: downstream steps can await this
                // element's readiness via OperationalStateObservable
                if (element.healthCheck().isPresent()) {
                    String readyBarrierId = "barrier_ready_" + element.name();
                    String readyBarrierStepId = "barrier_ready_step_" + element.name() + "_" + stepIndex++;
                    AtomicStep.BarrierSync readyBarrierStep = new AtomicStep.BarrierSync(
                        readyBarrierStepId,
                        readyBarrierId,
                        List.of(stepId),
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.none(),
                        Optional.empty(),
                        Map.of("element", element.name(), "scope", "PER_RUN")
                    );
                    steps.add(readyBarrierStep);
                    lastStepForElement.put(element.name(), readyBarrierStepId);

                    barriers.add(new DefaultBarrier(
                        readyBarrierId,
                        Barrier.BarrierType.ELEMENT_READY,
                        element.name() + " ready after deploy",
                        List.of(stepId),
                        List.of(),
                        null,
                        Barrier.TimeoutAction.FAIL_FAST,
                        Map.of("element", element.name(), "scope", "PER_RUN")
                    ));
                }
            }
        }

        // Phase 2: Per-trial steps
        // Track last sequential exec step for recycling teardown dependencies
        String lastSequentialExecId = null;
        // Collect all exec step IDs for Phase 3 final teardowns
        List<String> allExecStepIds = new ArrayList<>();

        for (int trialIdx = 0; trialIdx < trials.size(); trialIdx++) {
            Trial trial = trials.get(trialIdx);

            // === 2a: PER_GROUP elements — group boundary detection ===
            // Identify which PER_GROUP elements need teardown and/or deploy
            // based on configuration fingerprint changes at group boundaries.
            List<Element> toTeardown = new ArrayList<>();
            List<Element> toDeploy = new ArrayList<>();

            for (Element element : perGroupElements) {
                String currentFingerprint = computeElementFingerprint(element, trial, plan);
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

            // Teardown PER_GROUP elements at group boundaries in REVERSE
            // topological (LIFO) order. Each teardown depends on the previous
            // trial's execution step so it cannot race with an in-progress trial.
            // An ELEMENT_SCOPE_END barrier is emitted for each group boundary
            // teardown, signaling that all trials using this element instance
            // have completed.
            List<Element> teardownReversed = new ArrayList<>(toTeardown);
            Collections.reverse(teardownReversed);
            for (Element element : teardownReversed) {
                // ELEMENT_SCOPE_END barrier before teardown
                String scopeEndBarrierId = "barrier_scope_end_" + element.name() + "_t" + trialIdx;
                String scopeEndStepId = "barrier_scope_end_step_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                List<String> scopeEndDeps = new ArrayList<>();
                if (lastSequentialExecId != null) {
                    scopeEndDeps.add(lastSequentialExecId);
                } else {
                    String lastStep = lastStepForElement.get(element.name());
                    if (lastStep != null) scopeEndDeps.add(lastStep);
                }
                AtomicStep.BarrierSync scopeEndStep = new AtomicStep.BarrierSync(
                    scopeEndStepId,
                    scopeEndBarrierId,
                    scopeEndDeps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    Map.of("element", element.name(), "trial_index", trialIdx)
                );
                steps.add(scopeEndStep);
                barriers.add(new DefaultBarrier(
                    scopeEndBarrierId,
                    Barrier.BarrierType.ELEMENT_SCOPE_END,
                    element.name() + " group scope ended at trial " + trialIdx,
                    scopeEndDeps,
                    List.of(),
                    null,
                    Barrier.TimeoutAction.FAIL_FAST,
                    Map.of("element", element.name(), "trial_index", trialIdx)
                ));

                int teardownInstNum = currentInstanceNumber.get(element.name());
                String teardownId = "teardown_" + element.name() + "_" + stepIndex++;
                AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                    teardownId,
                    element.name(),
                    teardownInstNum,
                    false,
                    List.of(scopeEndStepId),
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    Map.of("reason", "group_boundary", "trial_index", trialIdx)
                );
                steps.add(teardown);
                lastStepForElement.put(element.name(), teardownId);
            }

            // Deploy PER_GROUP elements in FORWARD topological order
            for (Element element : toDeploy) {
                int instNum = nextInstanceNumber.getOrDefault(element.name(), 0);
                nextInstanceNumber.put(element.name(), instNum + 1);
                currentInstanceNumber.put(element.name(), instNum);

                List<String> deployDeps = computeDependencies(element, lastStepForElement);

                // Ensure deploy waits for this element's own teardown (if just recycled)
                String ownLastStep = lastStepForElement.get(element.name());
                if (ownLastStep != null && !deployDeps.contains(ownLastStep)) {
                    deployDeps.add(ownLastStep);
                }

                // Enforce max concurrency sliding window
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
                    Map.of("scope", "PER_GROUP", "trial_index", trialIdx)
                );
                steps.add(deploy);
                lastStepForElement.put(element.name(), deployId);

                // ELEMENT_READY barrier after PER_GROUP deploy
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
                        Map.of("element", element.name(), "scope", "PER_GROUP", "trial_index", trialIdx)
                    );
                    steps.add(readyBarrierStep);
                    lastStepForElement.put(element.name(), readyBarrierStepId);

                    barriers.add(new DefaultBarrier(
                        readyBarrierId,
                        Barrier.BarrierType.ELEMENT_READY,
                        element.name() + " ready after group redeploy at trial " + trialIdx,
                        List.of(deployId),
                        List.of(),
                        null,
                        Barrier.TimeoutAction.FAIL_FAST,
                        Map.of("element", element.name(), "scope", "PER_GROUP", "trial_index", trialIdx)
                    ));
                }

                if (maxConc > 0) {
                    concurrencyWindows
                            .computeIfAbsent(element.name(), k -> new ArrayDeque<>())
                            .addLast(deployId);
                }
            }

            // === 2b: Deploy PER_TRIAL elements (independent per trial) ===
            // Build a merged dependency map: PER_RUN + PER_GROUP steps from
            // lastStepForElement, plus this trial's PER_TRIAL deploys.
            Map<String, String> trialDeployMap = new HashMap<>();
            Map<String, String> mergedStepMap = new HashMap<>(lastStepForElement);

            for (Element element : perTrialElements) {
                int instNum = nextInstanceNumber.getOrDefault(element.name(), 0);
                nextInstanceNumber.put(element.name(), instNum + 1);
                currentInstanceNumber.put(element.name(), instNum);

                List<String> deployDeps = computeDependencies(element, mergedStepMap);

                // Enforce max concurrency: limit concurrent PER_TRIAL instances.
                // The window tracks teardown step IDs so a new deploy waits for
                // the oldest instance to finish tearing down.
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
                    Map.of("scope", "PER_TRIAL", "trial_index", trialIdx)
                );
                steps.add(deploy);
                trialDeployMap.put(element.name(), deployId);
                mergedStepMap.put(element.name(), deployId);
            }

            // === 2c: ExecuteTrial ===
            Map<String, String> elementBindings = new HashMap<>();
            for (Element element : elements) {
                Optional<CompilationContext.ElementInstance> inst = findInstanceForTrial(element, trial, allInstances);
                inst.ifPresent(i -> elementBindings.put(element.name(), i.instanceId()));
            }

            // Depend on PER_RUN + recycling deploy steps
            List<String> execDeps = new ArrayList<>();
            for (Element element : elements) {
                if (isPerTrialScope(element)) continue;
                String lastStep = lastStepForElement.get(element.name());
                if (lastStep != null) {
                    execDeps.add(lastStep);
                }
            }
            // Depend on this trial's PER_TRIAL deploy steps
            execDeps.addAll(trialDeployMap.values());

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
            lastSequentialExecId = execId;
            allExecStepIds.add(execId);
            lastStepForElement.put("__trial_" + trialIdx, execId);

            // === 2d: Eager teardown of PER_TRIAL elements in REVERSE topo (LIFO) order ===
            for (Element element : perTrialReversed) {
                int teardownInstNum = currentInstanceNumber.get(element.name());
                String teardownId = "teardown_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                    teardownId,
                    element.name(),
                    teardownInstNum,
                    false,
                    List.of(execId),
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    Map.of("reason", "per_trial_eager", "trial_index", trialIdx)
                );
                steps.add(teardown);

                // Track teardown in concurrency window so future deploys respect the limit
                int maxConc = getMaxConcurrency(element);
                if (maxConc > 0) {
                    concurrencyWindows
                            .computeIfAbsent(element.name(), k -> new ArrayDeque<>())
                            .addLast(teardownId);
                }
            }

            // === 2e: Barrier at trial boundary ===
            // Only emit barriers when PER_GROUP elements exist: the barrier
            // marks the trial boundary that the next trial's group-boundary
            // teardown-then-redeploy sequence synchronises against. When all
            // non-global elements are PER_TRIAL (independent), each trial is
            // fully self-contained and a trailing barrier would be a dangling leaf.
            if (!perGroupElements.isEmpty()) {
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
        }

        // Phase 3: Final teardown in reverse topological order.
        // PER_TRIAL elements are already torn down eagerly in phase 2.
        // An ELEMENT_SCOPE_END barrier is emitted before each final teardown
        // to signal that all trials using this element have completed.
        List<Element> reversedElements = new ArrayList<>(sortedElements);
        Collections.reverse(reversedElements);

        for (Element element : reversedElements) {
            if (isPerTrialScope(element)) {
                continue; // Already torn down eagerly after each trial
            }

            String lastStep = lastStepForElement.get(element.name());
            if (lastStep == null) {
                continue;
            }

            // Depend on the element's own last step plus all trial execution
            // steps. PER_RUN and PER_GROUP elements may be used by parallel
            // PER_TRIAL trials so they must wait for every execution to complete.
            List<String> scopeEndDeps = new ArrayList<>();
            scopeEndDeps.add(lastStep);
            for (String execStepId : allExecStepIds) {
                if (!scopeEndDeps.contains(execStepId)) {
                    scopeEndDeps.add(execStepId);
                }
            }

            // ELEMENT_SCOPE_END barrier before final teardown
            String scopeEndBarrierId = "barrier_scope_end_final_" + element.name();
            String scopeEndStepId = "barrier_scope_end_final_step_" + element.name() + "_" + stepIndex++;
            AtomicStep.BarrierSync scopeEndStep = new AtomicStep.BarrierSync(
                scopeEndStepId,
                scopeEndBarrierId,
                scopeEndDeps,
                Optional.empty(),
                AtomicStep.ResourceRequirements.none(),
                Optional.empty(),
                Map.of("element", element.name(), "phase", "cleanup")
            );
            steps.add(scopeEndStep);
            barriers.add(new DefaultBarrier(
                scopeEndBarrierId,
                Barrier.BarrierType.ELEMENT_SCOPE_END,
                element.name() + " final scope ended",
                scopeEndDeps,
                List.of(),
                null,
                Barrier.TimeoutAction.FAIL_FAST,
                Map.of("element", element.name(), "phase", "cleanup")
            ));

            int finalInstNum = currentInstanceNumber.getOrDefault(element.name(), 0);
            String teardownId = "teardown_final_" + element.name() + "_" + stepIndex++;
            AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                teardownId,
                element.name(),
                finalInstNum,
                true,
                List.of(scopeEndStepId),
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

    /// Returns the max concurrency limit for the element, or 0 if unlimited.
    ///
    /// Reads from the {@code max_concurrency} tag set by the composition
    /// pipeline. If the element is a {@link DefaultElement}, the typed
    /// accessor is used; otherwise falls back to tag lookup.
    private int getMaxConcurrency(Element element) {
        if (element instanceof DefaultElement de) {
            return de.maxConcurrency().orElse(0);
        }
        String val = element.tags().get("max_concurrency");
        if (val == null || val.isBlank()) return 0;
        return Integer.parseInt(val);
    }

    /// Returns {@code true} if the element has **explicitly** declared
    /// {@link Element.InstancingScope#PER_TRIAL} scope, meaning it gets
    /// a fresh independent instance for every trial with no cross-trial
    /// dependencies.
    ///
    /// Elements with PER_GROUP scope (inferred from axis targeting) use
    /// fingerprint-based group lifecycle instead — they persist for a
    /// contiguous block of trials with constant configuration.
    private boolean isPerTrialScope(Element element) {
        if (element instanceof DefaultElement de) {
            return de.instancingScope()
                .map(s -> s == Element.InstancingScope.PER_TRIAL)
                .orElse(false)
                && de.isScopeExplicit();
        }
        // Non-DefaultElement (e.g. MockElement): treat PER_TRIAL as explicit
        return element.instancingScope()
            .map(s -> s == Element.InstancingScope.PER_TRIAL)
            .orElse(false);
    }

    private boolean isGlobalScope(Element element, List<CompilationContext.ElementInstance> allInstances) {
        // An element is global-scope if it has a single instance covering all trials
        // (i.e., its scope description is "global")
        return allInstances.stream()
            .filter(inst -> inst.element().name().equals(element.name()))
            .anyMatch(inst -> inst.scopeDescription().equals("global"));
    }

    /// Computes a configuration fingerprint for the given element in the given
    /// trial. PER_GROUP elements use this fingerprint for group-boundary
    /// detection: when the fingerprint changes between adjacent trials, a
    /// teardown-then-redeploy cycle is triggered.
    ///
    /// The fingerprint incorporates:
    /// 1. The element's own formal parameter values from trial assignments
    /// 2. Axis values targeting this element
    /// 3. Dependency fingerprints — so that when a dependency's configuration
    ///    changes at a group boundary, this element is also redeployed (it
    ///    likely needs to reconnect/rebind to the new dependency instance)
    ///
    /// @param element the element to fingerprint
    /// @param trial the trial providing parameter assignments
    /// @param plan the test plan (for axis metadata)
    /// @return a string fingerprint for group-boundary comparison
    private String computeElementFingerprint(Element element, Trial trial, TestPlan plan) {
        // Concatenate sorted fingerprints of all parameter values for this element
        TreeMap<String, String> sortedFingerprints = new TreeMap<>();

        // Check formal parameters (existing)
        for (var param : element.parameters()) {
            trial.assignment(param.name()).ifPresent(value ->
                sortedFingerprints.put(param.name(), value.fingerprint())
            );
        }

        // Check axes targeting this element (via targetElement tag).
        // Trial assignment keys use the "elementId.parameterName" format,
        // so look up the qualified key when the axis targets a specific element.
        for (var axis : plan.axes()) {
            if (axis.targetElement().map(t -> t.equals(element.name())).orElse(false)) {
                String qualifiedKey = element.name() + "." + axis.name();
                trial.assignment(qualifiedKey).ifPresent(value ->
                    sortedFingerprints.put(axis.name(), value.fingerprint()));
            }
        }

        // Include dependency fingerprints so that when a dependency's
        // configuration changes at a group boundary, this element is also
        // redeployed. The dependency graph is acyclic (validated upstream),
        // so recursion terminates.
        for (Element dep : element.dependencies()) {
            String depFingerprint = computeElementFingerprint(dep, trial, plan);
            sortedFingerprints.put("__dep:" + dep.name(), depFingerprint);
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
