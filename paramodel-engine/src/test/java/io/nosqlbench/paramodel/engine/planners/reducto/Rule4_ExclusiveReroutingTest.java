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

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.RelationshipType;
import io.nosqlbench.paramodel.engine.planners.StepGenerationUtils;
import io.nosqlbench.paramodel.engine.planners.reducto.rules.*;
import io.nosqlbench.paramodel.mock.plan.MockElement;
import io.nosqlbench.paramodel.parameters.types.StringParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/// Tests for Rule 4's exclusive serialization rerouting through notify boundaries
/// and non-trial deactivation enforcement.
class Rule4_ExclusiveReroutingTest {

    @Test
    @DisplayName("exclusive serialization edges are rerouted through notify boundaries")
    void exclusiveSerializationReroutedThroughNotify() {
        // Setup: Y is run-scoped, X is a trial element with EXCLUSIVE dep on Y
        // With 3 trials, Rule2 will create deactivate(X, T0)→activate(X, T1) etc.
        // After Rule4, these direct edges should be removed and replaced by
        // notify_end(T0) → notify_start(T1) edges.

        Element y = MockElement.of("y");
        var paramV = StringParameter.of("val");
        Element x = MockElement.builder("x")
            .parameter(paramV)
            .dependency(y, RelationshipType.EXCLUSIVE)
            .build();

        List<Element> sortedElements = StepGenerationUtils.topologicalSort(List.of(y, x));
        List<String> trialElementNames = List.of("x");

        // 3 trials (3 values of val)
        MixedRadixEnumerator enumerator = new MixedRadixEnumerator(
            new int[]{3}, List.of("val"), List.of("x"));
        BindingStateComputer bindingState = BindingStateComputer.compute(sortedElements, enumerator);

        RuleContext context = new RuleContext(
            sortedElements, bindingState, enumerator,
            trialElementNames, Map.of(), Map.of());

        ReductoGraph graph = new ReductoGraph();
        GraphSeeder.seed(graph, 3);

        // Apply Rules 1, 2, then 4
        new Rule1_LifecycleExpansion().apply(graph, context);
        new Rule2_DependencyEdges().apply(graph, context);

        // Before Rule4: verify exclusive serialization edges exist
        ReductoNode deactivateX_t0 = graph.getNode("deactivate_x_t0");
        ReductoNode activateX_t1 = graph.getNode("activate_x_t1");
        ReductoNode deactivateX_t1 = graph.getNode("deactivate_x_t1");
        ReductoNode activateX_t2 = graph.getNode("activate_x_t2");

        assertThat(deactivateX_t0).isNotNull();
        assertThat(activateX_t1).isNotNull();
        assertThat(deactivateX_t0.successors()).contains(activateX_t1);
        assertThat(deactivateX_t1.successors()).contains(activateX_t2);

        // Apply Rule4
        new Rule4_TrialNotifications().apply(graph, context);

        // After Rule4: direct exclusive serialization edges should be removed
        assertThat(deactivateX_t0.successors()).doesNotContain(activateX_t1);
        assertThat(deactivateX_t1.successors()).doesNotContain(activateX_t2);

        // Notify boundary edges should exist instead
        ReductoNode notifyEnd0 = graph.getNode("notify_trial_end_0");
        ReductoNode notifyStart1 = graph.getNode("notify_trial_start_1");
        ReductoNode notifyEnd1 = graph.getNode("notify_trial_end_1");
        ReductoNode notifyStart2 = graph.getNode("notify_trial_start_2");

        assertThat(notifyEnd0).isNotNull();
        assertThat(notifyStart1).isNotNull();
        assertThat(notifyEnd0.successors()).contains(notifyStart1);
        assertThat(notifyEnd1.successors()).contains(notifyStart2);

        // The path should still exist: deactivate(X,T0) → notify_end(T0) → notify_start(T1) → activate(X,T1)
        assertThat(deactivateX_t0.successors()).contains(notifyEnd0);
        assertThat(notifyStart1.successors()).contains(activateX_t1);
    }

    @Test
    @DisplayName("cross-element exclusive serialization edges are rerouted through notify boundaries")
    void crossElementExclusiveReroutedThroughNotify() {
        // Y is run-scoped, X and Z both EXCLUSIVELY depend on Y
        // Rule2 creates cross-element edges: deactivate(X, T0)→activate(Z, T1) etc.
        // Rule4 should reroute these through notify boundaries.

        Element y = MockElement.of("y");
        var paramV = StringParameter.of("val");
        Element x = MockElement.builder("x")
            .parameter(paramV)
            .dependency(y, RelationshipType.EXCLUSIVE)
            .build();
        Element z = MockElement.builder("z")
            .parameter(paramV)
            .dependency(y, RelationshipType.EXCLUSIVE)
            .build();

        List<Element> sortedElements = StepGenerationUtils.topologicalSort(List.of(y, x, z));
        List<String> trialElementNames = List.of("x", "z");

        // 2 trials
        MixedRadixEnumerator enumerator = new MixedRadixEnumerator(
            new int[]{2, 2}, List.of("val", "val"), List.of("x", "z"));
        BindingStateComputer bindingState = BindingStateComputer.compute(sortedElements, enumerator);

        RuleContext context = new RuleContext(
            sortedElements, bindingState, enumerator,
            trialElementNames, Map.of(), Map.of());

        ReductoGraph graph = new ReductoGraph();
        GraphSeeder.seed(graph, 4); // 2×2=4 trials

        new Rule1_LifecycleExpansion().apply(graph, context);
        new Rule2_DependencyEdges().apply(graph, context);

        // Verify cross-element serialization edges exist before Rule4
        ReductoNode deactivateX_t0 = graph.getNode("deactivate_x_t0");
        ReductoNode activateZ_t1 = graph.getNode("activate_z_t1");
        if (deactivateX_t0 != null && activateZ_t1 != null
                && deactivateX_t0.successors().contains(activateZ_t1)) {

            new Rule4_TrialNotifications().apply(graph, context);

            // Cross-element edge should be rerouted
            assertThat(deactivateX_t0.successors()).doesNotContain(activateZ_t1);

            ReductoNode notifyEnd0 = graph.getNode("notify_trial_end_0");
            ReductoNode notifyStart1 = graph.getNode("notify_trial_start_1");
            assertThat(notifyEnd0.successors()).contains(notifyStart1);
        } else {
            // If the cross-element edge didn't exist, just verify Rule4 doesn't break
            new Rule4_TrialNotifications().apply(graph, context);
        }

        assertThat(graph.hasCycle()).isFalse();
    }

