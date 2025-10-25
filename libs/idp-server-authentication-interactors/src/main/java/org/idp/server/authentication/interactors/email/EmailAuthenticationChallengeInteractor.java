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

package org.idp.server.authentication.interactors.email;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import org.idp.server.core.openid.authentication.policy.AuthenticationStepDefinition;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class EmailAuthenticationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationExecutors authenticationExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(EmailAuthenticationChallengeInteractor.class);

  public EmailAuthenticationChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.interactionCommandRepository = interactionCommandRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_AUTHENTICATION_CHALLENGE.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.EMAIL.type();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    try {

      log.debug("EmailAuthenticationChallengeInteractor called");

      AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "email");
      AuthenticationInteractionConfig authenticationConfig =
          configuration.getAuthenticationConfig("email-authentication-challenge");
      AuthenticationExecutionConfig execution = authenticationConfig.execution();

      String email = resolveEmail(transaction, request);

      if (email.isEmpty()) {
        log.warn("Email is empty. method={}", method());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "email is unspecified.");

        return new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.CLIENT_ERROR,
            type,
            operationType(),
            method(),
            response,
            DefaultSecurityEventType.email_verification_failure);
      }

      AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

      AuthenticationExecutionRequest executionRequest =
          new AuthenticationExecutionRequest(request.toMap());
      AuthenticationExecutionResult executionResult =
          executor.execute(
              tenant, transaction.identifier(), executionRequest, requestAttributes, execution);

      AuthenticationResponseConfig responseConfig = authenticationConfig.response();
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
      JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
      Map<String, Object> contents =
          MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

      if (executionResult.isClientError()) {
        log.warn(
            "Email verification execution failed (client error). method={}, contents={}",
            method(),
            contents);
        return AuthenticationInteractionRequestResult.clientError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.email_verification_request_failure);
      }

      if (executionResult.isServerError()) {
        log.error(
            "Email verification execution failed (server error). method={}, contents={}",
            method(),
            contents);
        return AuthenticationInteractionRequestResult.serverError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.email_verification_request_failure);
      }

      String providerId = request.optValueAsString("provider_id", "idp-server");
      User user = resolveUser(tenant, transaction, email, providerId, userQueryRepository);

      // Check if user was not found (registration disabled or 2nd factor without authenticated
      // user)
      if (!user.exists()) {
        log.warn(
            "User resolution failed. email={}, providerId={}, method={}",
            email,
            providerId,
            method());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "user_not_found");
        response.put(
            "error_description",
            "User not found and registration is not allowed for this authentication flow.");

        return AuthenticationInteractionRequestResult.clientError(
            response,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.email_verification_request_failure);
      }

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          operationType(),
          method(),
          user,
          contents,
          DefaultSecurityEventType.email_verification_request_success);
    } catch (UserTooManyFoundResultException tooManyFoundResultException) {

      log.error(
          "Too many users found for email. email={}, method={}",
          request.getValueAsString("email"),
          method(),
          tooManyFoundResultException);

      Map<String, Object> response =
          Map.of(
              "error",
              "invalid_request",
              "error_description",
              "too many users found for email: " + request.getValueAsString("email"));
      return AuthenticationInteractionRequestResult.clientError(
          response,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.email_verification_request_failure);
    }
  }

  /**
   * Resolve user for authentication (1st factor - user identification).
   *
   * <p><b>Issue #800 Fix:</b> Search database by input identifier FIRST, before checking
   * transaction.
   *
   * <p><b>AuthenticationStepDefinition Integration:</b>
   *
   * <ul>
   *   <li>allowRegistration: controls whether new user creation is allowed
   *   <li>userIdentitySource: determines which field to use for identification
   * </ul>
   *
   * <p><b>Resolution Logic:</b>
   *
   * <ol>
   *   <li>Search database by input email (HIGHEST PRIORITY - Issue #800 fix)
   *   <li>Reuse transaction user if same identity (Challenge resend scenario)
   *   <li>Create new user if allowRegistration=true
   *   <li>Throw exception if user not found and registration disabled
   * </ol>
   *
   * @param tenant tenant
   * @param transaction authentication transaction
   * @param email input email address
   * @param providerId provider ID
   * @param userQueryRepository user query repository
   * @return resolved user
   */
  private User resolveUser(
      Tenant tenant,
      AuthenticationTransaction transaction,
      String email,
      String providerId,
      UserQueryRepository userQueryRepository) {

    // Get step definition from policy
    AuthenticationStepDefinition stepDefinition = getCurrentStepDefinition(transaction, method());

    // SECURITY: 2nd factor requires authenticated user (prevent authentication bypass)
    if (stepDefinition != null && stepDefinition.requiresUser()) {
      if (!transaction.hasUser()) {
        // 2nd factor without authenticated user -> security violation
        log.warn(
            "2nd factor requires authenticated user but transaction has no user. method={}, email={}",
            method(),
            email);
        return User.notFound();
      }
      // 2nd factor: return authenticated user (immutable)
      log.debug(
          "2nd factor: returning authenticated user. method={}, sub={}",
          method(),
          transaction.user().sub());
      return transaction.user();
    }

    // === 1st factor user identification ===

    // 1. Database search FIRST (Issue #800 fix)
    User existingUser = userQueryRepository.findByEmail(tenant, email, providerId);
    if (existingUser.exists()) {
      log.debug("User found in database. email={}, sub={}", email, existingUser.sub());
      return existingUser;
    }
    log.debug("User not found in database. email={}", email);

    // 2. Reuse transaction user if same identity (Challenge resend scenario)
    if (transaction.hasUser()) {
      User transactionUser = transaction.user();

      if (email.equals(transactionUser.email())) {
        log.debug(
            "Reusing transaction user (same email). email={}, sub={}",
            email,
            transactionUser.sub());
        return transactionUser; // Same identity -> reuse
      }
      // Different identity -> discard previous user and create new
      log.debug(
          "Transaction user has different email. requested={}, transaction={}",
          email,
          transactionUser.email());
    }

    // 3. New user creation decision
    boolean allowRegistration =
        stepDefinition != null ? stepDefinition.allowRegistration() : false; // default: disabled

    if (!allowRegistration) {
      log.warn(
          "User not found and registration disabled. email={}, allowRegistration=false", email);
      return User.notFound();
    }

    // 4. Create new user
    log.debug("Creating new user. email={}, allowRegistration=true", email);
    User user = new User();
    String id = UUID.randomUUID().toString();
    user.setSub(id);
    user.setEmail(email);

    return user;
  }

  private String resolveEmail(
      AuthenticationTransaction transaction, AuthenticationInteractionRequest request) {

    AuthenticationStepDefinition stepDefinition = getCurrentStepDefinition(transaction, method());

    // 2nd factor: use authenticated user's email only (ignore request input)
    if (stepDefinition != null && stepDefinition.requiresUser()) {
      if (transaction.hasUser()) {
        String email = transaction.user().email();
        log.debug("2nd factor: using authenticated user's email. email={}", email);
        return email;
      }
      log.warn(
          "2nd factor authentication failed: no authenticated user in transaction. method={}",
          method());
      return ""; // No authenticated user -> error
    }

    // 1st factor: get from request or transaction
    if (request.containsKey("email")) {
      String email = request.getValueAsString("email");
      log.debug("1st factor: email from request. email={}", email);
      return email;
    }
    if (transaction.hasUser()) {
      User user = transaction.user();
      log.debug("1st factor: email from transaction user. email={}", user.email());
      return user.email();
    }
    log.debug("No email found in request or transaction");
    return "";
  }

  /**
   * Get current step definition from authentication policy.
   *
   * @param transaction authentication transaction
   * @param method authentication method name
   * @return step definition or null if not found
   */
  private AuthenticationStepDefinition getCurrentStepDefinition(
      AuthenticationTransaction transaction, String method) {

    if (!transaction.hasAuthenticationPolicy()) {
      return null;
    }

    AuthenticationPolicy policy = transaction.authenticationPolicy();
    if (!policy.hasStepDefinitions()) {
      return null;
    }

    return policy.stepDefinitions().stream()
        .filter(step -> method.equals(step.authenticationMethod()))
        .findFirst()
        .orElse(null);
  }
}
