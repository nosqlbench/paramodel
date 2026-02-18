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
package io.nosqlbench.paramodel.engine.definition;

import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.parameters.SamplingStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// Raw test plan definition as parsed from YAML.
///
/// This is a data transfer object that captures the YAML structure before
/// validation and conversion to the domain model.
///
/// @param name human-readable plan name
/// @param description optional plan description
/// @param elements list of element definitions
/// @param axes list of axis definitions for varied parameters
/// @param bindings list of fixed binding definitions for cross-element parameter references
/// @param settings optional plan-level settings
public record TestPlanDefinition(
        String name,
        String description,
        List<ElementDefinition> elements,
        List<AxisDefinition> axes,
        List<BindingDefinition> bindings,
        SettingsDefinition settings
) {
    /// Valid sweep mode strings.
    public static final Set<String> VALID_SWEEP_MODES = Set.of("serial", "concurrent");

    /// Valid failure policy strings.
    public static final Set<String> VALID_FAILURE_POLICIES = Set.of("stop", "skip", "retry");

    /// An element definition from YAML.
    ///
    /// Core structural fields (`id`, `type`, `parameters`, etc.) are named
    /// record components. All type-specific optional fields (e.g., image,
    /// node role, output config) are carried in the generic `properties` map
    /// so that the engine has no knowledge of concrete element types.
    ///
    /// @param id unique element identifier
    /// @param type element type identifier (registered by implementing system)
    /// @param parameters fixed parameter bindings
    /// @param dependsOn list of dependency definitions
    /// @param exports exported values available to downstream elements
    /// @param properties type-specific optional fields (e.g., image, node_role, output)
    public record ElementDefinition(
            String id,
            String type,
            Map<String, Object> parameters,
            List<DependencyDefinition> dependsOn,
            Map<String, String> exports,
            Map<String, Object> properties
    ) {
        /// Returns true if the named property has a non-null value.
        public boolean hasProperty(String name) {
            if (properties == null) return false;
            Object val = properties.get(name);
            if (val == null) return false;
            if (val instanceof String s) return !s.isBlank();
            return true;
        }

        /// Returns a property value, or null if absent.
        public Object property(String name) {
            return properties == null ? null : properties.get(name);
        }

        /// Returns a string property value, or null if absent.
        public String stringProperty(String name) {
            Object val = property(name);
            return val instanceof String s ? s : (val != null ? val.toString() : null);
        }
    }

    /// A dependency definition from YAML.
    ///
    /// The lifeline concept is now expressed via {@link RelationshipType#LIFELINE}
    /// on the dependency edge rather than a separate boolean field.
    ///
    /// @param element the upstream element ID
    /// @param relationship the {@link RelationshipType} for this dependency edge
    public record DependencyDefinition(
            String element,
            RelationshipType relationship
    ) {}

    /// An axis definition from YAML.
    public record AxisDefinition(
            String parameter,
            String element,
            List<Object> values,
            Object min,
            Object max,
            String mode,
            Integer nesting,
            String section,
            SamplingDefinition sampling,
            Integer repetitions
    ) {}

    /// A sampling strategy definition from YAML.
    public record SamplingDefinition(
            String type,
            Integer count
    ) {
        /// Converts to the domain model SamplingStrategy.
        public SamplingStrategy toSamplingStrategy() {
            if (type == null || type.equalsIgnoreCase("GRID")) {
                return SamplingStrategy.grid();
            } else if (type.equalsIgnoreCase("RANDOM")) {
                return SamplingStrategy.random(count != null ? count : 10, System.currentTimeMillis());
            } else if (type.equalsIgnoreCase("LINSPACE")) {
                return SamplingStrategy.linspace(count != null ? count : 10);
            }
            throw new IllegalArgumentException("Unknown sampling type: " + type);
        }
    }

    /// A fixed binding definition from YAML.
    ///
    /// Bindings allow cross-element parameter references using `${element.export}` syntax.
    ///
    /// @param parameter the parameter name to bind
    /// @param element the target element that receives this binding
    /// @param value the value, which may contain `${element.export}` references
    public record BindingDefinition(
            String parameter,
            String element,
            String value
    ) {}

    /// Plan-level execution settings as parsed from YAML.
    ///
    /// @param onFailure failure handling: "stop", "skip", or "retry"
    /// @param retryCount number of retries when onFailure is "retry"
    /// @param maxConcurrency maximum concurrent trial executions
    /// @param timeoutSeconds global timeout for the plan
    /// @param labels user-defined labels for result tagging
    /// @param trialOrdering trial ordering strategy name
    /// @param checkpointIntervalTrials checkpoint interval in trials
    public record SettingsDefinition(
            String onFailure,
            int retryCount,
            Integer maxConcurrency,
            Integer timeoutSeconds,
            Map<String, String> labels,
            String trialOrdering,
            Integer checkpointIntervalTrials
    ) {}

    /// Renames an element and atomically updates all references to it throughout the
    /// definition. Returns a new {@link TestPlanDefinition} with the rename applied.
    ///
    /// References updated:
    /// - Element ID ({@link ElementDefinition#id})
    /// - Dependency targets ({@link DependencyDefinition#element})
    /// - Axis element targets ({@link AxisDefinition#element})
    /// - Binding element targets ({@link BindingDefinition#element})
    /// - {@code ${oldName.export}} references in binding values
    /// - {@code ${output_of:oldName}} references in element parameter values
    ///   and binding values
    ///
    /// @param oldName the current element name
    /// @param newName the desired new element name
    /// @return a new definition with the rename applied
    /// @throws IllegalArgumentException if {@code oldName} does not exist or
    ///         {@code newName} is already taken by another element
    public TestPlanDefinition renameElement(String oldName, String newName) {
        if (oldName.equals(newName)) return this;

        boolean found = false;
        boolean conflict = false;
        if (elements != null) {
            for (ElementDefinition e : elements) {
                if (e.id().equals(oldName)) found = true;
                if (e.id().equals(newName)) conflict = true;
            }
        }
        if (!found) {
            throw new IllegalArgumentException(
                "Cannot rename element '" + oldName + "': no element with that name exists");
        }
        if (conflict) {
            throw new IllegalArgumentException(
                "Cannot rename element '" + oldName + "' to '" + newName
                    + "': an element named '" + newName + "' already exists");
        }

        // Patterns for string-interpolation references
        Pattern exportRef = Pattern.compile(
            Pattern.quote("${" + oldName + "."));
        String exportReplacement = "\\${" + newName + ".";
        Pattern outputOfRef = Pattern.compile(
            Pattern.quote("${output_of:" + oldName + "}"));
        String outputOfReplacement = "\\${output_of:" + newName + "}";

        // Rename elements
        List<ElementDefinition> newElements = elements == null ? null : elements.stream()
            .map(e -> {
                String id = e.id().equals(oldName) ? newName : e.id();
                List<DependencyDefinition> deps = e.dependsOn() == null ? null : e.dependsOn().stream()
                    .map(d -> d.element().equals(oldName)
                        ? new DependencyDefinition(newName, d.relationship()) : d)
                    .toList();
                Map<String, Object> params = renameInValues(e.parameters(), exportRef,
                    exportReplacement, outputOfRef, outputOfReplacement);
                return new ElementDefinition(id, e.type(), params, deps, e.exports(), e.properties());
            })
            .toList();

        // Rename axis targets
        List<AxisDefinition> newAxes = axes == null ? null : axes.stream()
            .map(a -> oldName.equals(a.element())
                ? new AxisDefinition(a.parameter(), newName, a.values(), a.min(), a.max(),
                    a.mode(), a.nesting(), a.section(), a.sampling(), a.repetitions())
                : a)
            .toList();

        // Rename binding targets and references in binding values
        List<BindingDefinition> newBindings = bindings == null ? null : bindings.stream()
            .map(b -> {
                String elem = b.element().equals(oldName) ? newName : b.element();
                String val = renameInString(b.value(), exportRef, exportReplacement,
                    outputOfRef, outputOfReplacement);
                return new BindingDefinition(b.parameter(), elem, val);
            })
            .toList();

        return new TestPlanDefinition(name, description, newElements, newAxes, newBindings, settings);
    }

    /// Replaces element name references inside string-valued entries of a map.
    private static Map<String, Object> renameInValues(
            Map<String, Object> map, Pattern exportRef, String exportReplacement,
            Pattern outputOfRef, String outputOfReplacement) {
        if (map == null) return null;
        boolean changed = false;
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (var entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String s) {
                String replaced = renameInString(s, exportRef, exportReplacement,
                    outputOfRef, outputOfReplacement);
                result.put(entry.getKey(), replaced);
                if (!replaced.equals(s)) changed = true;
            } else {
                result.put(entry.getKey(), val);
            }
        }
        return changed ? result : map;
    }

    /// Replaces element name references in a single string.
    private static String renameInString(
            String value, Pattern exportRef, String exportReplacement,
            Pattern outputOfRef, String outputOfReplacement) {
        if (value == null) return null;
        String result = exportRef.matcher(value).replaceAll(exportReplacement);
        result = outputOfRef.matcher(result).replaceAll(outputOfReplacement);
        return result;
    }
}
