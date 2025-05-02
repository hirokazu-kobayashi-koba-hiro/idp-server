package org.idp.server.core.authentication.webauthn;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;

public class WebAuthnRegistrationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.WEBAUTHN_REGISTRATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationConfigurationQueryRepository configurationRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    WebAuthnExecutors webAuthnExecutors = container.resolve(WebAuthnExecutors.class);

    return new WebAuthnRegistrationInteractor(configurationRepository, webAuthnExecutors);
  }
}
