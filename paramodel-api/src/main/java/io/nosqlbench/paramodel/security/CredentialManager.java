package io.nosqlbench.paramodel.security;

import java.util.Optional;

///
/// # CredentialManager
///
/// Manages credentials for accessing elements and external services securely.
///
public interface CredentialManager {

    static CredentialManager create() {
        throw new UnsupportedOperationException(
            "CredentialManager.create() requires a concrete implementation");
    }

    void storeCredential(String key, Credential credential);

    Optional<Credential> getCredential(String key);

    void deleteCredential(String key);

    interface Credential {
        CredentialType type();
        String value();
    }

    enum CredentialType {
        PASSWORD,
        API_KEY,
        TOKEN,
        CERTIFICATE
    }
}
