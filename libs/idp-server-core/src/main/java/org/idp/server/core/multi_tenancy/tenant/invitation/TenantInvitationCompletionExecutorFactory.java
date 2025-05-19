package org.idp.server.core.multi_tenancy.tenant.invitation;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.identity.event.UserLifecycleEventExecutorFactory;

public class TenantInvitationCompletionExecutorFactory
    implements UserLifecycleEventExecutorFactory {

  @Override
  public UserLifecycleEventExecutor create(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer) {

    TenantInvitationCommandRepository tenantInvitationCommandRepository =
        applicationComponentContainer.resolve(TenantInvitationCommandRepository.class);
    TenantInvitationQueryRepository tenantInvitationQueryRepository =
        applicationComponentContainer.resolve(TenantInvitationQueryRepository.class);
    return new TenantInvitationCompletionExecutor(
        tenantInvitationCommandRepository, tenantInvitationQueryRepository);
  }
}
