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
package io.nosqlbench.paramodel.engine.execution.journal;

import io.nosqlbench.paramodel.execution.Executor;
import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.persistence.CheckpointStore;
import io.nosqlbench.paramodel.persistence.JournalStore;
import io.nosqlbench.paramodel.plan.ExecutionPlan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

///
/// Default implementation of {@link JournalStateReconstructor}.
///
/// Loads the latest checkpoint (if any) as base state, finds the
/// corresponding {@link JournalEvent.CheckpointCreated} sequence
/// number, then replays all subsequent journal events to build
/// the {@link ExecutionSnapshot}.
///
/// @see ExecutionSnapshot
/// @see DefaultExecutionSnapshot
/// @since 0.1.0
///
public class DefaultJournalStateReconstructor implements JournalStateReconstructor {

    /// Creates a new reconstructor.
    public DefaultJournalStateReconstructor() {}

    @Override
    public ExecutionSnapshot reconstruct(
            String executionId,
            ExecutionPlan plan,
            JournalStore journalStore,
            CheckpointStore checkpointStore) {

        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(journalStore, "journalStore must not be null");
        Objects.requireNonNull(checkpointStore, "checkpointStore must not be null");

        DefaultExecutionSnapshot snapshot = new DefaultExecutionSnapshot();

        // 1. Load latest checkpoint as base state
        Optional<Executor.Checkpoint> latestCheckpoint =
            checkpointStore.getLatestCheckpoint(plan.id());
        latestCheckpoint.ifPresent(snapshot::initFromCheckpoint);

        // 2. Find the CheckpointCreated event's sequence number
        long replayAfterSequence = 0;
        if (latestCheckpoint.isPresent()) {
            String checkpointId = latestCheckpoint.get().checkpointId();
            replayAfterSequence = findCheckpointSequence(
                journalStore.allEvents(executionId), checkpointId);
        }

        // 3. Replay all events after the checkpoint
        journalStore.replay(executionId, replayAfterSequence)
            .forEach(snapshot::apply);

        return snapshot;
    }

    /// Finds the sequence number of the CheckpointCreated event with the given ID.
    private long findCheckpointSequence(List<JournalEvent> events, String checkpointId) {
        for (JournalEvent event : events) {
            if (event instanceof JournalEvent.CheckpointCreated cc
                    && checkpointId.equals(cc.checkpointId())) {
                return cc.sequenceNumber();
            }
        }
        return 0;
    }
}
