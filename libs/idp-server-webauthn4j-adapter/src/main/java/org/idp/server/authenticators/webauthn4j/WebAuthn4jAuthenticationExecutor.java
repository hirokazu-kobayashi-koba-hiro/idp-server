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

import com.webauthn4j.data.AuthenticationData;
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

public class WebAuthn4jAuthenticationExecutor implements AuthenticationExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(WebAuthn4jAuthenticationExecutor.class);

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthn4jAuthenticationExecutor(
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
    return "webauthn4j_authentication";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    try {
      WebAuthn4jChallengeContext context =
          transactionQueryRepository.get(
              tenant, identifier, type().value(), WebAuthn4jChallengeContext.class);

      WebAuthn4jChallenge webAuthn4jChallenge = context.challenge();
      String requestString = jsonConverter.write(request.toMap());
      WebAuthn4jConfiguration webAuthn4jConfiguration =
          jsonConverter.read(configuration.details(), WebAuthn4jConfiguration.class);
      WebAuthn4jAuthenticationManager manager =
          new WebAuthn4jAuthenticationManager(
              webAuthn4jConfiguration, webAuthn4jChallenge, requestString);

      String id = request.optValueAsString("id", "");
      log.debug("webauthn4j authentication, retrieving credential with id: {}", id);

      WebAuthn4jCredential webAuthn4jCredential = credentialRepository.get(id);

      log.debug(
          "webauthn4j authentication, verifying credential id: {}, current signCount: {}",
          id,
          webAuthn4jCredential.signCount());

      AuthenticationData authenticationData =
          manager.verifyAndGetAuthenticationData(webAuthn4jCredential);

      long newSignCount = authenticationData.getAuthenticatorData().getSignCount();

      log.debug("webauthn4j authentication verified. new signCount: {}", newSignCount);

      if (newSignCount > 0 && newSignCount <= webAuthn4jCredential.signCount()) {
        log.error(
            "webauthn4j credential clone detected. id: {}, current signCount: {}, new signCount: {}",
            id,
            webAuthn4jCredential.signCount(),
            newSignCount);
        throw new WebAuthn4jBadRequestException(
            "Possible credential clone detected. Current: "
                + webAuthn4jCredential.signCount()
                + ", New: "
                + newSignCount);
      }

      credentialRepository.updateSignCount(id, newSignCount);
      log.debug("webauthn4j signCount updated for credential id: {}", id);

      String preferredUsername = webAuthn4jCredential.username();

      Map<String, Object> contents = new HashMap<>();
      contents.put("id", id);
      contents.put("status", "ok");
      contents.put("username", preferredUsername); // for user resolution in upper layer
      Map<String, Object> response = new HashMap<>();
      response.put("execution_webauthn4j", contents);

      log.info(
          "webauthn4j authentication succeeded. credential id: {}, username: {}",
          id,
          preferredUsername);
      return AuthenticationExecutionResult.success(response);

    } catch (WebAuthn4jBadRequestException e) {
      log.error("webauthn4j authentication failed: {}", e.getMessage(), e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "authentication_failed");
      errorResponse.put("error_description", e.getMessage());
      return AuthenticationExecutionResult.clientError(errorResponse);

    } catch (Exception e) {
      log.error("webauthn4j unexpected error during authentication", e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "server_error");
      errorResponse.put("error_description", "An unexpected error occurred during authentication");
      return AuthenticationExecutionResult.serverError(errorResponse);
    }
  }
}
