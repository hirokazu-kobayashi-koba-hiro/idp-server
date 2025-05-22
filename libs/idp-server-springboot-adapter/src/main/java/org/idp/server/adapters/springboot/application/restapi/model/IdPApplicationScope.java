/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.restapi.model;

import org.springframework.security.core.GrantedAuthority;

public enum IdPApplicationScope implements GrantedAuthority {
  tenant_management,
  client_management,
  user_management,
  identity_verification_application,
  identity_verification_delete,
  identity_credentials_update,
  unknown;

  public static IdPApplicationScope of(String value) {
    for (IdPApplicationScope scope : IdPApplicationScope.values()) {
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
