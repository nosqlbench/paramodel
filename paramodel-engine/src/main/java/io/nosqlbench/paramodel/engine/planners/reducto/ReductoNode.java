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

import java.util.*;

///
/// A mutable node in the reducto execution graph with bidirectional edges.
///
/// Each node has a unique ID, a type, optional element and trial associations,
/// and mutable predecessor/successor edge sets managed by
/// {@link ReductoGraph}.
///
public final class ReductoNode {

    private final String id;
    private ReductoNodeType type;
    private String elementName;
    private int trialIndex;
    private int groupIndex;
    private final Set<ReductoNode> predecessors = new LinkedHashSet<>();
    private final Set<ReductoNode> successors = new LinkedHashSet<>();
    private final Map<String, Object> metadata = new LinkedHashMap<>();

    /// Creates a new node with the given ID and type.
    ///
    /// @param id   unique node identifier
    /// @param type node type
    public ReductoNode(String id, ReductoNodeType type) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.trialIndex = -1;
        this.groupIndex = -1;
    }

    /// Returns the unique identifier for this node.
    public String id() { return id; }

    /// Returns the node type.
    public ReductoNodeType type() { return type; }

    /// Sets the node type (used during rule transformations).
    ///
    /// @param type the new type
    public void setType(ReductoNodeType type) { this.type = type; }

    /// Returns the element name associated with this node, or {@code null} if none.
    public String elementName() { return elementName; }

    /// Sets the element name associated with this node.
    ///
    /// @param elementName element name
    public void setElementName(String elementName) { this.elementName = elementName; }

    /// Returns the trial index, or -1 if not trial-specific.
    public int trialIndex() { return trialIndex; }

    /// Sets the trial index.
    ///
    /// @param trialIndex trial index
    public void setTrialIndex(int trialIndex) { this.trialIndex = trialIndex; }

    /// Returns the group index, or -1 if not group-specific.
    public int groupIndex() { return groupIndex; }

    /// Sets the group index.
    ///
    /// @param groupIndex group index
    public void setGroupIndex(int groupIndex) { this.groupIndex = groupIndex; }

    /// Returns the mutable set of predecessor nodes (nodes with edges pointing to this node).
    public Set<ReductoNode> predecessors() { return predecessors; }

    /// Returns the mutable set of successor nodes (nodes this node has edges pointing to).
    public Set<ReductoNode> successors() { return successors; }

    /// Returns the mutable metadata map for this node.
    public Map<String, Object> metadata() { return metadata; }

    /// Puts a metadata entry on this node.
    ///
    /// @param key   metadata key
    /// @param value metadata value
    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReductoNode that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return type + "(" + (elementName != null ? elementName : "") +
               (trialIndex >= 0 ? ",T" + trialIndex : "") +
               (groupIndex >= 0 ? ",G" + groupIndex : "") + ")[" + id + "]";
    }
}
