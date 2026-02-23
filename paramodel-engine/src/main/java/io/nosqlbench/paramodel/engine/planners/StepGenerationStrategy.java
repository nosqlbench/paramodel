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
package io.nosqlbench.paramodel.engine.planners;

import io.nosqlbench.paramodel.compilation.CompilationContext;
import io.nosqlbench.paramodel.engine.compiler.StepGenerationStage;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoStepGenerationStrategy;
import io.nosqlbench.paramodel.engine.planners.simple.SimpleStepGenerationStrategy;

///
/// Strategy interface for step generation within the compilation pipeline.
///
/// Implementations encapsulate the algorithm that converts trials and element
/// instances into a flat, ordered list of {@link io.nosqlbench.paramodel.plan.AtomicStep}
/// records.  The active strategy is selected via the
/// {@value StepGenerationStage#OPTION_STRATEGY} custom compiler option.
///
/// @see StepGenerationStage
/// @see ReductoStepGenerationStrategy
/// @see SimpleStepGenerationStrategy
///
public interface StepGenerationStrategy {

    /// Returns the unique name used to select this strategy via
    /// {@link io.nosqlbench.paramodel.compilation.Compiler.CompilerOptions#customOptions()}.
    ///
    /// @return strategy name, e.g. {@code "default"} or {@code "simple"}
    String strategyName();

    /// Returns a human-readable description of this strategy.
    ///
    /// @return description text
    String description();

    /// Generates atomic steps and barriers, storing them in the given context
    /// via {@link CompilationContext#setSteps} and {@link CompilationContext#setBarriers}.
    ///
    /// @param context the compilation context populated by earlier pipeline stages
    void generateSteps(CompilationContext context);
}
