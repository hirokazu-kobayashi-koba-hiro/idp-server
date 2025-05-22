/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