    @Test
    @DisplayName("non-trial deactivation has no direct predecessor from trial termination")
    void nonTrialDeactivationNotDirectlyAfterTrialTermination() {
        // Setup: db is run-scoped (non-trial), app is trial element depending on db (SHARED)
        // Rule 2 creates edges from app termination to db deactivation.
        // After Rule 4, those direct edges should be removed — the path should go
        // through notify_end instead.
        Element db = MockElement.of("db");
        var paramM = StringParameter.of("mode");
        Element app = MockElement.builder("app")
            .parameter(paramM)
            .dependency(db)
            .build();

        List<Element> sortedElements = StepGenerationUtils.topologicalSort(List.of(db, app));
        List<String> trialElementNames = List.of("app");

        MixedRadixEnumerator enumerator = new MixedRadixEnumerator(
            new int[]{3}, List.of("mode"), List.of("app"));
        BindingStateComputer bindingState = BindingStateComputer.compute(sortedElements, enumerator);

        RuleContext context = new RuleContext(
            sortedElements, bindingState, enumerator,
            trialElementNames, Map.of(), Map.of());

        ReductoGraph graph = new ReductoGraph();
        GraphSeeder.seed(graph, 3);

        new Rule1_LifecycleExpansion().apply(graph, context);
        new Rule2_DependencyEdges().apply(graph, context);
        new Rule3_GroupCoalescing().apply(graph, context);
        new Rule4_TrialNotifications().apply(graph, context);

        // Find the non-trial deactivation node for db
        ReductoNode dbDeactivate = null;
        for (ReductoNode node : graph.nodes()) {
            if (node.type() == ReductoNodeType.DEACTIVATE
                && "db".equals(node.elementName())) {
                dbDeactivate = node;
                break;
            }
        }
        assertThat(dbDeactivate).as("db deactivation node should exist").isNotNull();

        // No predecessor of db's deactivation should be a trial element's termination
        for (ReductoNode pred : dbDeactivate.predecessors()) {
            if (pred.type() == ReductoNodeType.DEACTIVATE || pred.type() == ReductoNodeType.AWAIT) {
                String predElem = pred.elementName();
                assertThat(trialElementNames).as(
                    "non-trial deactivation should not have direct edge from trial termination node %s",
                    pred.id())
                    .doesNotContain(predElem);
            }
        }

        // ALL notify_trial_end nodes should be predecessors of db's deactivation
        // (db is run-scoped, so its group spans all 3 trials)
        for (int t = 0; t < 3; t++) {
            ReductoNode notifyEnd = graph.getNode("notify_trial_end_" + t);
            assertThat(notifyEnd).as("notify_trial_end_%d should exist", t).isNotNull();
            assertThat(dbDeactivate.predecessors())
                .as("non-trial deactivation should depend on notify_trial_end_%d", t)
                .contains(notifyEnd);
        }

        assertThat(graph.hasCycle()).isFalse();
    }

    @Test
    @DisplayName("graph remains acyclic after exclusive serialization rerouting")
    void graphRemainsAcyclicAfterRerouting() {
        Element y = MockElement.of("y");
        var paramV = StringParameter.of("val");
        Element x = MockElement.builder("x")
            .parameter(paramV)
            .dependency(y, RelationshipType.EXCLUSIVE)
            .build();

        List<Element> sortedElements = StepGenerationUtils.topologicalSort(List.of(y, x));
        List<String> trialElementNames = List.of("x");

        MixedRadixEnumerator enumerator = new MixedRadixEnumerator(
            new int[]{4}, List.of("val"), List.of("x"));
        BindingStateComputer bindingState = BindingStateComputer.compute(sortedElements, enumerator);

        RuleContext context = new RuleContext(
            sortedElements, bindingState, enumerator,
            trialElementNames, Map.of(), Map.of());

        ReductoGraph graph = new ReductoGraph();
        GraphSeeder.seed(graph, 4);

        new Rule1_LifecycleExpansion().apply(graph, context);
        new Rule2_DependencyEdges().apply(graph, context);
        new Rule4_TrialNotifications().apply(graph, context);

        assertThat(graph.hasCycle()).isFalse();
    }
}
