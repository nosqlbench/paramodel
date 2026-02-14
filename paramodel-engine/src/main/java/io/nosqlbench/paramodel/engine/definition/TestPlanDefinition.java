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

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.parameters.SamplingStrategy;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /// @param scope optional explicit scope override
    /// @param properties type-specific optional fields (e.g., image, node_role, output)
    public record ElementDefinition(
            String id,
            String type,
            Map<String, Object> parameters,
            List<DependencyDefinition> dependsOn,
            Map<String, String> exports,
            Element.InstancingScope scope,
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
}
