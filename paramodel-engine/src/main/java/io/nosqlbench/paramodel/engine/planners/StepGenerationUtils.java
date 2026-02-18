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
package io.nosqlbench.paramodel.engine.planners;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.compiler.AxisBindingSet;
import io.nosqlbench.paramodel.plan.TestPlan;
import io.nosqlbench.paramodel.sequence.Trial;

import java.util.*;

///
/// Shared utility methods for step generation strategies.
///
/// All methods are pure/stateless and can be used by any
/// {@link StepGenerationStrategy} implementation.
///
public final class StepGenerationUtils {

    private StepGenerationUtils() {}

    /// Performs a topological sort of elements by their dependency graph.
    ///
    /// Elements with no dependencies appear first; dependents follow their
    /// targets.  Ties are broken by insertion order.
    ///
    /// @param elements elements to sort
    /// @return topologically sorted list
    public static List<Element> topologicalSort(List<Element> elements) {
        Map<String, Element> byName = new LinkedHashMap<>();
        for (Element e : elements) byName.put(e.name(), e);
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        for (Element e : elements) {
            inDegree.putIfAbsent(e.name(), 0);
            for (Element.Dependency dep : e.dependencies()) {
                adj.computeIfAbsent(dep.target().name(), k -> new ArrayList<>()).add(e.name());
                inDegree.merge(e.name(), 1, Integer::sum);
                inDegree.putIfAbsent(dep.target().name(), 0);
            }
        }
        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }
        List<Element> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            Element e = byName.get(name);
            if (e != null) result.add(e);
            for (String dependent : adj.getOrDefault(name, List.of())) {
                inDegree.put(dependent, inDegree.get(dependent) - 1);
                if (inDegree.get(dependent) == 0) queue.add(dependent);
            }
        }
        return result;
    }

    /// Computes a fingerprint for the element's configuration in the given trial.
    ///
    /// The fingerprint changes when any of the following change:
    /// - The element's own parameters (via trial assignments)
    /// - Axes that explicitly target the element
    /// - Forward dependencies' fingerprints (recursive)
    /// - DEDICATED reverse dependencies' parameter fingerprints
    ///
    /// @param element              the element to fingerprint
    /// @param trial                the trial providing parameter assignments
    /// @param plan                 the test plan (for axis information)
    /// @param dedicatedDependents  reverse DEDICATED dependency map
    /// @return fingerprint string
    public static String computeElementFingerprint(
            Element element, Trial trial, TestPlan plan,
            Map<String, List<Element>> dedicatedDependents) {
        TreeMap<String, String> sortedFingerprints = new TreeMap<>();
        for (var param : element.parameters()) {
            trial.assignment(param.name()).ifPresent(value ->
                sortedFingerprints.put(param.name(), value.fingerprint())
            );
        }
        for (var axis : plan.axes()) {
            if (axis.targetElement().map(t -> t.equals(element.name())).orElse(false)) {
                String qualifiedKey = element.name() + "." + axis.name();
                trial.assignment(qualifiedKey).ifPresent(value ->
                    sortedFingerprints.put(axis.name(), value.fingerprint()));
            }
        }
        for (Element.Dependency dep : element.dependencies()) {
            String depFingerprint = computeElementFingerprint(dep.target(), trial, plan, dedicatedDependents);
            sortedFingerprints.put("__dep:" + dep.target().name(), depFingerprint);
        }
        List<Element> dedicatedRevDeps = dedicatedDependents.getOrDefault(element.name(), List.of());
        for (Element dependent : dedicatedRevDeps) {
            for (var param : dependent.parameters()) {
                trial.assignment(param.name()).ifPresent(value ->
                    sortedFingerprints.put("__dedicated:" + dependent.name() + ":" + param.name(),
                        value.fingerprint()));
            }
            for (var axis : plan.axes()) {
                if (axis.targetElement().map(t -> t.equals(dependent.name())).orElse(false)) {
                    String qualifiedKey = dependent.name() + "." + axis.name();
                    trial.assignment(qualifiedKey).ifPresent(value ->
                        sortedFingerprints.put("__dedicated:" + dependent.name() + ":" + axis.name(),
                            value.fingerprint()));
                }
            }
        }
        if (sortedFingerprints.isEmpty()) {
            return "static:" + element.name();
        }
        return String.join("|", sortedFingerprints.values());
    }

    /// Identifies trial elements using a scope-aware, override-respecting algorithm.
    ///
    /// Implements **Design Rule 1** (trial element identity): trial elements
    /// are the innermost leaf nodes, even when the innermost layer is also
    /// the outermost layer.
    ///
    /// @param sortedElements    topologically sorted elements
    /// @param effectiveBindings resolved axis bindings per element
    /// @return list of trial element names
    public static List<String> identifyTrialElements(
            List<Element> sortedElements,
            Map<String, AxisBindingSet> effectiveBindings) {
        List<String> forcedOn = new ArrayList<>();
        Set<String> forcedOff = new HashSet<>();
        for (Element e : sortedElements) {
            e.trialElement().ifPresent(val -> {
                if (val) forcedOn.add(e.name());
                else forcedOff.add(e.name());
            });
        }

        Set<String> trialScoped = new HashSet<>();
        for (Element e : sortedElements) {
            if (forcedOff.contains(e.name())) continue;
            AxisBindingSet binding = resolveBinding(e, effectiveBindings);
            if (!binding.isRunScoped()) {
                trialScoped.add(e.name());
            }
        }

        Set<String> candidatePool;
        if (!trialScoped.isEmpty()) {
            candidatePool = trialScoped;
        } else {
            candidatePool = new HashSet<>();
            for (Element e : sortedElements) {
                if (!forcedOff.contains(e.name())) {
                    candidatePool.add(e.name());
                }
            }
        }

        Set<String> hasCandidateDependent = new HashSet<>();
        for (Element e : sortedElements) {
            if (!candidatePool.contains(e.name())) continue;
            for (Element.Dependency dep : e.dependencies()) {
                if (candidatePool.contains(dep.target().name())) {
                    hasCandidateDependent.add(dep.target().name());
                }
            }
        }

        Set<String> result = new LinkedHashSet<>(forcedOn);
        for (String name : candidatePool) {
            if (forcedOff.contains(name)) continue;
            if (!hasCandidateDependent.contains(name)) {
                result.add(name);
            }
        }
        return new ArrayList<>(result);
    }

    /// Computes lifeline clusters using union-find.
    ///
    /// Elements connected by {@link RelationshipType#LIFELINE} relationships
    /// form clusters where the root element controls the lifecycle of all
    /// members.
    ///
    /// @param elements all elements
    /// @return map from cluster root name to set of member names (only clusters with size > 1)
    public static Map<String, Set<String>> computeLifelineClusters(List<Element> elements) {
        Map<String, String> parent = new HashMap<>();
        for (Element e : elements) parent.put(e.name(), e.name());
        for (Element e : elements) {
            for (Element.Dependency dep : e.dependencies()) {
                if (dep.type() == RelationshipType.LIFELINE) {
                    lifelineUnion(parent, e.name(), dep.target().name());
                }
            }
        }
        Map<String, Set<String>> clusters = new HashMap<>();
        for (Element e : elements) {
            String root = lifelineFind(parent, e.name());
            clusters.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(e.name());
        }
        clusters.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        Map<String, Set<String>> result = new HashMap<>();
        for (var entry : clusters.entrySet()) {
            String clusterRoot = null;
            for (Element e : elements) {
                if (entry.getValue().contains(e.name())) {
                    clusterRoot = e.name();
                    break;
                }
            }
            result.put(clusterRoot, entry.getValue());
        }
        return result;
    }

    /// Returns {@code true} if the element is subsumed by a lifeline
    /// relationship (all its dependencies are LIFELINE).
    ///
    /// @param element the element to check
    /// @return true if lifeline-subsumed
    public static boolean isLifelineSubsumed(Element element) {
        List<Element.Dependency> deps = element.dependencies();
        if (deps.isEmpty()) return false;
        return deps.stream().allMatch(d -> d.type() == RelationshipType.LIFELINE);
    }

    /// Resolves the effective axis binding for an element.
    ///
    /// @param element            the element
    /// @param effectiveBindings  binding map from NormalizationStage
    /// @return the binding set, or {@link AxisBindingSet#runScoped()} if none
    public static AxisBindingSet resolveBinding(Element element, Map<String, AxisBindingSet> effectiveBindings) {
        AxisBindingSet fromContext = effectiveBindings.get(element.name());
        if (fromContext != null) return fromContext;
        return AxisBindingSet.runScoped();
    }

    /// Computes the axis-value path for a trial, used for nesting visualization.
    ///
    /// @param trial the trial
    /// @param axes  the plan's axes
    /// @return ordered list of axis-value strings
    public static List<String> computeTrialAxisPath(
            Trial trial,
            List<? extends io.nosqlbench.paramodel.plan.Axis<?>> axes) {
        List<String> path = new ArrayList<>();
        for (var axis : axes) {
            String qualifiedKey = axis.targetElement()
                .map(elem -> elem + "." + axis.name())
                .orElse(axis.name());
            String value = trial.assignment(qualifiedKey)
                .map(v -> String.valueOf(v.value()))
                .orElseGet(() -> trial.assignment(axis.name())
                    .map(v -> String.valueOf(v.value()))
                    .orElse("?"));
            path.add(value);
        }
        return path;
    }

    /// Builds a metadata map from alternating key-value pairs.
    ///
    /// @param kvPairs alternating String keys and Object values
    /// @return linked map preserving insertion order
    public static Map<String, Object> meta(Object... kvPairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            map.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }

    /// Prunes transitive dependencies from a dependency list.
    ///
    /// If step A depends on step B and B depends on C, then A's explicit
    /// dependency on C is redundant and can be removed.
    ///
    /// @param elements           all elements
    /// @param lastStepForElement map of element name to its latest step ID
    /// @param trialDeployMap     map of element name to its trial deploy step ID
    /// @param deps               candidate dependency list
    /// @return minimal dependency list with transitive redundancies removed
    public static List<String> minimalDeps(
            List<Element> elements,
            Map<String, String> lastStepForElement,
            Map<String, String> trialDeployMap,
            List<String> deps) {

        Map<String, String> stepToElement = new HashMap<>();
        for (Element e : elements) {
            String step = lastStepForElement.get(e.name());
            if (step != null) stepToElement.put(step, e.name());
            step = trialDeployMap.get(e.name());
            if (step != null) stepToElement.put(step, e.name());
        }

        Map<String, Set<String>> transitiveUpstream = new HashMap<>();
        for (Element e : elements) {
            transitiveUpstream.computeIfAbsent(e.name(), k -> {
                Set<String> upstream = new HashSet<>();
                collectUpstream(e, upstream);
                return upstream;
            });
        }

        Set<String> depSet = new LinkedHashSet<>(deps);
        Set<String> covered = new HashSet<>();
        for (String dep : depSet) {
            String elementName = stepToElement.get(dep);
            if (elementName == null) continue;
            for (String otherDep : depSet) {
                if (otherDep.equals(dep)) continue;
                String otherElement = stepToElement.get(otherDep);
                if (otherElement == null) continue;
                Set<String> otherUpstream = transitiveUpstream.get(otherElement);
                if (otherUpstream != null && otherUpstream.contains(elementName)) {
                    covered.add(dep);
                    break;
                }
            }
        }

        List<String> minimal = new ArrayList<>();
        for (String dep : deps) {
            if (!covered.contains(dep)) {
                minimal.add(dep);
            }
        }
        return minimal;
    }

    /// Computes direct dependency step IDs for an element.
    ///
    /// @param element            the element whose dependencies to resolve
    /// @param lastStepForElement map of element name to its latest step ID
    /// @return list of dependency step IDs
    public static List<String> computeDependencies(Element element, Map<String, String> lastStepForElement) {
        List<String> deps = new ArrayList<>();
        for (Element.Dependency dep : element.dependencies()) {
            String lastStep = lastStepForElement.get(dep.target().name());
            if (lastStep != null) {
                deps.add(lastStep);
            }
        }
        return deps;
    }

    /// Recursively collects all upstream (transitive) dependency names.
    ///
    /// @param element  the starting element
    /// @param upstream accumulator set
    public static void collectUpstream(Element element, Set<String> upstream) {
        for (Element.Dependency dep : element.dependencies()) {
            if (upstream.add(dep.target().name())) {
                collectUpstream(dep.target(), upstream);
            }
        }
    }

    // --- private helpers for lifeline union-find ---

    private static String lifelineFind(Map<String, String> parent, String x) {
        while (!parent.get(x).equals(x)) {
            parent.put(x, parent.get(parent.get(x)));
            x = parent.get(x);
        }
        return x;
    }

    private static void lifelineUnion(Map<String, String> parent, String a, String b) {
        String ra = lifelineFind(parent, a), rb = lifelineFind(parent, b);
        if (!ra.equals(rb)) parent.put(ra, rb);
    }
}
