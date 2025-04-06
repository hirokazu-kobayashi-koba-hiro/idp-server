package org.idp.server.core.authentication.webauthn;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.AuthenticationConfigurationQueryRepository;

public class WebAuthnAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.WEBAUTHN_AUTHENTICATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationConfigurationQueryRepository configurationRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    WebAuthnExecutors webAuthnExecutors = container.resolve(WebAuthnExecutors.class);
    return new WebAuthnAuthenticationInteractor(configurationRepository, webAuthnExecutors);
  }
}
