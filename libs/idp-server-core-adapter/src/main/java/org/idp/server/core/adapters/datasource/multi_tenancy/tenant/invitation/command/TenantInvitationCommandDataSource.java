package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitation;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitationCommandRepository;

public class TenantInvitationCommandDataSource implements TenantInvitationCommandRepository {

  TenantInvitationSqlExecutors executors;

  public TenantInvitationCommandDataSource() {
    this.executors = new TenantInvitationSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, TenantInvitation tenantInvitation) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, tenantInvitation);
  }

  @Override
  public void update(Tenant tenant, TenantInvitation tenantInvitation) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, tenantInvitation);
  }

  @Override
  public void delete(Tenant tenant, TenantInvitation tenantInvitation) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, tenantInvitation);
  }
}
