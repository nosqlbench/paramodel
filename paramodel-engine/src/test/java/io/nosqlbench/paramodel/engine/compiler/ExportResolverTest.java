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

import io.nosqlbench.paramodel.engine.plan.DefaultElement;
import io.nosqlbench.paramodel.engine.plan.DefaultTestPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for {@link ExportResolver}, including `${output_of:element}` pattern handling.
class ExportResolverTest {
    private ExportResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ExportResolver();
    }

    // --- ${output_of:element} pattern matching tests ---

    @Test
    void testContainsOutputOfReferences() {
        assertThat(ExportResolver.containsOutputOfReferences("${output_of:indexer}")).isTrue();
        assertThat(ExportResolver.containsOutputOfReferences("/data/${output_of:indexer}/results")).isTrue();
        assertThat(ExportResolver.containsOutputOfReferences("plain text")).isFalse();
        assertThat(ExportResolver.containsOutputOfReferences("${server.addr}")).isFalse();
        assertThat(ExportResolver.containsOutputOfReferences(null)).isFalse();
    }

    @Test
    void testExtractOutputOfReferences() {
        Set<String> refs = ExportResolver.extractOutputOfReferences("${output_of:indexer}");
        assertThat(refs).containsExactly("indexer");
    }

    @Test
    void testExtractMultipleOutputOfReferences() {
        Set<String> refs = ExportResolver.extractOutputOfReferences(
                "${output_of:indexer} and ${output_of:loader}");
        assertThat(refs).containsExactly("indexer", "loader");
    }

    @Test
    void testExtractOutputOfReferencesEmpty() {
        assertThat(ExportResolver.extractOutputOfReferences("plain text")).isEmpty();
        assertThat(ExportResolver.extractOutputOfReferences(null)).isEmpty();
    }

    @Test
    void testExtractMixedReferences() {
        // output_of and export refs can coexist
        String value = "${output_of:indexer}:${server.addr}";
        Set<String> outputOfRefs = ExportResolver.extractOutputOfReferences(value);
        Map<String, String> exportRefs = ExportResolver.extractExportReferences(value);

        assertThat(outputOfRefs).containsExactly("indexer");
        assertThat(exportRefs).containsEntry("server", "addr");
    }

    // --- ${output_of:element} validation tests ---

    @Test
    void testValidOutputOfReference() {
        DefaultTestPlan plan = buildMultiPhasePlan();

        Map<String, String> issues = resolver.validateOutputOfReferences(plan);

        assertThat(issues).isEmpty();
    }

    @Test
    void testOutputOfReferencesUnknownElement() {
        DefaultElement client = DefaultElement.builder("client")
                .label("type", "command")
                .tag("image", "benchmark:latest")

                .configuration("input", "${output_of:nonexistent}")
                .build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(client)
                .build();

        Map<String, String> issues = resolver.validateOutputOfReferences(plan);

        assertThat(issues).hasSize(1);
        assertThat(issues.values().iterator().next()).contains("unknown element");
    }

    @Test
    void testOutputOfReferencesNonCommandElement() {
        DefaultElement server = DefaultElement.builder("server")
                .label("type", "service")
                .tag("image", "server:latest")
                .build();
        DefaultElement client = DefaultElement.builder("client")
                .label("type", "command")
                .tag("image", "benchmark:latest")

                .dependency(server)
                .configuration("input", "${output_of:server}")
                .build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(server)
                .element(client)
                .build();

        Map<String, String> issues = resolver.validateOutputOfReferences(plan);

        assertThat(issues).hasSize(1);
        assertThat(issues.values().iterator().next()).contains("only COMMAND elements produce output");
    }

    @Test
    void testOutputOfReferencesNotUpstream() {
        // Two independent commands — querier references indexer but doesn't depend on it
        DefaultElement indexer = DefaultElement.builder("indexer")
                .label("type", "command")
                .tag("image", "indexer:latest")

                .build();
        DefaultElement querier = DefaultElement.builder("querier")
                .label("type", "command")
                .tag("image", "querier:latest")

                .configuration("input", "${output_of:indexer}")
                .build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(indexer)
                .element(querier)
                .build();

        Map<String, String> issues = resolver.validateOutputOfReferences(plan);

        assertThat(issues).hasSize(1);
        assertThat(issues.values().iterator().next()).contains("not an upstream dependency");
    }

    @Test
    void testOutputOfReferencesTransitiveUpstream() {
        // A → B → C, where C references A's output (transitive upstream)
        DefaultElement indexer = DefaultElement.builder("indexer")
                .label("type", "command")
                .tag("image", "indexer:latest")

                .build();
        DefaultElement processor = DefaultElement.builder("processor")
                .label("type", "command")
                .tag("image", "processor:latest")

                .dependency(indexer)
                .build();
        DefaultElement querier = DefaultElement.builder("querier")
                .label("type", "command")
                .tag("image", "querier:latest")

                .dependency(processor)
                .configuration("input", "${output_of:indexer}")
                .build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(indexer)
                .element(processor)
                .element(querier)
                .build();

        Map<String, String> issues = resolver.validateOutputOfReferences(plan);

        assertThat(issues).isEmpty();
    }

    @Test
    void testNoOutputOfReferences() {
        DefaultElement server = DefaultElement.builder("server")
                .label("type", "service")
                .tag("image", "server:latest")
                .configuration("port", "8080")
                .build();

        DefaultTestPlan plan = DefaultTestPlan.builder()
                .name("Test Study")
                .element(server)
                .build();

        Map<String, String> issues = resolver.validateOutputOfReferences(plan);

        assertThat(issues).isEmpty();
    }

    // --- ${element.export} tests (existing functionality) ---

    @Test
    void testContainsExportReferences() {
        assertThat(ExportResolver.containsExportReferences("${server.addr}")).isTrue();
        assertThat(ExportResolver.containsExportReferences("plain text")).isFalse();
        assertThat(ExportResolver.containsExportReferences(null)).isFalse();
    }

    @Test
    void testExtractExportReferences() {
        Map<String, String> refs = ExportResolver.extractExportReferences("http://${server.addr}/api");
        assertThat(refs).containsEntry("server", "addr");
    }

    /// Builds a multi-phase plan: indexer (COMMAND) → querier (COMMAND)
    /// where querier uses ${output_of:indexer}
    private DefaultTestPlan buildMultiPhasePlan() {
        DefaultElement indexer = DefaultElement.builder("indexer")
                .label("type", "command")
                .tag("image", "indexer:latest")

                .build();
        DefaultElement querier = DefaultElement.builder("querier")
                .label("type", "command")
                .tag("image", "querier:latest")

                .dependency(indexer)
                .configuration("input", "${output_of:indexer}")
                .build();

        return DefaultTestPlan.builder()
                .name("Multi-Phase Study")
                .element(indexer)
                .element(querier)
                .build();
    }
}
