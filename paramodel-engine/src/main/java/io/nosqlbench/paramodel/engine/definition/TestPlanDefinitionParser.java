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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.ElementTypeDescriptorProvider;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.AxisDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.BindingDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.DependencyDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.ElementDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.SamplingDefinition;
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.SettingsDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Parses YAML test plan definitions into {@link TestPlanDefinition} objects.
///
/// Supports flexible YAML structure with sensible defaults for optional fields.
public class TestPlanDefinitionParser {
    private static final Logger logger = LoggerFactory.getLogger(TestPlanDefinitionParser.class);

    private final ObjectMapper yamlMapper;
    private final ElementTypeDescriptorProvider typeProvider;

    public TestPlanDefinitionParser() {
        this(ElementTypeDescriptorProvider.open());
    }

    /// Creates a parser with the given element type descriptor provider.
    ///
    /// @param typeProvider supplies valid type IDs and aliases for normalization
    public TestPlanDefinitionParser(ElementTypeDescriptorProvider typeProvider) {
        this.typeProvider = typeProvider;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /// Parses a definition from a YAML file.
    public TestPlanDefinition parse(Path yamlPath) throws IOException {
        logger.debug("Parsing definition from: {}", yamlPath);
        try (InputStream is = Files.newInputStream(yamlPath)) {
            return parse(is);
        }
    }

    /// Parses a definition from an input stream.
    public TestPlanDefinition parse(InputStream is) throws IOException {
        Map<String, Object> rawYaml = yamlMapper.readValue(is, new TypeReference<>() {});
        return parseFromMap(rawYaml);
    }

    /// Parses a definition from a reader.
    public TestPlanDefinition parse(Reader reader) throws IOException {
        Map<String, Object> rawYaml = yamlMapper.readValue(reader, new TypeReference<>() {});
        return parseFromMap(rawYaml);
    }

    /// Parses a definition from a YAML string.
    public TestPlanDefinition parseString(String yaml) throws IOException {
        Map<String, Object> rawYaml = yamlMapper.readValue(yaml, new TypeReference<>() {});
        return parseFromMap(rawYaml);
    }

    /// Parses the raw YAML map into a TestPlanDefinition.
    @SuppressWarnings("unchecked")
    private TestPlanDefinition parseFromMap(Map<String, Object> rawYaml) {
        String name = getString(rawYaml, "name", "Unnamed Study");
        String description = getString(rawYaml, "description", null);

        List<ElementDefinition> elements = parseElements(
                (List<Map<String, Object>>) rawYaml.getOrDefault("elements", Collections.emptyList()));

        List<AxisDefinition> axes = parseAxes(
                (List<Map<String, Object>>) rawYaml.getOrDefault("axes", Collections.emptyList()));

        List<BindingDefinition> bindings = parseBindings(
                (List<Map<String, Object>>) rawYaml.getOrDefault("bindings", Collections.emptyList()));

        SettingsDefinition settings = parseSettings(
                (Map<String, Object>) rawYaml.get("settings"));

        return new TestPlanDefinition(name, description, elements, axes, bindings, settings);
    }

    /// Parses the elements list.
    @SuppressWarnings("unchecked")
    private List<ElementDefinition> parseElements(List<Map<String, Object>> rawElements) {
        List<ElementDefinition> elements = new ArrayList<>();

        // Core fields that are extracted into named ElementDefinition components
        Set<String> coreFields = Set.of("id", "type", "parameters", "depends_on", "exports", "scope");

        for (Map<String, Object> raw : rawElements) {
            String id = getString(raw, "id", null);
            if (id == null) {
                throw new IllegalArgumentException("Element missing required 'id' field");
            }

            String type = parseElementType(getString(raw, "type", "SERVICE"));

            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) raw.getOrDefault("parameters", Collections.emptyMap());
            List<DependencyDefinition> dependsOn = parseDependencies(raw.get("depends_on"));
            Map<String, String> exports = parseStringMap((Map<String, Object>) raw.get("exports"));
            Element.InstancingScope scope = parseInstancingScope(getString(raw, "scope", null));

            // All non-core fields go into the generic properties map
            Map<String, Object> properties = new java.util.LinkedHashMap<>();
            for (var entry : raw.entrySet()) {
                if (!coreFields.contains(entry.getKey()) && entry.getValue() != null) {
                    properties.put(entry.getKey(), entry.getValue());
                }
            }

            elements.add(new ElementDefinition(
                    id, type, parameters, dependsOn, exports, scope, properties));
        }

        return elements;
    }

