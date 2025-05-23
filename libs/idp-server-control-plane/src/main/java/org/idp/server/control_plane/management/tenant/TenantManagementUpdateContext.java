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

package org.idp.server.control_plane.management.tenant;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonDiffCalculator;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantManagementUpdateContext {

  Tenant adminTenant;
  Tenant before;
  Tenant after;
  User user;
  boolean dryRun;

  public TenantManagementUpdateContext(
      Tenant adminTenant, Tenant before, Tenant after, User user, boolean dryRun) {
    this.adminTenant = adminTenant;
    this.before = before;
    this.after = after;
    this.user = user;
    this.dryRun = dryRun;
  }

  public Tenant adminTenant() {
    return adminTenant;
  }

  public Tenant before() {
    return before;
  }

  public Tenant after() {
    return after;
  }

  public User user() {
    return user;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public Map<String, Object> diff() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromObject(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromObject(after.toMap());
    return JsonDiffCalculator.deepDiff(beforeJson, afterJson);
  }

  public TenantManagementResponse toResponse() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", after.toMap());
    contents.put("diff", diff());
    contents.put("dry_run", dryRun);
    return new TenantManagementResponse(TenantManagementStatus.OK, contents);
  }
}
