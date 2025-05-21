package org.idp.server.control_plane.management.tenant.invitation.operation;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventExecutorFactory;
import org.idp.server.platform.dependency.ApplicationComponentContainer;

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
