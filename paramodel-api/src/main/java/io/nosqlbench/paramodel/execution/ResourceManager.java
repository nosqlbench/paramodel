package io.nosqlbench.paramodel.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

///
/// # ResourceManager
///
/// Manages allocation, tracking, and enforcement of computational resources (CPU, memory,
/// storage, network) during execution. The resource manager ensures fair distribution,
/// prevents overallocation, and provides resource usage visibility.
///
/// ## Resource Management Model
///
/// The resource manager operates on a reservation-based model:
///
/// ```
/// Resource Management Flow:
///
/// Request Resources
///   ├─ Check availability
///   ├─ Apply admission control
///   ├─ Create reservation
///   └─ Return allocation handle
///   ↓
/// Use Resources
///   ├─ Track actual usage
///   ├─ Monitor for overuse
///   ├─ Enforce limits
///   └─ Collect metrics
///   ↓
/// Release Resources
///   ├─ Free reservation
///   ├─ Reclaim unused capacity
///   ├─ Notify waiters
///   └─ Update accounting
/// ```
///
/// ## Resource Types
///
/// The manager tracks multiple resource dimensions:
///
/// ```
/// Resource Dimensions:
///
/// CPU (cores)
///   ├─ Sharable: Yes
///   ├─ Unit: cores (fractional)
///   ├─ Limit enforcement: CPU quota/shares
///   └─ Example: 2.5 cores
///
/// Memory (GB)
///   ├─ Sharable: No (exclusive)
///   ├─ Unit: gigabytes
///   ├─ Limit enforcement: Memory limits (cgroups)
///   └─ Example: 8.0 GB
///
/// Storage (GB)
///   ├─ Sharable: Yes (but limited)
///   ├─ Unit: gigabytes
///   ├─ Limit enforcement: Disk quotas
///   └─ Example: 50.0 GB
///
/// Network (Gbps)
///   ├─ Sharable: Yes
///   ├─ Unit: gigabits per second
///   ├─ Limit enforcement: Traffic shaping
///   └─ Example: 1.0 Gbps
/// ```
///
/// ## Admission Control
///
/// The manager applies admission control before allocating resources:
///
/// ```
/// Admission Control Logic:
///
/// Request: 4 cores, 8 GB memory
/// Current state:
///   Total capacity: 16 cores, 64 GB
///   Allocated: 10 cores, 48 GB
///   Available: 6 cores, 16 GB
///
/// Check 1: Sufficient capacity?
///   Requested: 4 cores ≤ 6 available ✓
///   Requested: 8 GB ≤ 16 GB available ✓
///   → Proceed
///
/// Check 2: Within limits?
///   Total after allocation: 14 cores, 56 GB
///   Limit: 16 cores, 64 GB
///   → Within limits ✓
///
/// Check 3: Fair share?
///   User has 6 cores allocated
///   Fair share: 16 cores / 4 users = 4 cores
///   After allocation: 10 cores > fair share
///   → Apply throttling or queue
///
/// Decision: ADMIT (allocate immediately)
/// ```
///
/// ## Resource Accounting
///
/// The manager maintains detailed resource accounting:
///
/// ```
/// Resource Accounting:
///
/// Allocations (by owner):
///   trial_42:
///     CPU: 2.0 cores (reserved), 1.8 cores (actual)
///     Memory: 4.0 GB (reserved), 3.6 GB (actual)
///     Storage: 10.0 GB (reserved), 7.2 GB (actual)
///     Duration: 5m 30s
///
///   trial_43:
///     CPU: 4.0 cores (reserved), 4.2 cores (actual) ⚠️ Overuse
///     Memory: 8.0 GB (reserved), 8.0 GB (actual)
///     Storage: 20.0 GB (reserved), 18.5 GB (actual)
///     Duration: 3m 15s
///
/// Aggregate:
///   Total reserved: 6 cores, 12 GB, 30 GB
///   Total actual: 6 cores, 11.6 GB, 25.7 GB
///   Utilization: 100% CPU, 97% memory, 86% storage
///   Efficiency: 100% CPU, 97% memory (good)
/// ```
///
/// ## Oversubscription and Burstability
///
/// The manager supports controlled oversubscription:
///
/// ```
/// Oversubscription Model:
///
/// Physical capacity: 16 cores, 64 GB memory
/// Oversubscription factor: 1.25
/// Allocatable: 20 cores, 80 GB memory
///
/// Rationale:
///   - Not all workloads use 100% of reserved resources
///   - Statistical multiplexing improves utilization
///   - Allows bursty workloads
///
/// Safeguards:
///   - Monitor actual usage
///   - Throttle when approaching physical limits
///   - Prioritize critical workloads
///   - Evict low-priority tasks if needed
///
/// Example Scenario:
///   Allocated: 18 cores (90% of allocatable)
///   Actual usage: 14 cores (87% of physical)
///   → Safe, within physical capacity
///
///   Allocated: 20 cores (100% of allocatable)
///   Actual usage: 17 cores (106% of physical) ⚠️
///   → Approaching limit, apply throttling
/// ```
///
/// ## Resource Pooling
///
/// Resources can be organized into pools for isolation:
///
/// ```
/// Resource Pools:
///
/// Pool: production
///   Capacity: 8 cores, 32 GB
///   Guaranteed: Yes
///   Priority: High
///   Allocations: [trial_1, trial_2, trial_3]
///
/// Pool: development
///   Capacity: 4 cores, 16 GB
///   Guaranteed: No
///   Priority: Normal
///   Allocations: [trial_4, trial_5]
///
/// Pool: best-effort
///   Capacity: 4 cores, 16 GB
///   Guaranteed: No
///   Priority: Low
///   Allocations: [trial_6]
///
/// Isolation:
///   - Production pool never starved by development
///   - Development gets resources after production
///   - Best-effort uses leftover capacity
/// ```
///
/// ## Resource Quotas
///
/// Quotas limit resource usage per user/project:
///
/// ```
/// Quota System:
///
/// User: alice
///   Quota:
///     CPU: 8 cores
///     Memory: 32 GB
///     Storage: 100 GB
///     Duration: 10 hours/day
///
///   Current usage:
///     CPU: 6 cores (75% of quota)
///     Memory: 24 GB (75% of quota)
///     Storage: 80 GB (80% of quota)
///     Duration today: 8h 30m (85% of quota)
///
///   Remaining:
///     CPU: 2 cores
///     Memory: 8 GB
///     Storage: 20 GB
///     Duration: 1h 30m
///
/// Enforcement:
///   - New requests checked against remaining quota
///   - Exceeding quota → Request denied or queued
///   - Quota resets daily (for duration)
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: Basic Resource Allocation
///
/// ```java
/// ResourceManager rm = ResourceManager.create();
///
/// // Request resources
/// ResourceRequest request = ResourceRequest.builder()
///     .cpu(2.0)
///     .memoryGb(4.0)
///     .storageGb(10.0)
///     .owner("trial_42")
///     .build();
///
/// ResourceAllocation allocation = rm.allocate(request);
///
/// System.out.printf("Allocated: %s (id: %s)%n",
///     allocation.resources(),
///     allocation.allocationId());
///
/// try {
///     // Use resources
///     executeTrial(allocation);
/// } finally {
///     // Always release
///     rm.release(allocation);
/// }
/// ```
///
/// ### Example 2: Checking Resource Availability
///
/// ```java
/// ResourceManager rm = ResourceManager.create();
///
/// // Check before allocating
/// ResourceRequest request = ResourceRequest.builder()
///     .cpu(4.0)
///     .memoryGb(8.0)
///     .build();
///
/// if (rm.canAllocate(request)) {
///     ResourceAllocation allocation = rm.allocate(request);
///     // Use resources
/// } else {
///     ResourceAvailability available = rm.available();
///     System.err.printf("Insufficient resources. Available: %.1f cores, %.1f GB%n",
///         available.cpu(), available.memoryGb());
///
///     // Queue or wait
///     rm.queueRequest(request);
/// }
/// ```
///
/// ### Example 3: Resource Monitoring
///
/// ```java
/// ResourceManager rm = ResourceManager.create();
///
/// // Monitor resource usage
/// ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
/// monitor.scheduleAtFixedRate(() -> {
///     ResourceUsage usage = rm.currentUsage();
///
///     System.out.printf("Resource Usage:%n");
///     System.out.printf("  CPU: %.1f / %.1f cores (%.0f%%)%n",
///         usage.cpuUsed(),
///         usage.cpuTotal(),
///         usage.cpuUtilization() * 100);
///
///     System.out.printf("  Memory: %.1f / %.1f GB (%.0f%%)%n",
///         usage.memoryGbUsed(),
///         usage.memoryGbTotal(),
///         usage.memoryUtilization() * 100);
///
///     if (usage.cpuUtilization() > 0.9) {
///         System.err.println("⚠️  High CPU usage!");
///     }
/// }, 0, 5, TimeUnit.SECONDS);
/// ```
///
/// ### Example 4: Resource Pools
///
/// ```java
/// ResourceManager rm = ResourceManager.create();
///
/// // Create resource pools
/// ResourcePool prodPool = rm.createPool(
///     "production",
///     Resources.of(8.0, 32.0, 100.0),
///     PoolPriority.HIGH);
///
/// ResourcePool devPool = rm.createPool(
///     "development",
///     Resources.of(4.0, 16.0, 50.0),
///     PoolPriority.NORMAL);
///
/// // Allocate from specific pool
/// ResourceRequest request = ResourceRequest.builder()
///     .cpu(2.0)
///     .memoryGb(8.0)
///     .pool("production")
///     .owner("trial_critical")
///     .build();
///
/// ResourceAllocation allocation = rm.allocate(request);
/// ```
///
/// ### Example 5: Quota Management
///
/// ```java
/// ResourceManager rm = ResourceManager.create();
///
/// // Set quota for user
/// ResourceQuota quota = ResourceQuota.builder()
///     .user("alice")
///     .cpu(8.0)
///     .memoryGb(32.0)
///     .storageGb(100.0)
///     .maxDurationPerDay(Duration.ofHours(10))
///     .build();
///
/// rm.setQuota(quota);
///
/// // Check quota usage
/// QuotaUsage usage = rm.quotaUsage("alice");
/// System.out.printf("Quota usage for alice:%n");
/// System.out.printf("  CPU: %.1f / %.1f cores%n",
///     usage.cpuUsed(), quota.cpu());
/// System.out.printf("  Remaining today: %s%n",
///     usage.remainingDuration());
///
/// if (usage.isExceeded()) {
///     System.err.println("Quota exceeded!");
/// }
/// ```
///
/// ## Contract Requirements
///
/// ### Allocation Safety
/// - Manager MUST NOT overallocate non-sharable resources (memory)
/// - Manager MUST track all allocations accurately
/// - Manager MUST release resources on allocation release
///
/// ### Fairness
/// - Manager SHOULD prevent resource monopolization
/// - Manager SHOULD support priority-based allocation
/// - Manager SHOULD enforce quotas
///
/// ### Observability
/// - Manager MUST provide current resource usage
/// - Manager MUST track allocation history
/// - Manager SHOULD emit alerts on resource exhaustion
///
/// @see Runtime
/// @see Scheduler
/// @see Executor
///
public interface ResourceManager {

