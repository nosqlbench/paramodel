package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.ExecutionPlanMetadata;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Simple execution plan metadata implementation.
 */
public class MockExecutionPlanMetadata implements ExecutionPlanMetadata {
    private final String id;
    private final String testPlanFingerprint;
    private final Instant compiledAt;
    private final Duration compilationDuration;
    private final String compilerVersion;
    private final OptimizationLevel optimizationLevel;
    private final int trialCount;
    private final int stepCount;
    private final int barrierCount;
    private final int elementInstanceCount;
    private final ResourceProfile resourceProfile;
    private final Duration estimatedDuration;
    private final Double estimatedCost;
    private final PerformanceMetrics performanceMetrics;
    private final Map<String, Object> customMetadata;

    public MockExecutionPlanMetadata(String id, String testPlanFingerprint,
                                    Instant compiledAt, Duration compilationDuration,
                                    String compilerVersion, OptimizationLevel optimizationLevel,
                                    int trialCount, int stepCount, int barrierCount,
                                    int elementInstanceCount, Map<String, Object> customMetadata) {
        this.id = id;
        this.testPlanFingerprint = testPlanFingerprint;
        this.compiledAt = compiledAt;
        this.compilationDuration = compilationDuration;
        this.compilerVersion = compilerVersion;
        this.optimizationLevel = optimizationLevel;
        this.trialCount = trialCount;
        this.stepCount = stepCount;
        this.barrierCount = barrierCount;
        this.elementInstanceCount = elementInstanceCount;
        this.resourceProfile = new ResourceProfile(1.0, 1.0, 1.0, 1.0, 1.0, 0.1);
        this.estimatedDuration = Duration.ZERO;
        this.estimatedCost = null;
        this.performanceMetrics = new PerformanceMetrics(
            1, 1.0, Duration.ZERO, Duration.ZERO, 1.0, 1.0,
            new GraphComplexity(stepCount, 0, 0.0, 0, 0)
        );
        this.customMetadata = customMetadata != null ? Map.copyOf(customMetadata) : Map.of();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String testPlanFingerprint() {
        return testPlanFingerprint;
    }

    @Override
    public Instant compiledAt() {
        return compiledAt;
    }

    @Override
    public Duration compilationDuration() {
        return compilationDuration;
    }

    @Override
    public String compilerVersion() {
        return compilerVersion;
    }

    @Override
    public OptimizationLevel optimizationLevel() {
        return optimizationLevel;
    }

    @Override
    public int trialCount() {
        return trialCount;
    }

    @Override
    public int stepCount() {
        return stepCount;
    }

    @Override
    public int barrierCount() {
        return barrierCount;
    }

    @Override
    public int elementInstanceCount() {
        return elementInstanceCount;
    }

    @Override
    public ResourceProfile resourceProfile() {
        return resourceProfile;
    }

    @Override
    public Duration estimatedDuration() {
        return estimatedDuration;
    }

    @Override
    public Optional<Double> estimatedCost() {
        return Optional.ofNullable(estimatedCost);
    }

    @Override
    public PerformanceMetrics performanceMetrics() {
        return performanceMetrics;
    }

    @Override
    public Optional<OptimizationReport> optimizationReport() {
        return Optional.empty();
    }

    @Override
    public List<ExecutionRecord> executionHistory() {
        return List.of();
    }

    @Override
    public Optional<ExecutionRecord> latestExecution() {
        return Optional.empty();
    }

    @Override
    public int executionCount() {
        return 0;
    }

    @Override
    public int successfulExecutionCount() {
        return 0;
    }

    @Override
    public Map<String, Object> customMetadata() {
        return customMetadata;
    }

    public static MockExecutionPlanMetadata empty() {
        return new MockExecutionPlanMetadata(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            Instant.now(),
            Duration.ZERO,
            "1.0",
            OptimizationLevel.NONE,
            0, 0, 0, 0,
            Map.of()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder() {}

        private String id = UUID.randomUUID().toString();
        private String testPlanFingerprint = UUID.randomUUID().toString();
        private Instant compiledAt = Instant.now();
        private Duration compilationDuration = Duration.ZERO;
        private String compilerVersion = "1.0";
        private OptimizationLevel optimizationLevel = OptimizationLevel.NONE;
        private int trialCount = 0;
        private int stepCount = 0;
        private int barrierCount = 0;
        private int elementInstanceCount = 0;
        private final Map<String, Object> customMetadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder testPlanFingerprint(String fingerprint) {
            this.testPlanFingerprint = fingerprint;
            return this;
        }

        public Builder compiledAt(Instant instant) {
            this.compiledAt = instant;
            return this;
        }

        public Builder compilationDuration(Duration duration) {
            this.compilationDuration = duration;
            return this;
        }

        public Builder compilerVersion(String version) {
            this.compilerVersion = version;
            return this;
        }

        public Builder optimizationLevel(OptimizationLevel level) {
            this.optimizationLevel = level;
            return this;
        }

        public Builder trialCount(int count) {
            this.trialCount = count;
            return this;
        }

        public Builder stepCount(int count) {
            this.stepCount = count;
            return this;
        }

        public Builder barrierCount(int count) {
            this.barrierCount = count;
            return this;
        }

        public Builder elementInstanceCount(int count) {
            this.elementInstanceCount = count;
            return this;
        }

        public Builder customMetadata(String key, Object value) {
            this.customMetadata.put(key, value);
            return this;
        }

        public MockExecutionPlanMetadata build() {
            return new MockExecutionPlanMetadata(
                id, testPlanFingerprint, compiledAt, compilationDuration,
                compilerVersion, optimizationLevel, trialCount, stepCount,
                barrierCount, elementInstanceCount, customMetadata
            );
        }
    }
}
