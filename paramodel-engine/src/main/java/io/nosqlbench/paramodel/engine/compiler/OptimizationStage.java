package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.compilation.OptimizationPass;
import io.nosqlbench.paramodel.plan.AtomicStep;
import io.nosqlbench.paramodel.plan.OptimizationStrategy;

import java.util.*;
import java.util.stream.Collectors;

///
/// Stage 7: Optimization
///
/// Applies transformations to improve execution efficiency.
///
/// ## Optimization Passes
///
/// - **PruneRedundantPass**: Ported from SectionScopeOptimizer. For PER_GROUP-scoped
///   elements, detects adjacent trials where the element's parameter fingerprints are
///   identical. Removes the redundant teardown/deploy pairs, keeping the element
///   running across trials with unchanged configuration.
///
/// - **MergeEquivalentPass**: Placeholder for future step merging.
///
public class OptimizationStage implements CompilationStage {
    private final List<OptimizationPass> passes;

    public OptimizationStage() {
        this.passes = new ArrayList<>();
        passes.add(new PruneRedundantPass());
        passes.add(new MergeEquivalentPass());
    }

    public OptimizationStage(List<OptimizationPass> passes) {
        this.passes = new ArrayList<>(passes);
    }

    @Override
    public String name() {
        return "Optimization";
    }

    @Override
    public void execute(CompilationContext context) {
        OptimizationStrategy strategy = context.testPlan().optimizationStrategy();

        if (strategy == OptimizationStrategy.NONE) {
            return;
        }

        for (OptimizationPass pass : passes) {
            if (shouldApplyPass(pass, strategy, context)) {
                pass.apply(context);
            }
        }
    }

    private boolean shouldApplyPass(OptimizationPass pass, OptimizationStrategy strategy, CompilationContext context) {
        if (!pass.shouldApply(context)) {
            return false;
        }

        return switch (strategy) {
            case PRUNE_REDUNDANT -> pass instanceof PruneRedundantPass;
            case AGGRESSIVE -> true;
            case BASIC -> pass instanceof PruneRedundantPass;
            case NONE -> false;
        };
    }

    ///
    /// Prunes redundant deploy/teardown pairs for elements whose configuration
    /// is unchanged between adjacent trials.
    ///
    /// Ported from the SectionScopeOptimizer algorithm in hyperplane-study.
    ///
    /// For each element with per-trial scope:
    /// 1. Collect all DeployElement steps ordered by trial sequence
    /// 2. For adjacent pairs, compare the element's configuration map
    /// 3. If configurations match, mark the intermediate teardown and
    ///    subsequent deploy as redundant
    /// 4. Remove redundant steps from the step list
    ///
    static class PruneRedundantPass implements OptimizationPass {
        @Override
        public String name() {
            return "PruneRedundant";
        }

        @Override
        public boolean shouldApply(CompilationContext context) {
            return context.steps().isPresent() && !context.steps().get().isEmpty();
        }

        @Override
        public void apply(CompilationContext context) {
            List<AtomicStep> steps = new ArrayList<>(context.steps().get());

            // Group deploy steps by element, maintaining trial order
            Map<String, List<AtomicStep.DeployElement>> deploysByElement = new LinkedHashMap<>();
            for (AtomicStep step : steps) {
                if (step instanceof AtomicStep.DeployElement deploy) {
                    // Only consider per-trial deploys (not PER_RUN setup)
                    Object scope = deploy.metadata().get("scope");
                    if ("PER_TRIAL".equals(scope)) {
                        deploysByElement.computeIfAbsent(deploy.elementId(), k -> new ArrayList<>())
                            .add(deploy);
                    }
                }
            }

            Set<String> redundantStepIds = new HashSet<>();

            for (var entry : deploysByElement.entrySet()) {
                List<AtomicStep.DeployElement> deploys = entry.getValue();
                if (deploys.size() < 2) continue;

                for (int i = 1; i < deploys.size(); i++) {
                    AtomicStep.DeployElement prev = deploys.get(i - 1);
                    AtomicStep.DeployElement curr = deploys.get(i);

                    // Compare configurations
                    if (prev.configuration().equals(curr.configuration())) {
                        // Mark the current deploy as redundant
                        redundantStepIds.add(curr.id());

                        // Find and mark the teardown that preceded this deploy
                        // (the teardown for this element between prev and curr)
                        for (AtomicStep step : steps) {
                            if (step instanceof AtomicStep.TeardownElement teardown) {
                                if (teardown.elementId().equals(entry.getKey())
                                    && "parameter_change".equals(teardown.metadata().get("reason"))) {
                                    // Check if this teardown is between the two deploys by trial index
                                    Object teardownTrialIdx = teardown.metadata().get("trial_index");
                                    Object currTrialIdx = curr.metadata().get("trial_index");
                                    if (teardownTrialIdx != null && teardownTrialIdx.equals(currTrialIdx)) {
                                        redundantStepIds.add(teardown.id());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!redundantStepIds.isEmpty()) {
                List<AtomicStep> pruned = steps.stream()
                    .filter(s -> !redundantStepIds.contains(s.id()))
                    .collect(Collectors.toList());
                context.setSteps(pruned);
                context.recordMetric("steps_pruned", redundantStepIds.size());
            }
        }
    }

    ///
    /// Placeholder optimization pass that merges equivalent steps.
    ///
    static class MergeEquivalentPass implements OptimizationPass {
        @Override
        public String name() {
            return "MergeEquivalent";
        }

        @Override
        public boolean shouldApply(CompilationContext context) {
            return context.steps().isPresent();
        }

        @Override
        public void apply(CompilationContext context) {
            // Future: merge steps with identical effects
        }
    }
}
