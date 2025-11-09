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
      log.debug("webauthn4j registration, retrieving challenge context from transaction");

      WebAuthn4jChallengeContext context =
          transactionQueryRepository.get(
              tenant, identifier, type().value(), WebAuthn4jChallengeContext.class);

      WebAuthn4jChallenge webAuthn4jChallenge = context.challenge();
      WebAuthn4jUser expectedUser = context.user();

      log.debug(
          "webauthn4j registration, retrieved challenge and user info for: {}",
          expectedUser.name());

      // Validate user ID from request matches the expected user ID from challenge context
      String receivedUserId = request.optValueAsString("userId", null);
      if (receivedUserId != null && !expectedUser.id().equals(receivedUserId)) {
        log.error(
            "webauthn4j registration failed: user ID mismatch. Expected: {}, Received: {}",
            expectedUser.id(),
            receivedUserId);
        throw new WebAuthn4jBadRequestException(
            "User ID mismatch. The credential was created for a different user.");
      }

      String requestString = jsonConverter.write(request.toMap());
      WebAuthn4jConfiguration webAuthn4jConfiguration =
          jsonConverter.read(configuration.details(), WebAuthn4jConfiguration.class);

      log.debug(
          "webauthn4j registration, verifying credential for user: {}, displayName: {}",
          expectedUser.name(),
          expectedUser.displayName());

      WebAuthn4jRegistrationManager manager =
          new WebAuthn4jRegistrationManager(
              webAuthn4jConfiguration,
              webAuthn4jChallenge,
              requestString,
              expectedUser.id(),
              expectedUser.name(),
              expectedUser.displayName());

      log.debug("webauthn4j registration, verifying and creating credential");

      WebAuthn4jCredential webAuthn4jCredential = manager.verifyAndCreateCredential();

      log.debug(
          "webauthn4j registration, credential created with id: {}", webAuthn4jCredential.id());

      credentialRepository.register(tenant, webAuthn4jCredential);

      log.debug("webauthn4j registration, credential registered");

      Map<String, Object> response = new HashMap<>();
      response.put("execution_webauthn4j", webAuthn4jCredential.toMap());

      log.info(
          "webauthn4j registration succeeded. credential id: {}, userId: {}, username: {}",
          webAuthn4jCredential.id(),
          expectedUser.id(),
          expectedUser.name());
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
