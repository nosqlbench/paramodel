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
import io.nosqlbench.paramodel.engine.definition.TestPlanDefinition.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/// Tests for {@link TestPlanDefinition#renameElement(String, String)}.
class TestPlanDefinitionRenameTest {

    @Test
    @DisplayName("Rename updates the element ID")
    void renameUpdatesElementId() {
        TestPlanDefinition def = planWith(
            List.of(element("db"), element("app")),
            List.of(), List.of());

        TestPlanDefinition renamed = def.renameElement("db", "database");

        assertThat(renamed.elements().stream().map(ElementDefinition::id).toList())
            .containsExactly("database", "app");
    }

    @Test
    @DisplayName("Rename updates dependency references in other elements")
    void renameUpdatesDependencyReferences() {
        ElementDefinition db = element("db");
        ElementDefinition app = elementWithDeps("app",
            List.of(new DependencyDefinition("db", RelationshipType.LINEAR)));

        TestPlanDefinition def = planWith(List.of(db, app), List.of(), List.of());
        TestPlanDefinition renamed = def.renameElement("db", "database");

        assertThat(renamed.elements().get(1).dependsOn().getFirst().element())
            .isEqualTo("database");
    }

    @Test
    @DisplayName("Rename updates axis element target")
    void renameUpdatesAxisTarget() {
        TestPlanDefinition def = planWith(
            List.of(element("server")),
            List.of(axis("threads", "server")),
            List.of());

        TestPlanDefinition renamed = def.renameElement("server", "svc");

        assertThat(renamed.axes().getFirst().element()).isEqualTo("svc");
    }

    @Test
    @DisplayName("Rename updates binding element target")
    void renameUpdatesBindingTarget() {
        TestPlanDefinition def = planWith(
            List.of(element("server"), element("client")),
            List.of(),
            List.of(new BindingDefinition("URL", "client", "http://localhost")));

        TestPlanDefinition renamed = def.renameElement("client", "bench");

        assertThat(renamed.bindings().getFirst().element()).isEqualTo("bench");
    }

    @Test
    @DisplayName("Rename updates ${element.export} references in binding values")
    void renameUpdatesExportReferencesInBindingValues() {
        TestPlanDefinition def = planWith(
            List.of(element("server"), element("client")),
            List.of(),
            List.of(new BindingDefinition("URL", "client",
                "http://${server.service_addr}/api")));

        TestPlanDefinition renamed = def.renameElement("server", "svc");

        assertThat(renamed.bindings().getFirst().value())
            .isEqualTo("http://${svc.service_addr}/api");
    }

    @Test
    @DisplayName("Rename updates ${output_of:element} references in parameters")
    void renameUpdatesOutputOfReferencesInParameters() {
        ElementDefinition indexer = element("indexer");
        ElementDefinition querier = new ElementDefinition(
            "querier", "COMMAND",
            Map.of("input", "${output_of:indexer}"),
            List.of(new DependencyDefinition("indexer", RelationshipType.LINEAR)),
            null, null);

        TestPlanDefinition def = planWith(List.of(indexer, querier), List.of(), List.of());
        TestPlanDefinition renamed = def.renameElement("indexer", "builder");

        assertThat(renamed.elements().get(1).parameters().get("input"))
            .isEqualTo("${output_of:builder}");
        assertThat(renamed.elements().get(1).dependsOn().getFirst().element())
            .isEqualTo("builder");
    }

    @Test
    @DisplayName("Rename updates ${output_of:element} references in binding values")
    void renameUpdatesOutputOfReferencesInBindingValues() {
        TestPlanDefinition def = planWith(
            List.of(element("indexer"), element("querier")),
            List.of(),
            List.of(new BindingDefinition("input", "querier",
                "${output_of:indexer}")));

        TestPlanDefinition renamed = def.renameElement("indexer", "builder");

        assertThat(renamed.bindings().getFirst().value())
            .isEqualTo("${output_of:builder}");
    }

