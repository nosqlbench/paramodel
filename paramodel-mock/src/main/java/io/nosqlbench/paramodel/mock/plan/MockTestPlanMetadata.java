package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.core.metadata.TestPlanMetadata;

import java.util.*;

/**
 * Simple test plan metadata implementation.
 */
public class MockTestPlanMetadata implements TestPlanMetadata {
    private final String version;
    private final String fingerprint;
    private final Map<String, String> provenance;
    private final Map<String, String> tags;
    private final Optional<String> description;

    public MockTestPlanMetadata(String version, String fingerprint,
                               Map<String, String> provenance,
                               Map<String, String> tags,
                               Optional<String> description) {
        this.version = version;
        this.fingerprint = fingerprint;
        this.provenance = new HashMap<>(provenance);
        this.tags = new HashMap<>(tags);
        this.description = description;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public String fingerprint() {
        return fingerprint;
    }

    @Override
    public Map<String, String> provenance() {
        return Collections.unmodifiableMap(provenance);
    }

    @Override
    public Map<String, String> tags() {
        return Collections.unmodifiableMap(tags);
    }

    @Override
    public Optional<String> description() {
        return description;
    }

    public static MockTestPlanMetadata empty() {
        return new MockTestPlanMetadata("1.0", UUID.randomUUID().toString(), Map.of(), Map.of(), Optional.empty());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String version = "1.0";
        private String fingerprint = UUID.randomUUID().toString();
        private final Map<String, String> provenance = new HashMap<>();
        private final Map<String, String> tags = new HashMap<>();
        private Optional<String> description = Optional.empty();

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder fingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
            return this;
        }

        public Builder provenance(String key, String value) {
            this.provenance.put(key, value);
            return this;
        }

        public Builder tag(String key, String value) {
            this.tags.put(key, value);
            return this;
        }

        public Builder description(String description) {
            this.description = Optional.of(description);
            return this;
        }

        public MockTestPlanMetadata build() {
            return new MockTestPlanMetadata(version, fingerprint, provenance, tags, description);
        }
    }
}
