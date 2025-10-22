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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RoleRegistrationContext implements AuditableContext {

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

  public RoleRequest requestRole() {
    return request;
  }

  @Override
  public String type() {
    return "";
  }

  @Override
  public String description() {
    return "";
  }

  @Override
  public String tenantId() {
    return "";
  }

  @Override
  public String clientId() {
    return "";
  }

  @Override
  public String userId() {
    return "";
  }

  @Override
  public String externalUserId() {
    return "";
  }

  @Override
  public Map<String, Object> userPayload() {
    return Map.of();
  }

  @Override
  public String targetResource() {
    return "";
  }

  @Override
  public String targetResourceAction() {
    return "";
  }

  @Override
  public String ipAddress() {
    return "";
  }

  @Override
  public String userAgent() {
    return "";
  }

  @Override
  public Map<String, Object> request() {
    return Map.of();
  }

  @Override
  public Map<String, Object> before() {
    return Map.of();
  }

  @Override
  public Map<String, Object> after() {
    return Map.of();
  }

  @Override
  public String outcomeResult() {
    return "";
  }

  @Override
  public String outcomeReason() {
    return "";
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
  public String targetTenantId() {
    return tenant.identifierValue();
  }

  @Override
  public boolean dryRun() {
    return dryRun;
  }

  @Override
  public Map<String, Object> attributes() {
    return Collections.emptyMap();
  }

  public RoleManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", role.toMap());
    response.put("dry_run", dryRun);
    return new RoleManagementResponse(RoleManagementStatus.CREATED, response);
  }
}
