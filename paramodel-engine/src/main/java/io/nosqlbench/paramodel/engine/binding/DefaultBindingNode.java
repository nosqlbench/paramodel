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
import io.nosqlbench.paramodel.parameters.BindingNode;
import io.nosqlbench.paramodel.parameters.ParameterBinding;
import io.nosqlbench.paramodel.parameters.ValidationResult;

import java.util.*;

///
/// Default implementation of {@link BindingNode}.
///
/// Instances are constructed via the tree builder ({@link DefaultElementBindingTree.Builder})
/// and are immutable once frozen. During construction, children are accumulated
/// mutably and then frozen to an unmodifiable view.
///
/// The virtual root is created via {@link #root(Map)} and carries global inputs
/// with an empty binding.
///
/// @see DefaultElementBindingTree
/// @since 0.1.0
///
public class DefaultBindingNode implements BindingNode {

    private final Element element;
    private final ParameterBinding binding;
    private final Map<String, Object> cascadedInputs;
    private final Map<String, Object> localInputs;
    private final List<BindingNode> parents;
    private final LinkedHashMap<String, BindingNode> mutableChildren;
    private Map<String, BindingNode> frozenChildren;
    private final int depth;

    private DefaultBindingNode(
            Element element,
            ParameterBinding binding,
            Map<String, Object> cascadedInputs,
            Map<String, Object> localInputs,
            List<BindingNode> parents,
            int depth
    ) {
        this.element = element;
        this.binding = Objects.requireNonNull(binding, "binding must not be null");
        this.cascadedInputs = Collections.unmodifiableMap(new LinkedHashMap<>(cascadedInputs));
        this.localInputs = Collections.unmodifiableMap(new LinkedHashMap<>(localInputs));
        this.parents = List.copyOf(parents);
        this.mutableChildren = new LinkedHashMap<>();
        this.frozenChildren = null;
        this.depth = depth;
    }

    ///
    /// Creates the virtual root node with global inputs and an empty binding.
    ///
    /// @param globalInputs the global inputs that cascade to all elements
    /// @return the root binding node
    ///
    public static DefaultBindingNode root(Map<String, Object> globalInputs) {
        var emptyBinding = new DefaultParameterBinding(
                Map.of(),
                Collections.unmodifiableMap(new LinkedHashMap<>(globalInputs)),
                List.of(),
                new ValidationResult.Passed()
        );
        return new DefaultBindingNode(
                null,
                emptyBinding,
                globalInputs,
                globalInputs,
                List.of(),
                0
        );
    }

    ///
    /// Creates an element binding node.
    ///
    /// @param element the element this node represents
    /// @param binding the resolved parameter binding
    /// @param cascadedInputs the fully merged input map
    /// @param localInputs the element-specific local inputs
    /// @param parents the dependency nodes
    /// @param depth the depth in the tree
    /// @return the element binding node
    ///
    public static DefaultBindingNode forElement(
            Element element,
            ParameterBinding binding,
            Map<String, Object> cascadedInputs,
            Map<String, Object> localInputs,
            List<BindingNode> parents,
            int depth
    ) {
        Objects.requireNonNull(element, "element must not be null");
        return new DefaultBindingNode(element, binding, cascadedInputs, localInputs, parents, depth);
    }

    /// Adds a child node during tree construction. Must be called before {@link #freeze()}.
    void addChild(DefaultBindingNode child) {
        if (frozenChildren != null) {
            throw new IllegalStateException("Cannot add children after freeze");
        }
        mutableChildren.put(child.name(), child);
    }

    /// Freezes this node, making the children map unmodifiable.
    void freeze() {
        if (frozenChildren == null) {
            frozenChildren = Collections.unmodifiableMap(new LinkedHashMap<>(mutableChildren));
        }
    }

    @Override
    public String name() {
        return element != null ? element.name() : "root";
    }

    @Override
    public Map<String, String> tags() {
        if (element != null) {
            return element.tags();
        }
        return Map.of("name", "root", "type", "binding-root");
    }

    @Override
    public Optional<Element> element() {
        return Optional.ofNullable(element);
    }

    @Override
    public ParameterBinding binding() {
        return binding;
    }

    @Override
    public Map<String, Object> cascadedInputs() {
        return cascadedInputs;
    }

    @Override
    public Map<String, Object> localInputs() {
        return localInputs;
    }

    @Override
    public List<BindingNode> parents() {
        return parents;
    }

    @Override
    public Map<String, BindingNode> children() {
        return frozenChildren != null ? frozenChildren : Collections.unmodifiableMap(mutableChildren);
    }

    @Override
    public int depth() {
        return depth;
    }

    @Override
    public boolean isRoot() {
        return element == null;
    }

    @Override
    public String toString() {
        return "BindingNode[" + name() + ", depth=" + depth + ", isRoot=" + isRoot() + "]";
    }
}
