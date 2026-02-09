package io.nosqlbench.paramodel.mock.sequence;

import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.sequence.TrialResult;
import io.nosqlbench.paramodel.sequence.TrialStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Simple trial result implementation.
 */
public class MockTrialResult implements TrialResult {
    private final Trial trial;
    private final TrialStatus status;
    private final Map<String, Object> metrics;
    private final List<ArtifactReference> artifacts;
    private final ExecutionTiming timing;
    private final ProvenanceInfo provenance;
    private final ErrorInfo errorInfo;
    private final String skipReason;
    private final int attemptNumber;

    public MockTrialResult(Trial trial, TrialStatus status,
                          Map<String, Object> metrics,
                          List<ArtifactReference> artifacts,
                          ExecutionTiming timing,
                          ProvenanceInfo provenance,
                          ErrorInfo errorInfo,
                          String skipReason,
                          int attemptNumber) {
        this.trial = Objects.requireNonNull(trial);
        this.status = Objects.requireNonNull(status);
        this.metrics = metrics != null ? Map.copyOf(metrics) : Map.of();
        this.artifacts = artifacts != null ? List.copyOf(artifacts) : List.of();
        this.timing = Objects.requireNonNull(timing);
        this.provenance = Objects.requireNonNull(provenance);
        this.errorInfo = errorInfo;
        this.skipReason = skipReason;
        this.attemptNumber = attemptNumber;
    }

    @Override
    public Trial trial() {
        return trial;
    }

    @Override
    public TrialStatus status() {
        return status;
    }

    @Override
    public Map<String, Object> metrics() {
        return metrics;
    }

    @Override
    public List<ArtifactReference> artifacts() {
        return artifacts;
    }

    @Override
    public ExecutionTiming timing() {
        return timing;
    }

    @Override
    public ProvenanceInfo provenance() {
        return provenance;
    }

    @Override
    public Optional<ErrorInfo> error() {
        return Optional.ofNullable(errorInfo);
    }

    @Override
    public Optional<String> skipReason() {
        return Optional.ofNullable(skipReason);
    }

    @Override
    public int attemptNumber() {
        return attemptNumber;
    }

    public static MockTrialResult success(Trial trial, Map<String, Object> metrics) {
        Instant now = Instant.now();
        return new Builder(trial)
            .status(TrialStatus.COMPLETED)
            .metrics(metrics)
            .startTime(now)
            .endTime(now)
            .build();
    }

    public static MockTrialResult failed(Trial trial, String errorMessage) {
        Instant now = Instant.now();
        return new Builder(trial)
            .status(TrialStatus.FAILED)
            .startTime(now)
            .endTime(now)
            .error(errorMessage)
            .build();
    }

    public static Builder builder(Trial trial) {
        return new Builder(trial);
    }

    public static class Builder {
        private final Trial trial;
        private TrialStatus status = TrialStatus.COMPLETED;
        private Map<String, Object> metrics = new HashMap<>();
        private List<ArtifactReference> artifacts = new ArrayList<>();
        private Instant startTime = Instant.now();
        private Instant endTime = Instant.now();
        private String errorMessage;
        private String skipReason;
        private int attemptNumber = 1;

        public Builder(Trial trial) {
            this.trial = Objects.requireNonNull(trial);
        }

        public Builder status(TrialStatus status) {
            this.status = status;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = new HashMap<>(metrics);
            return this;
        }

        public Builder metric(String key, Object value) {
            this.metrics.put(key, value);
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder error(String errorMessage) {
            this.errorMessage = errorMessage;
            this.status = TrialStatus.FAILED;
            return this;
        }

        public Builder skipReason(String reason) {
            this.skipReason = reason;
            this.status = TrialStatus.SKIPPED;
            return this;
        }

        public Builder attemptNumber(int attemptNumber) {
            this.attemptNumber = attemptNumber;
            return this;
        }

        public MockTrialResult build() {
            ExecutionTiming timing = new MockExecutionTiming(startTime, endTime);
            ProvenanceInfo provenance = new MockProvenanceInfo(
                UUID.randomUUID().toString(),
                Optional.empty(),
                Optional.empty(),
                Map.of()
            );
            ErrorInfo errorInfo = errorMessage != null
                ? new MockErrorInfo(errorMessage)
                : null;
            return new MockTrialResult(
                trial, status, metrics, artifacts, timing, provenance,
                errorInfo, skipReason, attemptNumber
            );
        }
    }

    private record MockExecutionTiming(Instant startedAt, Instant completedAt) implements ExecutionTiming {}

    private record MockProvenanceInfo(
        String configurationFingerprint,
        Optional<String> sequenceId,
        Optional<String> executionPlanVersion,
        Map<String, String> executionEnvironment
    ) implements ProvenanceInfo {}

    private static class MockErrorInfo implements ErrorInfo {
        private final String message;

        MockErrorInfo(String message) {
            this.message = message;
        }

        @Override
        public String type() {
            return "RuntimeException";
        }

        @Override
        public String message() {
            return message;
        }

        @Override
        public Optional<String> stackTrace() {
            return Optional.empty();
        }

        @Override
        public boolean isRetryable() {
            return false;
        }

        @Override
        public Optional<String> errorCode() {
            return Optional.empty();
        }
    }
}
