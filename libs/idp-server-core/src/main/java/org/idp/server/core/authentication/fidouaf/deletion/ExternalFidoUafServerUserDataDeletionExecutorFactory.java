package org.idp.server.core.authentication.fidouaf.deletion;

import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutors;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.identity.event.UserLifecycleEventExecutorFactory;
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
