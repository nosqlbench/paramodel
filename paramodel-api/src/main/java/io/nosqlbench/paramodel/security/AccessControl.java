package io.nosqlbench.paramodel.security;

import java.util.List;

///
/// # AccessControl
///
/// Controls access to execution plans and results based on roles and permissions.
///
public interface AccessControl {

    static AccessControl create() {
        throw new UnsupportedOperationException(
            "AccessControl.create() requires a concrete implementation");
    }

    void grantPermission(String userId, String resource, Permission permission);

    void revokePermission(String userId, String resource, Permission permission);

    boolean hasPermission(String userId, String resource, Permission permission);

    List<Permission> getPermissions(String userId, String resource);

    enum Permission {
        READ,
        WRITE,
        EXECUTE,
        DELETE,
        ADMIN
    }
}
