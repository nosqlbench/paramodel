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
package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.engine.plan.DefaultTestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Resolves `${element.export}` and `${output_of:element}` tokens in parameter values.
///
/// Per spec section 3.2, downstream elements reference exports as `${jvector.service_addr}`.
/// Per spec section 6.4/11.6, downstream elements reference prior-phase output as `${output_of:indexer}`.
///
/// This resolver scans element configuration values for such tokens and records them
/// for runtime resolution (since actual values like IP addresses are only known at
/// deploy time).
///
/// Tokens are validated at composition time (element and export name must exist)
/// but actual value substitution happens at runtime when upstream elements are deployed.
public class ExportResolver {
    private static final Logger logger = LoggerFactory.getLogger(ExportResolver.class);

    /// Pattern matching `${element.export_name}` references.
    private static final Pattern EXPORT_REF_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)}");

    /// Pattern matching `${output_of:element}` references (spec section 6.4, 11.6).
    private static final Pattern OUTPUT_OF_PATTERN = Pattern.compile("\\$\\{output_of:([a-zA-Z0-9_-]+)}");

    /// Validates all export references in the plan's element configurations.
    ///
    /// Returns a map of issues: location -> error message. An empty map means all
    /// references are valid.
    ///
    /// @param plan the test plan to validate
    /// @return a map of validation issues (empty if none)
    public Map<String, String> validateExportReferences(DefaultTestPlan plan) {
        Map<String, String> issues = new LinkedHashMap<>();

        for (Element element : plan.elements()) {
            for (Map.Entry<String, Object> binding : element.configuration().entrySet()) {
                if (binding.getValue() instanceof String strValue) {
                    validateTokens(plan, element.name(), binding.getKey(), strValue, issues);
                }
            }
        }

        return issues;
    }

    /// Checks whether a string value contains export reference tokens.
    ///
    /// @param value the value to check
    /// @return true if the value contains `${element.export}` tokens
    public static boolean containsExportReferences(String value) {
        return value != null && EXPORT_REF_PATTERN.matcher(value).find();
    }

    /// Extracts all export references from a string value.
    ///
    /// @param value the value to scan
    /// @return a map of element ID -> export name pairs found in the value
    public static Map<String, String> extractExportReferences(String value) {
        Map<String, String> refs = new LinkedHashMap<>();
        if (value == null) {
            return refs;
        }
        Matcher matcher = EXPORT_REF_PATTERN.matcher(value);
        while (matcher.find()) {
            refs.put(matcher.group(1), matcher.group(2));
        }
        return refs;
    }

    /// Checks whether a string value contains `${output_of:element}` reference tokens.
    ///
    /// @param value the value to check
    /// @return true if the value contains `${output_of:element}` tokens
    public static boolean containsOutputOfReferences(String value) {
        return value != null && OUTPUT_OF_PATTERN.matcher(value).find();
    }

    /// Extracts all `${output_of:element}` references from a string value.
    ///
    /// @param value the value to scan
    /// @return the set of element IDs referenced via `${output_of:...}` tokens
    public static Set<String> extractOutputOfReferences(String value) {
        Set<String> refs = new LinkedHashSet<>();
        if (value == null) {
            return refs;
        }
        Matcher matcher = OUTPUT_OF_PATTERN.matcher(value);
        while (matcher.find()) {
            refs.add(matcher.group(1));
        }
        return refs;
    }

    /// Validates all `${output_of:element}` references in the plan's element configurations.
    ///
    /// Validates that:
    /// - The referenced element exists
    /// - The referenced element is a COMMAND type (only commands produce output)
    /// - The referenced element is upstream in the dependency graph
    ///
    /// @param plan the test plan to validate
    /// @return a map of validation issues (empty if none)
    public Map<String, String> validateOutputOfReferences(DefaultTestPlan plan) {
        Map<String, String> issues = new LinkedHashMap<>();

        for (Element element : plan.elements()) {
            // Build the transitive upstream set for this element
            Set<String> upstreamIds = collectTransitiveUpstream(plan, element.name());

            for (Map.Entry<String, Object> binding : element.configuration().entrySet()) {
                if (binding.getValue() instanceof String strValue) {
                    validateOutputOfTokens(plan, element.name(), binding.getKey(),
                            strValue, upstreamIds, issues);
                }
            }
        }

        return issues;
    }

    /// Validates individual tokens within a configuration value.
    private void validateTokens(
            DefaultTestPlan plan,
            String elementId,
            String paramName,
            String value,
            Map<String, String> issues) {

        Matcher matcher = EXPORT_REF_PATTERN.matcher(value);
        while (matcher.find()) {
            String referencedElement = matcher.group(1);
            String referencedExport = matcher.group(2);
            String location = "element:" + elementId + ".parameters." + paramName;

            // Check that the referenced element exists
            var upstreamOpt = plan.element(referencedElement);
            if (upstreamOpt.isEmpty()) {
                issues.put(location, String.format(
                        "Export reference '${%s.%s}' references unknown element '%s'",
                        referencedElement, referencedExport, referencedElement));
                continue;
            }

            // Check that the referenced element declares the export
            Element upstream = upstreamOpt.get();
            if (!upstream.exports().containsKey(referencedExport)) {
                issues.put(location, String.format(
                        "Export reference '${%s.%s}': element '%s' does not export '%s'. Available exports: %s",
                        referencedElement, referencedExport, referencedElement, referencedExport,
                        upstream.exports().keySet()));
            } else {
                logger.debug("Validated export reference ${}.{} in {}.{}",
                        referencedElement, referencedExport, elementId, paramName);
            }
        }
    }

    /// Validates `${output_of:element}` tokens within a configuration value.
    private void validateOutputOfTokens(
            DefaultTestPlan plan,
            String elementId,
            String paramName,
            String value,
            Set<String> upstreamIds,
            Map<String, String> issues) {

        Matcher matcher = OUTPUT_OF_PATTERN.matcher(value);
        while (matcher.find()) {
            String referencedElement = matcher.group(1);
            String location = "element:" + elementId + ".parameters." + paramName;

            // Check that the referenced element exists
            var referencedOpt = plan.element(referencedElement);
            if (referencedOpt.isEmpty()) {
                issues.put(location, String.format(
                        "output_of reference '${output_of:%s}' references unknown element '%s'",
                        referencedElement, referencedElement));
                continue;
            }

            // Check that the referenced element is a COMMAND type
            Element referenced = referencedOpt.get();
            String elementType = referenced.tags().getOrDefault("type", "unknown");
            if (!"command".equals(elementType)) {
                issues.put(location, String.format(
                        "output_of reference '${output_of:%s}': element '%s' is type %s, " +
                        "only COMMAND elements produce output",
                        referencedElement, referencedElement, elementType.toUpperCase()));
                continue;
            }

            // Check that the referenced element is upstream in the dependency graph
            if (!upstreamIds.contains(referencedElement)) {
                issues.put(location, String.format(
                        "output_of reference '${output_of:%s}': element '%s' is not an upstream " +
                        "dependency of '%s'. Output references must follow the dependency graph.",
                        referencedElement, referencedElement, elementId));
            } else {
                logger.debug("Validated output_of reference ${output_of:{}} in {}.{}",
                        referencedElement, elementId, paramName);
            }
        }
    }

    /// Collects the transitive set of upstream element IDs for a given element.
    private Set<String> collectTransitiveUpstream(DefaultTestPlan plan, String elementId) {
        Set<String> upstream = new HashSet<>();
        collectUpstreamRecursive(plan, elementId, upstream);
        return upstream;
    }

    /// Recursive helper for transitive upstream collection.
    ///
    /// Uses the Element interface's `dependencies()` method to traverse the
    /// dependency graph without needing hyperplane-specific types.
    private void collectUpstreamRecursive(DefaultTestPlan plan, String elementId, Set<String> visited) {
        plan.element(elementId).ifPresent(element -> {
            for (Element.Dependency dep : element.dependencies()) {
                if (visited.add(dep.target().name())) {
                    collectUpstreamRecursive(plan, dep.target().name(), visited);
                }
            }
        });
    }
}
