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
    /// Binds input values to an Element's parameters.
    ///
    /// Convenience overload that extracts parameters from the element.
    ///
    /// @param element the element whose parameters to bind
    /// @param inputs  raw input values
    /// @return the binding result with validation status
    ///
    default ParameterBinding bind(Element element, Map<String, Object> inputs) {
        return bind(element.parameters(), inputs);
    }
}
