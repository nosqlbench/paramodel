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
///    an {@code ELEMENT_READY} barrier after each deploy when the element has
///    a health check, providing a synchronization point for downstream steps.
/// 2. **Per-trial steps**: Non-global elements are classified into two categories:
///    - **PER_GROUP**: Elements that vary by axis. A group is a contiguous block
///      of trials with constant configuration (same fingerprint). The element
///      deploys at group start, persists across all trials in the group, and
///      tears down at the group boundary when the fingerprint changes. An
///      {@code ELEMENT_SCOPE_END} barrier at each group boundary synchronizes
///      outgoing work before teardown. Redeploys produce {@code ELEMENT_READY}
///      barriers when a health check is present.
///    - **PER_TRIAL (independent)**: Elements with explicit {@code PER_TRIAL}
///      instancing scope get a fresh instance per trial. Trials are independent
///      (no cross-trial dependencies unless {@code max_concurrency} is set) and
///      instances are eagerly torn down in LIFO order after each trial's execution.
/// 3. **Final teardown**: An {@code ELEMENT_SCOPE_END} barrier collects all trial
///    execution completions into a single synchronization point. Final teardowns
///    in reverse topological order for PER_RUN and PER_GROUP elements depend on
///    this barrier (PER_TRIAL elements are already torn down in phase 2).
///
/// ## Step Metadata
///
/// Every step carries a metadata map with:
/// - {@code scope}: {@code PER_RUN}, {@code PER_GROUP}, or {@code PER_TRIAL}
/// - {@code trial_index}: ordinal position in the trial list (per-trial steps)
/// - {@code trial_id}: the trial's unique identifier (per-trial steps)
/// - {@code nesting_path}: ordered list of axis-value strings for nested graph
///   visualization. PER_RUN → empty, PER_TRIAL → full axis path (leaf),
///   PER_GROUP → shared axis-value prefix for the element's group.
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

        // --- Precompute nesting paths for graph visualization ---
        //
        // Each step carries a `nesting_path` metadata entry: an ordered list of
        // axis-value strings that places the step at the correct depth in the
        // nested subgraph hierarchy.
        //
        // PER_RUN  → []               (outermost scope)
        // PER_TRIAL → full axis path  (leaf scope)
        // PER_GROUP → shared prefix   (intermediate scope, computed per group)
        //
        // For PER_GROUP elements, the nesting path is determined by which
        // axes the element is sensitive to.  The shared prefix across all
        // trials in a group is the axis-value path up to and including the
        // last axis that varies for this element.
        List<? extends io.nosqlbench.paramodel.plan.Axis<?>> planAxes = plan.axes();

        // Precompute full axis path for each trial
        Map<Integer, List<String>> trialFullPaths = new HashMap<>();
        for (int i = 0; i < trials.size(); i++) {
            Trial t = trials.get(i);
            trialFullPaths.put(i, computeTrialAxisPath(t, planAxes));
        }

        // For each PER_GROUP element, precompute group ranges and nesting paths.
        // A group is a contiguous range of trials with the same element fingerprint.
        // The nesting path for a group is the shared axis-value prefix across all
        // trials in the group.
        //
        // groupNestingPaths maps: elementName → trialIndex → nesting path
        Map<String, Map<Integer, List<String>>> groupNestingPaths = new HashMap<>();
        for (Element element : perGroupElements) {
            Map<Integer, List<String>> nestingByTrial = new HashMap<>();
            int groupStart = 0;
            String groupFingerprint = computeElementFingerprint(element, trials.getFirst(), plan);

            for (int i = 1; i <= trials.size(); i++) {
                String fp = (i < trials.size())
                    ? computeElementFingerprint(element, trials.get(i), plan)
                    : null;

                if (fp == null || !fp.equals(groupFingerprint)) {
                    // End of group [groupStart, i).  Compute shared prefix.
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
                    // Assign this path to all trials in the group
                    for (int j = groupStart; j < i; j++) {
                        nestingByTrial.put(j, new ArrayList<>(sharedPrefix));
                    }
                    // Start next group
                    groupStart = i;
                    groupFingerprint = fp;
                }
            }
            groupNestingPaths.put(element.name(), nestingByTrial);
        }

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

            // ELEMENT_SCOPE_END barrier at group boundaries: a synchronization
            // point that all downstream teardowns depend on.  The barrier
            // depends on the previous trial's exec step (or the element's own
            // last step if no trial has executed yet), ensuring all work in the
            // outgoing scope has completed before teardown proceeds.
            String groupBarrierStepId = null;
            if (!toTeardown.isEmpty()) {
                String barrierSource;
                if (lastSequentialExecId != null) {
                    barrierSource = lastSequentialExecId;
                } else {
                    barrierSource = lastStepForElement.getOrDefault(
                        toTeardown.getFirst().name(), null);
                }
                if (barrierSource != null) {
                    String groupBarrierId = "barrier_scope_end_" + trialIdx + "_" + stepIndex;
                    groupBarrierStepId = "barrier_scope_end_step_" + trialIdx + "_" + stepIndex++;
                    // Use the PREVIOUS group's nesting path for the barrier
                    // (it synchronizes the end of the outgoing scope, not the new one)
                    int prevTrialIdx = trialIdx > 0 ? trialIdx - 1 : 0;
                    List<String> barrierNesting = groupNestingPaths
                        .getOrDefault(toTeardown.getFirst().name(), Map.of())
                        .getOrDefault(prevTrialIdx, List.of());
                    AtomicStep.BarrierSync groupBarrierStep = new AtomicStep.BarrierSync(
                        groupBarrierStepId,
                        groupBarrierId,
                        List.of(barrierSource),
                        Optional.empty(),
                        AtomicStep.ResourceRequirements.none(),
                        Optional.empty(),
                        meta("scope", "PER_GROUP", "barrierType", "ELEMENT_SCOPE_END",
                             "trial_index", trialIdx, "trial_id", trial.id(),
                             "nesting_path", barrierNesting)
                    );
                    steps.add(groupBarrierStep);

                    barriers.add(new DefaultBarrier(
                        groupBarrierId,
                        Barrier.BarrierType.ELEMENT_SCOPE_END,
                        "Group boundary at trial " + trialIdx,
                        List.of(barrierSource),
                        List.of(),
                        null,
                        Barrier.TimeoutAction.FAIL_FAST,
                        meta("scope", "PER_GROUP", "trial_index", trialIdx,
                             "trial_id", trial.id(), "nesting_path", barrierNesting)
                    ));
                }
            }

            // Teardown PER_GROUP elements at group boundaries in REVERSE
            // topological (LIFO) order.  Each teardown depends on the
            // ELEMENT_SCOPE_END barrier so it cannot race with in-progress
            // work in the outgoing scope.
            //
            // Teardowns are chained: each subsequent teardown depends on the
            // previous teardown in the LIFO sequence. This prevents a concurrent
            // executor from tearing down an upstream element (e.g. db) while a
            // downstream element (e.g. app) is still shutting down.
            List<Element> teardownReversed = new ArrayList<>(toTeardown);
            Collections.reverse(teardownReversed);
            String previousTeardownId = null;
            for (Element element : teardownReversed) {
                int teardownInstNum = currentInstanceNumber.get(element.name());
                List<String> teardownDeps = new ArrayList<>();
                // Depend on the ELEMENT_SCOPE_END barrier
                if (groupBarrierStepId != null) {
                    teardownDeps.add(groupBarrierStepId);
                } else {
                    // Fallback: depend directly on exec step if no barrier
                    if (lastSequentialExecId != null) {
                        teardownDeps.add(lastSequentialExecId);
                    } else {
                        String lastStep = lastStepForElement.get(element.name());
                        if (lastStep != null) teardownDeps.add(lastStep);
                    }
                }
                if (previousTeardownId != null) {
                    teardownDeps.add(previousTeardownId);
                }
                String teardownId = "teardown_" + element.name() + "_" + stepIndex++;
                // Teardown is for the PREVIOUS group's instance — use
                // the previous trial's nesting path to place it correctly.
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
                    meta("scope", "PER_GROUP", "reason", "group_boundary",
                         "trial_index", trialIdx, "trial_id", trial.id(),
                         "nesting_path", teardownNesting)
                );
                steps.add(teardown);
                lastStepForElement.put(element.name(), teardownId);
                previousTeardownId = teardownId;
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
                    meta("scope", "PER_GROUP", "trial_index", trialIdx,
                         "trial_id", trial.id(), "nesting_path", deployNesting)
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
                        meta("element", element.name(), "scope", "PER_GROUP",
                             "trial_index", trialIdx, "trial_id", trial.id(),
                             "nesting_path", deployNesting)
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
                        meta("element", element.name(), "scope", "PER_GROUP",
                             "trial_index", trialIdx, "trial_id", trial.id(),
                             "nesting_path", deployNesting)
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

                List<String> trialNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
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
                    meta("scope", "PER_TRIAL", "trial_index", trialIdx,
                         "trial_id", trial.id(), "nesting_path", trialNesting)
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

            List<String> execNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
            String execId = "exec_trial_" + trialIdx + "_" + stepIndex++;
            AtomicStep.ExecuteTrial executeTrial = new AtomicStep.ExecuteTrial(
                execId,
                trial.id(),
                elementBindings,
                execDeps,
                Optional.empty(),
                AtomicStep.ResourceRequirements.minimal(),
                Optional.empty(),
                meta("scope", "PER_TRIAL", "trial_index", trialIdx,
                     "trial_id", trial.id(), "nesting_path", execNesting)
            );
            steps.add(executeTrial);
            lastSequentialExecId = execId;
            allExecStepIds.add(execId);
            lastStepForElement.put("__trial_" + trialIdx, execId);

            // === 2d: Eager teardown of PER_TRIAL elements in REVERSE topo (LIFO) order ===
            // Teardowns are chained so a concurrent executor cannot tear down
            // an upstream element while a downstream element is still shutting
            // down. E.g., if app depends on db, then app tears down first, and
            // db's teardown explicitly depends on app's teardown completing.
            String prevPerTrialTeardownId = null;
            for (Element element : perTrialReversed) {
                int teardownInstNum = currentInstanceNumber.get(element.name());
                List<String> teardownDeps = new ArrayList<>();
                teardownDeps.add(execId);
                if (prevPerTrialTeardownId != null) {
                    teardownDeps.add(prevPerTrialTeardownId);
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
                    meta("scope", "PER_TRIAL", "reason", "per_trial_eager",
                         "trial_index", trialIdx, "trial_id", trial.id(),
                         "nesting_path", teardownTrialNesting)
                );
                steps.add(teardown);
                prevPerTrialTeardownId = teardownId;

                // Track teardown in concurrency window so future deploys respect the limit
                int maxConc = getMaxConcurrency(element);
                if (maxConc > 0) {
                    concurrencyWindows
                            .computeIfAbsent(element.name(), k -> new ArrayDeque<>())
                            .addLast(teardownId);
                }
            }

            // NOTE: Trial-batch barriers were previously emitted here but removed
            // because nothing depends on them.  The group-boundary teardowns
            // synchronise directly against lastSequentialExecId (the trial's
            // exec step).  Emitting an un-awaited barrier step would create a
            // dangling leaf node in the DAG with no scheduling purpose.
        }

        // Phase 3: Final teardown in reverse topological order.
        // PER_TRIAL elements are already torn down eagerly in phase 2.
        //
        // An ELEMENT_SCOPE_END barrier synchronizes the transition from
        // execution to teardown: it depends on the element's last step plus
        // all trial execution steps, ensuring every trial has completed
        // before teardown begins.  All final teardowns depend on this
        // barrier rather than on the exec steps directly.
        //
        // Teardowns are chained: each subsequent teardown depends on the
        // previous one in the LIFO sequence, preventing a concurrent executor
        // from tearing down an upstream element while a downstream element
        // is still shutting down.

        // Emit the ELEMENT_SCOPE_END barrier that collects all exec step
        // completions into a single synchronization point.  Only emitted
        // when there are non-PER_TRIAL elements that will have final
        // teardowns — the barrier must have downstream dependents.
        boolean hasFinalTeardowns = sortedElements.stream()
            .filter(e -> !isPerTrialScope(e))
            .anyMatch(e -> lastStepForElement.containsKey(e.name()));
        String finalBarrierStepId = null;
        if (!allExecStepIds.isEmpty() && hasFinalTeardowns) {
            String finalBarrierId = "barrier_scope_end_final_" + stepIndex;
            finalBarrierStepId = "barrier_scope_end_final_step_" + stepIndex++;

            // Barrier sources: all exec step IDs
            AtomicStep.BarrierSync finalBarrierStep = new AtomicStep.BarrierSync(
                finalBarrierStepId,
                finalBarrierId,
                new ArrayList<>(allExecStepIds),
                Optional.empty(),
                AtomicStep.ResourceRequirements.none(),
                Optional.empty(),
                Map.of("scope", "PER_RUN", "barrierType", "ELEMENT_SCOPE_END",
                       "phase", "cleanup")
            );
            steps.add(finalBarrierStep);

            barriers.add(new DefaultBarrier(
                finalBarrierId,
                Barrier.BarrierType.ELEMENT_SCOPE_END,
                "All trials completed — ready for final teardown",
                new ArrayList<>(allExecStepIds),
                List.of(),
                null,
                Barrier.TimeoutAction.FAIL_FAST,
                Map.of("scope", "PER_RUN", "phase", "cleanup")
            ));
        }

        List<Element> reversedElements = new ArrayList<>(sortedElements);
        Collections.reverse(reversedElements);

        String previousFinalTeardownId = null;
        for (Element element : reversedElements) {
            if (isPerTrialScope(element)) {
                continue; // Already torn down eagerly after each trial
            }

            String lastStep = lastStepForElement.get(element.name());
            if (lastStep == null) {
                continue;
            }

            int finalInstNum = currentInstanceNumber.getOrDefault(element.name(), 0);
            List<String> finalTeardownDeps = new ArrayList<>();
            // Depend on the ELEMENT_SCOPE_END barrier (all trials complete)
            if (finalBarrierStepId != null) {
                finalTeardownDeps.add(finalBarrierStepId);
            }
            // Also depend on the element's own last step
            if (!finalTeardownDeps.contains(lastStep)) {
                finalTeardownDeps.add(lastStep);
            }
            if (previousFinalTeardownId != null) {
                finalTeardownDeps.add(previousFinalTeardownId);
            }
            String teardownId = "teardown_final_" + element.name() + "_" + stepIndex++;
            AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                teardownId,
                element.name(),
                finalInstNum,
                true,
                finalTeardownDeps,
                Optional.empty(),
                AtomicStep.ResourceRequirements.none(),
                Optional.empty(),
                Map.of("scope", "PER_RUN", "phase", "cleanup")
            );
            steps.add(teardown);
            previousFinalTeardownId = teardownId;
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

    /// Builds the configuration map for a deploy step.
    ///
    /// Starts with the element's fixed configuration (from the YAML
    /// `parameters:` block) and then overlays any trial-assigned values
    /// for formal parameters. This ensures that the deploy step carries
    /// all relevant configuration — both static bindings and per-trial
    /// overrides.
    private Map<String, Object> buildConfiguration(Element element, Trial trial, List<CompilationContext.ElementInstance> allInstances) {
        Map<String, Object> config = new HashMap<>(element.configuration());
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

    /// Builds a step metadata map with the given key-value pairs.
    ///
    /// This helper creates a mutable {@link LinkedHashMap} from the supplied
    /// pairs, allowing additional entries (such as {@code nesting_path}) to be
    /// added after construction.
    ///
    /// @param kvPairs alternating key/value pairs
    /// @return a mutable metadata map
    private static Map<String, Object> meta(Object... kvPairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            map.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }

    /// Computes the full axis-value path for a trial.
    ///
    /// Each axis in the plan contributes one level in the nesting hierarchy.
    /// The value for each axis is looked up from the trial's assignments using
    /// the qualified key ({@code element.param}) when the axis targets a
    /// specific element, falling back to the bare axis name.
    ///
    /// @param trial the trial to extract axis values from
    /// @param axes the ordered list of axes from the test plan
    /// @return ordered list of axis-value strings, one per axis
    private List<String> computeTrialAxisPath(Trial trial,
                                              List<? extends io.nosqlbench.paramodel.plan.Axis<?>> axes) {
        List<String> path = new ArrayList<>();
        for (var axis : axes) {
            String qualifiedKey = axis.targetElement()
                .map(elem -> elem + "." + axis.name())
                .orElse(axis.name());
            String value = trial.assignment(qualifiedKey)
                .map(v -> String.valueOf(v.value()))
                .orElseGet(() -> trial.assignment(axis.name())
                    .map(v -> String.valueOf(v.value()))
                    .orElse("?"));
            path.add(value);
        }
        return path;
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
