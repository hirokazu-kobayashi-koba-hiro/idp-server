package org.idp.server.authenticators.webauthn4j;

import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutor;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutorFactory;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;

public class WebAuthn4jExecutorFactory implements WebAuthnExecutorFactory {

  @Override
  public WebAuthnExecutor create(AuthenticationDependencyContainer container) {

    AuthenticationInteractionCommandRepository transactionCommandRepository =
        container.resolve(AuthenticationInteractionCommandRepository.class);
    AuthenticationInteractionQueryRepository transactionQueryRepository =
        container.resolve(AuthenticationInteractionQueryRepository.class);
    WebAuthn4jCredentialRepository credentialRepository =
        container.resolve(WebAuthn4jCredentialRepository.class);

    return new WebAuthn4jExecutor(
        transactionCommandRepository, transactionQueryRepository, credentialRepository);
  }
}
