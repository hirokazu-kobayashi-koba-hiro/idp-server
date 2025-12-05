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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
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

public class WebAuthn4jAuthenticationChallengeExecutor implements AuthenticationExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(WebAuthn4jAuthenticationChallengeExecutor.class);

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  public WebAuthn4jAuthenticationChallengeExecutor(
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
    return "webauthn4j_authentication_challenge";
  }

  @Override
  public AuthenticationExecutionResult execute(
      Tenant tenant,
      AuthenticationTransactionIdentifier identifier,
      AuthenticationExecutionRequest request,
      RequestAttributes requestAttributes,
      AuthenticationExecutionConfig configuration) {

    try {
      log.debug("webauthn4j authentication challenge, generating challenge");

      WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
      WebAuthn4jUser user = extractUserInfo(request);

      log.debug(
          "webauthn4j registration challenge, creating complete response with user: {}",
          user.name());

      WebAuthn4jConfiguration config =
          jsonConverter.read(configuration.details(), WebAuthn4jConfiguration.class);

      // Use static factory method to create response
      WebAuthn4jRegistrationChallengeResponse challengeResponse =
          WebAuthn4jRegistrationChallengeResponse.create(webAuthn4jChallenge, user, config);

      WebAuthn4jChallengeContext context =
          new WebAuthn4jChallengeContext(webAuthn4jChallenge, user);

      transactionCommandRepository.register(tenant, identifier, type().value(), context);

      Map<String, Object> contents = new HashMap<>();
      contents.put("challenge", webAuthn4jChallenge.challengeAsString());

      // Generate allowCredentials if username is provided
      if (request.containsKey("username")) {
        String username = request.getValueAsString("username");
        log.debug(
            "webauthn4j authentication challenge, generating allowCredentials for username: {}",
            username);

        WebAuthn4jCredentials credentials = credentialRepository.findByUsername(tenant, username);
        List<Map<String, Object>> allowCredentials = credentials.toAllowCredentials();

        if (!allowCredentials.isEmpty()) {
          contents.put("allow_credentials", allowCredentials);
          log.debug(
              "webauthn4j authentication challenge, generated {} allowCredentials",
              allowCredentials.size());
        }
      }

      Map<String, Object> response = new HashMap<>();
      response.put("execution_webauthn4j", challengeResponse.toMap());

      log.info("webauthn4j authentication challenge generated successfully");
      return AuthenticationExecutionResult.success(response);

    } catch (IllegalArgumentException validationException) {
      // Issue #1008: Handle validation errors from getValueAsString()
      log.warn("Request validation failed: {}", validationException.getMessage());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "invalid_request");
      errorResponse.put("error_description", validationException.getMessage());

      return AuthenticationExecutionResult.clientError(errorResponse);
    } catch (Exception e) {
      log.error("webauthn4j unexpected error during authentication challenge generation", e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "server_error");
      errorResponse.put(
          "error_description", "An unexpected error occurred during challenge generation");
      return AuthenticationExecutionResult.serverError(errorResponse);
    }
  }

  /**
   * Extracts user information from the authentication request.
   *
   * <p>This method follows the WebAuthn4j Spring Security pattern where user.id is generated from
   * preferredUsername rather than random bytes. This allows deterministic user resolution during
   * authentication.
   *
   * <p>Pattern: user.id = Base64URL(preferredUsername)
   *
   * @param request the authentication execution request
   * @return the WebAuthn4j user information
   */
  private WebAuthn4jUser extractUserInfo(AuthenticationExecutionRequest request) {
    String username = request.getValueAsString("username");
    String displayName = request.optValueAsString("displayName", username);

    if (username == null || displayName == null) {
      return new WebAuthn4jUser();
    }
    // Generate user ID from username (WebAuthn4j Spring Security pattern)
    // This allows credential.userId to be decoded back to preferredUsername for user resolution
    String userId =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(username.getBytes(StandardCharsets.UTF_8));

    return new WebAuthn4jUser(userId, username, displayName);
  }
}
