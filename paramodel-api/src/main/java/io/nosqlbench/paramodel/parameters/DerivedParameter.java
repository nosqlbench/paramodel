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

import java.util.Map;

///
/// A parameter whose value is computed from other bound parameter values.
///
/// Derived parameters are evaluated AFTER independent parameters are bound.
/// They participate in validation but not in direct user input.
///
/// ## Evaluation Order
///
/// ```
/// Binding Phase:
///   1. Bind independent parameters (from user input or defaults)
///   2. Evaluate derived parameters using bound values
///   3. Validate derived values against domain/constraints
///   4. Record in binding result
/// ```
///
/// ## Interaction with Axis
///
/// Derived parameters SHOULD NOT be used as axes in test plans.
/// Their values are deterministic functions of independent parameters,
/// so varying them independently would create contradictions.
///
/// ## Example
///
/// ```java
/// DerivedParameter<Integer> batchSize = new DerivedParameter<>() {
///     // ... name(), domain(), etc. ...
///
///     @Override
///     public Integer compute(Map<String, Object> boundValues) {
///         int threads = (int) boundValues.get("threads");
///         return threads * 2;
///     }
///
///     @Override
///     public String expression() {
///         return "threads * 2";
///     }
/// };
/// ```
///
/// @param <T> the type of the computed value
/// @see Parameter
/// @see ParameterBinder
/// @since 0.1.0
///
public interface DerivedParameter<T> extends Parameter<T> {

    ///
    /// Computes the value from already-bound parameter values.
    ///
    /// ## Contract
    ///
    /// - MUST NOT modify the input map
    /// - MUST return a value within this parameter's domain
    /// - SHOULD throw IllegalArgumentException if required inputs are missing
    /// - MUST be deterministic (same inputs → same output)
    ///
    /// @param boundValues current bindings (name → value) for all independent parameters
    /// @return the computed value
    /// @throws IllegalArgumentException if required input parameters are missing
    ///
    T compute(Map<String, Object> boundValues);

    ///
    /// Returns a human-readable expression describing the derivation.
    ///
    /// Used for documentation, logging, and debugging.
    ///
    /// ## Examples
    ///
    /// ```
    /// "threads * 2"
    /// "base_memory + overhead"
    /// "ceil(dataset_size / batch_size)"
    /// ```
    ///
    /// @return expression string, never null
    ///
    String expression();
}
