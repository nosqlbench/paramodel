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
package io.nosqlbench.paramodel.mock.persistence;

import io.nosqlbench.paramodel.execution.journal.JournalEvent;
import io.nosqlbench.paramodel.persistence.JournalStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

///
/// In-memory journal store for testing.
///
/// Stores events in a concurrent map keyed by execution ID. Enforces
/// contiguous sequence numbers and provides replay, truncation, and
/// deletion. Not durable across process restarts.
///
/// @see JournalStore
/// @since 0.1.0
///
public class MockJournalStore implements JournalStore {
    private final ConcurrentHashMap<String, List<JournalEvent>> events = new ConcurrentHashMap<>();

    /// Creates a new empty journal store.
    public MockJournalStore() {}

    @Override
    public void append(JournalEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        events.compute(event.executionId(), (id, existing) -> {
            if (existing == null) {
                existing = new ArrayList<>();
            }
            long expectedSeq = existing.isEmpty() ? 1
                : existing.getLast().sequenceNumber() + 1;
            if (event.sequenceNumber() != expectedSeq) {
                throw new IllegalArgumentException(
                    "Non-contiguous sequence number: expected " + expectedSeq
                        + " but got " + event.sequenceNumber());
            }
            existing.add(event);
            return existing;
        });
    }

    @Override
    public Stream<JournalEvent> replay(String executionId, long afterSequence) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        List<JournalEvent> list = events.getOrDefault(executionId, List.of());
        return list.stream()
            .filter(e -> e.sequenceNumber() > afterSequence);
    }

    @Override
    public List<JournalEvent> allEvents(String executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        List<JournalEvent> list = events.get(executionId);
        return list == null ? List.of() : List.copyOf(list);
    }

    @Override
    public Optional<JournalEvent> latestEvent(String executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        List<JournalEvent> list = events.get(executionId);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.getLast());
    }

    @Override
    public long latestSequenceNumber(String executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        List<JournalEvent> list = events.get(executionId);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        return list.getLast().sequenceNumber();
    }

    @Override
    public void truncateBefore(String executionId, long beforeSequence) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        events.computeIfPresent(executionId, (id, list) -> {
            list.removeIf(e -> e.sequenceNumber() < beforeSequence);
            return list.isEmpty() ? null : list;
        });
    }

    @Override
    public void deleteAll(String executionId) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        events.remove(executionId);
    }
}
