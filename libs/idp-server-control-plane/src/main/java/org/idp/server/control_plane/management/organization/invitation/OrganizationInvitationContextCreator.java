package org.idp.server.control_plane.management.organization.invitation;

import java.time.LocalDateTime;
import java.util.UUID;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.control_plane.base.AdminDashboardUrl;
import org.idp.server.control_plane.management.organization.invitation.io.TenantInvitationManagementRequest;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitation;

public class OrganizationInvitationContextCreator {

  Tenant tenant;
  TenantInvitationManagementRequest request;
  AdminDashboardUrl adminDashboardUrl;
  boolean dryRun;

  public OrganizationInvitationContextCreator(
      Tenant tenant,
      TenantInvitationManagementRequest request,
      AdminDashboardUrl adminDashboardUrl,
      boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.adminDashboardUrl = adminDashboardUrl;
    this.dryRun = dryRun;
  }

  public OrganizationInvitationContext create() {
    String id = UUID.randomUUID().toString();
    String tenantId = tenant.identifierValue();
    String tenantName = tenant.name().value();
    String email = request.getValueAsString("email");
    String role = request.getValueAsString("role");
    // TODO improve determining path
    String url =
        adminDashboardUrl.value()
            + "/signin/index.html/?invitation_id="
            + id
            + "tenant_id="
            + tenantId;
    // 1 week
    int expiresIn = 604800;
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt = createdAt.plusSeconds(expiresIn);

    TenantInvitation tenantInvitation =
        new TenantInvitation(
            id, tenantId, tenantName, email, role, url, expiresIn, createdAt, expiredAt);

    return new OrganizationInvitationContext(tenantInvitation, dryRun);
  }
}
