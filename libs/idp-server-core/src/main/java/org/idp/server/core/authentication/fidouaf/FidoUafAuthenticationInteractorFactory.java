package org.idp.server.core.authentication.fidouaf;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;

public class FidoUafAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.FIDO_UAF_AUTHENTICATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    FidoUafExecutors fidoUafExecutors = container.resolve(FidoUafExecutors.class);
    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new FidoUafAuthenticationInteractor(fidoUafExecutors, configurationQueryRepository);
  }
}
