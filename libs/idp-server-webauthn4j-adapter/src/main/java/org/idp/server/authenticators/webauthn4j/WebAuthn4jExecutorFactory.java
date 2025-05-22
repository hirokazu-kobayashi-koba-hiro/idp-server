/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
