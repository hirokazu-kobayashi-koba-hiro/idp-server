package org.idp.server.core.authentication.fidouaf.deletion;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutors;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.deletion.UserRelatedDataDeletionExecutor;
import org.idp.server.core.identity.deletion.UserRelatedDataDeletionExecutorFactory;

public class ExternalFidoUafServerUserDataDeletionExecutorFactory
    implements UserRelatedDataDeletionExecutorFactory {

  @Override
  public UserRelatedDataDeletionExecutor create(
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
