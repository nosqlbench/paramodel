package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.AtomicStep;

import java.time.Duration;
import java.util.*;

/**
 * Factory for creating {@link AtomicStep} instances for testing.
 *
 * Since {@link AtomicStep} is a sealed interface permitting only specific record types,
 * this class provides convenient factory methods rather than implementing the interface directly.
 */
public final class MockAtomicStep {

    private MockAtomicStep() {}

    /// Creates an {@link AtomicStep.ExecuteTrial} with minimal defaults.
    public static AtomicStep.ExecuteTrial executeTrial(String id, String trialId) {
        return new AtomicStep.ExecuteTrial(
            id,
            trialId,
            Map.of(),
            List.of(),
            Optional.empty(),
            AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(),
            Map.of()
        );
    }

    /// Creates an {@link AtomicStep.ExecuteTrial} with dependencies.
    public static AtomicStep.ExecuteTrial executeTrial(String id, String trialId,
                                                       List<String> dependencies) {
        return new AtomicStep.ExecuteTrial(
            id,
            trialId,
            Map.of(),
            dependencies,
            Optional.empty(),
            AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(),
            Map.of()
        );
    }

    /// Creates an {@link AtomicStep.DeployElement} with minimal defaults.
    public static AtomicStep.DeployElement deployElement(String id, String elementId) {
        return new AtomicStep.DeployElement(
            id,
            elementId,
            Map.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            AtomicStep.ResourceRequirements.minimal(),
            Optional.empty(),
            Map.of()
        );
    }

    /// Creates an {@link AtomicStep.TeardownElement} with minimal defaults.
    public static AtomicStep.TeardownElement teardownElement(String id, String elementId) {
        return new AtomicStep.TeardownElement(
            id,
            elementId,
            false,
            List.of(),
            Optional.empty(),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }

    /// Creates an {@link AtomicStep.BarrierSync} with minimal defaults.
    public static AtomicStep.BarrierSync barrierSync(String id, String barrierId,
                                                      List<String> dependencies) {
        return new AtomicStep.BarrierSync(
            id,
            barrierId,
            dependencies,
            Optional.empty(),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }

    /// Creates an {@link AtomicStep.CheckpointState} with minimal defaults.
    public static AtomicStep.CheckpointState checkpointState(String id, String checkpointId) {
        return new AtomicStep.CheckpointState(
            id,
            checkpointId,
            List.of(),
            Optional.empty(),
            AtomicStep.ResourceRequirements.none(),
            Optional.empty(),
            Map.of()
        );
    }
}
