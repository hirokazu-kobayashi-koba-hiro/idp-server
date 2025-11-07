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
import org.idp.server.authentication.interactors.fido2.*;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class WebAuthn4jExecutor implements Fido2Executor {

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
  public Fido2ExecutorType type() {
    return new Fido2ExecutorType("webauthn4j");
  }

  @Override
  public Fido2Challenge challengeRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      Fido2Configuration configuration) {

    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    Fido2Challenge fido2Challenge = webAuthn4jChallenge.toWebAuthnChallenge();
    transactionCommandRepository.register(
        tenant, authenticationTransactionIdentifier, type().value(), fido2Challenge);

    return fido2Challenge;
  }

  @Override
  public Fido2VerificationResult verifyRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      Fido2Configuration configuration) {

    Fido2Challenge fido2Challenge =
        transactionQueryRepository.get(
            tenant, authenticationTransactionIdentifier, type().value(), Fido2Challenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge = new WebAuthn4jChallenge(fido2Challenge.challenge());
    String requestString = jsonConverter.write(request.toMap());
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.getDetail(type()), WebAuthn4jConfiguration.class);
    WebAuthn4jRegistrationManager manager =
        new WebAuthn4jRegistrationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString, userId);

    WebAuthn4jCredential webAuthn4jCredential = manager.verifyAndCreateCredential();
    credentialRepository.register(webAuthn4jCredential);

    return new Fido2VerificationResult(Map.of("registration", webAuthn4jCredential.toMap()));
  }

  @Override
  public Fido2Challenge challengeAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      Fido2Configuration configuration) {

    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    Fido2Challenge fido2Challenge = webAuthn4jChallenge.toWebAuthnChallenge();
    transactionCommandRepository.register(
        tenant, authenticationTransactionIdentifier, type().value(), fido2Challenge);

    return fido2Challenge;
  }

  @Override
  public Fido2VerificationResult verifyAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      Fido2Configuration configuration) {

    Fido2Challenge fido2Challenge =
        transactionQueryRepository.get(
            tenant, authenticationTransactionIdentifier, type().value(), Fido2Challenge.class);

    WebAuthn4jChallenge webAuthn4jChallenge = new WebAuthn4jChallenge(fido2Challenge.challenge());
    String requestString = jsonConverter.write(request.toMap());
    WebAuthn4jConfiguration webAuthn4jConfiguration =
        jsonConverter.read(configuration.getDetail(type()), WebAuthn4jConfiguration.class);
    WebAuthn4jAuthenticationManager manager =
        new WebAuthn4jAuthenticationManager(
            webAuthn4jConfiguration, webAuthn4jChallenge, requestString);

    String extractUserId = manager.extractUserId();
    WebAuthn4jCredentials webAuthn4jCredentials = credentialRepository.findAll(extractUserId);

    manager.verify(webAuthn4jCredentials);

    return new Fido2VerificationResult(Map.of("user_id", extractUserId));
  }
}
