package org.idp.server.control_plane.management.organization.invitation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.organization.invitation.io.TenantInvitationManagementResponse;
import org.idp.server.control_plane.management.organization.invitation.io.TenantInvitationManagementStatus;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitation;

public class OrganizationInvitationContext {

  TenantInvitation tenantInvitation;
  boolean dryRun;

  public OrganizationInvitationContext() {}

  public OrganizationInvitationContext(TenantInvitation tenantInvitation, boolean dryRun) {
    this.tenantInvitation = tenantInvitation;
    this.dryRun = dryRun;
  }

  public TenantInvitation tenantInvitation() {
    return tenantInvitation;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public TenantInvitationManagementResponse toResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("result", tenantInvitation.toMap());
    response.put("dry_run", dryRun);
    return new TenantInvitationManagementResponse(TenantInvitationManagementStatus.OK, response);
  }
}
