package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.core.multi_tenancy.tenant.invitation.TenantInvitationCommandRepository;

public class TenantInvitationDataSourceProvider
    implements ApplicationComponentProvider<TenantInvitationCommandRepository> {

  @Override
  public Class<TenantInvitationCommandRepository> type() {
    return TenantInvitationCommandRepository.class;
  }

  @Override
  public TenantInvitationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new TenantInvitationCommandDataSource();
  }
}
