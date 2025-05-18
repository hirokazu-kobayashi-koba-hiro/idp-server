package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitationQueryRepository;

public class TenantInvitationQueryDataSourceProvider
    implements ApplicationComponentProvider<TenantInvitationQueryRepository> {

  @Override
  public Class<TenantInvitationQueryRepository> type() {
    return TenantInvitationQueryRepository.class;
  }

  @Override
  public TenantInvitationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new TenantInvitationQueryDataSource();
  }
}
