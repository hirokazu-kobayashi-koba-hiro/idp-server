package org.idp.server.authenticators.webauthn4j;

import org.idp.server.core.authentication.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.AuthenticationTransactionCommandRepository;
import org.idp.server.core.authentication.AuthenticationTransactionQueryRepository;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutor;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutorFactory;

public class WebAuthn4jExecutorFactory implements WebAuthnExecutorFactory {

  @Override
  public WebAuthnExecutor create(AuthenticationDependencyContainer container) {

    AuthenticationTransactionCommandRepository transactionCommandRepository =
        container.resolve(AuthenticationTransactionCommandRepository.class);
    AuthenticationTransactionQueryRepository transactionQueryRepository =
        container.resolve(AuthenticationTransactionQueryRepository.class);
    WebAuthn4jCredentialRepository credentialRepository =
        container.resolve(WebAuthn4jCredentialRepository.class);

    return new WebAuthn4jExecutor(
        transactionCommandRepository, transactionQueryRepository, credentialRepository);
  }
}
