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
package io.nosqlbench.paramodel.engine.binding;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.parameters.*;

import java.util.*;

///
/// Default implementation of {@link ElementBindingTree}.
///
/// Built via the fluent {@link Builder} which accepts the element list,
/// global inputs, per-element local inputs, and a binder. The builder
/// performs topological sorting (Kahn's algorithm), cascades inputs
/// through the dependency graph, and binds each element's parameters.
///
/// ## Example
///
/// ```java
/// ElementBindingTree tree = DefaultElementBindingTree.builder("my-tree")
///     .elements(List.of(storage, database, cache, app))
///     .globalInputs(Map.of("region", "us-east-1"))
///     .elementInputs("database", Map.of("port", 5432))
///     .elementInputs("cache", Map.of("ttl", 60))
///     .binder(new DefaultParameterBinder(BindingPolicy.LENIENT))
///     .build();
/// ```
///
/// @see DefaultBindingNode
/// @see ParameterBinder
/// @since 0.1.0
///
public class DefaultElementBindingTree implements ElementBindingTree {

    private final String name;
    private final DefaultBindingNode rootNode;
    private final Map<String, DefaultBindingNode> nodesByName;
    private final List<BindingNode> orderedNodes;
    private final ValidationResult aggregateValidation;

    private DefaultElementBindingTree(
            String name,
            DefaultBindingNode rootNode,
            Map<String, DefaultBindingNode> nodesByName,
            List<BindingNode> orderedNodes,
            ValidationResult aggregateValidation
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.rootNode = Objects.requireNonNull(rootNode, "rootNode must not be null");
        this.nodesByName = Collections.unmodifiableMap(new LinkedHashMap<>(nodesByName));
        this.orderedNodes = List.copyOf(orderedNodes);
        this.aggregateValidation = aggregateValidation;
    }

