package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.TestPlan;

import java.time.Instant;
import java.util.*;

/**
 * Simple test plan metadata implementation.
 */
public class MockTestPlanMetadata implements TestPlan.TestPlanMetadata {
    private final Instant createdAt;
    private final Optional<String> createdBy;
    private final Optional<String> description;
    private final Map<String, String> tags;
    private final Optional<String> version;

    public MockTestPlanMetadata(Instant createdAt,
                               Optional<String> createdBy,
                               Optional<String> description,
                               Map<String, String> tags,
                               Optional<String> version) {
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.description = description;
        this.tags = new HashMap<>(tags);
        this.version = version;
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Optional<String> createdBy() {
        return createdBy;
    }

    @Override
    public Optional<String> description() {
        return description;
    }

    @Override
    public Map<String, String> tags() {
        return Collections.unmodifiableMap(tags);
    }

    @Override
    public Optional<String> version() {
        return version;
    }

    public static MockTestPlanMetadata empty() {
        return new MockTestPlanMetadata(
            Instant.now(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Optional.empty()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder() {}

        private Instant createdAt = Instant.now();
        private Optional<String> createdBy = Optional.empty();
        private Optional<String> description = Optional.empty();
        private final Map<String, String> tags = new HashMap<>();
        private Optional<String> version = Optional.empty();

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = Optional.of(createdBy);
            return this;
        }

        public Builder description(String description) {
            this.description = Optional.of(description);
            return this;
        }

        public Builder tag(String key, String value) {
            this.tags.put(key, value);
            return this;
        }

        public Builder version(String version) {
            this.version = Optional.of(version);
            return this;
        }

        public MockTestPlanMetadata build() {
            return new MockTestPlanMetadata(createdAt, createdBy, description, tags, version);
        }
    }
}
