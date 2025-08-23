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

package org.idp.server.control_plane.management.permission;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.ConfigRegistrationContext;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionManagementStatus;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PermissionRegistrationContext implements ConfigRegistrationContext {

  Tenant tenant;
  Permission permission;
  boolean dryRun;

  public PermissionRegistrationContext(Tenant tenant, Permission permission, boolean dryRun) {
    this.tenant = tenant;
    this.permission = permission;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public Permission permission() {
    return permission;
  }

  @Override
  public String type() {
    return "permission";
  }

  @Override
  public Map<String, Object> payload() {
    return permission.toMap();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public PermissionManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", permission.toMap());
    response.put("dry_run", dryRun);
    return new PermissionManagementResponse(PermissionManagementStatus.CREATED, response);
  }
}
