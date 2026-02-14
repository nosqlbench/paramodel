package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.engine.CompactId;
import io.nosqlbench.paramodel.execution.ResourceManager;
import io.nosqlbench.paramodel.execution.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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
            throws Runtime.InsufficientResourcesException {
        log.info("Allocating resources for request: {}", request.owner());

        // Stub implementation
        Runtime.ResourceAllocation allocation = new StubResourceAllocation(
            request,
            CompactId.next()
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
        return new Runtime.ResourceAvailability(0.0, 0.0, 0.0, 0.0);
    }

    @Override
    public ResourceUsage currentUsage() {
        return new StubResourceUsage();
    }

    @Override
    public ResourcePool createPool(String name, Runtime.Resources capacity, PoolPriority priority) {
        log.info("Creating resource pool: {}", name);
        return new StubResourcePool(name, priority);
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
        public Runtime.Resources resources() {
            return Runtime.Resources.of(
                request.cpu(), request.memoryGb(), request.storageGb());
        }

        @Override
        public Instant allocatedAt() {
            return allocatedAt;
        }

        @Override
        public void release() {
            // Stub - no-op
        }
    }

    private static class StubResourceUsage implements ResourceUsage {
        @Override
        public double cpuTotal() {
            return 0.0;
        }

        @Override
        public double cpuUsed() {
            return 0.0;
        }

        @Override
        public double cpuAvailable() {
            return 0.0;
        }

        @Override
        public double cpuUtilization() {
            return 0.0;
        }

        @Override
        public double memoryGbTotal() {
            return 0.0;
        }

        @Override
        public double memoryGbUsed() {
            return 0.0;
        }

        @Override
        public double memoryGbAvailable() {
            return 0.0;
        }

        @Override
        public double memoryUtilization() {
            return 0.0;
        }

        @Override
        public double storageGbTotal() {
            return 0.0;
        }

        @Override
        public double storageGbUsed() {
            return 0.0;
        }

        @Override
        public double storageGbAvailable() {
            return 0.0;
        }

        @Override
        public Instant timestamp() {
            return Instant.now();
        }
    }

    private static class StubResourcePool implements ResourcePool {
        private final String name;
        private final PoolPriority priority;

        StubResourcePool(String name, PoolPriority priority) {
            this.name = name;
            this.priority = priority;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Runtime.Resources capacity() {
            return Runtime.Resources.of(0.0, 0.0, 0.0);
        }

        @Override
        public PoolPriority priority() {
            return priority;
        }

        @Override
        public List<Runtime.ResourceAllocation> allocations() {
            return List.of();
        }

        @Override
        public ResourceUsage usage() {
            return new StubResourceUsage();
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
        public double cpuUsed() {
            return 0.0;
        }

        @Override
        public double memoryGbUsed() {
            return 0.0;
        }

        @Override
        public double storageGbUsed() {
            return 0.0;
        }

        @Override
        public Duration durationUsedToday() {
            return Duration.ZERO;
        }

        @Override
        public Duration remainingDuration() {
            return Duration.ofHours(24);
        }

        @Override
        public boolean isExceeded() {
            return false;
        }
    }
}
