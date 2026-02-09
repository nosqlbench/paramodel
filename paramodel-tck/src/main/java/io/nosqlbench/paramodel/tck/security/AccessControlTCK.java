package io.nosqlbench.paramodel.tck.security;

import io.nosqlbench.paramodel.security.AccessControl;
import io.nosqlbench.paramodel.tck.ImplementationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

///
/// TCK tests for {@link AccessControl} implementations.
///
/// Validates grant, revoke, check, and list operations
/// for permission management.
///
/// @since 0.1.0
///
public abstract class AccessControlTCK {

    /// Returns the implementation provider under test.
    protected abstract ImplementationProvider getProvider();

    private AccessControl accessControl;

    @BeforeEach
    void setUp() {
        accessControl = getProvider().createAccessControl();
    }

    @Test
    void testGrantAndCheckPermission() {
        accessControl.grantPermission("user1", "plan-abc", AccessControl.Permission.READ);

        assertThat(accessControl.hasPermission("user1", "plan-abc",
            AccessControl.Permission.READ)).isTrue();
        assertThat(accessControl.hasPermission("user1", "plan-abc",
            AccessControl.Permission.WRITE)).isFalse();
    }

    @Test
    void testRevokePermission() {
        accessControl.grantPermission("user1", "plan-abc", AccessControl.Permission.WRITE);
        assertThat(accessControl.hasPermission("user1", "plan-abc",
            AccessControl.Permission.WRITE)).isTrue();

        accessControl.revokePermission("user1", "plan-abc", AccessControl.Permission.WRITE);
        assertThat(accessControl.hasPermission("user1", "plan-abc",
            AccessControl.Permission.WRITE)).isFalse();
    }

    @Test
    void testGetPermissions() {
        accessControl.grantPermission("user1", "plan-abc", AccessControl.Permission.READ);
        accessControl.grantPermission("user1", "plan-abc", AccessControl.Permission.EXECUTE);

        List<AccessControl.Permission> perms =
            accessControl.getPermissions("user1", "plan-abc");
        assertThat(perms).containsExactlyInAnyOrder(
            AccessControl.Permission.READ,
            AccessControl.Permission.EXECUTE);
    }

    @Test
    void testNoPermissionsByDefault() {
        assertThat(accessControl.hasPermission("unknown", "plan-xyz",
            AccessControl.Permission.READ)).isFalse();
        assertThat(accessControl.getPermissions("unknown", "plan-xyz")).isEmpty();
    }
}
