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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

///
/// A view over an element's parameters that distinguishes required parameters
/// from dynamically resolved parameters.
///
/// ## Concept
///
/// Elements may have parameters whose validity depends on the values of other
/// "structural" parameters. A {@code ParameterView} models this split:
///
/// - **Required parameters**: always present, must have defined values (from
///   input or defaults) before dynamic resolution can occur. Includes any
///   "type discriminator" parameters whose values determine which dynamic
///   parameters are valid.
/// - **Dynamic parameters**: resolved by an external {@link DynamicParameterResolver}
///   once required parameter values are known; must be re-resolved whenever a
///   required parameter value changes.
///
/// ## Static vs Dynamic Views
///
/// ```
/// Static View (isDynamic() = false):
///   requiredParameters() = all parameters
///   dynamicParameters()  = always empty
///
/// Dynamic View (isDynamic() = true):
///   requiredParameters() = structural/discriminator parameters
///   dynamicParameters()  = resolved from required bindings
/// ```
///
/// ## Usage
///
/// ```java
/// // Static view — all parameters are required, none are dynamic
/// ParameterView staticView = ParameterView.of(element.parameters());
///
/// // Dynamic view — required params + external resolver
/// ParameterView dynamicView = ParameterView.dynamic(
///     List.of(commandTypeParam),
///     requiredBindings -> resolveParamsForCommand(requiredBindings)
/// );
///
/// // Get all active parameters for a given configuration
/// List<Parameter<?>> active = dynamicView.activeParameters(
///     Map.of("command_type", "read")
/// );
/// ```
///
/// @see DynamicParameterResolver
/// @see io.nosqlbench.paramodel.elements.Element#parameterView()
/// @since 0.1.0
///
public interface ParameterView {

    ///
    /// Returns the required parameters — always present, must have defined values
    /// (from input or defaults) before dynamic parameters can be resolved.
    ///
    /// For static views, this returns all parameters. For dynamic views, this
    /// returns only the structural/discriminator parameters.
    ///
    /// @return unmodifiable list of required parameters, never null
    ///
    List<Parameter<?>> requiredParameters();

    ///
    /// Resolves the dynamic parameters given current required parameter bindings.
    ///
    /// Returns an empty list for static views or when required bindings are
    /// incomplete. Must be re-invoked when any required parameter value changes.
    ///
    /// @param requiredBindings current values for all required parameters
    /// @return the dynamic parameters valid for this configuration, never null
    ///
    List<Parameter<?>> dynamicParameters(Map<String, Object> requiredBindings);

    ///
    /// Returns all active parameters (required + dynamic) for the given
    /// required bindings.
    ///
    /// @param requiredBindings current values for all required parameters
    /// @return combined list of required and dynamic parameters, never null
    ///
    default List<Parameter<?>> activeParameters(Map<String, Object> requiredBindings) {
        List<Parameter<?>> dynamic = dynamicParameters(requiredBindings);
        if (dynamic.isEmpty()) {
            return requiredParameters();
        }
        List<Parameter<?>> all = new ArrayList<>(requiredParameters());
        all.addAll(dynamic);
        return Collections.unmodifiableList(all);
    }

    ///
    /// Returns whether this view has dynamic behavior.
    ///
    /// Static views always return {@code false}. Dynamic views return {@code true}
    /// when constructed with a {@link DynamicParameterResolver}.
    ///
    /// @return true if this view can resolve dynamic parameters
    ///
    default boolean isDynamic() {
        return false;
    }

    ///
    /// Creates a static view where all parameters are required and none are dynamic.
    ///
    /// @param parameters the full parameter list
    /// @return a static parameter view
    ///
    static ParameterView of(List<Parameter<?>> parameters) {
        List<Parameter<?>> immutable = List.copyOf(parameters);
        return new ParameterView() {
            @Override
            public List<Parameter<?>> requiredParameters() {
                return immutable;
            }

            @Override
            public List<Parameter<?>> dynamicParameters(Map<String, Object> requiredBindings) {
                return List.of();
            }
        };
    }

    ///
    /// Creates a dynamic view with required parameters and an external resolver.
    ///
    /// @param requiredParameters the required/structural parameters
    /// @param resolver           the resolver for dynamic parameters
    /// @return a dynamic parameter view
    ///
    static ParameterView dynamic(List<Parameter<?>> requiredParameters,
                                  DynamicParameterResolver resolver) {
        List<Parameter<?>> immutableRequired = List.copyOf(requiredParameters);
        return new ParameterView() {
            @Override
            public List<Parameter<?>> requiredParameters() {
                return immutableRequired;
            }

            @Override
            public List<Parameter<?>> dynamicParameters(Map<String, Object> requiredBindings) {
                return resolver.resolve(requiredBindings);
            }

            @Override
            public boolean isDynamic() {
                return true;
            }
        };
    }
}
