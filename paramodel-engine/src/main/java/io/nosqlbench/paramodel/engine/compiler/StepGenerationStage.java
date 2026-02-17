package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
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
/// ## Unified Two-Phase Algorithm
///
/// 1. **Depth-0 elements** (run-scoped): Single deploy at the start, with
///    an {@code ELEMENT_READY} barrier after each deploy when the element has
///    a health check, providing a synchronization point for downstream steps.
/// 2. **Per-trial steps**: All non-depth-0 elements are processed through a
///    unified fingerprint-based group boundary mechanism:
///    - **Phase 2a**: All bound elements — fingerprint check, teardown/deploy
///      at group boundaries (ascending depth, then topo order).
///    - **Phase 2b**: NotifyTrialStart + ExecuteTrial/AwaitElement + NotifyTrialEnd
///    - **Phase 2c**: Predictive eager teardown — elements whose fingerprint
///      will change for the next trial are torn down eagerly (LIFO reverse topo)
///      to free resources sooner. Previous fingerprint cleared so Phase 2a of
///      the next trial deploys fresh without redundant teardown.
/// 3. **Final teardown**: Elements not already eagerly torn down in the last
///    trial's Phase 2c, in reverse topo order.
///
/// ## Step Metadata
///
/// Every step carries a metadata map with:
/// - {@code binding_depth}: number of bound axes (0 = run-scoped)
/// - {@code bound_axes}: list of bound axis names
/// - {@code trial_index}: ordinal position in the trial list (per-trial steps)
/// - {@code trial_id}: the trial's unique identifier (per-trial steps)
/// - {@code nesting_path}: ordered list of axis-value strings for nested graph
///   visualization.
///
public class StepGenerationStage implements CompilationStage {
    /// Effective axis bindings resolved from context, populated at the start of execute().
    private Map<String, AxisBindingSet> effectiveBindings = Map.of();

    public StepGenerationStage() {}

    @Override
    public String name() {
        return "StepGeneration";
    }

    /// Custom option key that controls whether execution steps include
    /// explicit dependencies on transitively-reachable upstream deploy
    /// steps.
    public static final String OPTION_EXPLICIT_TRANSITIVE_DEPS = "explicitTransitiveDeps";

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

        // Resolve effective bindings from context (populated by NormalizationStage)
        @SuppressWarnings("unchecked")
        Map<String, AxisBindingSet> resolved = context.get(NormalizationStage.EFFECTIVE_BINDINGS_KEY)
            .filter(v -> v instanceof Map)
            .map(v -> (Map<String, AxisBindingSet>) v)
            .orElse(Map.of());
        this.effectiveBindings = resolved;
        List<Element> elements = plan.elements();

        // Topological sort elements
        List<Element> sortedElements = topologicalSort(elements);

        // Identify the trial element (most interior leaf) and lifeline clusters
        String trialElement = identifyTrialElement(sortedElements);
        Map<String, Set<String>> lifelineClusters = computeLifelineClusters(sortedElements);

        boolean explicitTransitiveDeps = Boolean.TRUE.equals(
            context.options().customOptions().get(OPTION_EXPLICIT_TRANSITIVE_DEPS));

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

        // Sliding window for per-element max concurrency enforcement
        Map<String, Deque<String>> concurrencyWindows = new HashMap<>();

        // Classify non-run-scoped elements into bound list
        List<Element> boundElements = new ArrayList<>();
        for (Element element : sortedElements) {
            if (isRunScoped(element, allInstances)) continue;
            boundElements.add(element);
        }
        // Sort bound elements by ascending depth (stable preserves topo order)
        boundElements.sort(Comparator.comparingInt(e ->
            resolveBinding(e).depth()));

        // Precompute element names for notification steps
        List<String> allElementNames = elements.stream().map(Element::name).toList();

        // Resolve trial element's shutdown semantics for COMMAND vs SERVICE handling
        Element trialElementObj = trialElement != null
            ? elements.stream().filter(e -> e.name().equals(trialElement)).findFirst().orElse(null)
            : null;
        boolean trialElementIsCommand = trialElementObj != null
            && trialElementObj.shutdownSemantics() == Element.ShutdownSemantics.COMMAND;

        // --- Precompute nesting paths for graph visualization ---
        List<? extends io.nosqlbench.paramodel.plan.Axis<?>> planAxes = plan.axes();

