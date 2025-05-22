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


package org.idp.server.control_plane.management.tenant.invitation;

import java.time.LocalDateTime;
import java.util.UUID;
import org.idp.server.basic.http.QueryParams;
import org.idp.server.control_plane.base.AdminDashboardUrl;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementRequest;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantInvitationContextCreator {

  Tenant tenant;
  TenantInvitationManagementRequest request;
  AdminDashboardUrl adminDashboardUrl;
  boolean dryRun;

  public TenantInvitationContextCreator(
      Tenant tenant,
      TenantInvitationManagementRequest request,
      AdminDashboardUrl adminDashboardUrl,
      boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.adminDashboardUrl = adminDashboardUrl;
    this.dryRun = dryRun;
  }

  public TenantInvitationContext create() {
    String id = UUID.randomUUID().toString();
    String tenantId = tenant.identifierValue();
    String tenantName = tenant.name().value();
    String email = request.getValueAsString("email");
    String roleId = request.getValueAsString("role_id");
    String roleName = request.getValueAsString("role_name");

    // TODO improve determining path
    QueryParams queryParams = new QueryParams();
    queryParams.add("invitation_id", id);
    queryParams.add("invitation_tenant_id", tenantId);
    String url = adminDashboardUrl.value() + "/invitation/?" + queryParams.params();
    String status = "created";
    // 1 week
    int expiresIn = 604800;
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt = createdAt.plusSeconds(expiresIn);
    LocalDateTime updatedAt = SystemDateTime.now();

    TenantInvitation tenantInvitation =
        new TenantInvitation(
            id,
            tenantId,
            tenantName,
            email,
            roleId,
            roleName,
            url,
            status,
            expiresIn,
            createdAt,
            expiredAt,
            updatedAt);

    return new TenantInvitationContext(tenantInvitation, dryRun);
  }
}
