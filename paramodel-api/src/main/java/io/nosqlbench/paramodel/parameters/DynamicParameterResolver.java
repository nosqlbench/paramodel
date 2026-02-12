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

///
/// Resolves the dynamic parameter set for an element given bound required parameter values.
///
/// ## Concept
///
/// A {@code DynamicParameterResolver} externalizes the resolution of dynamic parameters
/// whose validity depends on the values of required (structural) parameters. This supports
/// scenarios where a "type discriminator" parameter determines which additional parameters
/// are valid — for example, selecting a command type that changes the available options.
///
/// The resolver is intentionally opaque: the host system implements it however it needs to.
/// It parallels the existing {@link io.nosqlbench.paramodel.parameters.types.SelectionResolver}
/// pattern but for the parameter list itself rather than individual parameter values.
///
/// ## Contract
///
/// - Implementations MUST return a non-null list (may be empty)
/// - Implementations MUST be idempotent for the same input bindings
/// - Implementations SHOULD be thread-safe
/// - The resolver is called whenever a required parameter value changes
///
/// ## Example
///
/// ```java
/// DynamicParameterResolver resolver = requiredBindings -> {
///     String commandType = (String) requiredBindings.get("command_type");
///     return switch (commandType) {
///         case "read" -> List.of(
///             IntegerParameter.range("consistency_level", 1, 3),
///             BooleanParameter.of("allow_filtering")
///         );
///         case "write" -> List.of(
///             IntegerParameter.range("ttl", 0, 86400),
///             StringParameter.of("timestamp_format")
///         );
///         default -> List.of();
///     };
/// };
/// ```
///
/// @see ParameterView
/// @since 0.1.0
///
@FunctionalInterface
public interface DynamicParameterResolver {

    ///
    /// Resolves the dynamic parameters given bound required parameter values.
    ///
    /// Called whenever a required parameter value changes. The returned list
    /// represents the full set of dynamic parameters valid for the given
    /// required parameter configuration.
    ///
    /// @param requiredBindings current values for all required parameters
    /// @return the dynamic parameters valid for this configuration, never null
    ///
    List<Parameter<?>> resolve(Map<String, Object> requiredBindings);
}
