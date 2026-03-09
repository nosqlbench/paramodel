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

import io.nosqlbench.paramodel.attributes.Labeled;

import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// A hierarchical parameter binding structure that mirrors the element dependency graph.
///
/// The tree organizes {@link BindingNode} instances hierarchically, where inputs
/// cascade from dependencies (parents) to dependents (children). A virtual root
/// node provides global inputs that cascade to all elements.
///
/// ## Cascade Algorithm
///
/// For each element in topological order (dependencies first):
///
/// 1. Start with global inputs from the root
/// 2. Merge each dependency's cascaded inputs in dependency list order
/// 3. Override with the element's local inputs
/// 4. Bind the merged inputs against the element's parameters
///
/// ## Identity
///
/// The tree itself extends {@link Labeled} with its name (e.g. a test plan name)
/// and a {@code "type"} label of {@code "binding-tree"}.
///
/// @see BindingNode
/// @see ParameterBinder
/// @since 0.1.0
///
public interface ElementBindingTree extends Labeled {

    /// Returns the virtual root node that carries global inputs.
    BindingNode root();

    /// Looks up a binding node by its element name ({@link Labeled#name()}).
    /// Returns empty if no element with the given name exists in the tree.
    Optional<BindingNode> node(String elementName);

    /// Returns all element nodes (excluding the root) in topological order,
    /// where dependencies always appear before their dependents.
    List<BindingNode> nodesInOrder();

    /// Returns a map of element name to resolved {@link ParameterBinding} for every
    /// element in the tree. Does not include the root node.
    Map<String, ParameterBinding> resolvedBindings();

    /// Returns the aggregate validation result across all nodes in the tree.
    /// If any node has a failed binding, the aggregate result is failed.
    ValidationResult validationResult();
}
