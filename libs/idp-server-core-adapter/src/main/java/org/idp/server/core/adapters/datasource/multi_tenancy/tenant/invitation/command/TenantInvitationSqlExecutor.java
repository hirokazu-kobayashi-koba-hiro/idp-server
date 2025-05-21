package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface TenantInvitationSqlExecutor {
  void insert(Tenant tenant, TenantInvitation tenantInvitation);

  void delete(Tenant tenant, TenantInvitation tenantInvitation);

  void update(Tenant tenant, TenantInvitation tenantInvitation);
}
