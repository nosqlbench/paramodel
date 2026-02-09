package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.execution.ResourceManager;
import io.nosqlbench.paramodel.execution.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Default resource manager with admission control.
 *
 * This is a stub implementation.
 */
public class DefaultResourceManager implements ResourceManager {
    private static final Logger log = LoggerFactory.getLogger(DefaultResourceManager.class);

    private final Runtime.Resources capacity;
    private final List<Runtime.ResourceAllocation> allocations;

    public DefaultResourceManager(Runtime.Resources capacity) {
        this.capacity = capacity;
        this.allocations = new ArrayList<>();
    }

    @Override
    public Runtime.ResourceAllocation allocate(ResourceRequest request)
            throws ResourceExhaustedException {
        log.info("Allocating resources for request: {}", request.owner());

        // Stub implementation
        Runtime.ResourceAllocation allocation = new StubResourceAllocation(
            request,
            UUID.randomUUID().toString()
        );
        allocations.add(allocation);
        return allocation;
    }

    @Override
    public boolean canAllocate(ResourceRequest request) {
        // Stub - always return true
        return true;
    }

    @Override
    public void release(Runtime.ResourceAllocation allocation) {
        log.info("Releasing allocation: {}", allocation.allocationId());
        allocations.remove(allocation);
    }

    @Override
    public int queueRequest(ResourceRequest request) {
        // Stub - no queuing
        return 0;
    }

    @Override
    public Runtime.ResourceAvailability available() {
        return new StubResourceAvailability();
    }

    @Override
    public ResourceUsage currentUsage() {
        return new StubResourceUsage();
    }

    @Override
    public ResourcePool createPool(String name, Runtime.Resources capacity, PoolPriority priority) {
        log.info("Creating resource pool: {}", name);
        return new StubResourcePool(name);
    }

    @Override
    public List<ResourcePool> pools() {
        return List.of();
    }

    @Override
    public void setQuota(ResourceQuota quota) {
        log.info("Setting quota for user: {}", quota.user());
    }

    @Override
    public QuotaUsage quotaUsage(String user) {
        return new StubQuotaUsage(user);
    }

    @Override
    public List<Runtime.ResourceAllocation> allocations() {
        return Collections.unmodifiableList(allocations);
    }

    @Override
    public List<AllocationRecord> history() {
        return List.of();
    }

    public static DefaultResourceManager create(Runtime.Resources capacity) {
        return new DefaultResourceManager(capacity);
    }

    private static class StubResourceAllocation implements Runtime.ResourceAllocation {
        private final ResourceRequest request;
        private final String allocationId;
        private final Instant allocatedAt;

        StubResourceAllocation(ResourceRequest request, String allocationId) {
            this.request = request;
            this.allocationId = allocationId;
            this.allocatedAt = Instant.now();
        }

        @Override
        public String allocationId() {
            return allocationId;
        }

        @Override
        public Runtime.Resources allocated() {
            return new StubResources();
        }

        @Override
        public Instant allocatedAt() {
            return allocatedAt;
        }

        @Override
        public String owner() {
            return request.owner();
        }

        @Override
        public Optional<String> pool() {
            return Optional.empty();
        }
    }

    private static class StubResources implements Runtime.Resources {
        @Override
        public double cpu() {
            return 0.0;
        }

        @Override
        public double memoryGb() {
            return 0.0;
        }

        @Override
        public double storageGb() {
            return 0.0;
        }

        @Override
        public Optional<Double> networkGbps() {
            return Optional.empty();
        }

        @Override
        public Map<String, Double> custom() {
            return Map.of();
        }
    }

    private static class StubResourceAvailability implements Runtime.ResourceAvailability {
        @Override
        public Runtime.Resources total() {
            return new StubResources();
        }

        @Override
        public Runtime.Resources available() {
            return new StubResources();
        }

        @Override
        public Runtime.Resources allocated() {
            return new StubResources();
        }

        @Override
        public double utilizationPercentage() {
            return 0.0;
        }
    }

    private static class StubResourceUsage implements ResourceUsage {
        @Override
        public double cpuUsagePercentage() {
            return 0.0;
        }

        @Override
        public double memoryUsagePercentage() {
            return 0.0;
        }

        @Override
        public double storageUsagePercentage() {
            return 0.0;
        }

        @Override
        public Map<String, Double> customUsage() {
            return Map.of();
        }
    }

    private static class StubResourcePool implements ResourcePool {
        private final String name;

        StubResourcePool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Runtime.Resources capacity() {
            return new StubResources();
        }

        @Override
        public Runtime.Resources available() {
            return new StubResources();
        }

        @Override
        public PoolPriority priority() {
            return PoolPriority.NORMAL;
        }

        @Override
        public List<Runtime.ResourceAllocation> allocations() {
            return List.of();
        }
    }

    private static class StubQuotaUsage implements QuotaUsage {
        private final String user;

        StubQuotaUsage(String user) {
            this.user = user;
        }

        @Override
        public String user() {
            return user;
        }

        @Override
        public Runtime.Resources used() {
            return new StubResources();
        }

        @Override
        public Runtime.Resources limit() {
            return new StubResources();
        }

        @Override
        public Runtime.Resources remaining() {
            return new StubResources();
        }

        @Override
        public boolean isOverQuota() {
            return false;
        }
    }
}
