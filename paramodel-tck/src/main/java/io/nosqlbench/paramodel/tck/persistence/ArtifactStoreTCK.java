package io.nosqlbench.paramodel.tck.persistence;

import io.nosqlbench.paramodel.execution.ArtifactCollector;
import io.nosqlbench.paramodel.persistence.ArtifactStore;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link ArtifactStore} implementations.
///
/// Validates save, get, list, download, and delete operations
/// for artifact persistence.
///
/// @since 0.1.0
///
public abstract class ArtifactStoreTCK {

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private ArtifactStore store;

    @BeforeEach
    void setUp() {
        store = getProvider().createArtifactStore();
    }

    @Test
    void testSaveAndGetArtifact() {
        ArtifactCollector.Artifact artifact = getProvider().createArtifact(
            "art-1", "test.log", "trial-1");
        store.saveArtifact(artifact, new ByteArrayInputStream("hello".getBytes()));

        Optional<ArtifactCollector.Artifact> retrieved = store.getArtifact("art-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo("art-1");
        assertThat(retrieved.get().name()).isEqualTo("test.log");
    }

    @Test
    void testListArtifacts() {
        ArtifactCollector.Artifact art1 = getProvider().createArtifact(
            "art-1", "log1.txt", "trial-1");
        ArtifactCollector.Artifact art2 = getProvider().createArtifact(
            "art-2", "log2.txt", "trial-1");
        ArtifactCollector.Artifact art3 = getProvider().createArtifact(
            "art-3", "log3.txt", "trial-2");

        store.saveArtifact(art1, new ByteArrayInputStream(new byte[0]));
        store.saveArtifact(art2, new ByteArrayInputStream(new byte[0]));
        store.saveArtifact(art3, new ByteArrayInputStream(new byte[0]));

        List<ArtifactCollector.Artifact> trial1Artifacts = store.listArtifacts("trial-1");
        assertThat(trial1Artifacts).hasSize(2);

        List<ArtifactCollector.Artifact> trial2Artifacts = store.listArtifacts("trial-2");
        assertThat(trial2Artifacts).hasSize(1);
    }

    @Test
    void testDeleteArtifact() {
        ArtifactCollector.Artifact artifact = getProvider().createArtifact(
            "art-del", "to-delete.log", "trial-1");
        store.saveArtifact(artifact, new ByteArrayInputStream(new byte[0]));

        assertThat(store.getArtifact("art-del")).isPresent();

        store.deleteArtifact("art-del");
        assertThat(store.getArtifact("art-del")).isEmpty();
    }

    @Test
    void testDownloadArtifact() throws Exception {
        byte[] content = "artifact content data".getBytes();
        ArtifactCollector.Artifact artifact = getProvider().createArtifact(
            "art-dl", "data.bin", "trial-1");
        store.saveArtifact(artifact, new ByteArrayInputStream(content));

        try (InputStream downloaded = store.downloadArtifact("art-dl")) {
            assertThat(downloaded).isNotNull();
            byte[] downloadedBytes = downloaded.readAllBytes();
            assertThat(downloadedBytes).isEqualTo(content);
        }
    }

    @Test
    void testGetNonExistentArtifact() {
        Optional<ArtifactCollector.Artifact> result = store.getArtifact("does-not-exist");
        assertThat(result).isEmpty();
    }
}
