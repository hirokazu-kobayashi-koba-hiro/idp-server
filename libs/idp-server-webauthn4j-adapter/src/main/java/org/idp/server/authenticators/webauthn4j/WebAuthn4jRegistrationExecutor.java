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
import java.util.UUID;
import org.idp.server.authentication.interactors.fido2.Fido2Challenge;
import org.idp.server.authentication.interactors.fido2.Fido2ExecutorType;
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

public class WebAuthn4jRegistrationExecutor implements AuthenticationExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(WebAuthn4jRegistrationExecutor.class);

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthn4jRegistrationExecutor(
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
    return "webauthn4j_registration";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    try {
      Fido2Challenge fido2Challenge =
          transactionQueryRepository.get(tenant, identifier, type().value(), Fido2Challenge.class);

      log.debug("webauthn4j registration, retrieved challenge from transaction");

      WebAuthn4jChallenge webAuthn4jChallenge = new WebAuthn4jChallenge(fido2Challenge.challenge());
      String requestString = jsonConverter.write(request.toMap());
      WebAuthn4jConfiguration webAuthn4jConfiguration =
          jsonConverter.read(configuration.details(), WebAuthn4jConfiguration.class);

      String userId = UUID.randomUUID().toString();
      log.debug("webauthn4j registration, generated userId: {}", userId);

      // Extract username and displayName from request
      String username = request.optValueAsString("username", null);
      String displayName = request.optValueAsString("displayName", null);

      log.debug("webauthn4j registration, username: {}, displayName: {}", username, displayName);

      WebAuthn4jRegistrationManager manager =
          new WebAuthn4jRegistrationManager(
              webAuthn4jConfiguration,
              webAuthn4jChallenge,
              requestString,
              userId,
              username,
              displayName);

      log.debug("webauthn4j registration, verifying and creating credential");

      WebAuthn4jCredential webAuthn4jCredential = manager.verifyAndCreateCredential();

      log.debug(
          "webauthn4j registration, credential created with id: {}", webAuthn4jCredential.id());

      credentialRepository.register(webAuthn4jCredential);

      log.debug("webauthn4j registration, credential registered");

      Map<String, Object> response = new HashMap<>();
      response.put("execution_webauthn4j", webAuthn4jCredential.toMap());

      log.info(
          "webauthn4j registration succeeded. credential id: {}, userId: {}",
          webAuthn4jCredential.id(),
          userId);
      return AuthenticationExecutionResult.success(response);

    } catch (WebAuthn4jBadRequestException e) {
      log.error("webauthn4j registration failed: {}", e.getMessage(), e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "registration_failed");
      errorResponse.put("error_description", e.getMessage());
      return AuthenticationExecutionResult.clientError(errorResponse);

    } catch (Exception e) {
      log.error("webauthn4j unexpected error during registration", e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "server_error");
      errorResponse.put("error_description", "An unexpected error occurred during registration");
      return AuthenticationExecutionResult.serverError(errorResponse);
    }
  }
}
