package org.idp.server.adapters.springboot.domain.model.operation;

import org.springframework.security.core.GrantedAuthority;

public enum IdPScope implements GrantedAuthority {
  tenant_management,
  client_management,
  user_management,
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
