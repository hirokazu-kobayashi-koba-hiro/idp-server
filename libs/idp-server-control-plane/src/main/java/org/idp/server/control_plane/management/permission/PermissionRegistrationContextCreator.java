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

import java.util.UUID;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PermissionRegistrationContextCreator {

  Tenant tenant;
  PermissionRequest request;
  boolean dryRun;

  public PermissionRegistrationContextCreator(
      Tenant tenant, PermissionRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
  }

  public PermissionRegistrationContext create() {
    JsonNodeWrapper requestJson = JsonNodeWrapper.fromMap(request.toMap());
    String id =
        requestJson.contains("id")
            ? requestJson.getValueOrEmptyAsString("id")
            : UUID.randomUUID().toString();
    String name = requestJson.getValueOrEmptyAsString("name");
    String description = requestJson.getValueOrEmptyAsString("description");

    Permission permission = new Permission(id, name, description);

    return new PermissionRegistrationContext(tenant, permission, dryRun);
  }
}
