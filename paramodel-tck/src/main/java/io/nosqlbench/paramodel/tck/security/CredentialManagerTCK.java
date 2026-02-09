package io.nosqlbench.paramodel.tck.security;

import io.nosqlbench.paramodel.security.CredentialManager;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link CredentialManager} implementations.
///
/// Validates store, get, and delete operations for credential
/// management.
///
/// @since 0.1.0
///
public abstract class CredentialManagerTCK {

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private CredentialManager credentialManager;

    @BeforeEach
    void setUp() {
        credentialManager = getProvider().createCredentialManager();
    }

    @Test
    void testStoreAndGetCredential() {
        CredentialManager.Credential credential = getProvider().createCredential(
            CredentialManager.CredentialType.API_KEY, "sk-test-12345");
        credentialManager.storeCredential("my-api-key", credential);

        Optional<CredentialManager.Credential> retrieved =
            credentialManager.getCredential("my-api-key");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().type()).isEqualTo(CredentialManager.CredentialType.API_KEY);
        assertThat(retrieved.get().value()).isEqualTo("sk-test-12345");
    }

    @Test
    void testDeleteCredential() {
        CredentialManager.Credential credential = getProvider().createCredential(
            CredentialManager.CredentialType.PASSWORD, "secret123");
        credentialManager.storeCredential("db-password", credential);

        assertThat(credentialManager.getCredential("db-password")).isPresent();

        credentialManager.deleteCredential("db-password");
        assertThat(credentialManager.getCredential("db-password")).isEmpty();
    }

    @Test
    void testGetNonExistentCredential() {
        Optional<CredentialManager.Credential> result =
            credentialManager.getCredential("does-not-exist");
        assertThat(result).isEmpty();
    }
}
