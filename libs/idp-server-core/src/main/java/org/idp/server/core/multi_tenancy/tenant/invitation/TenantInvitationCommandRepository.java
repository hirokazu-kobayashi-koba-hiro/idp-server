package org.idp.server.core.multi_tenancy.tenant.invitation;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface TenantInvitationCommandRepository {

  void register(Tenant tenant, TenantInvitation tenantInvitation);

  void update(Tenant tenant, TenantInvitation tenantInvitation);

  void delete(Tenant tenant, TenantInvitation tenantInvitation);
}
