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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for {@link TestPlanDefinitionParser}.
class TestPlanDefinitionParserTest {
    private TestPlanDefinitionParser parser;

    @BeforeEach
    void setUp() {
        parser = new TestPlanDefinitionParser();
    }

    @Test
    void testParseMinimalStudy() throws IOException {
        String yaml = """
            name: Minimal Study
            elements:
              - id: server
                type: SERVICE
                image: nginx:latest
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.name()).isEqualTo("Minimal Study");
        assertThat(def.elements()).hasSize(1);
        assertThat(def.elements().getFirst().id()).isEqualTo("server");
        assertThat(def.elements().getFirst().type()).isEqualTo("service");
        assertThat(def.elements().getFirst().stringProperty("image")).isEqualTo("nginx:latest");
    }

    @Test
    void testParseFullStudy() throws IOException {
        String yaml = """
            name: Vector Benchmark
            description: Benchmarks vector search performance

            elements:
              - id: infra
                type: NODE
                constellation: vector-bench-infra

              - id: server
                type: SERVICE
                image: jvector-server:latest
                depends_on:
                  - element: infra
                parameters:
                  threads: 4
                  memory: 4g
                exports:
                  addr: "${self.ip}:8080"

              - id: client
                type: COMMAND
                image: vector-client:latest
                depends_on:
                  - element: server
                    policy: SHARED

            axes:
              - parameter: threads
                element: server
                values: [1, 2, 4, 8]
                mode: SERIAL
                nesting: 0
                section: thread-count

              - parameter: dataset
                element: client
                values: [sift-1m, deep-1m]
                mode: SERIAL
                nesting: 1
                repetitions: 3

            settings:
              fail_fast: true
              max_concurrency: 4
              timeout_seconds: 3600
              labels:
                project: vector-bench
                version: "1.0"
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        // Study level
        assertThat(def.name()).isEqualTo("Vector Benchmark");
        assertThat(def.description()).isEqualTo("Benchmarks vector search performance");

        // Elements
        assertThat(def.elements()).hasSize(3);

        var infra = def.elements().get(0);
        assertThat(infra.id()).isEqualTo("infra");
        assertThat(infra.type()).isEqualTo("node");
        assertThat(infra.stringProperty("constellation")).isEqualTo("vector-bench-infra");

        var server = def.elements().get(1);
        assertThat(server.id()).isEqualTo("server");
        assertThat(server.type()).isEqualTo("service");
        assertThat(server.stringProperty("image")).isEqualTo("jvector-server:latest");
        assertThat(server.dependsOn()).hasSize(1);
        assertThat(server.dependsOn().getFirst().element()).isEqualTo("infra");
        assertThat(server.parameters()).containsEntry("threads", 4);
        assertThat(server.parameters()).containsEntry("memory", "4g");
        assertThat(server.exports()).containsEntry("addr", "${self.ip}:8080");

        var client = def.elements().get(2);
        assertThat(client.id()).isEqualTo("client");
        assertThat(client.type()).isEqualTo("command");
        assertThat(client.dependsOn()).hasSize(1);
        assertThat(client.dependsOn().getFirst().element()).isEqualTo("server");
        assertThat(client.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.SHARED);

        // Axes
        assertThat(def.axes()).hasSize(2);

        var threadAxis = def.axes().get(0);
        assertThat(threadAxis.parameter()).isEqualTo("threads");
        assertThat(threadAxis.element()).isEqualTo("server");
        assertThat(threadAxis.values()).containsExactly(1, 2, 4, 8);
        assertThat(threadAxis.mode()).isEqualTo("serial");
        assertThat(threadAxis.nesting()).isEqualTo(0);
        assertThat(threadAxis.section()).isEqualTo("thread-count");

        var datasetAxis = def.axes().get(1);
        assertThat(datasetAxis.parameter()).isEqualTo("dataset");
        assertThat(datasetAxis.values()).containsExactly("sift-1m", "deep-1m");
        assertThat(datasetAxis.nesting()).isEqualTo(1);
        assertThat(datasetAxis.repetitions()).isEqualTo(3);

        // Settings
        assertThat(def.settings()).isNotNull();
        // fail_fast: true maps to STOP via backward compatibility
        assertThat(def.settings().onFailure()).isEqualTo("stop");
        assertThat(def.settings().maxConcurrency()).isEqualTo(4);
        assertThat(def.settings().timeoutSeconds()).isEqualTo(3600);
        assertThat(def.settings().labels()).containsEntry("project", "vector-bench");
    }

