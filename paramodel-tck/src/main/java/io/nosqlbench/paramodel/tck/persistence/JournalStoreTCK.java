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
package io.nosqlbench.paramodel.tck.persistence;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.AtomicStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

///
/// Technology Compatibility Kit tests for {@link JournalStore} implementations.
///
/// Validates:
/// - Append and replay in order
/// - Replay with afterSequence filter
/// - latestEvent / latestSequenceNumber
/// - truncateBefore removes correct events
/// - Contiguity enforcement (rejects gaps)
/// - Multiple executions are isolated
/// - Empty execution returns empty/0
///
/// @see JournalStore
/// @since 0.1.0
///
public abstract class JournalStoreTCK {

    /// Creates a new TCK test instance.
    protected JournalStoreTCK() {}

    /// Returns a fresh JournalStore instance for testing.
    protected abstract JournalStore createJournalStore();

    private JournalStore store;

    @BeforeEach
    void setUp() {
        store = createJournalStore();
    }

    @Test
    void testAppendAndReplayInOrder() {
        JournalEvent e1 = executionStarted("exec-1", "plan-1", 1);
        JournalEvent e2 = phaseTransition("exec-1", "plan-1", 2);
        JournalEvent e3 = stepStarted("exec-1", "plan-1", 3, "step-1");

        store.append(e1);
        store.append(e2);
        store.append(e3);

        List<JournalEvent> all = store.allEvents("exec-1");
        assertThat(all).hasSize(3);
        assertThat(all.get(0).sequenceNumber()).isEqualTo(1);
        assertThat(all.get(1).sequenceNumber()).isEqualTo(2);
        assertThat(all.get(2).sequenceNumber()).isEqualTo(3);
    }

    @Test
    void testReplayWithAfterSequenceFilter() {
        store.append(executionStarted("exec-1", "plan-1", 1));
        store.append(phaseTransition("exec-1", "plan-1", 2));
        store.append(stepStarted("exec-1", "plan-1", 3, "step-1"));

        List<JournalEvent> fromSeq2 = store.replay("exec-1", 2).toList();
        assertThat(fromSeq2).hasSize(1);
        assertThat(fromSeq2.getFirst().sequenceNumber()).isEqualTo(3);
    }

    @Test
    void testReplayFromBeginning() {
        store.append(executionStarted("exec-1", "plan-1", 1));
        store.append(phaseTransition("exec-1", "plan-1", 2));

        List<JournalEvent> all = store.replay("exec-1", 0).toList();
        assertThat(all).hasSize(2);
    }

    @Test
    void testLatestEvent() {
        store.append(executionStarted("exec-1", "plan-1", 1));
        store.append(phaseTransition("exec-1", "plan-1", 2));

        Optional<JournalEvent> latest = store.latestEvent("exec-1");
        assertThat(latest).isPresent();
        assertThat(latest.get().sequenceNumber()).isEqualTo(2);
    }

    @Test
    void testLatestSequenceNumber() {
        store.append(executionStarted("exec-1", "plan-1", 1));
        store.append(phaseTransition("exec-1", "plan-1", 2));
        store.append(stepStarted("exec-1", "plan-1", 3, "step-1"));

        assertThat(store.latestSequenceNumber("exec-1")).isEqualTo(3);
    }

    @Test
    void testTruncateBeforeRemovesCorrectEvents() {
        store.append(executionStarted("exec-1", "plan-1", 1));
        store.append(phaseTransition("exec-1", "plan-1", 2));
        store.append(stepStarted("exec-1", "plan-1", 3, "step-1"));

        store.truncateBefore("exec-1", 3);

        List<JournalEvent> remaining = store.allEvents("exec-1");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().sequenceNumber()).isEqualTo(3);
    }

    @Test
    void testContiguityEnforcementRejectsGaps() {
        store.append(executionStarted("exec-1", "plan-1", 1));

        assertThatThrownBy(() -> store.append(stepStarted("exec-1", "plan-1", 3, "step-1")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testContiguityEnforcementRejectsDuplicates() {
        store.append(executionStarted("exec-1", "plan-1", 1));

        assertThatThrownBy(() -> store.append(phaseTransition("exec-1", "plan-1", 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMultipleExecutionsAreIsolated() {
        store.append(executionStarted("exec-1", "plan-1", 1));
        store.append(phaseTransition("exec-1", "plan-1", 2));

        store.append(executionStarted("exec-2", "plan-2", 1));

        assertThat(store.allEvents("exec-1")).hasSize(2);
        assertThat(store.allEvents("exec-2")).hasSize(1);
        assertThat(store.latestSequenceNumber("exec-1")).isEqualTo(2);
        assertThat(store.latestSequenceNumber("exec-2")).isEqualTo(1);
    }

    @Test
    void testEmptyExecutionReturnsEmptyAndZero() {
        assertThat(store.allEvents("nonexistent")).isEmpty();
        assertThat(store.latestEvent("nonexistent")).isEmpty();
        assertThat(store.latestSequenceNumber("nonexistent")).isEqualTo(0);
        assertThat(store.replay("nonexistent", 0).toList()).isEmpty();
    }

    @Test
    void testDeleteAllRemovesAllEvents() {
        store.append(executionStarted("exec-1", "plan-1", 1));
        store.append(phaseTransition("exec-1", "plan-1", 2));

        store.deleteAll("exec-1");

        assertThat(store.allEvents("exec-1")).isEmpty();
        assertThat(store.latestSequenceNumber("exec-1")).isEqualTo(0);
    }

    @Test
    void testDeleteAllDoesNotAffectOtherExecutions() {
        store.append(executionStarted("exec-1", "plan-1", 1));
        store.append(executionStarted("exec-2", "plan-2", 1));

        store.deleteAll("exec-1");

        assertThat(store.allEvents("exec-1")).isEmpty();
        assertThat(store.allEvents("exec-2")).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Helper methods for creating test events
    // -----------------------------------------------------------------------

    private static JournalEvent executionStarted(String execId, String planId, long seq) {
        return new JournalEvent.ExecutionStarted(
            seq, execId, planId, Instant.now(), Optional.empty(), Map.of());
    }

    private static JournalEvent phaseTransition(String execId, String planId, long seq) {
        return new JournalEvent.PhaseTransition(
            seq, execId, planId, Instant.now(),
            Executor.ExecutionPhase.INITIALIZING, Executor.ExecutionPhase.DEPLOYING);
    }

    private static JournalEvent stepStarted(String execId, String planId, long seq, String stepId) {
        return new JournalEvent.StepStarted(
            seq, execId, planId, Instant.now(),
            stepId, AtomicStep.StepType.DEPLOY_ELEMENT, Optional.empty());
    }
}
