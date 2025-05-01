package org.idp.server.core.authentication.fidouaf.external;

import org.idp.server.core.authentication.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.authentication.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutor;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutorFactory;

public class ExternalFidoUafServerExecutorFactory implements FidoUafExecutorFactory {

  @Override
  public FidoUafExecutor create(AuthenticationDependencyContainer container) {
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new ExternalFidoUafServerExecutor(configurationQueryRepository);
  }
}
