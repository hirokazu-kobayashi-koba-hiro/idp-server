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

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.ConfigUpdateContext;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonDiffCalculator;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantManagementUpdateContext implements AuditableContext {

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

  public Tenant beforeTenant() {
    return before;
  }

  public Tenant afterTenant() {
    return after;
  }

  @Override
  public String outcomeResult() {
    return "";
  }

  @Override
  public String outcomeReason() {
    return "";
  }

  @Override
  public String targetTenantId() {
    return "";
  }

  @Override
  public Map<String, Object> attributes() {
    return Map.of();
  }

  @Override
  public boolean dryRun() {
    return false;
  }

  public User user() {
    return user;
  }

  @Override
  public String type() {
    return "tenant";
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
    return before.toMap();
  }

  @Override
  public Map<String, Object> after() {
    return after.toMap();
  }

  public Map<String, Object> diff() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromMap(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromMap(after.toMap());
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
