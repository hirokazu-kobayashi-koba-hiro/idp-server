package org.idp.server.authentication.interactors.webauthn;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.StandardAuthenticationInteraction;

public class WebAuthnAuthenticationChallengeInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.WEBAUTHN_AUTHENTICATION_CHALLENGE.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationConfigurationQueryRepository configurationRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    WebAuthnExecutors webAuthnExecutors = container.resolve(WebAuthnExecutors.class);

    return new WebAuthnAuthenticationChallengeInteractor(
        configurationRepository, webAuthnExecutors);
  }
}
