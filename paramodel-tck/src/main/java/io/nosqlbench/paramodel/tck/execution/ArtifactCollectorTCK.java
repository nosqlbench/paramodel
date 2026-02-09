package io.nosqlbench.paramodel.tck.execution;

import io.nosqlbench.paramodel.execution.ArtifactCollector;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for ArtifactCollector contract.
///
/// Validates that implementations correctly:
/// - Start and stop artifact collection
/// - Collect logs and metrics
/// - Finalize collection summaries
/// - Set collection policies
/// - Perform cleanup
///
/// @see ArtifactCollector
/// @since 0.1.0
///
public abstract class ArtifactCollectorTCK {
    protected ArtifactCollectorTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testCollectorStartCollection() {
        ArtifactCollector collector = getProvider().createArtifactCollector();
        Trial trial = getProvider().createTrial("trial-1");

        assertThatCode(() -> collector.startCollection(trial))
            .doesNotThrowAnyException();
    }

    @Test
    public void testCollectorCollectLogs() {
        ArtifactCollector collector = getProvider().createArtifactCollector();
        Trial trial = getProvider().createTrial("trial-1");
        collector.startCollection(trial);

        collector.collectLogs(trial, "test log output", ArtifactCollector.ArtifactType.STDOUT);

        assertThat(collector.artifacts(trial)).isNotEmpty();
    }

    @Test
    public void testCollectorCollectMetrics() {
        ArtifactCollector collector = getProvider().createArtifactCollector();
        Trial trial = getProvider().createTrial("trial-1");
        collector.startCollection(trial);

        collector.collectMetrics(trial, Map.of("cpu", 45.0, "memory", 2.1));

        assertThat(collector.artifacts(trial)).isNotEmpty();
    }

    @Test
    public void testCollectorArtifacts() {
        ArtifactCollector collector = getProvider().createArtifactCollector();
        Trial trial = getProvider().createTrial("trial-1");

        assertThat(collector.artifacts(trial)).isNotNull();
    }

    @Test
    public void testCollectorFinalizeCollection() {
        ArtifactCollector collector = getProvider().createArtifactCollector();
        Trial trial = getProvider().createTrial("trial-1");
        collector.startCollection(trial);
        collector.collectLogs(trial, "test output", ArtifactCollector.ArtifactType.STDOUT);

        ArtifactCollector.ArtifactCollection collection = collector.finalizeCollection(trial);

        assertThat(collection).isNotNull();
        assertThat(collection.trialId()).isEqualTo("trial-1");
        assertThat(collection.count()).isGreaterThanOrEqualTo(1);
        assertThat(collection.artifacts()).isNotEmpty();
        assertThat(collection.collectedAt()).isNotNull();
    }

    @Test
    public void testCollectorCleanup() {
        ArtifactCollector collector = getProvider().createArtifactCollector();

        ArtifactCollector.CleanupReport report = collector.cleanup();

        assertThat(report).isNotNull();
        assertThat(report.deletedCount()).isGreaterThanOrEqualTo(0);
        assertThat(report.duration()).isNotNull();
    }
}