    /// Parses the dependencies field, which can be a string, list of strings, or list of objects.
    @SuppressWarnings("unchecked")
    private List<DependencyDefinition> parseDependencies(Object rawDeps) {
        if (rawDeps == null) {
            return Collections.emptyList();
        }

        List<DependencyDefinition> deps = new ArrayList<>();

        if (rawDeps instanceof String s) {
            deps.add(new DependencyDefinition(s, RelationshipType.SHARED));
        } else if (rawDeps instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    deps.add(new DependencyDefinition(s, RelationshipType.SHARED));
                } else if (item instanceof Map<?, ?> map) {
                    Map<String, Object> depMap = (Map<String, Object>) map;
                    String element = getString(depMap, "element", null);
                    if (element == null) {
                        throw new IllegalArgumentException("Dependency missing 'element' field");
                    }
                    RelationshipType relationship = parseRelationshipType(getString(depMap, "policy", "SHARED"));
                    deps.add(new DependencyDefinition(element, relationship));
                }
            }
        }

        return deps;
    }

    /// Parses the axes list.
    @SuppressWarnings("unchecked")
    private List<AxisDefinition> parseAxes(List<Map<String, Object>> rawAxes) {
        List<AxisDefinition> axes = new ArrayList<>();
        int defaultNesting = 0;

        for (Map<String, Object> raw : rawAxes) {
            String parameter = getString(raw, "parameter", null);
            if (parameter == null) {
                throw new IllegalArgumentException("Axis missing required 'parameter' field");
            }

            String element = getString(raw, "element", null);
            if (element == null) {
                throw new IllegalArgumentException("Axis missing required 'element' field");
            }

            List<Object> values = (List<Object>) raw.get("values");
            Object min = raw.get("min");
            Object max = raw.get("max");

            if (values == null && min != null && max != null) {
                values = List.of(min, max);
            }

            String mode = parseSweepMode(getString(raw, "mode", "SERIAL"));
            Integer nesting = getInteger(raw, "nesting", defaultNesting++);
            String section = getString(raw, "section", null);
            SamplingDefinition sampling = parseSampling((Map<String, Object>) raw.get("sampling"));
            Integer repetitions = getInteger(raw, "repetitions", 1);

            axes.add(new AxisDefinition(
                    parameter, element, values, min, max, mode, nesting, section, sampling, repetitions));
        }

        return axes;
    }

    /// Parses the bindings section.
    private List<BindingDefinition> parseBindings(List<Map<String, Object>> rawBindings) {
        if (rawBindings == null) {
            return Collections.emptyList();
        }

        List<BindingDefinition> bindings = new ArrayList<>();
        for (Map<String, Object> raw : rawBindings) {
            String parameter = getString(raw, "parameter", null);
            if (parameter == null) {
                throw new IllegalArgumentException("Binding missing required 'parameter' field");
            }
            String element = getString(raw, "element", null);
            if (element == null) {
                throw new IllegalArgumentException("Binding missing required 'element' field");
            }
            String value = getString(raw, "value", null);
            if (value == null) {
                throw new IllegalArgumentException("Binding missing required 'value' field");
            }
            bindings.add(new BindingDefinition(parameter, element, value));
        }
        return bindings;
    }

    /// Parses the sampling field.
    @SuppressWarnings("unchecked")
    private SamplingDefinition parseSampling(Map<String, Object> raw) {
        if (raw == null) {
            return new SamplingDefinition("GRID", null);
        }
        String type = getString(raw, "type", "GRID");
        Integer count = getInteger(raw, "count", null);
        return new SamplingDefinition(type, count);
    }

    /// Parses the settings section.
    @SuppressWarnings("unchecked")
    private SettingsDefinition parseSettings(Map<String, Object> raw) {
        if (raw == null) {
            return new SettingsDefinition("skip", 0, 1, null, Collections.emptyMap(), null, null);
        }

        String onFailureStr = getString(raw, "on_failure", null);
        String onFailure;
        int retryCount;

        if (onFailureStr != null) {
            onFailure = parseFailurePolicy(onFailureStr);
            retryCount = parseRetryCount(onFailureStr);
        } else {
            Boolean failFast = getBoolean(raw, "fail_fast", false);
            onFailure = failFast ? "stop" : "skip";
            retryCount = 0;
        }

        Integer maxConcurrency = getInteger(raw, "max_concurrency", 1);
        Integer timeoutSeconds = getInteger(raw, "timeout_seconds", null);
        Map<String, String> labels = parseStringMap((Map<String, Object>) raw.get("labels"));

        String trialOrdering = getString(raw, "trial_ordering", null);
        Integer checkpointInterval = getInteger(raw, "checkpoint_interval_trials", null);
        return new SettingsDefinition(onFailure, retryCount, maxConcurrency, timeoutSeconds, labels,
                trialOrdering, checkpointInterval);
    }

    /// Converts a Map<String, Object> to Map<String, String>.
    private Map<String, String> parseStringMap(Map<String, Object> raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        return raw.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() != null ? e.getValue().toString() : ""));
    }

    /// Normalizes an element type string, applying any registered aliases.
    ///
    /// Type validation is deferred to the validation stage; this method
    /// only normalizes case and applies aliases from the type provider.
    private String parseElementType(String value) {
        if (value == null) {
            return "service";
        }
        String normalized = value.trim().toLowerCase();
        // Apply aliases from the type provider (e.g., "constellation" → "node")
        Map<String, String> aliases = typeProvider.typeAliases();
        if (aliases.containsKey(normalized)) {
            String target = aliases.get(normalized);
            logger.warn("Element type '{}' is deprecated; use '{}' instead", normalized, target);
            return target;
        }
        return normalized;
    }

    /// Parses an {@link Element.InstancingScope} from a YAML scope string.
    private Element.InstancingScope parseInstancingScope(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toUpperCase()) {
            case "PER_RUN" -> Element.InstancingScope.PER_RUN;
            case "PER_TRIAL" -> Element.InstancingScope.PER_TRIAL;
            case "PER_GROUP" -> Element.InstancingScope.PER_GROUP;
            default -> throw new IllegalArgumentException("Unknown instancing scope: " + value);
        };
    }

    /// Parses a sweep mode string, validating against known modes.
    private String parseSweepMode(String value) {
        if (value == null) {
            return "serial";
        }
        String normalized = value.trim().toLowerCase();
        if (!TestPlanDefinition.VALID_SWEEP_MODES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown sweep mode: " + value);
        }
        return normalized;
    }

    /// Parses a failure policy string, validating against known policies.
    private String parseFailurePolicy(String value) {
        if (value == null) {
            return "skip";
        }
        String trimmed = value.trim().toLowerCase();
        if (trimmed.startsWith("retry")) {
            return "retry";
        }
        if (!TestPlanDefinition.VALID_FAILURE_POLICIES.contains(trimmed)) {
            throw new IllegalArgumentException("Unknown failure policy: " + value);
        }
        return trimmed;
    }

    /// Parses the retry count from a string like "retry(3)".
    private int parseRetryCount(String value) {
        if (value == null) {
            return 0;
        }
        String trimmed = value.trim().toLowerCase();
        if (trimmed.startsWith("retry(") && trimmed.endsWith(")")) {
            String countStr = trimmed.substring(6, trimmed.length() - 1).trim();
            try {
                int count = Integer.parseInt(countStr);
                if (count < 1) {
                    throw new IllegalArgumentException("Retry count must be at least 1: " + count);
                }
                return count;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid retry count: " + countStr);
            }
        }
        if (trimmed.equals("retry")) {
            return 3;
        }
        return 0;
    }

    /// Parses a {@link RelationshipType} from a YAML policy string.
    private RelationshipType parseRelationshipType(String value) {
        if (value == null) {
            return RelationshipType.SHARED;
        }
        return switch (value.toUpperCase()) {
            case "SHARED" -> RelationshipType.SHARED;
            case "MUTUALLY_EXCLUSIVE" -> RelationshipType.MUTUALLY_EXCLUSIVE;
            case "INSTANCED_PER" -> RelationshipType.INSTANCED_PER;
            default -> throw new IllegalArgumentException("Unknown relationship type: " + value);
        };
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Boolean getBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
