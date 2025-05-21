package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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