    ///
    /// Creates a resource manager with system defaults.
    ///
    /// @return Resource manager instance
    ///
    static ResourceManager create() {
        throw new UnsupportedOperationException(
            "ResourceManager.create() requires a concrete implementation");
    }

    ///
    /// Creates a resource manager with specified capacity.
    ///
    /// @param capacity Total resource capacity
    /// @return Resource manager instance
    ///
    static ResourceManager create(Runtime.Resources capacity) {
        throw new UnsupportedOperationException(
            "ResourceManager.create(capacity) requires a concrete implementation");
    }

    ///
    /// Allocates resources.
    ///
    /// @param request Resource request
    /// @return Resource allocation
    /// @throws Runtime.InsufficientResourcesException if resources unavailable
    ///
    Runtime.ResourceAllocation allocate(ResourceRequest request)
        throws Runtime.InsufficientResourcesException;

    ///
    /// Checks if resources can be allocated.
    ///
    /// @param request Resource request
    /// @return True if resources available
    ///
    boolean canAllocate(ResourceRequest request);

    ///
    /// Releases allocated resources.
    ///
    /// @param allocation Resource allocation to release
    ///
    void release(Runtime.ResourceAllocation allocation);

    ///
    /// Queues a resource request for later allocation.
    ///
    /// @param request Resource request
    /// @return Queue position
    ///
    int queueRequest(ResourceRequest request);

