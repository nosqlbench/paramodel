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

import io.nosqlbench.paramodel.elements.Element;

import java.util.*;

///
/// Computes element binding states from parameter rank assignments.
///
/// An element with K varying parameters occupying contiguous ranks R through R+K-1
/// becomes concretely bound at group level R+K. This means all its parameters have
/// determined values at that level, and it can be activated.
///
/// Elements with no varying parameters (fixed or no parameters) are run-scoped
/// (binding level 0) and need only a single activation for the entire graph.
///
public final class BindingStateComputer {

    /// Per-element binding information.
    ///
    /// @param elementName   element name
    /// @param bindingLevel  the group level at which the element becomes concretely bound
    /// @param firstRank     the first parameter rank owned by this element (-1 if run-scoped)
    /// @param parameterCount number of varying parameters
    public record ElementBinding(String elementName, int bindingLevel,
                                  int firstRank, int parameterCount) {

        /// Returns true if this element is run-scoped (no varying parameters).
        public boolean isRunScoped() { return parameterCount == 0; }

        /// Returns true if this element is bound at the per-trial level.
        ///
        /// @param totalRanks total number of parameter ranks in the enumeration
        /// @return true if binding level equals total ranks
        public boolean isTrialScoped(int totalRanks) { return bindingLevel == totalRanks; }
    }

    private final Map<String, ElementBinding> bindings;
    private final int totalRanks;

    private BindingStateComputer(Map<String, ElementBinding> bindings, int totalRanks) {
        this.bindings = Map.copyOf(bindings);
        this.totalRanks = totalRanks;
    }

    /// Computes binding state for all elements given an enumerator.
    ///
    /// @param sortedElements topologically sorted elements
    /// @param enumerator     the mixed-radix enumerator providing rank assignments
    /// @return the binding state computer
    public static BindingStateComputer compute(List<Element> sortedElements,
                                                MixedRadixEnumerator enumerator) {
        Map<String, ElementBinding> bindings = new LinkedHashMap<>();

        for (Element elem : sortedElements) {
            int firstRank = -1;
            int paramCount = 0;

            for (int rank = 0; rank < enumerator.rankCount(); rank++) {
                if (elem.name().equals(enumerator.elementName(rank))) {
                    if (firstRank < 0) firstRank = rank;
                    paramCount++;
                }
            }

            int bindingLevel = (paramCount > 0) ? (firstRank + paramCount) : 0;
            bindings.put(elem.name(), new ElementBinding(elem.name(), bindingLevel, firstRank, paramCount));
        }

        return new BindingStateComputer(bindings, enumerator.rankCount());
    }

    /// Returns the binding information for the given element.
    ///
    /// @param elementName element name
    /// @return the binding, or null if not found
    public ElementBinding binding(String elementName) {
        return bindings.get(elementName);
    }

    /// Returns all element bindings.
    ///
    /// @return unmodifiable map of element name to binding
    public Map<String, ElementBinding> allBindings() {
        return bindings;
    }

    /// Returns the total number of parameter ranks.
    public int totalRanks() { return totalRanks; }

    /// Returns the number of groups at the given level.
    ///
    /// @param enumerator the enumerator
    /// @param level      group level
    /// @return number of groups
    public int groupCount(MixedRadixEnumerator enumerator, int level) {
        return enumerator.groupCount(level);
    }

    /// Returns true if two trial numbers belong to the same group for the given element.
    ///
    /// @param elementName element name
    /// @param enumerator  the enumerator
    /// @param t1          first trial number
    /// @param t2          second trial number
    /// @return true if same group
    public boolean sameGroupForElement(String elementName, MixedRadixEnumerator enumerator,
                                        long t1, long t2) {
        ElementBinding eb = bindings.get(elementName);
        if (eb == null || eb.isRunScoped()) return true;
        return enumerator.sameGroup(t1, t2, eb.bindingLevel());
    }

    /// Returns the group index for a trial and element.
    ///
    /// @param elementName element name
    /// @param enumerator  the enumerator
    /// @param trialNumber trial number
    /// @return group index
    public int groupIndexForElement(String elementName, MixedRadixEnumerator enumerator,
                                     long trialNumber) {
        ElementBinding eb = bindings.get(elementName);
        if (eb == null || eb.isRunScoped()) return 0;
        return enumerator.groupIndex(trialNumber, eb.bindingLevel());
    }
}
