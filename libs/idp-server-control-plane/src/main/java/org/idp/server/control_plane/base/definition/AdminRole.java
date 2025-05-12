package org.idp.server.control_plane.base.definition;

import static org.idp.server.control_plane.base.definition.AdminPermission.*;

import java.util.Set;

public enum AdminRole {
  ADMINISTRATOR(
      Set.of(
          TENANT_CREATE,
          TENANT_READ,
          TENANT_UPDATE,
          TENANT_DELETE,
          CLIENT_CREATE,
          CLIENT_READ,
          CLIENT_UPDATE,
          CLIENT_DELETE,
          USER_CREATE,
          USER_READ,
          USER_UPDATE,
          USER_DELETE,
          PAYMENT_CREATE,
          PAYMENT_READ,
          PAYMENT_UPDATE,
          PAYMENT_DELETE)),
  EDITOR(
      Set.of(
          TENANT_READ, TENANT_UPDATE,
          CLIENT_READ, CLIENT_UPDATE,
          USER_READ, USER_UPDATE,
          PAYMENT_READ, PAYMENT_UPDATE)),
  VIEWER(Set.of(TENANT_READ, CLIENT_READ, USER_READ, PAYMENT_READ));

  private final Set<AdminPermission> permissions;

  AdminRole(Set<AdminPermission> permissions) {
    this.permissions = permissions;
  }

  public Set<AdminPermission> permissions() {
    return permissions;
  }

  public boolean hasPermission(AdminPermission permission) {
    return permissions.contains(permission);
  }
}