    ///
    /// Returns current resource availability.
    ///
    /// @return Available resources
    ///
    Runtime.ResourceAvailability available();

    ///
    /// Returns current resource usage.
    ///
    /// @return Resource usage snapshot
    ///
    ResourceUsage currentUsage();

    ///
    /// Creates a resource pool.
    ///
    /// @param name Pool name
    /// @param capacity Pool capacity
    /// @param priority Pool priority
    /// @return Created resource pool
    ///
    ResourcePool createPool(String name, Runtime.Resources capacity, PoolPriority priority);

    ///
    /// Returns all resource pools.
    ///
    /// @return Resource pools
    ///
    List<ResourcePool> pools();

    ///
    /// Sets a resource quota.
    ///
    /// @param quota Resource quota
    ///
    void setQuota(ResourceQuota quota);

    ///
    /// Returns quota usage for a user.
    ///
    /// @param user User identifier
    /// @return Quota usage
    ///
    QuotaUsage quotaUsage(String user);

    ///
    /// Returns all active allocations.
    ///
    /// @return Active allocations
    ///
    List<Runtime.ResourceAllocation> allocations();

    ///
    /// Returns resource allocation history.
    ///
    /// @return Allocation history
    ///
    List<AllocationRecord> history();

    ///
    /// Resource request.
    ///
    interface ResourceRequest {
        double cpu();
        double memoryGb();
        double storageGb();
        Optional<String> pool();
        String owner();
        Optional<Duration> duration();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "ResourceRequest.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder cpu(double cpu);
            Builder memoryGb(double memoryGb);
            Builder storageGb(double storageGb);
            Builder pool(String pool);
            Builder owner(String owner);
            Builder duration(Duration duration);
            ResourceRequest build();
        }
    }

    ///
    /// Resource usage snapshot.
    ///
    interface ResourceUsage {
        double cpuTotal();
        double cpuUsed();
        double cpuAvailable();
        double cpuUtilization();
        double memoryGbTotal();
        double memoryGbUsed();
        double memoryGbAvailable();
        double memoryUtilization();
        double storageGbTotal();
        double storageGbUsed();
        double storageGbAvailable();
        Instant timestamp();
    }

    ///
    /// Resource pool.
    ///
    interface ResourcePool {
        String name();
        Runtime.Resources capacity();
        PoolPriority priority();
        List<Runtime.ResourceAllocation> allocations();
        ResourceUsage usage();
    }

    ///
    /// Pool priority.
    ///
    enum PoolPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    ///
    /// Resource quota.
    ///
    interface ResourceQuota {
        String user();
        double cpu();
        double memoryGb();
        double storageGb();
        Optional<Duration> maxDurationPerDay();

        static Builder builder() {
            throw new UnsupportedOperationException(
                "ResourceQuota.builder() requires a concrete implementation");
        }

        interface Builder {
            Builder user(String user);
            Builder cpu(double cpu);
            Builder memoryGb(double memoryGb);
            Builder storageGb(double storageGb);
            Builder maxDurationPerDay(Duration duration);
            ResourceQuota build();
        }
    }

    ///
    /// Quota usage.
    ///
    interface QuotaUsage {
        String user();
        double cpuUsed();
        double memoryGbUsed();
        double storageGbUsed();
        Duration durationUsedToday();
        Duration remainingDuration();
        boolean isExceeded();
    }

    ///
    /// Allocation record (historical).
    ///
    record AllocationRecord(
        String allocationId,
        String owner,
        Runtime.Resources resources,
        Instant allocatedAt,
        Optional<Instant> releasedAt,
        Duration duration
    ) {}
}
