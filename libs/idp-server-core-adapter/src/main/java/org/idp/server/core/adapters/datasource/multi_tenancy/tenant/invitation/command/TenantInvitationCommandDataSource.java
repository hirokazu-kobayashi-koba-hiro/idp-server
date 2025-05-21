package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
