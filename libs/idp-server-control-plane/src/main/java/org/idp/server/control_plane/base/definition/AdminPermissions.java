package org.idp.server.control_plane.base.definition;

import java.util.Set;
import java.util.stream.Collectors;

public class AdminPermissions {
  Set<AdminPermission> values;

  public AdminPermissions(Set<AdminPermission> values) {
    this.values = values;
  }

  public Set<String> valuesAsSetString() {
    return values.stream().map(AdminPermission::value).collect(Collectors.toSet());
  }

  public String valuesAsString() {
    return values.stream().map(AdminPermission::value).collect(Collectors.joining(","));
  }

  public boolean includesAll(Set<String> userPermissions) {
    return valuesAsSetString().containsAll(userPermissions);
  }
}
