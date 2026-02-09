package io.nosqlbench.paramodel.mock.security;

import io.nosqlbench.paramodel.security.AccessControl;

import java.util.*;

///
/// In-memory access control for testing.
///
/// Stores permissions in a map keyed by user ID and resource.
/// Permission checks are simple set lookups.
///
/// @see AccessControl
/// @since 0.1.0
///
public class MockAccessControl implements AccessControl {
    private final Map<String, Set<Permission>> permissions = new HashMap<>();

    @Override
    public void grantPermission(String userId, String resource, Permission permission) {
        String key = userId + ":" + resource;
        permissions.computeIfAbsent(key, k -> new HashSet<>()).add(permission);
    }

    @Override
    public void revokePermission(String userId, String resource, Permission permission) {
        String key = userId + ":" + resource;
        Set<Permission> perms = permissions.get(key);
        if (perms != null) {
            perms.remove(permission);
        }
    }

    @Override
    public boolean hasPermission(String userId, String resource, Permission permission) {
        String key = userId + ":" + resource;
        Set<Permission> perms = permissions.get(key);
        return perms != null && perms.contains(permission);
    }

    @Override
    public List<Permission> getPermissions(String userId, String resource) {
        String key = userId + ":" + resource;
        Set<Permission> perms = permissions.get(key);
        return perms != null ? List.copyOf(perms) : List.of();
    }
}
