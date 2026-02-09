package io.nosqlbench.paramodel.mock.execution;

import io.nosqlbench.paramodel.execution.ResourceManager;
import io.nosqlbench.paramodel.execution.Runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

///
/// Simple resource manager implementation for testing.
///
/// Provides in-memory resource allocation tracking with
/// configurable capacity.
///
/// @see ResourceManager
/// @since 0.1.0
///
public class MockResourceManager implements ResourceManager {
    private final double cpuCapacity;
    private final double memoryCapacity;
    private final double storageCapacity;
    private final List<MockAllocation> activeAllocations = new ArrayList<>();
    private final List<AllocationRecord> allocationHistory = new ArrayList<>();
    private final List<ResourcePool> resourcePools = new ArrayList<>();
    private final Map<String, ResourceQuota> quotas = new HashMap<>();
    private final List<ResourceRequest> waitQueue = new ArrayList<>();

    ///
    /// Creates a mock resource manager with default capacity.
    ///
    public MockResourceManager() {
        this(16.0, 64.0, 200.0);
    }

    ///
    /// Creates a mock resource manager with specified capacity.
    ///
    /// @param cpu      CPU capacity in cores
    /// @param memory   memory capacity in GB
    /// @param storage  storage capacity in GB
    ///
    public MockResourceManager(double cpu, double memory, double storage) {
        this.cpuCapacity = cpu;
        this.memoryCapacity = memory;
        this.storageCapacity = storage;
    }

    @Override
    public Runtime.ResourceAllocation allocate(ResourceRequest request)
            throws Runtime.InsufficientResourcesException {
        if (!canAllocate(request)) {
            throw new Runtime.InsufficientResourcesException("Insufficient resources");
        }
        Runtime.Resources resources = Runtime.Resources.of(
            request.cpu(), request.memoryGb(), request.storageGb());
        MockAllocation alloc = new MockAllocation(
            UUID.randomUUID().toString(), resources, Instant.now());
        activeAllocations.add(alloc);
        allocationHistory.add(new AllocationRecord(
            alloc.allocationId(), request.owner(), resources,
            alloc.allocatedAt(), Optional.empty(), Duration.ZERO));
        return alloc;
    }

    @Override
    public boolean canAllocate(ResourceRequest request) {
        Runtime.ResourceAvailability avail = available();
        return request.cpu() <= avail.cpu()
            && request.memoryGb() <= avail.memoryGb()
            && request.storageGb() <= avail.storageGb();
    }

    @Override
    public void release(Runtime.ResourceAllocation allocation) {
        activeAllocations.removeIf(a -> a.allocationId().equals(allocation.allocationId()));
    }

    @Override
    public int queueRequest(ResourceRequest request) {
        waitQueue.add(request);
        return waitQueue.size();
    }

    @Override
    public Runtime.ResourceAvailability available() {
        double usedCpu = activeAllocations.stream()
            .mapToDouble(a -> a.resources().cpu()).sum();
        double usedMem = activeAllocations.stream()
            .mapToDouble(a -> a.resources().memoryGb()).sum();
        double usedStorage = activeAllocations.stream()
            .mapToDouble(a -> a.resources().storageGb()).sum();
        return new Runtime.ResourceAvailability(
            cpuCapacity - usedCpu,
            memoryCapacity - usedMem,
            storageCapacity - usedStorage,
            10.0
        );
    }

    @Override
    public ResourceUsage currentUsage() {
        double usedCpu = activeAllocations.stream()
            .mapToDouble(a -> a.resources().cpu()).sum();
        double usedMem = activeAllocations.stream()
            .mapToDouble(a -> a.resources().memoryGb()).sum();
        double usedStorage = activeAllocations.stream()
            .mapToDouble(a -> a.resources().storageGb()).sum();
        return new MockResourceUsage(
            cpuCapacity, usedCpu, cpuCapacity - usedCpu,
            cpuCapacity > 0 ? usedCpu / cpuCapacity : 0,
            memoryCapacity, usedMem, memoryCapacity - usedMem,
            memoryCapacity > 0 ? usedMem / memoryCapacity : 0,
            storageCapacity, usedStorage, storageCapacity - usedStorage,
            Instant.now()
        );
    }

    @Override
    public ResourcePool createPool(String name, Runtime.Resources capacity, PoolPriority priority) {
        MockResourcePool pool = new MockResourcePool(name, capacity, priority);
        resourcePools.add(pool);
        return pool;
    }

    @Override
    public List<ResourcePool> pools() {
        return Collections.unmodifiableList(resourcePools);
    }

    @Override
    public void setQuota(ResourceQuota quota) {
        quotas.put(quota.user(), quota);
    }

    @Override
    public QuotaUsage quotaUsage(String user) {
        return new MockQuotaUsage(user, 0, 0, 0,
            Duration.ZERO, Duration.ofHours(10), false);
    }

    @Override
    public List<Runtime.ResourceAllocation> allocations() {
        return Collections.unmodifiableList(new ArrayList<>(activeAllocations));
    }

    @Override
    public List<AllocationRecord> history() {
        return Collections.unmodifiableList(allocationHistory);
    }

    private record MockAllocation(
        String allocationId,
        Runtime.Resources resources,
        Instant allocatedAt
    ) implements Runtime.ResourceAllocation {
        @Override
        public void release() {}
    }

    private record MockResourceUsage(
        double cpuTotal, double cpuUsed, double cpuAvailable, double cpuUtilization,
        double memoryGbTotal, double memoryGbUsed, double memoryGbAvailable, double memoryUtilization,
        double storageGbTotal, double storageGbUsed, double storageGbAvailable,
        Instant timestamp
    ) implements ResourceUsage {}

    private record MockResourcePool(
        String name,
        Runtime.Resources capacity,
        PoolPriority priority
    ) implements ResourcePool {
        @Override
        public List<Runtime.ResourceAllocation> allocations() { return List.of(); }
        @Override
        public ResourceUsage usage() {
            return new MockResourceUsage(
                capacity.cpu(), 0, capacity.cpu(), 0,
                capacity.memoryGb(), 0, capacity.memoryGb(), 0,
                capacity.storageGb(), 0, capacity.storageGb(),
                Instant.now());
        }
    }

    private record MockQuotaUsage(
        String user,
        double cpuUsed,
        double memoryGbUsed,
        double storageGbUsed,
        Duration durationUsedToday,
        Duration remainingDuration,
        boolean isExceeded
    ) implements QuotaUsage {}
}