    @Test
    void testParseDependencyAsString() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
              - id: client
                type: COMMAND
                image: client:latest
                depends_on: server
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var client = def.elements().get(1);
        assertThat(client.dependsOn()).hasSize(1);
        assertThat(client.dependsOn().getFirst().element()).isEqualTo("server");
        assertThat(client.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.SHARED);
    }

    @Test
    void testParseDependencyAsListOfStrings() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: a
                type: SERVICE
                image: a:latest
              - id: b
                type: SERVICE
                image: b:latest
              - id: client
                type: COMMAND
                image: client:latest
                depends_on:
                  - a
                  - b
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var client = def.elements().get(2);
        assertThat(client.dependsOn()).hasSize(2);
        assertThat(client.dependsOn().get(0).element()).isEqualTo("a");
        assertThat(client.dependsOn().get(1).element()).isEqualTo("b");
    }

    @Test
    void testParseExclusivePolicy() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
              - id: client
                type: COMMAND
                image: client:latest
                depends_on:
                  - element: server
                    policy: EXCLUSIVE
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var client = def.elements().get(1);
        assertThat(client.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.EXCLUSIVE);
    }

    @Test
    void testParseMutuallyExclusiveMapsToExclusive() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
              - id: client
                type: COMMAND
                image: client:latest
                depends_on:
                  - element: server
                    policy: MUTUALLY_EXCLUSIVE
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var client = def.elements().get(1);
        assertThat(client.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.EXCLUSIVE);
    }

    @Test
    void testParseSamplingStrategy() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
            axes:
              - parameter: threads
                element: server
                min: 0
                max: 100
                sampling:
                  type: LINSPACE
                  count: 10
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var axis = def.axes().getFirst();
        assertThat(axis.sampling()).isNotNull();
        assertThat(axis.sampling().type()).isEqualTo("LINSPACE");
        assertThat(axis.sampling().count()).isEqualTo(10);
    }

    @Test
    void testParseBindingsSection() throws IOException {
        String yaml = """
            name: Study with Bindings
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
                exports:
                  service_addr: "${self.ip}:4567"
              - id: client
                type: COMMAND
                image: benchmark:latest
                depends_on: server
            bindings:
              - parameter: BASE_URL
                element: client
                value: "http://${server.service_addr}"
              - parameter: DATASET
                element: client
                value: "cap:1m"
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.bindings()).hasSize(2);
        assertThat(def.bindings().get(0).parameter()).isEqualTo("BASE_URL");
        assertThat(def.bindings().get(0).element()).isEqualTo("client");
        assertThat(def.bindings().get(0).value()).isEqualTo("http://${server.service_addr}");
        assertThat(def.bindings().get(1).parameter()).isEqualTo("DATASET");
        assertThat(def.bindings().get(1).value()).isEqualTo("cap:1m");
    }

    @Test
    void testParseOnFailureSkip() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
            settings:
              on_failure: skip
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.settings().onFailure()).isEqualTo("skip");
        assertThat(def.settings().retryCount()).isEqualTo(0);
    }

    @Test
    void testParseOnFailureStop() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
            settings:
              on_failure: stop
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.settings().onFailure()).isEqualTo("stop");
    }

    @Test
    void testParseOnFailureRetry() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
            settings:
              on_failure: retry(5)
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.settings().onFailure()).isEqualTo("retry");
        assertThat(def.settings().retryCount()).isEqualTo(5);
    }

    @Test
    void testParseNodeRole() throws IOException {
        String yaml = """
            name: Node Role Study
            elements:
              - id: server
                type: SERVICE
                image: jvector:latest
                node_role: worker
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.elements().getFirst().stringProperty("node_role")).isEqualTo("worker");
    }

    @Test
    void testParseOutputSection() throws IOException {
        String yaml = """
            name: Output Study
            elements:
              - id: client
                type: COMMAND
                image: benchmark:latest
                output:
                  volume: /output
                  format: json
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.elements().getFirst().hasProperty("output")).isTrue();
        @SuppressWarnings("unchecked")
        var output1 = (java.util.Map<String, Object>) def.elements().getFirst().property("output");
        assertThat(output1.get("volume")).isEqualTo("/output");
        assertThat(output1.get("format")).isEqualTo("json");
    }

    @Test
    void testParseOutputSectionPartial() throws IOException {
        String yaml = """
            name: Partial Output Study
            elements:
              - id: client
                type: COMMAND
                image: benchmark:latest
                output:
                  volume: /data
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.elements().getFirst().hasProperty("output")).isTrue();
        @SuppressWarnings("unchecked")
        var output2 = (java.util.Map<String, Object>) def.elements().getFirst().property("output");
        assertThat(output2.get("volume")).isEqualTo("/data");
        assertThat(output2).doesNotContainKey("format");
    }

    @Test
    void testParseNoNodeRoleOrOutput() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        assertThat(def.elements().getFirst().hasProperty("node_role")).isFalse();
        assertThat(def.elements().getFirst().hasProperty("output")).isFalse();
    }

    @Test
    void testParseDefaultValues() throws IOException {
        String yaml = """
            name: Study
            elements:
              - id: server
                type: SERVICE
                image: server:latest
            axes:
              - parameter: threads
                element: server
                values: [1, 2]
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var axis = def.axes().getFirst();
        // Check default values
        assertThat(axis.mode()).isEqualTo("serial");
        assertThat(axis.nesting()).isEqualTo(0);
        assertThat(axis.repetitions()).isEqualTo(1);
    }

    @Test
    void testParsesLifelineDependency() throws IOException {
        String yaml = """
            name: Lifeline Study
            elements:
              - id: infra
                type: NODE
                constellation: test-infra
              - id: service
                type: SERVICE
                image: app:latest
                depends_on:
                  - element: infra
                    lifeline: true
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var service = def.elements().get(1);
        assertThat(service.dependsOn()).hasSize(1);
        assertThat(service.dependsOn().getFirst().element()).isEqualTo("infra");
        assertThat(service.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.LIFELINE);
    }

    @Test
    void testParsesLifelinePolicyDirectly() throws IOException {
        String yaml = """
            name: Lifeline Policy Study
            elements:
              - id: infra
                type: NODE
                constellation: test-infra
              - id: service
                type: SERVICE
                image: app:latest
                depends_on:
                  - element: infra
                    policy: LIFELINE
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var service = def.elements().get(1);
        assertThat(service.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.LIFELINE);
    }

    @Test
    void testParsesDedicatedPolicy() throws IOException {
        String yaml = """
            name: Dedicated Study
            elements:
              - id: infra
                type: NODE
                constellation: test-infra
              - id: service
                type: SERVICE
                image: app:latest
                depends_on:
                  - element: infra
                    policy: DEDICATED
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        var service = def.elements().get(1);
        assertThat(service.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.DEDICATED);
    }

    @Test
    void testDefaultsToSharedRelationship() throws IOException {
        String yaml = """
            name: No Lifeline Study
            elements:
              - id: infra
                type: NODE
                constellation: test-infra
              - id: service_string
                type: SERVICE
                image: app:latest
                depends_on: infra
              - id: service_list
                type: SERVICE
                image: app:latest
                depends_on:
                  - infra
              - id: service_map
                type: SERVICE
                image: app:latest
                depends_on:
                  - element: infra
                    policy: SHARED
            """;

        TestPlanDefinition def = parser.parseString(yaml);

        // String format defaults to SHARED
        var serviceString = def.elements().get(1);
        assertThat(serviceString.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.SHARED);

        // List-of-strings format defaults to SHARED
        var serviceList = def.elements().get(2);
        assertThat(serviceList.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.SHARED);

        // Map format with explicit SHARED
        var serviceMap = def.elements().get(3);
        assertThat(serviceMap.dependsOn().getFirst().relationship()).isEqualTo(RelationshipType.SHARED);
    }
}
