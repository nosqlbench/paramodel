package io.nosqlbench.paramodel.mock.sequence;

import io.nosqlbench.paramodel.core.Value;
import io.nosqlbench.paramodel.sequence.Trial;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Simple trial result implementation.
 */
public class MockTrialResult implements Trial.TrialResult {
    private final String trialId;
    private final Map<String, Object> observations;
    private final Instant startTime;
    private final Instant endTime;
    private final Status status;
    private final Optional<Throwable> error;

    public MockTrialResult(String trialId, Map<String, Object> observations,
                          Instant startTime, Instant endTime,
                          Status status, Optional<Throwable> error) {
        this.trialId = Objects.requireNonNull(trialId);
        this.observations = new HashMap<>(observations);
        this.startTime = Objects.requireNonNull(startTime);
        this.endTime = Objects.requireNonNull(endTime);
        this.status = Objects.requireNonNull(status);
        this.error = Objects.requireNonNull(error);
    }

    @Override
    public String trialId() {
        return trialId;
    }

    @Override
    public Map<String, Object> observations() {
        return Collections.unmodifiableMap(observations);
    }

    @Override
    public Duration executionTime() {
        return Duration.between(startTime, endTime);
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public Optional<Throwable> error() {
        return error;
    }

    @Override
    public Instant startTime() {
        return startTime;
    }

    @Override
    public Instant endTime() {
        return endTime;
    }

    public static Builder builder(String trialId) {
        return new Builder(trialId);
    }

    public static MockTrialResult success(String trialId, Map<String, Object> observations) {
        Instant now = Instant.now();
        return new MockTrialResult(trialId, observations, now, now, Status.SUCCESS, Optional.empty());
    }

    public static MockTrialResult failed(String trialId, Throwable error) {
        Instant now = Instant.now();
        return new MockTrialResult(trialId, Map.of(), now, now, Status.FAILED, Optional.of(error));
    }

    public static class Builder {
        private final String trialId;
        private final Map<String, Object> observations = new HashMap<>();
        private Instant startTime = Instant.now();
        private Instant endTime = Instant.now();
        private Status status = Status.SUCCESS;
        private Optional<Throwable> error = Optional.empty();

        public Builder(String trialId) {
            this.trialId = trialId;
        }

        public Builder observation(String key, Object value) {
            this.observations.put(key, value);
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

        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        public Builder error(Throwable error) {
            this.error = Optional.of(error);
            this.status = Status.FAILED;
            return this;
        }

        public MockTrialResult build() {
            return new MockTrialResult(trialId, observations, startTime, endTime, status, error);
        }
    }
}
