package org.idp.server.adapters.springboot.control_plane.model;

import org.springframework.security.core.GrantedAuthority;

public enum IdpControlPlaneScope implements GrantedAuthority {
  tenant_management,
  client_management,
  user_management,
  unknown;

  public static IdpControlPlaneScope of(String value) {
    for (IdpControlPlaneScope scope : IdpControlPlaneScope.values()) {
      if (scope.name().equalsIgnoreCase(value)) {
        return scope;
      }
    }
    return unknown;
  }

  @Override
  public String getAuthority() {
    return name();
  }
}
