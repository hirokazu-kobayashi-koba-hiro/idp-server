package org.idp.server.core.identity.role;

import java.io.Serializable;
import java.util.List;
import org.idp.server.core.identity.permission.Permission;

public class Role implements Serializable {
  String id;
  String name;
  String description;
  List<Permission> permissions;

  public Role() {}

  public Role(String id, String name, String description, List<Permission> permissions) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.permissions = permissions;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public List<Permission> permissions() {
    return permissions;
  }

  public boolean exists() {
    return name != null && !name.isEmpty();
  }

  public boolean match(Role role) {
    if (!exists())
      return false;

    return this.name.equals(role.name());
  }
}
