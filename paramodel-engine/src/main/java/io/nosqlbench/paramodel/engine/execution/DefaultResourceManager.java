package io.nosqlbench.paramodel.engine.execution;

import io.nosqlbench.paramodel.execution.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Default resource manager with admission control.
 *
 * Manages execution resources:
 * - CPU slots
 * - Memory limits
 * - I/O bandwidth
 * - Custom resources
 */
public class DefaultResourceManager implements ResourceManager {
    private static final Logger log = LoggerFactory.getLogger(DefaultResourceManager.class);

    private final Map<String, ResourcePool> pools;

    public DefaultResourceManager() {
        this.pools = new ConcurrentHashMap<>();

        // Register default resource pools
        int cpuCount = java.lang.Runtime.getRuntime().availableProcessors();
        registerResource("cpu", cpuCount);
        registerResource("memory", 1024 * 1024 * 1024); // 1GB default
    }

    @Override
    public boolean acquire(Map<String, Integer> resources) {
        // Try to acquire all resources atomically
        for (Map.Entry<String, Integer> entry : resources.entrySet()) {
            ResourcePool pool = pools.get(entry.getKey());
            if (pool == null) {
                log.warn("Unknown resource: {}", entry.getKey());
                return false;
            }

            if (!pool.tryAcquire(entry.getValue())) {
                // Failed to acquire - release what we got
                releasePartial(resources, entry.getKey());
                return false;
            }
        }

        log.debug("Acquired resources: {}", resources);
        return true;
    }

    @Override
    public void release(Map<String, Integer> resources) {
        for (Map.Entry<String, Integer> entry : resources.entrySet()) {
            ResourcePool pool = pools.get(entry.getKey());
            if (pool != null) {
                pool.release(entry.getValue());
            }
        }

        log.debug("Released resources: {}", resources);
    }

    @Override
    public Map<String, Integer> available() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, ResourcePool> entry : pools.entrySet()) {
            result.put(entry.getKey(), entry.getValue().available());
        }
        return result;
    }

    @Override
    public void registerResource(String name, int capacity) {
        Objects.requireNonNull(name);
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1");
        }

        pools.put(name, new ResourcePool(name, capacity));
        log.info("Registered resource: {} with capacity: {}", name, capacity);
    }

    private void releasePartial(Map<String, Integer> resources, String stopAt) {
        for (Map.Entry<String, Integer> entry : resources.entrySet()) {
            if (entry.getKey().equals(stopAt)) {
                break;
            }
            ResourcePool pool = pools.get(entry.getKey());
            if (pool != null) {
                pool.release(entry.getValue());
            }
        }
    }

    /**
     * Resource pool with semaphore-based capacity management.
     */
    private static class ResourcePool {
        private final String name;
        private final int capacity;
        private final Semaphore semaphore;

        ResourcePool(String name, int capacity) {
            this.name = name;
            this.capacity = capacity;
            this.semaphore = new Semaphore(capacity);
        }

        boolean tryAcquire(int permits) {
            return semaphore.tryAcquire(permits);
        }

        void release(int permits) {
            semaphore.release(permits);
        }

        int available() {
            return semaphore.availablePermits();
        }
    }
}
