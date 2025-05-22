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
