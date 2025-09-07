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

package org.idp.server.control_plane.management.identity.user.io;

import java.util.Map;
import java.util.Optional;

public class UserRegistrationRequest {

  Map<String, Object> values;

  public UserRegistrationRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public java.util.List<org.idp.server.core.openid.identity.UserRole> roles() {
    Object rolesValue = values.get("roles");
    return Optional.ofNullable(rolesValue)
        .filter(value -> value instanceof java.util.List)
        .map(value -> (java.util.List<Object>) value)
        .map(
            rolesList ->
                rolesList.stream()
                    .filter(role -> role instanceof java.util.Map)
                    .map(
                        role -> {
                          java.util.Map<String, Object> roleMap =
                              (java.util.Map<String, Object>) role;
                          return new org.idp.server.core.openid.identity.UserRole(
                              (String) roleMap.get("role_id"), (String) roleMap.get("role_name"));
                        })
                    .collect(java.util.stream.Collectors.toList()))
        .orElse(java.util.List.of());
  }

  public java.util.List<String> permissions() {
    Object permissionsValue = values.get("permissions");
    return Optional.ofNullable(permissionsValue)
        .filter(value -> value instanceof java.util.List)
        .map(value -> (java.util.List<String>) value)
        .orElse(java.util.List.of());
  }

  public String currentTenant() {
    Object value = values.get("current_tenant_id");
    return value != null ? (String) value : null;
  }

  public java.util.List<String> assignedTenants() {
    Object value = values.get("assigned_tenants");
    if (value instanceof java.util.List) {
      return (java.util.List<String>) value;
    }
    return java.util.List.of();
  }

  public String currentOrganizationId() {
    Object value = values.get("current_organization_id");
    return value != null ? (String) value : null;
  }

  public java.util.List<String> assignedOrganizations() {
    Object value = values.get("assigned_organizations");
    if (value instanceof java.util.List) {
      return (java.util.List<String>) value;
    }
    return java.util.List.of();
  }
}
