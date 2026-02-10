package io.nosqlbench.paramodel.engine.compiler;

import io.nosqlbench.paramodel.plan.Barrier;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

///
/// Structural {@link Barrier} implementation produced during compilation.
///
/// This is a data-holder barrier. The runtime methods ({@code await}, {@code release},
/// {@code fail}) throw {@link UnsupportedOperationException} — they must be replaced
/// by a runtime-capable barrier implementation before execution.
///
public class DefaultBarrier implements Barrier {

    private final String id;
    private final BarrierType type;
    private final String description;
    private final List<String> dependencies;
    private final List<String> dependentSteps;
    private final Duration timeout;
    private final TimeoutAction timeoutAction;
    private final Map<String, Object> metadata;
    private final Instant createdAt;

    public DefaultBarrier(
        String id,
        BarrierType type,
        String description,
        List<String> dependencies,
        List<String> dependentSteps,
        Duration timeout,
        TimeoutAction timeoutAction,
        Map<String, Object> metadata
    ) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.description = Objects.requireNonNull(description);
        this.dependencies = List.copyOf(dependencies);
        this.dependentSteps = List.copyOf(dependentSteps);
        this.timeout = timeout;
        this.timeoutAction = timeoutAction != null ? timeoutAction : TimeoutAction.FAIL_FAST;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.createdAt = Instant.now();
    }

    @Override public String id() { return id; }
    @Override public BarrierType type() { return type; }
    @Override public String description() { return description; }
    @Override public List<String> dependencies() { return dependencies; }
    @Override public List<String> dependentSteps() { return dependentSteps; }
    @Override public Optional<Duration> timeout() { return Optional.ofNullable(timeout); }
    @Override public TimeoutAction timeoutAction() { return timeoutAction; }
    @Override public BarrierState state() { return BarrierState.PENDING; }
    @Override public Set<String> satisfiedDependencies() { return Set.of(); }
    @Override public Set<String> pendingDependencies() { return Set.copyOf(dependencies); }
    @Override public Instant createdAt() { return createdAt; }
    @Override public Optional<Instant> satisfiedAt() { return Optional.empty(); }
    @Override public Optional<Duration> waitDuration() { return Optional.empty(); }
    @Override public Map<String, Object> metadata() { return metadata; }
    @Override public boolean isSatisfied() { return false; }
    @Override public boolean isFailed() { return false; }
    @Override public boolean isTimedOut() { return false; }

    @Override
    public void await() throws InterruptedException, BarrierException {
        throw new UnsupportedOperationException("DefaultBarrier is a structural placeholder; use a runtime barrier for execution");
    }

    @Override
    public boolean await(Duration timeout) throws InterruptedException, BarrierException {
        throw new UnsupportedOperationException("DefaultBarrier is a structural placeholder; use a runtime barrier for execution");
    }

    @Override
    public void release() {
        throw new UnsupportedOperationException("DefaultBarrier is a structural placeholder; use a runtime barrier for execution");
    }

    @Override
    public void fail(String reason) {
        throw new UnsupportedOperationException("DefaultBarrier is a structural placeholder; use a runtime barrier for execution");
    }
}
