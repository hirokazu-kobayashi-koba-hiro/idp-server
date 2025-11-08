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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.fido2.*;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class WebAuthn4jRegistrationChallengeExecutor implements AuthenticationExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(WebAuthn4jRegistrationChallengeExecutor.class);

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthn4jRegistrationChallengeExecutor(
      AuthenticationInteractionCommandRepository transactionCommandRepository,
      AuthenticationInteractionQueryRepository transactionQueryRepository,
      WebAuthn4jCredentialRepository credentialRepository) {
    this.transactionCommandRepository = transactionCommandRepository;
    this.transactionQueryRepository = transactionQueryRepository;
    this.credentialRepository = credentialRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public Fido2ExecutorType type() {
    return new Fido2ExecutorType("webauthn4j");
  }

  @Override
  public String function() {
    return "webauthn4j_registration_challenge";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    try {
      log.debug("webauthn4j registration challenge, generating challenge");

      WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
      Fido2Challenge fido2Challenge = webAuthn4jChallenge.toWebAuthnChallenge();

      log.debug("webauthn4j registration challenge, registering to transaction");

      transactionCommandRepository.register(tenant, identifier, type().value(), fido2Challenge);

      Map<String, Object> contents = new HashMap<>();
      contents.put("challenge", webAuthn4jChallenge.challengeAsString());
      Map<String, Object> response = new HashMap<>();
      response.put("execution_webauthn4j", contents);

      log.info("webauthn4j registration challenge generated successfully");
      return AuthenticationExecutionResult.success(response);

    } catch (Exception e) {
      log.error("webauthn4j unexpected error during registration challenge generation", e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "server_error");
      errorResponse.put(
          "error_description", "An unexpected error occurred during challenge generation");
      return AuthenticationExecutionResult.serverError(errorResponse);
    }
  }
}
