package io.nosqlbench.paramodel.mock.plan;

import io.nosqlbench.paramodel.plan.Barrier;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Simple barrier implementation for synchronization points.
 */
public class MockBarrier implements Barrier {
    private final String id;
    private final BarrierType type;
    private final String description;
    private final List<String> dependencies;
    private final List<String> dependentSteps;
    private final Duration timeout;
    private final TimeoutAction timeoutAction;
    private final Map<String, Object> metadata;
    private final Instant createdAt;
    private volatile BarrierState state;
    private final Set<String> satisfiedDependencies;
    private volatile Instant satisfiedAt;
    private volatile String failureReason;

    public MockBarrier(String id, BarrierType type, String description,
                      List<String> dependencies, List<String> dependentSteps,
                      Duration timeout, TimeoutAction timeoutAction,
                      Map<String, Object> metadata) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.description = description != null ? description : "Barrier: " + id;
        this.dependencies = List.copyOf(dependencies);
        this.dependentSteps = List.copyOf(dependentSteps);
        this.timeout = timeout;
        this.timeoutAction = timeoutAction != null ? timeoutAction : TimeoutAction.FAIL_FAST;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.createdAt = Instant.now();
        this.state = BarrierState.PENDING;
        this.satisfiedDependencies = new HashSet<>();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public BarrierType type() {
        return type;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<String> dependencies() {
        return dependencies;
    }

    @Override
    public List<String> dependentSteps() {
        return dependentSteps;
    }

    @Override
    public Optional<Duration> timeout() {
        return Optional.ofNullable(timeout);
    }

    @Override
    public TimeoutAction timeoutAction() {
        return timeoutAction;
    }

    @Override
    public BarrierState state() {
        return state;
    }

    @Override
    public Set<String> satisfiedDependencies() {
        return Collections.unmodifiableSet(satisfiedDependencies);
    }

    @Override
    public Set<String> pendingDependencies() {
        Set<String> pending = new HashSet<>(dependencies);
        pending.removeAll(satisfiedDependencies);
        return Collections.unmodifiableSet(pending);
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Optional<Instant> satisfiedAt() {
        return Optional.ofNullable(satisfiedAt);
    }

    @Override
    public Optional<Duration> waitDuration() {
        if (satisfiedAt != null) {
            return Optional.of(Duration.between(createdAt, satisfiedAt));
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    public boolean isSatisfied() {
        return state == BarrierState.SATISFIED;
    }

    @Override
    public boolean isFailed() {
        return state == BarrierState.FAILED;
    }

    @Override
    public boolean isTimedOut() {
        return state == BarrierState.TIMEOUT;
    }

    @Override
    public void await() throws InterruptedException, BarrierException {
        if (state == BarrierState.FAILED) {
            throw new BarrierException(id, "Barrier failed: " + failureReason, BarrierState.FAILED);
        }
        release();
    }

    @Override
    public boolean await(Duration timeout) throws InterruptedException, BarrierException {
        await();
        return true;
    }

    @Override
    public void release() {
        satisfiedDependencies.addAll(dependencies);
        state = BarrierState.SATISFIED;
        satisfiedAt = Instant.now();
    }

    @Override
    public void fail(String reason) {
        this.failureReason = reason;
        this.state = BarrierState.FAILED;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private BarrierType type = BarrierType.CUSTOM;
        private String description;
        private final List<String> dependencies = new ArrayList<>();
        private final List<String> dependentSteps = new ArrayList<>();
        private Duration timeout;
        private TimeoutAction timeoutAction;
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder(String id) {
            this.id = id;
        }

        public Builder type(BarrierType type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder dependency(String stepId) {
            this.dependencies.add(stepId);
            return this;
        }

        public Builder dependentStep(String stepId) {
            this.dependentSteps.add(stepId);
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder timeoutAction(TimeoutAction action) {
            this.timeoutAction = action;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public MockBarrier build() {
            return new MockBarrier(id, type, description, dependencies,
                dependentSteps, timeout, timeoutAction, metadata);
        }
    }
}
