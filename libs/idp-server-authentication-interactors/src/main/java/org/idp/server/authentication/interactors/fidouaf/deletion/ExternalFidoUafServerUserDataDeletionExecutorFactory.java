package org.idp.server.authentication.interactors.fidouaf.deletion;

import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutors;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventExecutorFactory;
import org.idp.server.platform.dependency.ApplicationComponentContainer;

public class ExternalFidoUafServerUserDataDeletionExecutorFactory
    implements UserLifecycleEventExecutorFactory {

  @Override
  public UserLifecycleEventExecutor create(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer) {

    FidoUafExecutors fidoUafExecutors =
        authenticationDependencyContainer.resolve(FidoUafExecutors.class);
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        authenticationDependencyContainer.resolve(AuthenticationConfigurationQueryRepository.class);
    return new ExternalFidoUafServerUserDataDeletionExecutor(
        fidoUafExecutors, configurationQueryRepository);
  }
}
