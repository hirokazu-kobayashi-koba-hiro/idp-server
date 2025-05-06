package org.idp.server.core.authentication.fidouaf.external;

import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutor;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutorFactory;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;

public class ExternalFidoUafServerExecutorFactory implements FidoUafExecutorFactory {

  @Override
  public FidoUafExecutor create(AuthenticationDependencyContainer container) {
    AuthenticationConfigurationQueryRepository configurationQueryRepository = container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new ExternalFidoUafServerExecutor(configurationQueryRepository);
  }
}
