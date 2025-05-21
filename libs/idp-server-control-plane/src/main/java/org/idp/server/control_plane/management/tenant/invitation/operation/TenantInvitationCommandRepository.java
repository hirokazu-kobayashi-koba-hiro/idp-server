package org.idp.server.control_plane.management.tenant.invitation.operation;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface TenantInvitationCommandRepository {

  void register(Tenant tenant, TenantInvitation tenantInvitation);

  void update(Tenant tenant, TenantInvitation tenantInvitation);

  void delete(Tenant tenant, TenantInvitation tenantInvitation);
}
