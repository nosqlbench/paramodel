package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.ExecutionPlanMetadata;

import java.time.Instant;
import java.util.*;

/**
 * Simple execution plan metadata implementation.
 */
public class MockExecutionPlanMetadata implements ExecutionPlanMetadata {
    private final String compilationVersion;
    private final Instant compiledAt;
    private final Map<String, Object> optimizationMetrics;
    private final String fingerprint;

    public MockExecutionPlanMetadata(String compilationVersion, Instant compiledAt,
                                    Map<String, Object> optimizationMetrics, String fingerprint) {
        this.compilationVersion = compilationVersion;
        this.compiledAt = compiledAt;
        this.optimizationMetrics = new HashMap<>(optimizationMetrics);
        this.fingerprint = fingerprint;
    }

    @Override
    public String compilationVersion() {
        return compilationVersion;
    }

    @Override
    public Instant compiledAt() {
        return compiledAt;
    }

    @Override
    public Map<String, Object> optimizationMetrics() {
        return Collections.unmodifiableMap(optimizationMetrics);
    }

    @Override
    public String fingerprint() {
        return fingerprint;
    }

    public static MockExecutionPlanMetadata empty() {
        return new MockExecutionPlanMetadata("1.0", Instant.now(), Map.of(), UUID.randomUUID().toString());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String compilationVersion = "1.0";
        private Instant compiledAt = Instant.now();
        private final Map<String, Object> optimizationMetrics = new HashMap<>();
        private String fingerprint = UUID.randomUUID().toString();

        public Builder compilationVersion(String version) {
            this.compilationVersion = version;
            return this;
        }

        public Builder compiledAt(Instant instant) {
            this.compiledAt = instant;
            return this;
        }

        public Builder metric(String key, Object value) {
            this.optimizationMetrics.put(key, value);
            return this;
        }

        public Builder fingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
            return this;
        }

        public MockExecutionPlanMetadata build() {
            return new MockExecutionPlanMetadata(compilationVersion, compiledAt, optimizationMetrics, fingerprint);
        }
    }
}
