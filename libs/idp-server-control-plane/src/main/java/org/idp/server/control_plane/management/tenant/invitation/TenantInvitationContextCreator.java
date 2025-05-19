package org.idp.server.control_plane.management.tenant.invitation;

import java.time.LocalDateTime;
import java.util.UUID;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.http.QueryParams;
import org.idp.server.control_plane.base.AdminDashboardUrl;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementRequest;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitation;

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
