package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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
