package org.idp.server.adapters.springboot.application.restapi.model;

import org.springframework.security.core.GrantedAuthority;

public enum IdPScope implements GrantedAuthority {
  tenant_management,
  client_management,
  user_management,
  identity_verification_application,
  identity_verification_delete,
  identity_credentials_update,
  unknown;

  public static IdPScope of(String value) {
    for (IdPScope scope : IdPScope.values()) {
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
