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

package org.idp.server.control_plane.base.definition;

import java.util.Set;
import java.util.stream.Collectors;
import org.idp.server.core.openid.identity.permission.PermissionMatcher;

public class AdminPermissions {
  Set<DefaultAdminPermission> values;

  public AdminPermissions(Set<DefaultAdminPermission> values) {
    this.values = values;
  }

  public Set<String> valuesAsSetString() {
    return values.stream().map(DefaultAdminPermission::value).collect(Collectors.toSet());
  }

  public String valuesAsString() {
    return values.stream().map(DefaultAdminPermission::value).collect(Collectors.joining(","));
  }

  /**
   * Checks if user permissions include all required permissions.
   *
   * <p>Supports wildcard matching where user permissions like:
   *
   * <ul>
   *   <li>{@code *} - matches all permissions
   *   <li>{@code idp:*} - matches all control plane permissions
   *   <li>{@code idp:user:*} - matches all user management permissions
   * </ul>
   *
   * @param userPermissions set of permissions the user has
   * @return true if user has all required permissions (considering wildcards)
   */
  public boolean includesAll(Set<String> userPermissions) {
    return PermissionMatcher.matchesAll(userPermissions, valuesAsSetString());
  }
}
