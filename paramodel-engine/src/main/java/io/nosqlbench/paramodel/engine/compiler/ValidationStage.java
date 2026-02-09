package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.Compiler;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.plan.TestPlan;

/**
 * Stage 1: Validation
 *
 * Verifies TestPlan correctness:
 * - TestPlan validates successfully
 * - All axes are valid
 * - Elements reference valid parameters
 */
public class ValidationStage implements CompilationStage {

    @Override
    public String name() {
        return "Validation";
    }

    @Override
    public void execute(CompilationContext context) {
        TestPlan plan = context.testPlan();

        // Validate TestPlan itself
        io.nosqlbench.paramodel.core.ValidationResult planValidation = plan.validate();
        if (planValidation.isFailed()) {
            planValidation.message().ifPresent(msg ->
                context.addError(Compiler.ErrorSeverity.ERROR, "TestPlan validation failed: " + msg, null, null)
            );
            for (String violation : planValidation.violations()) {
                context.addError(Compiler.ErrorSeverity.ERROR, violation, null, null);
            }
            return;
        }

        // Validate axes exist
        if (plan.axes().isEmpty()) {
            context.addWarning("TestPlan has no axes", "Add at least one axis to define parameter space");
        }

        // Validate elements exist
        if (plan.elements().isEmpty()) {
            context.addWarning("TestPlan has no elements", "Add at least one element to test");
        }

        // Record validation metrics
        context.recordMetric("axes_count", plan.axes().size());
        context.recordMetric("elements_count", plan.elements().size());
        context.recordMetric("trial_space_size", plan.trialSpaceSize());
    }
}
