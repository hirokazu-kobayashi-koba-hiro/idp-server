package org.idp.server.core.handler.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.oauth.identity.permission.Permission;
import org.idp.server.core.oauth.identity.permission.Permissions;

public class PermissionRegistrationRequestConvertor {

  List<Map> permissions;

  public PermissionRegistrationRequestConvertor(List<Map> permissions) {
    this.permissions = permissions;
  }

  public Permissions toPermissions() {
    List<Permission> permissionList = permissions.stream().map(this::toPermission).toList();
    return new Permissions(permissionList);
  }

  private Permission toPermission(Map value) {
    String id = UUID.randomUUID().toString();
    String name = (String) value.get("name");
    String description = (String) value.get("description");

    return new Permission(id, name, description);
  }
}
