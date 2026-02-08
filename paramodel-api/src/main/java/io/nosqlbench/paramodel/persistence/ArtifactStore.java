package io.nosqlbench.paramodel.persistence;

import io.nosqlbench.paramodel.execution.ArtifactCollector;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

///
/// # ArtifactStore
///
/// Persists execution artifacts (logs, metrics, traces, etc.).
///
public interface ArtifactStore {

    static ArtifactStore create() {
        throw new UnsupportedOperationException(
            "ArtifactStore.create() requires a concrete implementation");
    }

    void saveArtifact(ArtifactCollector.Artifact artifact, InputStream content);

    Optional<ArtifactCollector.Artifact> getArtifact(String artifactId);

    InputStream downloadArtifact(String artifactId);

    List<ArtifactCollector.Artifact> listArtifacts(String trialId);

    void deleteArtifact(String artifactId);
}
