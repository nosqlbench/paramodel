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
package io.nosqlbench.paramodel.engine.planners.reducto;

///
/// A warning emitted during reducto graph construction.
///
/// @param code     warning code (e.g. "W001", "W002")
/// @param severity severity level
/// @param message  human-readable warning message
///
public record ReductoWarning(String code, Severity severity, String message) {

    /// Warning severity levels.
    public enum Severity {
        /// Informational — notable but harmless.
        INFO,
        /// Warning — valid but likely unintended behavior.
        WARN,
        /// Error — semantically valid but operationally problematic.
        ERROR
    }

    /// Creates a W001 warning for broad-scope exclusive dependency serialization.
    ///
    /// @param exclusiveElement the element with the exclusive dependency
    /// @param targetElement    the broad-scope target
    /// @param targetLevel      the coalescing level of the target
    /// @param dependentCount   number of exclusive dependents serialized
    /// @return the warning
    public static ReductoWarning w001(String exclusiveElement, String targetElement,
                                      int targetLevel, int dependentCount) {
        return new ReductoWarning("W001", Severity.WARN,
            "Element '" + exclusiveElement + "' exclusively depends on '" + targetElement
                + "' which is scoped to level " + targetLevel + ". This serializes all "
                + dependentCount + " exclusive dependents of '" + targetElement
                + "' across the entire level-" + targetLevel + " group. Consider adding "
                + "intermediate elements to narrow the exclusivity scope, or verify that "
                + "full serialization is intended.");
    }

    /// Creates a W002 warning for unsatisfiable mutual exclusivity.
    ///
    /// @param elementX  first exclusive dependent
    /// @param elementZ  second exclusive dependent
    /// @param targetY   the shared exclusive target
    /// @param trialId   the trial where the conflict occurs
    /// @return the warning
    public static ReductoWarning w002(String elementX, String elementZ,
                                      String targetY, int trialId) {
        return new ReductoWarning("W002", Severity.ERROR,
            "Elements '" + elementX + "' and '" + elementZ + "' both exclusively depend on '"
                + targetY + "' within trial " + trialId + ". Mutual exclusivity cannot be "
                + "satisfied when both must be active simultaneously. Restructure dependencies "
                + "so that at most one element exclusively depends on '" + targetY + "' per trial.");
    }
}
