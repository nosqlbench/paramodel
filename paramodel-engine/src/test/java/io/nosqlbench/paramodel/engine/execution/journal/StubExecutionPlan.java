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

import io.nosqlbench.paramodel.plan.*;
import io.nosqlbench.paramodel.sequence.TrialResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// Minimal stub {@link ExecutionPlan} for unit tests.
///
class StubExecutionPlan implements ExecutionPlan {

    private final String id;
    private final List<AtomicStep> steps;

    StubExecutionPlan(String id, List<AtomicStep> steps) {
        this.id = id;
        this.steps = steps;
    }

    static StubExecutionPlan withId(String id) {
        return new StubExecutionPlan(id, List.of());
    }

    static StubExecutionPlan withSteps(String id, List<AtomicStep> steps) {
        return new StubExecutionPlan(id, steps);
    }

    @Override public String id() { return id; }
    @Override public String testPlanFingerprint() { return "test-fingerprint"; }
    @Override public List<AtomicStep> steps() { return steps; }
    @Override public List<Barrier> barriers() { return List.of(); }
    @Override public ExecutionGraph executionGraph() {
        throw new UnsupportedOperationException("Stub");
    }
    @Override public TrialOrdering trialOrdering() {
        throw new UnsupportedOperationException("Stub");
    }
    @Override public Optional<Duration> estimatedDuration() { return Optional.empty(); }
    @Override public int estimatedMaxParallelism() { return 1; }
    @Override public ResourceRequirements resourceRequirements() {
        return new ResourceRequirements(0, 0, 0, 0, Map.of());
    }
    @Override public Optional<CheckpointStrategy> checkpointStrategy() { return Optional.empty(); }
    @Override public Optional<Checkpoint> latestCheckpoint() { return Optional.empty(); }
    @Override public List<Checkpoint> checkpoints() { return List.of(); }
    @Override public ExecutionResults execute() { throw new UnsupportedOperationException("Stub"); }
    @Override public ExecutionResults execute(ExecutionObserver observer) {
        throw new UnsupportedOperationException("Stub");
    }
    @Override public ExecutionResults executeWithCheckpoints(Duration d) {
        throw new UnsupportedOperationException("Stub");
    }
    @Override public ExecutionPlan resumeFrom(Checkpoint c) {
        throw new UnsupportedOperationException("Stub");
    }
    @Override public ExecutionPlan withMaxConcurrency(int m) {
        throw new UnsupportedOperationException("Stub");
    }
    @Override public ExecutionPlanMetadata metadata() {
        throw new UnsupportedOperationException("Stub");
    }
}
