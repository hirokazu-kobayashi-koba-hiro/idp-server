/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.webauthn;

import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface WebAuthnExecutor {

  WebAuthnExecutorType type();

  WebAuthnChallenge challengeRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnVerificationResult verifyRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnChallenge challengeAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  WebAuthnVerificationResult verifyAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);
}
