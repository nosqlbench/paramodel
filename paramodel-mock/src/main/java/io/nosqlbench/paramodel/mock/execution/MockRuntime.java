package io.nosqlbench.paramodel.mock.execution;

import io.nosqlbench.paramodel.elements.Element;
import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.mock.sequence.MockTrialResult;
import io.nosqlbench.paramodel.sequence.Trial;
import io.nosqlbench.paramodel.sequence.TrialResult;
import io.nosqlbench.paramodel.sequence.TrialStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

///
/// Simple runtime implementation for testing.
///
/// Provides in-memory element instance management, stub trial execution,
/// and basic resource tracking.
///
/// @see Runtime
/// @since 0.1.0
///
public class MockRuntime implements Runtime {
    private final RuntimeConfig config;
    private final Map<String, MockElementInstance> instances = new LinkedHashMap<>();
    private final List<MockResourceAllocation> allocations = new ArrayList<>();
    private double cpuCapacity = 16.0;
    private double memoryCapacity = 64.0;
    private double storageCapacity = 200.0;

    ///
    /// Creates a mock runtime with default configuration.
    ///
    public MockRuntime() {
        this.config = new MockRuntimeConfig();
    }

    @Override
    public ElementInstance deploy(DeploymentRequest request) throws DeploymentException {
        MockElementInstance instance = new MockElementInstance(
            request.instanceId(),
            request.element(),
            "localhost:" + (8000 + instances.size()),
            InstanceState.READY,
            request.configuration(),
            Instant.now(),
            Instant.now()
        );
        instances.put(request.instanceId(), instance);
        return instance;
    }

    @Override
    public void awaitReady(ElementInstance instance, Duration timeout)
            throws TimeoutException, InterruptedException {
        // Mock: instances are immediately ready
    }

    @Override
    public HealthStatus checkHealth(ElementInstance instance) {
        return new MockHealthStatus(true, null, Map.of());
    }

    @Override
    public void restart(ElementInstance instance) throws DeploymentException {
        // Mock: no-op restart
    }

    @Override
    public void teardown(ElementInstance instance, boolean collectArtifacts) {
        instances.remove(instance.instanceId());
    }

    @Override
    public TrialResult executeTrial(TrialExecutionRequest request)
            throws TrialExecutionException {
        return MockTrialResult.builder(request.trial())
            .status(TrialStatus.COMPLETED)
            .build();
    }

    @Override
    public ResourceAvailability availableResources() {
        double usedCpu = allocations.stream().mapToDouble(a -> a.resources().cpu()).sum();
        double usedMem = allocations.stream().mapToDouble(a -> a.resources().memoryGb()).sum();
        double usedStorage = allocations.stream().mapToDouble(a -> a.resources().storageGb()).sum();
        return new ResourceAvailability(
            cpuCapacity - usedCpu,
            memoryCapacity - usedMem,
            storageCapacity - usedStorage,
            10.0
        );
    }

    @Override
    public ResourceAllocation allocateResources(Resources resources)
            throws InsufficientResourcesException {
        ResourceAvailability avail = availableResources();
        if (resources.cpu() > avail.cpu() || resources.memoryGb() > avail.memoryGb()) {
            throw new InsufficientResourcesException("Insufficient resources");
        }
        MockResourceAllocation alloc = new MockResourceAllocation(
            UUID.randomUUID().toString(), resources, Instant.now());
        allocations.add(alloc);
        return alloc;
    }

    @Override
    public void releaseResources(ResourceAllocation allocation) {
        allocations.removeIf(a -> a.allocationId().equals(allocation.allocationId()));
    }

    @Override
    public MetricsCollector metricsCollector() {
        return new MockMetricsCollector();
    }

    @Override
    public RuntimeConfig config() {
        return config;
    }

    private record MockElementInstance(
        String instanceId,
        Element element,
        String endpoint,
        InstanceState state,
        Map<String, Object> configuration,
        Instant deployedAt,
        Instant readyInstant
    ) implements ElementInstance {
        @Override
        public Optional<Instant> readyAt() {
            return Optional.ofNullable(readyInstant);
        }
    }

    private record MockHealthStatus(
        boolean isHealthy,
        String reasonText,
        Map<String, Object> details
    ) implements HealthStatus {
        @Override
        public Optional<String> reason() {
            return Optional.ofNullable(reasonText);
        }
    }

    private record MockResourceAllocation(
        String allocationId,
        Resources resources,
        Instant allocatedAt
    ) implements ResourceAllocation {
        @Override
        public void release() {
            // no-op; release is handled by the runtime
        }
    }

    private static class MockMetricsCollector implements MetricsCollector {
        @Override
        public void start(ElementInstance instance) {}

        @Override
        public void stop(ElementInstance instance) {}

        @Override
        public MetricsSnapshot snapshot(ElementInstance instance) {
            return new MockMetricsSnapshot(Instant.now(), Map.of());
        }

        @Override
        public List<MetricsSnapshot> history(ElementInstance instance) {
            return List.of();
        }
    }

    private record MockMetricsSnapshot(
        Instant timestamp,
        Map<String, MetricValue> metrics
    ) implements MetricsSnapshot {
        @Override
        public MetricValue metric(String name) {
            return metrics.getOrDefault(name, new MockMetricValue(0.0));
        }
    }

    private record MockMetricValue(double value) implements MetricValue {
        @Override
        public double asDouble() { return value; }
        @Override
        public long asLong() { return (long) value; }
        @Override
        public String asString() { return String.valueOf(value); }
        @Override
        public boolean asBoolean() { return value != 0.0; }
    }

    private static class MockRuntimeConfig implements RuntimeConfig {
        @Override
        public Duration defaultHealthCheckTimeout() { return Duration.ofSeconds(30); }
        @Override
        public Duration defaultDeploymentTimeout() { return Duration.ofMinutes(5); }
        @Override
        public Duration defaultTrialTimeout() { return Duration.ofMinutes(30); }
        @Override
        public Map<String, Object> customConfig() { return Map.of(); }
    }
}
