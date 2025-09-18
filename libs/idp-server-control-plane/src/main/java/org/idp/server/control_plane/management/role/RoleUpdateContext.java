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

import java.util.Map;
import org.idp.server.control_plane.base.ConfigUpdateContext;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RoleUpdateContext implements ConfigUpdateContext {

  Tenant tenant;
  Role before;
  RoleRequest request;
  Role after;
  Roles roles;
  Permissions permissions;
  boolean dryRun;

  public RoleUpdateContext(
      Tenant tenant,
      Role before,
      RoleRequest request,
      Role after,
      Roles roles,
      Permissions permissions,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.after = after;
    this.roles = roles;
    this.permissions = permissions;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public Role before() {
    return before;
  }

  public RoleRequest request() {
    return request;
  }

  public Role after() {
    return after;
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
  public Map<String, Object> beforePayload() {
    return before.toMap();
  }

  @Override
  public Map<String, Object> afterPayload() {
    return after.toMap();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public RoleManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    return new RoleManagementResponse(RoleManagementStatus.OK, contents);
  }
}