    /// Creates a new builder for constructing an {@link ElementBindingTree}.
    ///
    /// @param name the tree name (e.g. test plan name)
    /// @return a new builder
    public static Builder builder(String name) {
        return new Builder(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, String> labels() {
        return Map.of("name", name, "type", "binding-tree");
    }

    @Override
    public Map<String, String> attributes() {
        return labels();
    }

    @Override
    public BindingNode root() {
        return rootNode;
    }

    @Override
    public Optional<BindingNode> node(String elementName) {
        return Optional.ofNullable(nodesByName.get(elementName));
    }

    @Override
    public List<BindingNode> nodesInOrder() {
        return orderedNodes;
    }

    @Override
    public Map<String, ParameterBinding> resolvedBindings() {
        var result = new LinkedHashMap<String, ParameterBinding>();
        for (BindingNode node : orderedNodes) {
            result.put(node.name(), node.binding());
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public ValidationResult validationResult() {
        return aggregateValidation;
    }

    ///
    /// Builder for constructing {@link DefaultElementBindingTree} instances.
    ///
    /// Requires at minimum: elements and a binder. Global inputs default to
    /// an empty map. Per-element local inputs are optional.
    ///
    public static class Builder {
        private final String name;
        private List<Element> elements = List.of();
        private Map<String, Object> globalInputs = Map.of();
        private final Map<String, Map<String, Object>> elementInputsMap = new LinkedHashMap<>();
        private ParameterBinder binder;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        /// Sets the elements that form the dependency graph.
        ///
        /// @param elements the elements to include in the tree
        /// @return this builder
        public Builder elements(List<Element> elements) {
            this.elements = Objects.requireNonNull(elements, "elements must not be null");
            return this;
        }

        /// Sets the global inputs that cascade from the root to all elements.
        ///
        /// @param globalInputs the global input map
        /// @return this builder
        public Builder globalInputs(Map<String, Object> globalInputs) {
            this.globalInputs = Objects.requireNonNull(globalInputs, "globalInputs must not be null");
            return this;
        }

        /// Adds local inputs for a specific element, keyed by element name.
        /// These override cascaded values for the named element.
        ///
        /// @param elementName the element's name
        /// @param inputs the local input map for this element
        /// @return this builder
        public Builder elementInputs(String elementName, Map<String, Object> inputs) {
            Objects.requireNonNull(elementName, "elementName must not be null");
            Objects.requireNonNull(inputs, "inputs must not be null");
            elementInputsMap.put(elementName, inputs);
            return this;
        }

        /// Sets the parameter binder used to resolve bindings per element.
        ///
        /// @param binder the parameter binder
        /// @return this builder
        public Builder binder(ParameterBinder binder) {
            this.binder = Objects.requireNonNull(binder, "binder must not be null");
            return this;
        }

        /// Builds the element binding tree.
        ///
        /// Performs topological sorting, cascades inputs through the dependency
        /// graph, and binds each element's parameters.
        ///
        /// @return the constructed element binding tree
        /// @throws IllegalStateException if a cycle is detected in the dependency graph
        public DefaultElementBindingTree build() {
            Objects.requireNonNull(binder, "binder must be set before build()");

            // Topological sort
            List<Element> sorted = topologicalSort(elements);

            // Create root
            DefaultBindingNode root = DefaultBindingNode.root(globalInputs);

            // Build nodes in topological order
            Map<String, DefaultBindingNode> nodesByName = new LinkedHashMap<>();
            List<BindingNode> orderedNodes = new ArrayList<>();
            List<String> allViolations = new ArrayList<>();

            for (Element elem : sorted) {
                // Compute cascaded inputs
                LinkedHashMap<String, Object> cascaded = new LinkedHashMap<>(globalInputs);

                // Merge dependency cascades
                List<BindingNode> parentNodes = new ArrayList<>();
                for (Element.Dependency dep : elem.dependencies()) {
                    DefaultBindingNode depNode = nodesByName.get(dep.target().name());
                    if (depNode != null) {
                        cascaded.putAll(depNode.cascadedInputs());
                        parentNodes.add(depNode);
                    }
                }

                // Apply local inputs
                Map<String, Object> local = elementInputsMap.getOrDefault(elem.name(), Map.of());
                cascaded.putAll(local);

                // Compute depth: max parent depth + 1, or 1 if no parents
                int depth = parentNodes.isEmpty() ? 1
                        : parentNodes.stream().mapToInt(BindingNode::depth).max().orElse(0) + 1;

                // Bind parameters
                ParameterBinding binding = binder.bind(elem, cascaded);

                // Create node
                DefaultBindingNode node = DefaultBindingNode.forElement(
                        elem, binding, cascaded, local, parentNodes, depth
                );

                // Wire parent→child links
                if (parentNodes.isEmpty()) {
                    root.addChild(node);
                } else {
                    for (BindingNode parent : parentNodes) {
                        ((DefaultBindingNode) parent).addChild(node);
                    }
                }

                nodesByName.put(elem.name(), node);
                orderedNodes.add(node);

                // Collect violations
                if (binding.validationResult().isFailed()) {
                    allViolations.addAll(binding.validationResult().violations());
                }
            }

            // Freeze all nodes
            root.freeze();
            for (DefaultBindingNode node : nodesByName.values()) {
                node.freeze();
            }

            // Aggregate validation
            ValidationResult aggregate;
            if (allViolations.isEmpty()) {
                aggregate = new ValidationResult.Passed();
            } else {
                aggregate = new ValidationResult.Failed(
                        "Binding tree has " + allViolations.size() + " violation(s)",
                        allViolations
                );
            }

            return new DefaultElementBindingTree(name, root, nodesByName, orderedNodes, aggregate);
        }

        private List<Element> topologicalSort(List<Element> elements) {
            Map<String, Element> byName = new LinkedHashMap<>();
            Map<String, Integer> inDegree = new LinkedHashMap<>();
            Map<String, List<String>> adjacency = new LinkedHashMap<>();

            for (Element e : elements) {
                byName.put(e.name(), e);
                inDegree.putIfAbsent(e.name(), 0);
                adjacency.putIfAbsent(e.name(), new ArrayList<>());

                for (Element.Dependency dep : e.dependencies()) {
                    inDegree.putIfAbsent(dep.target().name(), 0);
                    adjacency.putIfAbsent(dep.target().name(), new ArrayList<>());
                    adjacency.get(dep.target().name()).add(e.name());
                    inDegree.merge(e.name(), 1, Integer::sum);
                }
            }

            Queue<String> queue = new LinkedList<>();
            for (var entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.add(entry.getKey());
                }
            }

            List<Element> result = new ArrayList<>();
            while (!queue.isEmpty()) {
                String name = queue.poll();
                Element elem = byName.get(name);
                if (elem != null) {
                    result.add(elem);
                }
                for (String dependent : adjacency.getOrDefault(name, List.of())) {
                    int newDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newDegree);
                    if (newDegree == 0) {
                        queue.add(dependent);
                    }
                }
            }

            if (result.size() != elements.size()) {
                throw new IllegalStateException(
                        "Cycle detected in element dependency graph: sorted " + result.size()
                                + " of " + elements.size() + " elements"
                );
            }

            return result;
        }
    }
}
