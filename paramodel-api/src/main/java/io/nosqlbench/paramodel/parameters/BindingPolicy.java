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

///
/// Controls how the binder handles edge cases during parameter binding.
///
/// ## Policy Dimensions
///
/// | Policy             | STRICT  | LENIENT |
/// |--------------------|---------|---------|
/// | Unknown inputs     | Reject  | Pass through |
/// | Validation errors  | Fail fast | Collect all |
/// | String coercion    | Enabled | Enabled |
///
/// ## Usage
///
/// ```java
/// ParameterBinder strictBinder = new DefaultParameterBinder(BindingPolicy.STRICT);
/// ParameterBinder lenientBinder = new DefaultParameterBinder(BindingPolicy.LENIENT);
///
/// // Custom policy
/// BindingPolicy custom = new BindingPolicy(true, true, false);
/// ```
///
/// @param allowPassthrough whether to pass through input values that don't match
///        any parameter definition. If false, unknown inputs cause a validation error.
/// @param failFast whether to fail on the first validation error or collect all errors.
///        Strict mode fails fast; lenient mode collects all violations.
/// @param coerceTypes whether to coerce string inputs to parameter domain types.
///        For example, {@code "42"} → {@code Integer 42} for integer parameters.
/// @since 0.1.0
///
public record BindingPolicy(
    boolean allowPassthrough,
    boolean failFast,
    boolean coerceTypes
) {
    /// Strict policy: reject unknown inputs, fail on first error, coerce types.
    public static final BindingPolicy STRICT = new BindingPolicy(false, true, true);

    /// Lenient policy: pass through unknowns, collect all errors, coerce types.
    public static final BindingPolicy LENIENT = new BindingPolicy(true, false, true);
}
