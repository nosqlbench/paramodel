package io.nosqlbench.paramodel.mock.persistence;

import io.nosqlbench.paramodel.execution.ArtifactCollector;
import io.nosqlbench.paramodel.persistence.ArtifactStore;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

///
/// In-memory artifact store for testing.
///
/// Stores artifacts and their content in memory maps. Content is
/// read from the provided input stream and stored as byte arrays.
///
/// @see ArtifactStore
/// @since 0.1.0
///
public class MockArtifactStore implements ArtifactStore {
    private final Map<String, ArtifactCollector.Artifact> artifacts = new LinkedHashMap<>();
    private final Map<String, byte[]> contents = new HashMap<>();

    /// Creates a new empty artifact store.
    public MockArtifactStore() {}

    @Override
    public void saveArtifact(ArtifactCollector.Artifact artifact, InputStream content) {
        Objects.requireNonNull(artifact, "artifact must not be null");
        Objects.requireNonNull(content, "content must not be null");
        artifacts.put(artifact.id(), artifact);
        try {
            contents.put(artifact.id(), content.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read artifact content", e);
        }
    }

    @Override
    public Optional<ArtifactCollector.Artifact> getArtifact(String artifactId) {
        return Optional.ofNullable(artifacts.get(artifactId));
    }

    @Override
    public InputStream downloadArtifact(String artifactId) {
        byte[] data = contents.get(artifactId);
        if (data == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public List<ArtifactCollector.Artifact> listArtifacts(String trialId) {
        return artifacts.values().stream()
            .filter(a -> trialId.equals(a.trialId()))
            .toList();
    }

    @Override
    public void deleteArtifact(String artifactId) {
        artifacts.remove(artifactId);
        contents.remove(artifactId);
    }
}
