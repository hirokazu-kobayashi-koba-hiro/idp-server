/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.webauthn;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class WebAuthnRegistrationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  WebAuthnExecutors webAuthnExecutors;

  public WebAuthnRegistrationChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository,
      WebAuthnExecutors webAuthnExecutors) {
    this.configurationRepository = configurationRepository;
    this.webAuthnExecutors = webAuthnExecutors;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {

    WebAuthnConfiguration configuration =
        configurationRepository.get(tenant, "webauthn", WebAuthnConfiguration.class);

    WebAuthnExecutor webAuthnExecutor = webAuthnExecutors.get(configuration.type());
    WebAuthnChallenge webAuthnChallenge =
        webAuthnExecutor.challengeRegistration(
            tenant, authorizationIdentifier, request, configuration);

    Map<String, Object> response = new HashMap<>();
    response.put("challenge", webAuthnChallenge.challenge());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        transaction.user(),
        new Authentication(),
        response,
        DefaultSecurityEventType.webauthn_registration_challenge);
  }
}
