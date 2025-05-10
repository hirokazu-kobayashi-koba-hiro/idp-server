package org.idp.server.control_plane.io;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.identity.permission.Permission;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.identity.role.Role;
import org.idp.server.core.identity.role.Roles;

public class RoleRegistrationRequestConvertor implements JsonReadable {

  List<Map> roles;
  Permissions permissions;

  public RoleRegistrationRequestConvertor(List<Map> roles, Permissions permissions) {
    this.roles = roles;
    this.permissions = permissions;
  }

  public Roles toRoles() {
    List<Role> roleList = roles.stream().map(this::toRole).toList();
    return new Roles(roleList);
  }

  public Role toRole(Map value) {

    String id = UUID.randomUUID().toString();
    String name = (String) value.get("name");
    String description = (String) value.get("description");
    List<String> permissionNames = (List<String>) value.get("permissions");
    List<Permission> permissionList = permissions.filter(permissionNames).toList();

    return new Role(id, name, description, permissionList);
  }
}