    @Test
    @DisplayName("Rename throws when oldName does not exist")
    void renameThrowsWhenOldNameNotFound() {
        TestPlanDefinition def = planWith(
            List.of(element("db")), List.of(), List.of());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> def.renameElement("missing", "x"))
            .withMessageContaining("missing");
    }

    @Test
    @DisplayName("Rename throws when newName is already taken")
    void renameThrowsWhenNewNameConflicts() {
        TestPlanDefinition def = planWith(
            List.of(element("db"), element("app")), List.of(), List.of());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> def.renameElement("db", "app"))
            .withMessageContaining("already exists");
    }

    @Test
    @DisplayName("Rename to same name returns same instance")
    void renameToSameNameReturnsSameInstance() {
        TestPlanDefinition def = planWith(
            List.of(element("db")), List.of(), List.of());

        assertThat(def.renameElement("db", "db")).isSameAs(def);
    }

    @Test
    @DisplayName("Rename does not disturb unrelated elements, axes, or bindings")
    void renameDoesNotDisturbUnrelatedItems() {
        ElementDefinition db = element("db");
        ElementDefinition cache = element("cache");
        ElementDefinition app = elementWithDeps("app",
            List.of(new DependencyDefinition("db", RelationshipType.LINEAR)));
        AxisDefinition cacheAxis = axis("mem", "cache");
        BindingDefinition cacheBind = new BindingDefinition("MEM", "cache", "1024");

        TestPlanDefinition def = planWith(
            List.of(db, cache, app),
            List.of(cacheAxis),
            List.of(cacheBind));

        TestPlanDefinition renamed = def.renameElement("db", "database");

        // cache element unchanged
        assertThat(renamed.elements().get(1).id()).isEqualTo("cache");
        // cache axis unchanged
        assertThat(renamed.axes().getFirst().element()).isEqualTo("cache");
        // cache binding unchanged
        assertThat(renamed.bindings().getFirst().element()).isEqualTo("cache");
        // app's dependency updated
        assertThat(renamed.elements().get(2).dependsOn().getFirst().element())
            .isEqualTo("database");
    }

    @Test
    @DisplayName("Rename handles multiple references atomically")
    void renameHandlesMultipleReferencesAtomically() {
        ElementDefinition server = element("server");
        ElementDefinition app = elementWithDeps("app",
            List.of(new DependencyDefinition("server", RelationshipType.LINEAR)));
        ElementDefinition bench = elementWithDeps("bench",
            List.of(new DependencyDefinition("server", RelationshipType.LIFELINE)));
        AxisDefinition serverAxis = axis("threads", "server");
        BindingDefinition bind = new BindingDefinition("URL", "app",
            "http://${server.addr}");

        TestPlanDefinition def = planWith(
            List.of(server, app, bench),
            List.of(serverAxis),
            List.of(bind));

        TestPlanDefinition renamed = def.renameElement("server", "svc");

        assertThat(renamed.elements().get(0).id()).isEqualTo("svc");
        assertThat(renamed.elements().get(1).dependsOn().getFirst().element()).isEqualTo("svc");
        assertThat(renamed.elements().get(2).dependsOn().getFirst().element()).isEqualTo("svc");
        assertThat(renamed.axes().getFirst().element()).isEqualTo("svc");
        assertThat(renamed.bindings().getFirst().value()).isEqualTo("http://${svc.addr}");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static ElementDefinition element(String id) {
        return new ElementDefinition(id, "SERVICE", null, null, null, null);
    }

    private static ElementDefinition elementWithDeps(String id, List<DependencyDefinition> deps) {
        return new ElementDefinition(id, "SERVICE", null, deps, null, null);
    }

    private static AxisDefinition axis(String parameter, String element) {
        return new AxisDefinition(parameter, element, List.of(1, 2),
            null, null, null, null, null, null, null);
    }

    private static TestPlanDefinition planWith(
            List<ElementDefinition> elements,
            List<AxisDefinition> axes,
            List<BindingDefinition> bindings) {
        return new TestPlanDefinition("test-plan", "test", elements, axes, bindings, null);
    }
}
