package io.nosqlbench.paramodel.mock.security;

import io.nosqlbench.paramodel.security.CredentialManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

///
/// In-memory credential manager for testing.
///
/// Stores credentials in a simple map keyed by credential key.
/// No encryption or secure storage is performed.
///
/// @see CredentialManager
/// @since 0.1.0
///
public class MockCredentialManager implements CredentialManager {
    private final Map<String, Credential> credentials = new HashMap<>();

    @Override
    public void storeCredential(String key, Credential credential) {
        credentials.put(key, credential);
    }

    @Override
    public Optional<Credential> getCredential(String key) {
        return Optional.ofNullable(credentials.get(key));
    }

    @Override
    public void deleteCredential(String key) {
        credentials.remove(key);
    }

    ///
    /// Creates a credential with the given type and value.
    ///
    /// @param type credential type
    /// @param value credential value
    /// @return credential
    ///
    public static Credential credential(CredentialType type, String value) {
        return new MockCredential(type, value);
    }

    ///
    /// Simple credential implementation.
    ///
    public record MockCredential(
        CredentialType type,
        String value
    ) implements Credential {}
}
