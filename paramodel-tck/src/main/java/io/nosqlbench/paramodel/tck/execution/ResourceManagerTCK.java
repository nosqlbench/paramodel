package io.nosqlbench.paramodel.tck.execution;

import io.nosqlbench.paramodel.execution.ResourceManager;
import io.nosqlbench.paramodel.execution.Runtime;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

///
/// Technology Compatibility Kit tests for ResourceManager contract.
///
/// Validates that implementations correctly:
/// - Allocate and release resources
/// - Check resource availability
/// - Track current usage
/// - Manage resource pools
/// - Track allocation history
///
/// @see ResourceManager
/// @since 0.1.0
///
public abstract class ResourceManagerTCK {
    protected ResourceManagerTCK() {}

    protected abstract ImplementationProvider getProvider();

    @Test
    public void testResourceManagerAvailable() {
        ResourceManager rm = getProvider().createResourceManager();

        Runtime.ResourceAvailability avail = rm.available();

        assertThat(avail).isNotNull();
        assertThat(avail.cpu()).isGreaterThanOrEqualTo(0);
        assertThat(avail.memoryGb()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testResourceManagerCanAllocate() {
        ResourceManager rm = getProvider().createResourceManager();

        boolean canAllocate = rm.canAllocate(
            getProvider().createResourceRequest(1.0, 2.0, 5.0, "test-owner"));

        assertThat(canAllocate).isTrue();
    }

    @Test
    public void testResourceManagerAllocate() throws Exception {
        ResourceManager rm = getProvider().createResourceManager();

        Runtime.ResourceAllocation alloc = rm.allocate(
            getProvider().createResourceRequest(1.0, 2.0, 5.0, "test-owner"));

        assertThat(alloc).isNotNull();
        assertThat(alloc.allocationId()).isNotNull();
        assertThat(alloc.resources()).isNotNull();
        assertThat(alloc.allocatedAt()).isNotNull();

        rm.release(alloc);
    }

    @Test
    public void testResourceManagerRelease() throws Exception {
        ResourceManager rm = getProvider().createResourceManager();

        Runtime.ResourceAllocation alloc = rm.allocate(
            getProvider().createResourceRequest(1.0, 2.0, 5.0, "test-owner"));
        double cpuBefore = rm.available().cpu();

        rm.release(alloc);

        assertThat(rm.available().cpu()).isGreaterThan(cpuBefore);
    }

    @Test
    public void testResourceManagerCurrentUsage() {
        ResourceManager rm = getProvider().createResourceManager();

        ResourceManager.ResourceUsage usage = rm.currentUsage();

        assertThat(usage).isNotNull();
        assertThat(usage.cpuTotal()).isGreaterThan(0);
        assertThat(usage.memoryGbTotal()).isGreaterThan(0);
        assertThat(usage.timestamp()).isNotNull();
    }

    @Test
    public void testResourceManagerPools() {
        ResourceManager rm = getProvider().createResourceManager();

        assertThat(rm.pools()).isNotNull();
    }

    @Test
    public void testResourceManagerAllocations() {
        ResourceManager rm = getProvider().createResourceManager();

        assertThat(rm.allocations()).isNotNull();
    }
}
