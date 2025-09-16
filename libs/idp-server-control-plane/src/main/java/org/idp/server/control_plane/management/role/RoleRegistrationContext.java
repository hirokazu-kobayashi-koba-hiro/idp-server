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

package org.idp.server.control_plane.management.role;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.ConfigRegistrationContext;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RoleRegistrationContext implements ConfigRegistrationContext {

  Tenant tenant;
  RoleRequest request;
  Role role;
  Roles roles;
  Permissions permissions;
  boolean dryRun;

  public RoleRegistrationContext(
      Tenant tenant,
      RoleRequest request,
      Role role,
      Roles roles,
      Permissions permissions,
      boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.role = role;
    this.roles = roles;
    this.permissions = permissions;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public RoleRequest request() {
    return request;
  }

  public Role role() {
    return role;
  }

  public Roles roles() {
    return roles;
  }

  public Permissions permissions() {
    return permissions;
  }

  @Override
  public String type() {
    return "role";
  }

  @Override
  public Map<String, Object> payload() {
    return role.toMap();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public RoleManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", role.toMap());
    response.put("dry_run", dryRun);
    return new RoleManagementResponse(RoleManagementStatus.CREATED, response);
  }
}
