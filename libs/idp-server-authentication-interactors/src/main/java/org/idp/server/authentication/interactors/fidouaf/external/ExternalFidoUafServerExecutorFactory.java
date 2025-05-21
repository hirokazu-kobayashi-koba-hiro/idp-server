package org.idp.server.authentication.interactors.fidouaf.external;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutor;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutorFactory;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;

public class ExternalFidoUafServerExecutorFactory implements FidoUafExecutorFactory {

  @Override
  public FidoUafExecutor create(AuthenticationDependencyContainer container) {
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new ExternalFidoUafServerExecutor(configurationQueryRepository);
  }
}
