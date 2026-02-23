package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.compilation.CompilationStage;
import io.nosqlbench.paramodel.engine.planners.StepGenerationStrategy;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoStepGenerationStrategy;
import io.nosqlbench.paramodel.engine.planners.simple.SimpleStepGenerationStrategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

///
/// Stage 5: Step Generation
///
/// Converts trials and element instances into a flat, ordered list of
/// {@link io.nosqlbench.paramodel.plan.AtomicStep} records by delegating to a
/// pluggable {@link StepGenerationStrategy}.
///
/// The active strategy is selected via the {@value #OPTION_STRATEGY} key in
/// {@link io.nosqlbench.paramodel.compilation.Compiler.CompilerOptions#customOptions()}.
/// When the key is absent the {@link ReductoStepGenerationStrategy} is used.
///
/// Custom strategies are registered with {@link #register(StepGenerationStrategy)}
/// and can be enumerated via {@link #registeredStrategies()}.
///
/// @see StepGenerationStrategy
/// @see SimpleStepGenerationStrategy
///
public class StepGenerationStage implements CompilationStage {

    /// Custom option key for selecting the step generation strategy.
    public static final String OPTION_STRATEGY = "stepGenerationStrategy";

    private static final Map<String, StepGenerationStrategy> STRATEGIES = new LinkedHashMap<>();

    static {
        register(new SimpleStepGenerationStrategy());
        ReductoStepGenerationStrategy reducto = new ReductoStepGenerationStrategy();
        register(reducto);
        STRATEGIES.put("default", reducto);
    }

    /// Registers a step generation strategy.
    ///
    /// If a strategy with the same name already exists it is replaced.
    ///
    /// @param strategy the strategy to register
    public static void register(StepGenerationStrategy strategy) {
        STRATEGIES.put(strategy.strategyName(), strategy);
    }

    /// Returns an unmodifiable view of all registered strategies.
    ///
    /// @return map from strategy name to strategy instance
    public static Map<String, StepGenerationStrategy> registeredStrategies() {
        return Collections.unmodifiableMap(STRATEGIES);
    }

    public StepGenerationStage() {}

    @Override
    public String name() {
        return "StepGeneration";
    }

    @Override
    public void execute(CompilationContext context) {
        String name = (String) context.options().customOptions()
            .getOrDefault(OPTION_STRATEGY, "reducto");
        StepGenerationStrategy strategy = STRATEGIES.get(name);
        if (strategy == null) {
            context.addError(
                io.nosqlbench.paramodel.compilation.Compiler.ErrorSeverity.ERROR,
                "Unknown step generation strategy: '" + name
                    + "'. Registered strategies: " + STRATEGIES.keySet(),
                "StepGenerationStage",
                "Use one of: " + STRATEGIES.keySet());
            return;
        }
        strategy.generateSteps(context);
    }
}
