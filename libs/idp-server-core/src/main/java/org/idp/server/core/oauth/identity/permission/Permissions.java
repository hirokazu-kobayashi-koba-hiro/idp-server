package org.idp.server.core.oauth.identity.permission;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Permissions implements Iterable<Permission> {

  List<Permission> values;

  public Permissions() {
    this.values = new ArrayList<>();
  }

  public Permissions(List<Permission> values) {
    this.values = values;
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public boolean contains(Permission permission) {
    return values.stream().anyMatch(value -> value.match((permission)));
  }

  @Override
  public Iterator<Permission> iterator() {
    return values.iterator();
  }

  public Permissions filter(List<String> permissionNames) {
    return new Permissions(
        values.stream().filter(permission -> permissionNames.contains(permission.name())).toList());
  }

  public List<Permission> toList() {
    return values;
  }
}
