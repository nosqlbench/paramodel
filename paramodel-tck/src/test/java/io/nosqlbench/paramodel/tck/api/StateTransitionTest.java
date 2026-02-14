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
package io.nosqlbench.paramodel.tck.api;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.elements.OperationalStateObservable.StateTransition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for {@link StateTransition} record contract.
///
class StateTransitionTest {

    @Test
    @DisplayName("StateTransition stores all fields")
    void testFieldAccess() {
        Instant ts = Instant.parse("2026-01-15T10:30:00Z");
        StateTransition transition = new StateTransition(
            Element.OperationalState.UNKNOWN,
            Element.OperationalState.INACTIVE,
            "Not started",
            ts
        );

        assertThat(transition.from()).isEqualTo(Element.OperationalState.UNKNOWN);
        assertThat(transition.to()).isEqualTo(Element.OperationalState.INACTIVE);
        assertThat(transition.summary()).isEqualTo("Not started");
        assertThat(transition.timestamp()).isEqualTo(ts);
    }

    @Test
    @DisplayName("StateTransition rejects null from")
    void testRejectsNullFrom() {
        assertThatThrownBy(() -> new StateTransition(
            null,
            Element.OperationalState.INACTIVE,
            "summary",
            Instant.now()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("from");
    }

    @Test
    @DisplayName("StateTransition rejects null to")
    void testRejectsNullTo() {
        assertThatThrownBy(() -> new StateTransition(
            Element.OperationalState.UNKNOWN,
            null,
            "summary",
            Instant.now()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("to");
    }

    @Test
    @DisplayName("StateTransition rejects null summary")
    void testRejectsNullSummary() {
        assertThatThrownBy(() -> new StateTransition(
            Element.OperationalState.UNKNOWN,
            Element.OperationalState.INACTIVE,
            null,
            Instant.now()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("summary");
    }

    @Test
    @DisplayName("StateTransition rejects null timestamp")
    void testRejectsNullTimestamp() {
        assertThatThrownBy(() -> new StateTransition(
            Element.OperationalState.UNKNOWN,
            Element.OperationalState.INACTIVE,
            "summary",
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("timestamp");
    }

    @Test
    @DisplayName("StateTransition record equality is value-based")
    void testValueEquality() {
        Instant ts = Instant.parse("2026-01-15T10:30:00Z");
        StateTransition a = new StateTransition(
            Element.OperationalState.UNKNOWN,
            Element.OperationalState.RUNNING,
            "Started",
            ts
        );
        StateTransition b = new StateTransition(
            Element.OperationalState.UNKNOWN,
            Element.OperationalState.RUNNING,
            "Started",
            ts
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
