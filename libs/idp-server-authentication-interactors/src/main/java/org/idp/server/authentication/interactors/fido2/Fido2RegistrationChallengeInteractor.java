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
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
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

    // FIDO2 registration requires authenticated session
    if (!transaction.hasUser()) {
      log.warn("FIDO2 registration challenge: unauthenticated request");

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "unauthorized");
      errorResponse.put(
          "error_description", "FIDO2 device registration requires authenticated session");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          null,
          DefaultSecurityEventType.fido2_registration_challenge_failure);
    }

    // Verify device registration ACR/MFA requirements
    if (!isDeviceRegistrationPolicyMet(tenant, transaction)) {
      log.warn(
          "FIDO2 device registration policy check failed: user={}, tenant={}",
          transaction.user().sub(),
          tenant.identifier().value());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "forbidden");
      errorResponse.put(
          "error_description",
          "Current authentication level does not meet device registration requirements. "
              + "Please complete required authentication steps (e.g., MFA or existing device authentication).");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          transaction.user(),
          DefaultSecurityEventType.fido2_registration_challenge_failure);
    }

    // Resolve username from request
    Map<String, Object> requestMap = resolveUsernameFromRequest(tenant, transaction, request);
    if (requestMap == null) {
      // Username mismatch detected
      log.warn(
          "FIDO2 registration: username mismatch or resolution failed for user={}",
          transaction.user().sub());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "forbidden");
      errorResponse.put("error_description", "Cannot register FIDO2 device for a different user");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          transaction.user(),
          DefaultSecurityEventType.fido2_registration_challenge_failure);
    }

    AuthenticationConfiguration configuration = configurationRepository.get(tenant, "fido2");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("fido2-registration-challenge");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();

    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

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

    if (!executionResult.isSuccess()) {

      log.warn(
          "Fido2 registration challenge failed. status={}, contents={}",
          executionResult.statusCode(),
          executionResult.contents());

      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
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
   * @return request map with username resolved, or null if username mismatch detected
   */
  private Map<String, Object> resolveUsernameFromRequest(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionRequest request) {

    Map<String, Object> requestMap = new HashMap<>(request.toMap());

    // Resolve username from transaction.user() only (prevent username manipulation)
    User authenticatedUser = transaction.user();
    TenantIdentityPolicy identityPolicy = tenant.identityPolicyConfig();
    String authenticatedUsername = resolveUsernameFromUser(authenticatedUser, identityPolicy);

    if (authenticatedUsername == null || authenticatedUsername.isEmpty()) {
      log.warn("Cannot resolve username from authenticated user: {}", authenticatedUser.sub());
      return null;
    }

    // Verify username match if provided in request (prevent user impersonation)
    if (requestMap.containsKey("username")) {
      String requestedUsername = (String) requestMap.get("username");
      if (!authenticatedUsername.equals(requestedUsername)) {
        log.warn(
            "FIDO2 registration: username mismatch detected. authenticated={}, requested={}",
            authenticatedUsername,
            requestedUsername);
        return null;
      }
    }

    // Force authenticated username (prevent manipulation)
    requestMap.put("username", authenticatedUsername);

    // Add displayName if available
    if (authenticatedUser.name() != null && !authenticatedUser.name().isEmpty()) {
      requestMap.put("displayName", authenticatedUser.name());
    }

    log.info(
        "FIDO2 registration challenge: authenticated user verified, username={}",
        authenticatedUsername);

    return requestMap;
  }

  /**
   * Checks if device registration policy requirements are met.
   *
   * <p>Penetration Test Issue #8: ACR-based access control bypass prevention
   *
   * <p>Ensures that FIDO2 device registration requires authentication meeting configured policy,
   * typically either:
   *
   * <ul>
   *   <li>Authentication with existing FIDO2 device, OR
   *   <li>Multi-factor authentication (password + TOTP)
   * </ul>
   *
   * @param tenant the tenant
   * @param transaction the authentication transaction
   * @return true if policy is met or not configured, false if policy check fails
   */
  private boolean isDeviceRegistrationPolicyMet(
      Tenant tenant, AuthenticationTransaction transaction) {

    AuthenticationPolicy authPolicy = transaction.authenticationPolicy();

    // Only verify if device_registration_conditions is configured
    if (!authPolicy.hasDeviceRegistrationConditions()) {
      log.debug(
          "No device_registration_conditions configured, skipping ACR verification for FIDO2 registration");
      return true;
    }

    AuthenticationInteractionResults interactionResults = transaction.interactionResults();
    AuthenticationResultConditionConfig conditions = authPolicy.deviceRegistrationConditions();

    // Evaluate conditions using existing MfaConditionEvaluator
    boolean satisfied =
        org.idp.server.core.openid.authentication.evaluator.MfaConditionEvaluator
            .isSuccessSatisfied(conditions, interactionResults);

    if (satisfied) {
      log.info(
          "FIDO2 device registration policy check passed: user={}, tenant={}",
          transaction.user().sub(),
          tenant.identifier().value());
    }

    return satisfied;
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
