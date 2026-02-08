package io.nosqlbench.paramodel.observability;

import java.time.Duration;
import java.util.List;
import java.util.Map;

///
/// # Profiler
///
/// Captures performance profiles for execution analysis and optimization.
/// The profiler tracks CPU, memory, and I/O patterns to identify bottlenecks.
///
/// ## Profile Types
///
/// ```
/// CPU Profile
///   ├─ Stack traces sampled at intervals
///   ├─ Hot spots (expensive methods)
///   ├─ Call graphs
///   └─ Flame graphs
///
/// Memory Profile
///   ├─ Heap allocations
///   ├─ Garbage collection events
///   ├─ Memory leaks
///   └─ Object retention
///
/// I/O Profile
///   ├─ Disk read/write operations
///   ├─ Network requests/responses
///   ├─ Latency distributions
///   └─ Throughput metrics
///
/// Concurrency Profile
///   ├─ Thread activity
///   ├─ Lock contention
///   ├─ Context switches
///   └─ Parallelism efficiency
/// ```
///
/// ## Usage Examples
///
/// ### Example 1: CPU Profiling
///
/// ```java
/// Profiler profiler = Profiler.create();
///
/// profiler.startCpuProfiling();
/// executeTrial(trial);
/// CpuProfile profile = profiler.stopCpuProfiling();
///
/// System.out.printf("Top hotspots:%n");
/// for (Hotspot hotspot : profile.hotspots()) {
///     System.out.printf("  %s: %.1f%%%n",
///         hotspot.method(), hotspot.percentage() * 100);
/// }
/// ```
///
/// ### Example 2: Memory Profiling
///
/// ```java
/// profiler.startMemoryProfiling();
/// executeTrial(trial);
/// MemoryProfile profile = profiler.stopMemoryProfiling();
///
/// System.out.printf("Allocations: %d objects, %.2f MB%n",
///     profile.allocationCount(),
///     profile.allocationSizeMb());
/// ```
///
public interface Profiler {

    ///
    /// Creates a profiler with default configuration.
    ///
    /// @return Profiler instance
    ///
    static Profiler create() {
        throw new UnsupportedOperationException(
            "Profiler.create() requires a concrete implementation");
    }

    ///
    /// Starts CPU profiling.
    ///
    void startCpuProfiling();

    ///
    /// Stops CPU profiling and returns profile.
    ///
    /// @return CPU profile
    ///
    CpuProfile stopCpuProfiling();

    ///
    /// Starts memory profiling.
    ///
    void startMemoryProfiling();

    ///
    /// Stops memory profiling and returns profile.
    ///
    /// @return Memory profile
    ///
    MemoryProfile stopMemoryProfiling();

    ///
    /// CPU profile.
    ///
    interface CpuProfile {
        Duration duration();
        List<Hotspot> hotspots();
        Map<String, Long> sampleCounts();
    }

    ///
    /// CPU hotspot.
    ///
    interface Hotspot {
        String method();
        long samples();
        double percentage();
    }

    ///
    /// Memory profile.
    ///
    interface MemoryProfile {
        long allocationCount();
        double allocationSizeMb();
        List<Allocation> topAllocations();
    }

    ///
    /// Memory allocation.
    ///
    interface Allocation {
        String type();
        long count();
        double sizeMb();
    }
}
