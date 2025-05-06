package org.idp.server.core.authentication.legacy;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;

public class LegacyIdServiceAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return new AuthenticationInteractionType("legacy-authentication");
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationConfigurationQueryRepository configurationRepository = container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new LegacyIdServiceAuthenticationInteractor(configurationRepository);
  }
}
