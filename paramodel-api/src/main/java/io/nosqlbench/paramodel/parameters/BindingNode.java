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
import io.nosqlbench.paramodel.elements.Element;

import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// A node in the hierarchical element binding tree.
///
/// Each {@code BindingNode} corresponds to either a concrete {@link Element}
/// or the synthetic virtual root of the tree. Nodes mirror the element
/// dependency graph: an element's dependencies become the node's parents,
/// and the element's dependents become its children.
///
/// ## Cascade Semantics
///
/// Inputs cascade from parents to children in the dependency graph:
///
/// 1. Global inputs are provided by the virtual root
/// 2. Each dependency's {@link #cascadedInputs()} are merged in
///    {@code dependencies()} list order (later overrides earlier)
/// 3. The node's {@link #localInputs()} override all cascaded values
/// 4. The merged result is bound against the element's parameters
///
/// ## Identity
///
/// Each node is identified by {@link Labeled#name()}, which for element
/// nodes delegates to {@code element.name()} and for the root node
/// returns {@code "root"}.
///
/// @see ElementBindingTree
/// @see ParameterBinding
/// @since 0.1.0
///
public interface BindingNode extends Labeled {

    /// Returns the element backing this node, or empty for the virtual root.
    Optional<Element> element();

    /// Returns the resolved parameter binding for this node's element.
    /// The root node returns an empty binding (no parameters, no assignments).
    ParameterBinding binding();

    /// Returns the fully merged input map: global inputs + ancestor cascades + local inputs.
    /// This is the input map that was used to produce the {@link #binding()}.
    Map<String, Object> cascadedInputs();

    /// Returns only the inputs specific to this node, excluding inherited values.
    Map<String, Object> localInputs();

    /// Returns the parent nodes (dependency nodes in the element graph).
    /// Empty for the virtual root.
    List<BindingNode> parents();

    /// Returns the child nodes (dependent nodes), keyed by {@link Labeled#name()}.
    Map<String, BindingNode> children();

    /// Returns the depth of this node in the tree. The root is depth 0,
    /// root-level elements are depth 1, and so on.
    int depth();

    /// Returns {@code true} if this is the virtual root node.
    boolean isRoot();
}
