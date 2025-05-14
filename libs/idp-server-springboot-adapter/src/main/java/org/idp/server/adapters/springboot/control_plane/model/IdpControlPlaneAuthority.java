package org.idp.server.adapters.springboot.control_plane.model;

import org.idp.server.control_plane.base.definition.IdpControlPlaneScope;
import org.springframework.security.core.GrantedAuthority;

public class IdpControlPlaneAuthority implements GrantedAuthority {

  String name;

  public IdpControlPlaneAuthority() {}

  public IdpControlPlaneAuthority(String name) {
    this.name = name;
  }

  public static IdpControlPlaneAuthority of(String value) {
    for (IdpControlPlaneScope scope : IdpControlPlaneScope.values()) {
      if (scope.name().equalsIgnoreCase(value)) {
        return new IdpControlPlaneAuthority(scope.name());
      }
    }
    return new IdpControlPlaneAuthority("");
  }

  @Override
  public String getAuthority() {
    return name;
  }

  public boolean exists() {
    return name != null && !name.isEmpty();
  }
}
