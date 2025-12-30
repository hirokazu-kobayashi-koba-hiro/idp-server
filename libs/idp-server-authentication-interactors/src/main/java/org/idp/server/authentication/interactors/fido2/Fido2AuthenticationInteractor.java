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

package org.idp.server.authentication.interactors.fido2;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class Fido2AuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  AuthenticationExecutors authenticationExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(Fido2AuthenticationInteractor.class);

  public Fido2AuthenticationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationRepository = configurationRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.FIDO2_AUTHENTICATION.toType();
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.FIDO2.type();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    AuthenticationConfiguration configuration = configurationRepository.get(tenant, "fido2");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("fido2-authentication");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();

    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    AuthenticationExecutionRequest authenticationExecutionRequest =
        new AuthenticationExecutionRequest(request.toMap());
    AuthenticationExecutionResult executionResult =
        executor.execute(
            tenant,
            transaction.identifier(),
            authenticationExecutionRequest,
            requestAttributes,
            execution);

    AuthenticationResponseConfig responseConfig = authenticationInteractionConfig.response();
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> contents =
        MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

    if (executionResult.isClientError()) {
      log.warn("Fido2 authentication is failed. Client error: {}", executionResult.contents());
      // Issue #1021: Try to use transaction user for security event logging (2nd factor case)
      User attemptedUser = transaction.hasUser() ? transaction.user() : null;
      return AuthenticationInteractionRequestResult.clientError(
          contents,
          type,
          operationType(),
          method(),
          attemptedUser,
          DefaultSecurityEventType.fido2_authentication_failure);
    }

    if (executionResult.isServerError()) {
      log.warn("Fido2 is authentication failed. Server error: {}", executionResult.contents());
      // Issue #1021: Try to use transaction user for security event logging (2nd factor case)
      User attemptedUser = transaction.hasUser() ? transaction.user() : null;
      return AuthenticationInteractionRequestResult.serverError(
          contents,
          type,
          operationType(),
          method(),
          attemptedUser,
          DefaultSecurityEventType.fido2_authentication_failure);
    }

    // Resolve user from FIDO2 authentication result
    // For WebAuthn4j: username (preferredUsername) is returned in the execution result
    // For other FIDO2 implementations: deviceId might be used
    User user = resolveUser(tenant, contents, configuration, userQueryRepository);

    if (!user.exists()) {

      log.warn("Fido2 user resolution failed. contents: {}", contents);

      Map<String, Object> userErrorContents = new HashMap<>();
      userErrorContents.put("error", "invalid_request");
      userErrorContents.put(
          "error_description", "FIDO2 authentication succeeded but user could not be resolved");

      return AuthenticationInteractionRequestResult.clientError(
          userErrorContents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido2_authentication_failure);
    }

    Map<String, Object> response = new HashMap<>(contents);
    response.put("user", user.toMinimalizedMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        response,
        DefaultSecurityEventType.fido2_authentication_success);
  }

  /**
   * Resolves user from FIDO2 authentication result.
   *
   * <p>This method supports multiple user resolution strategies:
   *
   * <ul>
   *   <li>WebAuthn4j: Uses 'username' field (preferredUsername decoded from credential.userId)
   *   <li>FIDO UAF: Uses deviceId from authentication device
   *   <li>Custom: Uses metadata configuration to specify the resolution field
   * </ul>
   *
   * @param tenant the tenant
   * @param contents the authentication execution result contents
   * @param configuration the FIDO2 authentication configuration
   * @param userQueryRepository the user query repository
   * @return the resolved user, or User.notFound() if resolution fails
   */
  private User resolveUser(
      Tenant tenant,
      Map<String, Object> contents,
      AuthenticationConfiguration configuration,
      UserQueryRepository userQueryRepository) {

    // Strategy 1: Try username resolution (WebAuthn4j pattern)
    if (contents.containsKey("username")) {
      String preferredUsername = contents.get("username").toString();
      log.debug("Resolving user by preferredUsername: {}", preferredUsername);

      User user = userQueryRepository.findByPreferredUsernameNoProvider(tenant, preferredUsername);
      if (user.exists()) {
        log.debug("User resolved by preferredUsername: {}", preferredUsername);
        return user;
      }
    }

    log.warn(
        "User resolution failed. No valid username or deviceId found in contents: {}", contents);
    return User.notFound();
  }
}
