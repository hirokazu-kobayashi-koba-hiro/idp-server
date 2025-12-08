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
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
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

      String providerId = request.optValueAsString("provider_id", "idp-server");
      String email = resolveEmail(transaction, request);

      if (email.isEmpty()) {
        log.warn("Email is empty. method={}", method());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "email is unspecified or invalid format.");

        return new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.CLIENT_ERROR,
            type,
            operationType(),
            method(),
            response,
            DefaultSecurityEventType.email_verification_failure);
      }

      AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

      Map<String, Object> executionRequestValues = new HashMap<>(request.toMap());
      executionRequestValues.put("email", email);
      AuthenticationExecutionRequest executionRequest =
          new AuthenticationExecutionRequest(executionRequestValues);
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
        // Issue #1034: Try to resolve user for security event logging
        User attemptedUser =
            transaction.hasUser()
                ? transaction.user()
                : tryResolveUserForLogging(tenant, email, providerId, userQueryRepository);
        return AuthenticationInteractionRequestResult.clientError(
            contents,
            type,
            operationType(),
            method(),
            attemptedUser,
            DefaultSecurityEventType.email_verification_request_failure);
      }

      if (executionResult.isServerError()) {
        log.error(
            "Email verification execution failed (server error). method={}, contents={}",
            method(),
            contents);
        // Issue #1034: Try to resolve user for security event logging
        User attemptedUser =
            transaction.hasUser()
                ? transaction.user()
                : tryResolveUserForLogging(tenant, email, providerId, userQueryRepository);
        return AuthenticationInteractionRequestResult.serverError(
            contents,
            type,
            operationType(),
            method(),
            attemptedUser,
            DefaultSecurityEventType.email_verification_request_failure);
      }

      EmailVerificationChallengeRequest emailVerificationChallengeRequest =
          new EmailVerificationChallengeRequest(providerId, email);
      interactionCommandRepository.register(
          tenant,
          transaction.identifier(),
          "email-authentication-challenge-request",
          emailVerificationChallengeRequest);

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          operationType(),
          method(),
          transaction.user(),
          contents,
          DefaultSecurityEventType.email_verification_request_success);
    } catch (UserTooManyFoundResultException tooManyFoundResultException) {
      // Issue #1008: Use optValueAsString() to avoid IllegalArgumentException in error handling
      String email = request.optValueAsString("email", "<unspecified>");

      log.error(
          "Too many users found for email. email={}, method={}",
          email,
          method(),
          tooManyFoundResultException);

      Map<String, Object> response =
          Map.of(
              "error",
              "invalid_request",
              "error_description",
              "too many users found for email: " + email);
      return AuthenticationInteractionRequestResult.clientError(
          response,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.email_verification_request_failure);
    } catch (IllegalArgumentException validationException) {
      // Issue #1008: Handle validation errors from getValueAsString()
      log.warn("Request validation failed: {}", validationException.getMessage());

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", validationException.getMessage());

      return AuthenticationInteractionRequestResult.clientError(
          response,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.email_verification_request_failure);
    }
  }

  private String resolveEmail(
      AuthenticationTransaction transaction, AuthenticationInteractionRequest request) {

    AuthenticationStepDefinition stepDefinition = transaction.getCurrentStepDefinition(method());

    // 2nd factor: use authenticated user's email only (ignore request input)
    if (stepDefinition != null && stepDefinition.requiresUser() && !transaction.hasUser()) {
      log.debug("2nd factor: using authenticated user's email. But user is not specified.");
      return "";
    }

    if (stepDefinition != null && stepDefinition.requiresUser()) {
      if (transaction.hasUser() && transaction.user().hasEmail()) {
        String email = transaction.user().email();
        log.debug("2nd factor: using authenticated user's email. email={}", email);
        return email;
      }

      if (stepDefinition.allowRegistration()) {
        String email = request.optValueAsString("email", "");
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
      // Issue #1008: Use optValueAsString to handle type mismatch gracefully
      String email = request.optValueAsString("email", "");
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
   * Try to resolve user for security event logging purposes only.
   *
   * <p>This method attempts to find a user by email for logging purposes. It does NOT modify the
   * transaction user to avoid identifier switching attacks (Issue #801).
   *
   * @param tenant the tenant
   * @param email the email from the challenge request
   * @param providerId the provider ID
   * @param userQueryRepository the user query repository
   * @return the resolved user, or null if not found
   */
  private User tryResolveUserForLogging(
      Tenant tenant, String email, String providerId, UserQueryRepository userQueryRepository) {
    if (email == null || email.isEmpty()) {
      return null;
    }

    try {
      User user = userQueryRepository.findByEmail(tenant, email, providerId);
      if (user.exists()) {
        log.debug("User resolved for security event logging. email={}, sub={}", email, user.sub());
        return user;
      }
    } catch (Exception e) {
      log.debug("Failed to resolve user for security event logging. email={}", email);
    }

    return null;
  }
}
