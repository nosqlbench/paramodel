package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.compilation.OptimizationPass;
import io.nosqlbench.paramodel.plan.OptimizationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 7: Optimization
 *
 * Applies transformations to improve execution:
 * - Prune redundant trials
 * - Merge equivalent steps
 * - Reorder for better cache locality
 * - Apply user-specified optimizations
 * - Track optimization metrics
 */
public class OptimizationStage implements CompilationStage {
    private final List<OptimizationPass> passes;

    public OptimizationStage() {
        this.passes = new ArrayList<>();
        // Register standard optimization passes
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

        // Skip optimization if strategy is NONE
        if (strategy == OptimizationStrategy.NONE) {
            return;
        }

        // Apply optimization passes based on strategy
        for (OptimizationPass pass : passes) {
            if (shouldApplyPass(pass, strategy, context)) {
                pass.apply(context);
            }
        }
    }

    private boolean shouldApplyPass(OptimizationPass pass, OptimizationStrategy strategy, CompilationContext context) {
        // Check if pass should apply first
        if (!pass.shouldApply(context)) {
            return false;
        }

        // Determine if pass should be applied based on strategy
        return switch (strategy) {
            case PRUNE_REDUNDANT -> pass instanceof PruneRedundantPass;
            case AGGRESSIVE -> true; // Apply all passes
            case BASIC -> pass instanceof PruneRedundantPass; // Only basic optimizations
            case NONE -> false;
        };
    }

    /**
     * Optimization pass that prunes redundant trials.
     */
    private static class PruneRedundantPass implements OptimizationPass {
        @Override
        public String name() {
            return "PruneRedundant";
        }

        @Override
        public boolean shouldApply(CompilationContext context) {
            // Only apply if we have steps to optimize
            return context.steps().isPresent();
        }

        @Override
        public void apply(CompilationContext context) {
            // For now, no-op
            // Full implementation would identify and remove redundant trials
        }
    }

    /**
     * Optimization pass that merges equivalent steps.
     */
    private static class MergeEquivalentPass implements OptimizationPass {
        @Override
        public String name() {
            return "MergeEquivalent";
        }

        @Override
        public boolean shouldApply(CompilationContext context) {
            // Only apply if we have steps to optimize
            return context.steps().isPresent();
        }

        @Override
        public void apply(CompilationContext context) {
            // For now, no-op
            // Full implementation would merge steps with identical effects
        }
    }
}
