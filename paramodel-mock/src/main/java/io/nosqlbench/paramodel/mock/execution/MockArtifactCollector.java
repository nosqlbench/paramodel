package io.nosqlbench.paramodel.mock.execution;

import io.nosqlbench.paramodel.execution.ArtifactCollector;
import io.nosqlbench.paramodel.sequence.Trial;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

///
/// Simple artifact collector implementation for testing.
///
/// Provides in-memory artifact storage with basic
/// collection, search, and cleanup operations.
///
/// @see ArtifactCollector
/// @since 0.1.0
///
public class MockArtifactCollector implements ArtifactCollector {
    private final Map<String, List<MockArtifact>> trialArtifacts = new LinkedHashMap<>();
    private final Map<String, CollectionPolicy> policies = new HashMap<>();
    private RetentionPolicy retentionPolicy;

    ///
    /// Creates a mock artifact collector.
    ///
    public MockArtifactCollector() {}

    @Override
    public void startCollection(Trial trial) {
        trialArtifacts.putIfAbsent(trial.id(), new ArrayList<>());
    }

    @Override
    public void stopCollection(Trial trial) {
        // no-op
    }

    @Override
    public void collectLogs(Trial trial, String logs, ArtifactType type) {
        trialArtifacts.computeIfAbsent(trial.id(), k -> new ArrayList<>()).add(
            new MockArtifact(
                UUID.randomUUID().toString(),
                type.name().toLowerCase() + ".log",
                type,
                logs.length(),
                Instant.now(),
                trial.id(),
                Optional.of("text/plain"),
                Map.of()
            )
        );
    }

    @Override
    public void collectMetrics(Trial trial, Map<String, Object> metrics) {
        trialArtifacts.computeIfAbsent(trial.id(), k -> new ArrayList<>()).add(
            new MockArtifact(
                UUID.randomUUID().toString(),
                "metrics.json",
                ArtifactType.METRIC,
                metrics.toString().length(),
                Instant.now(),
                trial.id(),
                Optional.of("application/json"),
                Map.of()
            )
        );
    }

    @Override
    public void collectArtifact(Trial trial, String name, InputStream content, ArtifactType type) {
        trialArtifacts.computeIfAbsent(trial.id(), k -> new ArrayList<>()).add(
            new MockArtifact(
                UUID.randomUUID().toString(),
                name,
                type,
                0,
                Instant.now(),
                trial.id(),
                Optional.empty(),
                Map.of()
            )
        );
    }

    @Override
    public LogStream openLogStream(Trial trial, ArtifactType type) {
        return new MockLogStream(trial, type);
    }

    @Override
    public ArtifactCollection finalizeCollection(Trial trial) {
        List<MockArtifact> artifacts = trialArtifacts.getOrDefault(trial.id(), List.of());
        long totalSize = artifacts.stream().mapToLong(MockArtifact::size).sum();
        return new MockArtifactCollection(
            trial.id(),
            artifacts.size(),
            totalSize,
            new ArrayList<>(artifacts),
            Instant.now()
        );
    }

    @Override
    public void setPolicy(Trial trial, CollectionPolicy policy) {
        policies.put(trial.id(), policy);
    }

    @Override
    public List<Artifact> artifacts(Trial trial) {
        return Collections.unmodifiableList(
            new ArrayList<>(trialArtifacts.getOrDefault(trial.id(), List.of())));
    }

    @Override
    public List<Artifact> search(Trial trial, ArtifactQuery query) {
        List<MockArtifact> all = trialArtifacts.getOrDefault(trial.id(), List.of());
        return all.stream()
            .filter(a -> query.type().map(t -> a.type() == t).orElse(true))
            .map(a -> (Artifact) a)
            .toList();
    }

    @Override
    public InputStream download(Artifact artifact) {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void setRetentionPolicy(RetentionPolicy policy) {
        this.retentionPolicy = policy;
    }

    @Override
    public CleanupReport cleanup() {
        return new MockCleanupReport(0, 0, 0L, 0.0, Duration.ZERO);
    }

    private record MockArtifact(
        String id,
        String name,
        ArtifactType type,
        long size,
        Instant collectedAt,
        String trialId,
        Optional<String> contentType,
        Map<String, String> metadata
    ) implements Artifact {}

    private record MockArtifactCollection(
        String trialId,
        int count,
        long totalSizeBytes,
        List<Artifact> artifacts,
        Instant collectedAt
    ) implements ArtifactCollection {}

    private record MockCleanupReport(
        int deletedCount,
        int compressedCount,
        long spaceFreedBytes,
        double spaceFreedGb,
        Duration duration
    ) implements CleanupReport {}

    private class MockLogStream implements LogStream {
        private final Trial trial;
        private final ArtifactType type;
        private final StringBuilder buffer = new StringBuilder();

        MockLogStream(Trial trial, ArtifactType type) {
            this.trial = trial;
            this.type = type;
        }

        @Override
        public void write(String line) {
            buffer.append(line).append("\n");
        }

        @Override
        public void write(byte[] data) {
            buffer.append(new String(data));
        }

        @Override
        public void flush() {}

        @Override
        public void close() {
            if (!buffer.isEmpty()) {
                collectLogs(trial, buffer.toString(), type);
            }
        }
    }
}