        // Precompute full axis path for each trial
        Map<Integer, List<String>> trialFullPaths = new HashMap<>();
        for (int i = 0; i < trials.size(); i++) {
            Trial t = trials.get(i);
            trialFullPaths.put(i, computeTrialAxisPath(t, planAxes));
        }

        // For each bound element, precompute group ranges and nesting paths.
        Map<String, Map<Integer, List<String>>> groupNestingPaths = new HashMap<>();
        for (Element element : boundElements) {
            Map<Integer, List<String>> nestingByTrial = new HashMap<>();
            int groupStart = 0;
            String groupFingerprint = computeElementFingerprint(element, trials.getFirst(), plan);

            for (int i = 1; i <= trials.size(); i++) {
                String fp = (i < trials.size())
                    ? computeElementFingerprint(element, trials.get(i), plan)
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

        // Phase 1: Deploy depth-0 (run-scoped) elements
        for (Element element : sortedElements) {
            if (isRunScoped(element, allInstances)) {
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
                    buildBindingMeta(element, "setup")
                );
                steps.add(deployStep);
                lastStepForElement.put(element.name(), stepId);

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
                        meta("element", element.name(), "binding_depth", 0)
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
                        meta("element", element.name(), "binding_depth", 0)
                    ));
                }
            }
        }

        // Phase 2: Per-trial steps
        String lastSequentialExecId = null;
        List<String> allExecStepIds = new ArrayList<>();
        List<String> allEagerTeardownStepIds = new ArrayList<>();
        List<String> latestStepPerTrial = new ArrayList<>();

        for (int trialIdx = 0; trialIdx < trials.size(); trialIdx++) {
            Trial trial = trials.get(trialIdx);

            // === 2a: ALL bound elements — unified fingerprint check ===
            List<Element> toTeardown = new ArrayList<>();
            List<Element> toDeploy = new ArrayList<>();

            for (Element element : boundElements) {
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

            // Filter out lifeline-subsumed elements
            toTeardown.removeIf(this::isLifelineSubsumed);

            // Teardown at group boundaries in REVERSE topological (LIFO) order
            List<Element> teardownReversed = new ArrayList<>(toTeardown);
            Collections.reverse(teardownReversed);
            String previousTeardownId = null;
            for (Element element : teardownReversed) {
                int teardownInstNum = currentInstanceNumber.get(element.name());
                List<String> teardownDeps = new ArrayList<>();
                if (lastSequentialExecId != null) {
                    teardownDeps.add(lastSequentialExecId);
                } else {
                    String lastStep = lastStepForElement.get(element.name());
                    if (lastStep != null) teardownDeps.add(lastStep);
                }
                if (previousTeardownId != null) {
                    teardownDeps.add(previousTeardownId);
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
                    buildBoundaryTeardownMeta(element, trialIdx, trial.id(), teardownNesting)
                );
                steps.add(teardown);
                lastStepForElement.put(element.name(), teardownId);
                previousTeardownId = teardownId;
            }

            // Deploy bound elements in FORWARD order (ascending depth, then topo)
            for (Element element : toDeploy) {
                int instNum = nextInstanceNumber.getOrDefault(element.name(), 0);
                nextInstanceNumber.put(element.name(), instNum + 1);
                currentInstanceNumber.put(element.name(), instNum);

                List<String> deployDeps = computeDependencies(element, lastStepForElement);

                String ownLastStep = lastStepForElement.get(element.name());
                if (ownLastStep != null && !deployDeps.contains(ownLastStep)) {
                    deployDeps.add(ownLastStep);
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
                    buildDeployMeta(element, trialIdx, trial.id(), deployNesting)
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
                        buildDeployMeta(element, trialIdx, trial.id(), deployNesting)
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
                        buildDeployMeta(element, trialIdx, trial.id(), deployNesting)
                    ));
                }

                if (maxConc > 0) {
                    concurrencyWindows
                            .computeIfAbsent(element.name(), k -> new ArrayDeque<>())
                            .addLast(deployId);
                }
            }

            // === 2b: ExecuteTrial ===
            Map<String, String> elementBindings = new HashMap<>();
            for (Element element : elements) {
                Optional<CompilationContext.ElementInstance> inst = findInstanceForTrial(element, trial, allInstances);
                inst.ifPresent(i -> elementBindings.put(element.name(), i.instanceId()));
            }

            List<String> execDeps = new ArrayList<>();
            for (Element element : elements) {
                String lastStep = lastStepForElement.get(element.name());
                if (lastStep != null) {
                    execDeps.add(lastStep);
                }
            }

            if (!explicitTransitiveDeps) {
                // Build trialDeployMap from bound elements deployed this trial
                Map<String, String> trialDeployMap = new HashMap<>();
                for (Element element : boundElements) {
                    String step = lastStepForElement.get(element.name());
                    if (step != null) trialDeployMap.put(element.name(), step);
                }
                execDeps = minimalDeps(elements, lastStepForElement, trialDeployMap, execDeps);
            }

            // NotifyTrialStart
            boolean notifyTrialStartEmitted = false;
            if (trialElement != null) {
                List<String> notifyDeps = new ArrayList<>(execDeps);
                if (!explicitTransitiveDeps) {
                    Map<String, String> trialDeployMap = new HashMap<>();
                    for (Element element : boundElements) {
                        String step = lastStepForElement.get(element.name());
                        if (step != null) trialDeployMap.put(element.name(), step);
                    }
                    notifyDeps = minimalDeps(elements, lastStepForElement, trialDeployMap, notifyDeps);
                }

                String notifyStartId = "notify_trial_start_" + trialIdx + "_" + stepIndex++;
                List<String> trialNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
                AtomicStep.NotifyTrialStart notifyStart = new AtomicStep.NotifyTrialStart(
                    notifyStartId, trial.id(), allElementNames, notifyDeps,
                    Optional.empty(), AtomicStep.ResourceRequirements.none(), Optional.empty(),
                    meta("trial_index", trialIdx, "trial_id", trial.id(), "nesting_path", trialNesting)
                );
                steps.add(notifyStart);
                notifyTrialStartEmitted = true;

                execDeps = new ArrayList<>();
                execDeps.add(notifyStartId);
            }

            List<String> execNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
            Map<String, Object> execMeta = meta("trial_index", trialIdx,
                     "trial_id", trial.id(), "nesting_path", execNesting);
            if (trialElement != null) {
                execMeta.put("trial_element", trialElement);
            }

            String execId;
            if (trialElementIsCommand) {
                int awaitInstNum = currentInstanceNumber.getOrDefault(trialElement, 0);
                execId = "await_" + trialElement + "_t" + trialIdx + "_" + stepIndex++;
                AtomicStep.AwaitElement awaitStep = new AtomicStep.AwaitElement(
                    execId, trialElement, awaitInstNum, trial.id(),
                    elementBindings, execDeps,
                    Optional.empty(), AtomicStep.ResourceRequirements.minimal(),
                    Optional.empty(), execMeta
                );
                steps.add(awaitStep);
            } else {
                execId = "exec_trial_" + trialIdx + "_" + stepIndex++;
                AtomicStep.ExecuteTrial executeTrial = new AtomicStep.ExecuteTrial(
                    execId,
                    trial.id(),
                    elementBindings,
                    execDeps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.minimal(),
                    Optional.empty(),
                    execMeta
                );
                steps.add(executeTrial);
            }
            lastSequentialExecId = execId;
            allExecStepIds.add(execId);
            lastStepForElement.put("__trial_" + trialIdx, execId);

            // === 2c: Predictive eager teardown ===
            // Emit NotifyTrialEnd first
            String notifyTrialEndId = null;
            String prevEagerTeardownId = null;

            if (trialElement != null) {
                notifyTrialEndId = "notify_trial_end_" + trialIdx + "_" + stepIndex++;
                List<String> trialNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
                AtomicStep.NotifyTrialEnd notifyEnd = new AtomicStep.NotifyTrialEnd(
                    notifyTrialEndId, trial.id(), allElementNames,
                    AtomicStep.ShutdownReason.NORMAL,
                    List.of(execId),
                    Optional.empty(), AtomicStep.ResourceRequirements.none(), Optional.empty(),
                    meta("trial_index", trialIdx, "trial_id", trial.id(), "nesting_path", trialNesting)
                );
                steps.add(notifyEnd);
                allEagerTeardownStepIds.add(notifyTrialEndId);
                prevEagerTeardownId = notifyTrialEndId;
            }

            // Eagerly tear down bound elements whose fingerprint will change for the
            // next trial (or all bound elements on the last trial). LIFO reverse topo.
            List<Element> boundReversed = new ArrayList<>(boundElements);
            Collections.reverse(boundReversed);

            for (Element element : boundReversed) {
                boolean isLastTrial = (trialIdx == trials.size() - 1);
                boolean fingerprintWillChange;

                if (isLastTrial) {
                    fingerprintWillChange = true; // last trial — always eager
                } else {
                    String nextFingerprint = computeElementFingerprint(element, trials.get(trialIdx + 1), plan);
                    String currentFingerprint = currentFingerprintForElement.get(element.name());
                    fingerprintWillChange = currentFingerprint != null
                        && !nextFingerprint.equals(currentFingerprint);
                }

                if (!fingerprintWillChange) continue;

                // Skip teardown for COMMAND trial element — it terminates itself
                if (trialElementIsCommand && element.name().equals(trialElement)) {
                    currentFingerprintForElement.remove(element.name());
                    continue;
                }

                if (isLifelineSubsumed(element)) continue;

                int teardownInstNum = currentInstanceNumber.get(element.name());
                List<String> teardownDeps = new ArrayList<>();
                teardownDeps.add(execId);
                if (prevEagerTeardownId != null) {
                    teardownDeps.add(prevEagerTeardownId);
                }
                List<String> teardownTrialNesting = trialFullPaths.getOrDefault(trialIdx, List.of());
                String teardownId = "teardown_" + element.name() + "_t" + trialIdx + "_" + stepIndex++;
                AtomicStep.TeardownElement teardown = new AtomicStep.TeardownElement(
                    teardownId,
                    element.name(),
                    teardownInstNum,
                    isLastTrial,
                    teardownDeps,
                    Optional.empty(),
                    AtomicStep.ResourceRequirements.none(),
                    Optional.empty(),
                    meta("reason", "predictive_eager",
                         "binding_depth", resolveBinding(element).depth(),
                         "trial_index", trialIdx, "trial_id", trial.id(),
                         "nesting_path", teardownTrialNesting)
                );
                steps.add(teardown);
                prevEagerTeardownId = teardownId;
                allEagerTeardownStepIds.add(teardownId);

                // Clear fingerprint so Phase 2a of next trial deploys fresh
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
            }

            String latestTrialStep = prevEagerTeardownId != null
                ? prevEagerTeardownId
                : (notifyTrialEndId != null ? notifyTrialEndId : execId);
            latestStepPerTrial.add(latestTrialStep);
        }

        // Phase 3: Final teardown in reverse topological order.
        // Elements eagerly torn down in the last trial's Phase 2c are skipped.
        List<String> finalPhaseDeps = new ArrayList<>(latestStepPerTrial);

        // Collect elements already eagerly torn down in the last trial
        Set<String> eagerlyTornDown = new HashSet<>();
        for (Element element : boundElements) {
            if (!currentFingerprintForElement.containsKey(element.name())) {
                eagerlyTornDown.add(element.name());
            }
        }

        List<Element> reversedElements = new ArrayList<>(sortedElements);
        Collections.reverse(reversedElements);

        String previousFinalTeardownId = null;
        for (Element element : reversedElements) {
            if (eagerlyTornDown.contains(element.name())) {
                continue; // Already torn down eagerly in Phase 2c
            }
            if (isLifelineSubsumed(element)) {
                continue;
            }

            String lastStep = lastStepForElement.get(element.name());
            if (lastStep == null) {
                continue;
            }

            int finalInstNum = currentInstanceNumber.getOrDefault(element.name(), 0);
            List<String> finalTeardownDeps = new ArrayList<>();
            if (previousFinalTeardownId == null) {
                if (!latestStepPerTrial.isEmpty()) {
                    finalTeardownDeps.addAll(latestStepPerTrial);
                } else {
                    if (lastStep != null) finalTeardownDeps.add(lastStep);
                }
            }
            if (previousFinalTeardownId != null) {
                finalTeardownDeps.add(previousFinalTeardownId);
            }
            String teardownId = "teardown_final_" + element.name() + "_" + stepIndex++;
            Map<String, Object> teardownMeta = buildBindingMeta(element, "cleanup");
            Set<String> clusterMembers = lifelineClusters.get(element.name());
            if (clusterMembers != null) {
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
            previousFinalTeardownId = teardownId;
        }

        context.setSteps(steps);
        context.setBarriers(barriers);
        context.recordMetric("steps_generated", steps.size());
        context.recordMetric("barriers_generated", barriers.size());
    }

    /// Returns the max concurrency limit for the element, or 0 if unlimited.
    private int getMaxConcurrency(Element element) {
        if (element instanceof DefaultElement de) {
            return de.maxConcurrency().orElse(0);
        }
        String val = element.tags().get("max_concurrency");
        if (val == null || val.isBlank()) return 0;
        return Integer.parseInt(val);
    }

    /// Resolves the effective axis binding set for an element using
    /// the bindings computed by NormalizationStage.
    private AxisBindingSet resolveBinding(Element element) {
        AxisBindingSet fromContext = effectiveBindings.get(element.name());
        if (fromContext != null) return fromContext;
        return AxisBindingSet.runScoped();
    }

    /// Returns true if the element has dependencies and ALL of them are lifeline.
    private boolean isLifelineSubsumed(Element element) {
        List<Element.Dependency> deps = element.dependencies();
        if (deps.isEmpty()) return false;
        return deps.stream().allMatch(d -> d.type() == RelationshipType.LIFELINE);
    }

    /// Returns true if the element is run-scoped (depth 0, not independent).
    private boolean isRunScoped(Element element, List<CompilationContext.ElementInstance> allInstances) {
        AxisBindingSet binding = resolveBinding(element);
        return binding.isRunScoped();
    }

    /// Computes a configuration fingerprint for group-boundary detection.
    ///
    /// The fingerprint incorporates:
    /// 1. The element's own formal parameter values from trial assignments
    /// 2. Axis values targeting this element
    /// 3. Dependency fingerprints — recursive propagation
    private String computeElementFingerprint(Element element, Trial trial, TestPlan plan) {
        TreeMap<String, String> sortedFingerprints = new TreeMap<>();

        for (var param : element.parameters()) {
            trial.assignment(param.name()).ifPresent(value ->
                sortedFingerprints.put(param.name(), value.fingerprint())
            );
        }

        for (var axis : plan.axes()) {
            if (axis.targetElement().map(t -> t.equals(element.name())).orElse(false)) {
                String qualifiedKey = element.name() + "." + axis.name();
                trial.assignment(qualifiedKey).ifPresent(value ->
                    sortedFingerprints.put(axis.name(), value.fingerprint()));
            }
        }

        for (Element.Dependency dep : element.dependencies()) {
            String depFingerprint = computeElementFingerprint(dep.target(), trial, plan);
            sortedFingerprints.put("__dep:" + dep.target().name(), depFingerprint);
        }

        if (sortedFingerprints.isEmpty()) {
            return "static:" + element.name();
        }
        return String.join("|", sortedFingerprints.values());
    }

    /// Builds metadata with binding information for an element.
    private Map<String, Object> buildBindingMeta(Element element, String phase) {
        AxisBindingSet binding = resolveBinding(element);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("binding_depth", binding.depth());
        m.put("bound_axes", List.copyOf(binding.boundAxes()));
        m.put("phase", phase);
        return m;
    }

    /// Builds metadata for a deploy step of a bound element.
    private Map<String, Object> buildDeployMeta(Element element, int trialIdx, String trialId, List<String> nestingPath) {
        AxisBindingSet binding = resolveBinding(element);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("binding_depth", binding.depth());
        m.put("bound_axes", List.copyOf(binding.boundAxes()));
        m.put("trial_index", trialIdx);
        m.put("trial_id", trialId);
        m.put("nesting_path", nestingPath);
        return m;
    }

    /// Builds metadata for a group-boundary teardown step.
    private Map<String, Object> buildBoundaryTeardownMeta(Element element, int trialIdx, String trialId, List<String> nestingPath) {
        AxisBindingSet binding = resolveBinding(element);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("binding_depth", binding.depth());
        m.put("bound_axes", List.copyOf(binding.boundAxes()));
        m.put("reason", "group_boundary");
        m.put("trial_index", trialIdx);
        m.put("trial_id", trialId);
        m.put("nesting_path", nestingPath);
        return m;
    }

    private List<String> computeDependencies(Element element, Map<String, String> lastStepForElement) {
        List<String> deps = new ArrayList<>();
        for (Element.Dependency dep : element.dependencies()) {
            String lastStep = lastStepForElement.get(dep.target().name());
            if (lastStep != null) {
                deps.add(lastStep);
            }
        }
        return deps;
    }

    private List<String> minimalDeps(
            List<Element> elements,
            Map<String, String> lastStepForElement,
            Map<String, String> trialDeployMap,
            List<String> deps) {

        Map<String, String> stepToElement = new HashMap<>();
        for (Element e : elements) {
            String step = lastStepForElement.get(e.name());
            if (step != null) stepToElement.put(step, e.name());
            step = trialDeployMap.get(e.name());
            if (step != null) stepToElement.put(step, e.name());
        }

        Map<String, Set<String>> transitiveUpstream = new HashMap<>();
        for (Element e : elements) {
            transitiveUpstream.computeIfAbsent(e.name(), k -> {
                Set<String> upstream = new HashSet<>();
                collectUpstream(e, upstream, elements);
                return upstream;
            });
        }

        Set<String> depSet = new LinkedHashSet<>(deps);
        Set<String> covered = new HashSet<>();
        for (String dep : depSet) {
            String elementName = stepToElement.get(dep);
            if (elementName == null) continue;
            for (String otherDep : depSet) {
                if (otherDep.equals(dep)) continue;
                String otherElement = stepToElement.get(otherDep);
                if (otherElement == null) continue;
                Set<String> otherUpstream = transitiveUpstream.get(otherElement);
                if (otherUpstream != null && otherUpstream.contains(elementName)) {
                    covered.add(dep);
                    break;
                }
            }
        }

        List<String> minimal = new ArrayList<>();
        for (String dep : deps) {
            if (!covered.contains(dep)) {
                minimal.add(dep);
            }
        }
        return minimal;
    }

    private void collectUpstream(Element element, Set<String> upstream, List<Element> allElements) {
        for (Element.Dependency dep : element.dependencies()) {
            if (upstream.add(dep.target().name())) {
                collectUpstream(dep.target(), upstream, allElements);
            }
        }
    }

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

    private static Map<String, Object> meta(Object... kvPairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            map.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }

    private String identifyTrialElement(List<Element> sortedElements) {
        Set<String> hasDependent = new HashSet<>();
        for (Element e : sortedElements) {
            for (Element.Dependency dep : e.dependencies()) {
                hasDependent.add(dep.target().name());
            }
        }
        String trialElement = null;
        for (Element e : sortedElements) {
            if (!hasDependent.contains(e.name())) {
                trialElement = e.name();
            }
        }
        return trialElement;
    }

    private Map<String, Set<String>> computeLifelineClusters(List<Element> elements) {
        Map<String, String> parent = new HashMap<>();
        for (Element e : elements) parent.put(e.name(), e.name());

        for (Element e : elements) {
            for (Element.Dependency dep : e.dependencies()) {
                if (dep.type() == RelationshipType.LIFELINE) {
                    lifelineUnion(parent, e.name(), dep.target().name());
                }
            }
        }

        Map<String, Set<String>> clusters = new HashMap<>();
        for (Element e : elements) {
            String root = lifelineFind(parent, e.name());
            clusters.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(e.name());
        }

        clusters.entrySet().removeIf(entry -> entry.getValue().size() <= 1);

        Map<String, Set<String>> result = new HashMap<>();
        for (var entry : clusters.entrySet()) {
            String clusterRoot = null;
            for (Element e : elements) {
                if (entry.getValue().contains(e.name())) {
                    clusterRoot = e.name();
                    break;
                }
            }
            result.put(clusterRoot, entry.getValue());
        }
        return result;
    }

    private String lifelineFind(Map<String, String> parent, String x) {
        while (!parent.get(x).equals(x)) {
            parent.put(x, parent.get(parent.get(x)));
            x = parent.get(x);
        }
        return x;
    }

    private void lifelineUnion(Map<String, String> parent, String a, String b) {
        String ra = lifelineFind(parent, a), rb = lifelineFind(parent, b);
        if (!ra.equals(rb)) parent.put(ra, rb);
    }

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
            for (Element.Dependency dep : e.dependencies()) {
                adj.computeIfAbsent(dep.target().name(), k -> new ArrayList<>()).add(e.name());
                inDegree.merge(e.name(), 1, Integer::sum);
                inDegree.putIfAbsent(dep.target().name(), 0);
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
