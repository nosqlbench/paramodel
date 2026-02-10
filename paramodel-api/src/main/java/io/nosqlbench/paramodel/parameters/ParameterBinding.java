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
package io.nosqlbench.paramodel.parameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// The result of binding input values to a set of parameter definitions.
///
/// Represents the **Element Instance** maturity level: parameters have been
/// assigned specific values, validated against domains and constraints,
/// with defaults applied for missing inputs.
///
/// ## Maturity Levels
///
/// ```
/// Parameter<T> (model)     →  what can be configured
/// ParameterBinding (this)  →  what was configured
/// Materialized             →  live resource handle
/// ```
///
/// ## Thread Safety
///
/// ParameterBinding instances are immutable and thread-safe after construction.
///
/// ## Usage Example
///
/// ```java
/// ParameterBinder binder = new DefaultParameterBinder(BindingPolicy.LENIENT);
/// ParameterBinding binding = binder.bind(element, Map.of("threads", "8"));
///
/// if (binding.validationResult().isPassed()) {
///     Map<String, Object> envVars = binding.toValueMap();
///     container.start(envVars);
/// }
/// ```
///
/// @see ParameterBinder
/// @see Parameter
/// @see Value
/// @since 0.1.0
///
public interface ParameterBinding {

    ///
    /// Returns all defined parameter bindings as an unmodifiable map.
    ///
    /// Keys are parameter names. Values are {@link Value} wrappers containing
    /// the resolved object along with provenance metadata. Ordering is
    /// deterministic — matches parameter definition order.
    ///
    /// @return unmodifiable map of parameter name → Value, never null
    ///
    Map<String, Value<?>> assignments();

    ///
    /// Returns the raw value map for defined parameters only.
    ///
    /// Keys are parameter names. Values are the unwrapped objects from each
    /// {@code Value<T>.value()}. This is the primary consumption point for
    /// systems that need a flat name→value map. Does NOT include passthrough
    /// values — use {@link #passthroughValues()} for those.
    ///
    /// Ordering is deterministic — matches parameter definition order.
    ///
    /// @return unmodifiable map of parameter name → raw value, never null
    ///
    Map<String, Object> toValueMap();

    ///
    /// Returns input values that did not match any parameter definition.
    ///
    /// When the binding policy allows passthrough, unmatched input keys are
    /// collected here rather than causing validation errors. This supports
    /// use cases like Docker ENV vars that aren't annotated with parameter
    /// metadata.
    ///
    /// @return unmodifiable map of unmatched input name → value, never null
    ///
    Map<String, Object> passthroughValues();

    ///
    /// Returns the value for a specific parameter, if bound.
    ///
    /// @param parameterName the parameter name to look up
    /// @return the bound Value if present, empty otherwise
    ///
    Optional<Value<?>> get(String parameterName);

    ///
    /// Returns the typed value for a specific parameter.
    ///
    /// Convenience method that unwraps the Value and casts to the requested type.
    ///
    /// @param <T>           the expected value type
    /// @param parameterName the parameter name to look up
    /// @param type          the expected value class
    /// @return the typed value
    /// @throws IllegalArgumentException if the parameter is not bound or
    ///         the value is not assignable to the requested type
    ///
    <T> T getValue(String parameterName, Class<T> type);

    ///
    /// Returns the parameter definitions that were used in this binding.
    ///
    /// This is the original parameter list passed to the binder, preserving
    /// order. Does NOT include passthrough parameters (those have no definition).
    ///
    /// @return unmodifiable list of parameter definitions, never null
    ///
    List<Parameter<?>> parameters();

    ///
    /// Returns a {@link ValidationResult} summarizing all validation outcomes.
    ///
    /// If all parameters bound and validated successfully, returns
    /// {@link ValidationResult.Passed}. If any parameter failed validation,
    /// returns {@link ValidationResult.Failed} with aggregate violation messages.
    ///
    /// @return aggregate validation result, never null
    ///
    ValidationResult validationResult();
}
