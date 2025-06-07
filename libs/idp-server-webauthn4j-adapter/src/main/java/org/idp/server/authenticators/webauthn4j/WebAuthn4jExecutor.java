/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.authenticators.webauthn4j;

import java.util.Map;
import org.idp.server.authentication.interactors.webauthn.*;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class WebAuthn4jExecutor implements WebAuthnExecutor {

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthn4jExecutor(
      AuthenticationInteractionCommandRepository transactionCommandRepository,
      AuthenticationInteractionQueryRepository transactionQueryRepository,
      WebAuthn4jCredentialRepository credentialRepository) {
    this.transactionCommandRepository = transactionCommandRepository;
    this.transactionQueryRepository = transactionQueryRepository;
    this.credentialRepository = credentialRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public WebAuthnExecutorType type() {
    return new WebAuthnExecutorType("webauthn4j");
  }

  @Override
  public WebAuthnChallenge challengeRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    WebAuthnChallenge webAuthnChallenge = webAuthn4jChallenge.toWebAuthnChallenge();
    transactionCommandRepository.register(
        tenant, authenticationTransactionIdentifier, type().value(), webAuthnChallenge);

    return webAuthnChallenge;
  }

  @Override
  public WebAuthnVerificationResult verifyRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    WebAuthnChallenge webAuthnChallenge =
        transactionQueryRepository.get(
            tenant, authenticationTransactionIdentifier, type().value(), WebAuthnChallenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge =
        new WebAuthn4jChallenge(webAuthnChallenge.challenge());
    String requestString = jsonConverter.write(request.toMap());
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.getDetail(type()), WebAuthn4jConfiguration.class);
    WebAuthn4jRegistrationManager manager =
        new WebAuthn4jRegistrationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString, userId);

    WebAuthn4jCredential webAuthn4jCredential = manager.verifyAndCreateCredential();
    credentialRepository.register(webAuthn4jCredential);

    return new WebAuthnVerificationResult(Map.of("registration", webAuthn4jCredential.toMap()));
  }

  @Override
  public WebAuthnChallenge challengeAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    WebAuthnChallenge webAuthnChallenge = webAuthn4jChallenge.toWebAuthnChallenge();
    transactionCommandRepository.register(
        tenant, authenticationTransactionIdentifier, type().value(), webAuthnChallenge);

    return webAuthnChallenge;
  }

  @Override
  public WebAuthnVerificationResult verifyAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    WebAuthnChallenge webAuthnChallenge =
        transactionQueryRepository.get(
            tenant, authenticationTransactionIdentifier, type().value(), WebAuthnChallenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge =
        new WebAuthn4jChallenge(webAuthnChallenge.challenge());
    String requestString = jsonConverter.write(request.toMap());
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.getDetail(type()), WebAuthn4jConfiguration.class);
    WebAuthn4jAuthenticationManager manager =
        new WebAuthn4jAuthenticationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString);

    String extractUserId = manager.extractUserId();
    WebAuthn4jCredentials webAuthn4jCredentials = credentialRepository.findAll(extractUserId);

    manager.verify(webAuthn4jCredentials);

    return new WebAuthnVerificationResult(Map.of("user_id", extractUserId));
  }
}
