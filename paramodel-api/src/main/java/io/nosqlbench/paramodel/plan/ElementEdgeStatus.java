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
package io.nosqlbench.paramodel.plan;

///
/// Status of a dependency edge between two elements in a {@link LiveElementGraph}.
///
/// Element edge statuses are derived from the upstream element's
/// {@link io.nosqlbench.paramodel.elements.Element.OperationalState OperationalState}.
///
/// ## Derivation Rules
///
/// ```
/// Element Edge Status Derivation:
///   upstream READY or RUNNING → SATISFIED
///   upstream FAILED            → FAILED
///   upstream TERMINATED        → TERMINATED
///   otherwise                  → PENDING
/// ```
///
/// @see LiveElementGraph
///
public enum ElementEdgeStatus {

    /// The dependency is satisfied — upstream element is READY or RUNNING.
    SATISFIED,

    /// The dependency is not yet satisfied — upstream element has not reached READY.
    PENDING,

    /// The dependency can never be satisfied because the upstream element FAILED.
    FAILED,

    /// The upstream element has been torn down.
    TERMINATED
}
