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

import io.nosqlbench.paramodel.elements.TrialContext;
import io.nosqlbench.paramodel.mock.sequence.MockTrial;
import io.nosqlbench.paramodel.sequence.Trial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

///
/// Tests for {@link TrialContext} record contract.
///
class TrialContextTest {

    @Test
    @DisplayName("TrialContext stores trial and timestamp")
    void testFieldAccess() {
        Trial trial = MockTrial.builder().id("t-1").build();
        Instant ts = Instant.parse("2026-01-15T10:30:00Z");

        TrialContext ctx = new TrialContext(trial, ts);

        assertThat(ctx.trial()).isSameAs(trial);
        assertThat(ctx.timestamp()).isEqualTo(ts);
    }

    @Test
    @DisplayName("TrialContext rejects null trial")
    void testRejectsNullTrial() {
        assertThatThrownBy(() -> new TrialContext(null, Instant.now()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("trial");
    }

    @Test
    @DisplayName("TrialContext rejects null timestamp")
    void testRejectsNullTimestamp() {
        Trial trial = MockTrial.builder().id("t-1").build();

        assertThatThrownBy(() -> new TrialContext(trial, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    @DisplayName("TrialContext.now() uses current instant")
    void testNowFactory() {
        Trial trial = MockTrial.builder().id("t-1").build();
        Instant before = Instant.now();

        TrialContext ctx = TrialContext.now(trial);

        Instant after = Instant.now();
        assertThat(ctx.trial()).isSameAs(trial);
        assertThat(ctx.timestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("TrialContext record equality is value-based")
    void testValueEquality() {
        Trial trial = MockTrial.builder().id("t-1").build();
        Instant ts = Instant.parse("2026-01-15T10:30:00Z");

        TrialContext a = new TrialContext(trial, ts);
        TrialContext b = new TrialContext(trial, ts);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
