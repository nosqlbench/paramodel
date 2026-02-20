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
package io.nosqlbench.paramodel.engine.planners.reducto.rules;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.planners.reducto.BindingStateComputer;
import io.nosqlbench.paramodel.engine.planners.reducto.MixedRadixEnumerator;
import io.nosqlbench.paramodel.engine.planners.reducto.ReductoWarning;

import java.util.*;

///
/// Shared context passed to all reducto transformation rules.
///
/// Bundles the element metadata, binding state, enumerator, and a warning
/// collector that rules populate during graph construction.
///
public final class RuleContext {

    private final List<Element> sortedElements;
    private final Map<String, Element> elementsByName;
    private final BindingStateComputer bindingState;
    private final MixedRadixEnumerator enumerator;
    private final List<String> trialElementNames;
    private final Map<String, Set<String>> lifelineClusters;
    private final Map<String, List<Element>> dedicatedDependents;
    private final List<ReductoWarning> warnings = new ArrayList<>();
    private int nodeCounter = 0;

    /// Creates a new rule context.
    ///
    /// @param sortedElements      topologically sorted elements
    /// @param bindingState         binding state computer
    /// @param enumerator           mixed-radix enumerator
    /// @param trialElementNames    names of trial elements
    /// @param lifelineClusters     lifeline cluster map
    /// @param dedicatedDependents  reverse DEDICATED dependency map
    public RuleContext(List<Element> sortedElements,
                       BindingStateComputer bindingState,
                       MixedRadixEnumerator enumerator,
                       List<String> trialElementNames,
                       Map<String, Set<String>> lifelineClusters,
                       Map<String, List<Element>> dedicatedDependents) {
        this.sortedElements = List.copyOf(sortedElements);
        this.elementsByName = new LinkedHashMap<>();
        for (Element e : sortedElements) {
            this.elementsByName.put(e.name(), e);
        }
        this.bindingState = bindingState;
        this.enumerator = enumerator;
        this.trialElementNames = List.copyOf(trialElementNames);
        this.lifelineClusters = lifelineClusters;
        this.dedicatedDependents = dedicatedDependents;
    }

    /// Returns the topologically sorted elements.
    public List<Element> sortedElements() { return sortedElements; }

    /// Returns the element with the given name, or null.
    public Element element(String name) { return elementsByName.get(name); }

    /// Returns the binding state computer.
    public BindingStateComputer bindingState() { return bindingState; }

    /// Returns the mixed-radix enumerator.
    public MixedRadixEnumerator enumerator() { return enumerator; }

    /// Returns the list of trial element names.
    public List<String> trialElementNames() { return trialElementNames; }

    /// Returns true if the given element is a trial element.
    public boolean isTrialElement(String elementName) { return trialElementNames.contains(elementName); }

    /// Returns the lifeline clusters map.
    public Map<String, Set<String>> lifelineClusters() { return lifelineClusters; }

    /// Returns the reverse DEDICATED dependency map.
    public Map<String, List<Element>> dedicatedDependents() { return dedicatedDependents; }

    /// Returns the collected warnings.
    public List<ReductoWarning> warnings() { return Collections.unmodifiableList(warnings); }

    /// Adds a warning.
    public void addWarning(ReductoWarning warning) { warnings.add(warning); }

    /// Generates a unique node ID with the given prefix.
    ///
    /// @param prefix ID prefix
    /// @return unique node ID
    public String nextNodeId(String prefix) {
        return prefix + "_" + (nodeCounter++);
    }

    /// Returns all element names.
    public List<String> allElementNames() {
        return new ArrayList<>(elementsByName.keySet());
    }
}
