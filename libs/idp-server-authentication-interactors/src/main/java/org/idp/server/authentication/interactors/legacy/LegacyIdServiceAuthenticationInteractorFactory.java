package org.idp.server.authentication.interactors.legacy;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;

public class LegacyIdServiceAuthenticationInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return new AuthenticationInteractionType("legacy-authentication");
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationConfigurationQueryRepository configurationRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    return new LegacyIdServiceAuthenticationInteractor(configurationRepository);
  }
}
