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

import io.nosqlbench.paramodel.elements.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

///
/// Binds raw input values to parameter definitions, producing a {@link ParameterBinding}.
///
/// ## Binding Algorithm
///
/// ```
/// For each Parameter<T> in the definition set:
///   1. Look up input value by parameter.name()
///   2. If present:
///      a. Coerce to parameter's domain type (if coerceTypes enabled)
///      b. Validate against domain and constraints
///      c. Wrap in Value<T> with provenance metadata
///   3. If absent:
///      a. Use default value if available (from parameter.defaultValue())
///      b. If no default → validation error
///   4. Record in binding
///
/// After independent parameters are bound:
///   5. Evaluate DerivedParameter instances using bound values as inputs
///   6. Validate derived values against their domains/constraints
///
/// For remaining input keys not matching any parameter:
///   7. If allowPassthrough: store in passthroughValues()
///   8. If !allowPassthrough: record as validation error
/// ```
///
/// ## Type Coercion
///
/// When {@code coerceTypes} is enabled, the binder performs these coercions:
///
/// ```
/// Input Type    Target Type    Coercion
/// ──────────────────────────────────────────────────
/// String        Integer        Integer.parseInt(input)
/// String        Double         Double.parseDouble(input)
/// String        Boolean        Boolean.parseBoolean(input)
/// String        List<String>   List.of(input)  (single-element list)
/// Number        Integer        number.intValue()
/// Number        Double         number.doubleValue()
/// ```
///
/// ## Thread Safety
///
/// ParameterBinder implementations MUST be thread-safe. The same binder
/// instance can be used concurrently from multiple threads.
///
/// @see ParameterBinding
/// @see BindingPolicy
/// @see DerivedParameter
/// @since 0.1.0
///
public interface ParameterBinder {

    ///
    /// Binds input values to the given parameters.
    ///
    /// @param parameters the parameter definitions
    /// @param inputs     raw input values (typically String-keyed)
    /// @return the binding result with validation status
    ///
    ParameterBinding bind(List<Parameter<?>> parameters, Map<String, Object> inputs);

    ///
    /// Binds input values using a {@link ParameterView}, resolving dynamic parameters
    /// from the required parameter bindings.
    ///
    /// ## Algorithm
    ///
    /// 1. Bind required parameters from inputs
    /// 2. If the view is dynamic, resolve dynamic parameters using required bindings
    /// 3. Bind dynamic parameters from remaining inputs
    /// 4. Combine into a single {@link ParameterBinding}
    ///
    /// @param view   the parameter view defining required and dynamic parameters
    /// @param inputs raw input values
    /// @return the binding result with validation status
    ///
    default ParameterBinding bind(ParameterView view, Map<String, Object> inputs) {
        ParameterBinding requiredBinding = bind(view.requiredParameters(), inputs);

        if (!view.isDynamic()) {
            return requiredBinding;
        }

        List<Parameter<?>> dynamicParams =
            view.dynamicParameters(requiredBinding.toValueMap());
        if (dynamicParams.isEmpty()) {
            return requiredBinding;
        }
        ParameterBinding dynamicBinding = bind(dynamicParams, inputs);

        return mergedBinding(requiredBinding, dynamicBinding);
    }

    ///
    /// Binds input values to an Element's parameters using its {@link ParameterView}.
    ///
    /// Convenience overload that delegates to {@link #bind(ParameterView, Map)}
    /// using the element's parameter view.
    ///
    /// @param element the element whose parameters to bind
    /// @param inputs  raw input values
    /// @return the binding result with validation status
    ///
    default ParameterBinding bind(Element element, Map<String, Object> inputs) {
        return bind(element.parameterView(), inputs);
    }

    ///
    /// Merges two {@link ParameterBinding}s into one, combining assignments,
    /// passthroughs, parameter lists, and validation results.
    ///
    /// Used internally to combine the results of binding required parameters
    /// and dynamic parameters into a single unified binding.
    ///
    /// @param first  the first binding (typically required parameters)
    /// @param second the second binding (typically dynamic parameters)
    /// @return a merged binding combining both results
    ///
    static ParameterBinding mergedBinding(ParameterBinding first, ParameterBinding second) {
        LinkedHashMap<String, Value<?>> mergedAssignments = new LinkedHashMap<>(first.assignments());
        mergedAssignments.putAll(second.assignments());

        LinkedHashMap<String, Object> mergedPassthrough = new LinkedHashMap<>(first.passthroughValues());
        // Remove from passthrough any keys that the second binding consumed as assignments
        for (String key : second.assignments().keySet()) {
            mergedPassthrough.remove(key);
        }
        mergedPassthrough.putAll(second.passthroughValues());

        List<Parameter<?>> mergedParams = new ArrayList<>(first.parameters());
        mergedParams.addAll(second.parameters());

        ValidationResult mergedValidation;
        if (first.validationResult().isFailed() || second.validationResult().isFailed()) {
            List<String> allViolations = new ArrayList<>(first.validationResult().violations());
            allViolations.addAll(second.validationResult().violations());
            mergedValidation = new ValidationResult.Failed(
                "Binding validation failed", List.copyOf(allViolations));
        } else {
            mergedValidation = new ValidationResult.Passed();
        }

        Map<String, Value<?>> finalAssignments = Map.copyOf(mergedAssignments);
        Map<String, Object> finalPassthrough = Map.copyOf(mergedPassthrough);
        List<Parameter<?>> finalParams = List.copyOf(mergedParams);

        return new ParameterBinding() {
            @Override
            public Map<String, Value<?>> assignments() {
                return finalAssignments;
            }

            @Override
            public Map<String, Object> toValueMap() {
                LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                for (var entry : finalAssignments.entrySet()) {
                    values.put(entry.getKey(), entry.getValue().value());
                }
                return Map.copyOf(values);
            }

            @Override
            public Map<String, Object> passthroughValues() {
                return finalPassthrough;
            }

            @Override
            public java.util.Optional<Value<?>> get(String parameterName) {
                return java.util.Optional.ofNullable(finalAssignments.get(parameterName));
            }

            @Override
            public <T> T getValue(String parameterName, Class<T> type) {
                Value<?> value = finalAssignments.get(parameterName);
                if (value == null) {
                    throw new IllegalArgumentException(
                        "Parameter '" + parameterName + "' is not bound");
                }
                Object raw = value.value();
                if (!type.isInstance(raw)) {
                    throw new IllegalArgumentException(
                        "Parameter '" + parameterName + "' value is " +
                        raw.getClass().getName() + ", not " + type.getName());
                }
                return type.cast(raw);
            }

            @Override
            public List<Parameter<?>> parameters() {
                return finalParams;
            }

            @Override
            public ValidationResult validationResult() {
                return mergedValidation;
            }
        };
    }
}
