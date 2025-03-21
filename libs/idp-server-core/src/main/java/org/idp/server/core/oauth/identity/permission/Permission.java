package org.idp.server.core.oauth.identity.permission;

import java.io.Serializable;

public class Permission implements Serializable {
  String id;
  String name;
  String description;

  public Permission() {}

  public Permission(String id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
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

  public boolean exists() {
    return name != null && !name.isEmpty();
  }

  public boolean match(Permission permission) {
    if (!exists()) return false;

    return this.name.equals(permission.name());
  }
}
