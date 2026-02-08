package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.core.Parameter;
import io.nosqlbench.paramodel.core.ValidationResult;
import io.nosqlbench.paramodel.plan.Axis;
import io.nosqlbench.paramodel.plan.Element;
import io.nosqlbench.paramodel.plan.TestPlan;

/**
 * Stage 1: Validation
 *
 * Verifies TestPlan correctness:
 * - All parameters are valid
 * - Axes reference existing parameters
 * - Constraints are well-formed
 * - No circular dependencies
 */
public class ValidationStage implements CompilationStage {

    @Override
    public String name() {
        return "Validation";
    }

    @Override
    public CompilationContext execute(CompilationContext context) {
        TestPlan plan = context.testPlan();
        CompilationContext result = context;

        // Validate TestPlan itself
        ValidationResult planValidation = plan.validate();
        if (!planValidation.isValid()) {
            result = result.withError("TestPlan validation failed: " + planValidation.message());
            for (String violation : planValidation.violations()) {
                result = result.withError("  - " + violation);
            }
            return result;
        }

        // Validate all parameters
        for (Parameter<?> param : plan.parameters().values()) {
            ValidationResult paramValidation = param.validate(null);
            if (!paramValidation.isValid()) {
                result = result.withError("Parameter '" + param.name() + "' validation failed: " +
                    paramValidation.message());
            }
        }

        // Validate axes reference existing parameters
        for (Axis axis : plan.axes()) {
            for (Element element : axis.elements()) {
                if (!plan.parameters().containsKey(element.parameterName())) {
                    result = result.withError("Axis '" + axis.name() + "' references unknown parameter: " +
                        element.parameterName());
                }
            }
        }

        // Check for empty parameter space
        if (plan.parameters().isEmpty()) {
            result = result.withWarning("TestPlan has no parameters");
        }

        return result;
    }
}
