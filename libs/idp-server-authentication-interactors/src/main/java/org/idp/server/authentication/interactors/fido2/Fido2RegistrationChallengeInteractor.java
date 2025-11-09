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
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class Fido2RegistrationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  AuthenticationExecutors authenticationExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(Fido2RegistrationChallengeInteractor.class);

  public Fido2RegistrationChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationRepository = configurationRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.FIDO2_REGISTRATION_CHALLENGE.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
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

    log.debug("WebAuthnRegistrationChallengeInteractor called");

    AuthenticationConfiguration configuration = configurationRepository.get(tenant, "fido2");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("fido2-registration-challenge");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();

    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    // Resolve username based on Tenant Identity Policy
    Map<String, Object> requestMap = resolveUsernameFromRequest(tenant, transaction, request);

    AuthenticationExecutionRequest authenticationExecutionRequest =
        new AuthenticationExecutionRequest(requestMap);
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

      log.warn(
          "Fido2 registration challenge is failed. Client error: {}", executionResult.contents());

      return AuthenticationInteractionRequestResult.clientError(
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido2_registration_challenge_failure);
    }

    if (executionResult.isServerError()) {

      log.warn(
          "Fido2 registration challenge is failed. Server error: {}", executionResult.contents());

      return AuthenticationInteractionRequestResult.serverError(
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido2_registration_challenge_failure);
    }

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        transaction.user(),
        contents,
        DefaultSecurityEventType.fido2_registration_challenge_success);
  }

  /**
   * Resolves username from request or transaction user based on Tenant Identity Policy.
   *
   * <p>Resolution strategy:
   *
   * <ol>
   *   <li>If request contains "username": use it directly
   *   <li>If request doesn't contain "username": resolve from transaction.user() based on Tenant
   *       Identity Policy
   * </ol>
   *
   * <p>This method supports Tenant Identity Policy patterns:
   *
   * <ul>
   *   <li>USERNAME / USERNAME_OR_EXTERNAL_USER_ID: Use preferredUsername
   *   <li>EMAIL / EMAIL_OR_EXTERNAL_USER_ID: Use email
   *   <li>PHONE / PHONE_OR_EXTERNAL_USER_ID: Use phoneNumber
   *   <li>EXTERNAL_USER_ID: Use externalUserId (for federated users)
   * </ul>
   *
   * @param tenant the tenant
   * @param transaction the authentication transaction
   * @param request the authentication interaction request
   * @return request map with username resolved
   */
  private Map<String, Object> resolveUsernameFromRequest(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionRequest request) {

    Map<String, Object> requestMap = new HashMap<>(request.toMap());

    // Strategy 1 : Resolve username from transaction.user() based on Tenant Identity Policy
    if (transaction.hasUser()) {

      User user = transaction.user();
      TenantIdentityPolicy identityPolicy = tenant.identityPolicyConfig();
      String username = resolveUsernameFromUser(user, identityPolicy);
      if (username != null && !username.isEmpty()) {
        log.debug(
            "FIDO2 registration challenge: resolved username from user based on policy {}: {}",
            identityPolicy.uniqueKeyType(),
            username);
        requestMap.put("username", username);

        // Also add displayName if available
        if (user.name() != null && !user.name().isEmpty()) {
          requestMap.put("displayName", user.name());
        }
      }
      return requestMap;
    }

    // Strategy 2: Use username from request if available
    if (requestMap.containsKey("username")) {
      log.debug(
          "FIDO2 registration challenge: using username from request: {}",
          requestMap.get("username"));
    }

    return requestMap;
  }

  /**
   * Resolves username from User based on Tenant Identity Policy.
   *
   * @param user the user
   * @param identityPolicy the tenant identity policy
   * @return username, or empty string if not resolvable
   */
  private String resolveUsernameFromUser(User user, TenantIdentityPolicy identityPolicy) {
    switch (identityPolicy.uniqueKeyType()) {
      case USERNAME:
      case USERNAME_OR_EXTERNAL_USER_ID:
        return user.preferredUsername();

      case EMAIL:
      case EMAIL_OR_EXTERNAL_USER_ID:
        return user.email();

      case PHONE:
      case PHONE_OR_EXTERNAL_USER_ID:
        return user.phoneNumber();

      case EXTERNAL_USER_ID:
        return user.externalUserId();

      default:
        // Fallback to preferredUsername
        return user.preferredUsername();
    }
  }
}
