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

package org.idp.server.authentication.interactors.sms;

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

public class SmsAuthenticationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationExecutors executors;
  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(SmsAuthenticationChallengeInteractor.class);

  public SmsAuthenticationChallengeInteractor(
      AuthenticationExecutors executors,
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.executors = executors;
    this.interactionCommandRepository = interactionCommandRepository;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.SMS_AUTHENTICATION_CHALLENGE.toType();
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.SMS.type();
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

      log.debug("SmsAuthenticationChallengeInteractor called");

      AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "sms");
      AuthenticationInteractionConfig authenticationInteractionConfig =
          configuration.getAuthenticationConfig("sms-authentication-challenge");
      AuthenticationExecutionConfig executionConfig = authenticationInteractionConfig.execution();

      String providerId = request.optValueAsString("provider_id", "idp-server");
      String phoneNumber = resolvePhoneNumber(transaction, request);

      if (phoneNumber.isEmpty()) {
        log.warn("Phone number is empty. method={}", method());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "phone_number is unspecified or invalid format.");

        return new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.CLIENT_ERROR,
            type,
            operationType(),
            method(),
            response,
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      AuthenticationExecutor executor = executors.get(executionConfig.function());

      Map<String, Object> executionRequestValues = new HashMap<>(request.toMap());
      executionRequestValues.put("phone_number", phoneNumber);
      AuthenticationExecutionRequest executionRequest =
          new AuthenticationExecutionRequest(executionRequestValues);
      AuthenticationExecutionResult executionResult =
          executor.execute(
              tenant,
              transaction.identifier(),
              executionRequest,
              requestAttributes,
              executionConfig);

      AuthenticationResponseConfig responseConfig = authenticationInteractionConfig.response();
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
      JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
      Map<String, Object> contents =
          MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

      if (executionResult.isClientError()) {
        log.warn(
            "SMS verification execution failed (client error). method={}, contents={}",
            method(),
            contents);
        return AuthenticationInteractionRequestResult.clientError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      if (executionResult.isServerError()) {
        log.error(
            "SMS verification execution failed (server error). method={}, contents={}",
            method(),
            contents);
        return AuthenticationInteractionRequestResult.serverError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.sms_verification_challenge_failure);
      }

      User resolvedUser =
          resolveUser(tenant, transaction, phoneNumber, providerId, userQueryRepository);

      SmslVerificationChallengeRequest smslVerificationChallengeRequest =
          new SmslVerificationChallengeRequest(providerId, phoneNumber);
      interactionCommandRepository.register(
          tenant,
          transaction.identifier(),
          "sms-authentication-challenge-request",
          smslVerificationChallengeRequest);

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          operationType(),
          method(),
          resolvedUser,
          contents,
          DefaultSecurityEventType.sms_verification_challenge_success);
    } catch (UserTooManyFoundResultException tooManyFoundResultException) {
      // Issue #1008: Use optValueAsString() to avoid IllegalArgumentException in error handling
      String phoneNumber = request.optValueAsString("phone_number", "<unspecified>");

      log.error(
          "Too many users found for phone number. phoneNumber={}, method={}",
          phoneNumber,
          method(),
          tooManyFoundResultException);

      Map<String, Object> response =
          Map.of(
              "error",
              "invalid_request",
              "error_description",
              "too many users found for phone number: " + phoneNumber);
      return AuthenticationInteractionRequestResult.clientError(
          response,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.sms_verification_challenge_failure);
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
          DefaultSecurityEventType.sms_verification_challenge_failure);
    }
  }

  private String resolvePhoneNumber(
      AuthenticationTransaction transaction, AuthenticationInteractionRequest request) {

    AuthenticationStepDefinition stepDefinition = transaction.getCurrentStepDefinition(method());

    // 2nd factor: use authenticated user's phone number only (ignore request input)
    if (stepDefinition != null && stepDefinition.requiresUser() && !transaction.hasUser()) {
      log.debug("2nd factor: using authenticated user's phone. But user is not specified.");
      return "";
    }

    if (stepDefinition != null && stepDefinition.requiresUser()) {
      if (transaction.hasUser() && transaction.user().hasPhoneNumber()) {
        String phoneNumber = transaction.user().phoneNumber();
        log.debug("2nd factor: using authenticated user's phone. phoneNumber={}", phoneNumber);
        return phoneNumber;
      }

      if (stepDefinition.allowRegistration()) {
        String phoneNumber = request.optValueAsString("phone_number", "");
        log.debug("2nd factor: using authenticated user's phone. phoneNumber={}", phoneNumber);
        return phoneNumber;
      }
      log.warn(
          "2nd factor authentication failed: no authenticated user in transaction. method={}",
          method());
      return ""; // No authenticated user -> error
    }

    // 1st factor: get from request or transaction
    if (request.containsKey("phone_number")) {
      String phoneNumber = request.getValueAsString("phone_number");
      log.debug("1st factor: phone from request. phoneNumber={}", phoneNumber);
      return phoneNumber;
    }
    if (transaction.hasUser()) {
      User user = transaction.user();
      log.debug("1st factor: phone from transaction user. phoneNumber={}", user.phoneNumber());
      return user.phoneNumber();
    }
    log.debug("No phone number found in request or transaction");
    return "";
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
   *   <li>Search database by input phone number (HIGHEST PRIORITY - Issue #800 fix)
   *   <li>Reuse transaction user if same identity (Challenge resend scenario)
   *   <li>Create new user if allowRegistration=true
   *   <li>Throw exception if user not found and registration disabled
   * </ol>
   *
   * @param tenant tenant
   * @param transaction authentication transaction
   * @param phoneNumber input phone number
   * @param providerId provider ID
   * @param userQueryRepository user query repository
   * @return resolved user
   */
  private User resolveUser(
      Tenant tenant,
      AuthenticationTransaction transaction,
      String phoneNumber,
      String providerId,
      UserQueryRepository userQueryRepository) {

    // Get step definition from policy
    AuthenticationStepDefinition stepDefinition = transaction.getCurrentStepDefinition(method());
    // New user creation decision
    boolean allowRegistration =
        stepDefinition != null && stepDefinition.allowRegistration(); // default: disabled

    // SECURITY: 2nd factor requires authenticated user (prevent authentication bypass)
    if (stepDefinition != null && stepDefinition.requiresUser()) {
      if (!transaction.hasUser()) {
        // 2nd factor without authenticated user -> security violation
        log.warn(
            "2nd factor requires authenticated user but transaction has no user. method={}, phoneNumber={}",
            method(),
            phoneNumber);
        return User.notFound();
      }
      if (allowRegistration) {
        log.info(
            "2nd factor requires authenticated and allowRegistration. method={}, phoneNumber={}",
            method(),
            phoneNumber);

        User user = transaction.user();
        user.setPhoneNumber(phoneNumber);
        return user;
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
    User existingUser = userQueryRepository.findByPhone(tenant, phoneNumber, providerId);
    if (existingUser.exists()) {
      log.debug("User found in database. phoneNumber={}, sub={}", phoneNumber, existingUser.sub());
      return existingUser;
    }
    log.debug("User not found in database. phoneNumber={}", phoneNumber);

    // 2. Reuse transaction user if same identity (Challenge resend scenario)
    if (transaction.hasUser()) {
      User transactionUser = transaction.user();

      if (phoneNumber.equals(transactionUser.phoneNumber())) {
        log.debug(
            "Reusing transaction user (same phone). phoneNumber={}, sub={}",
            phoneNumber,
            transactionUser.sub());
        return transactionUser; // Same identity -> reuse
      }
      // Different identity -> discard previous user and create new
      log.debug(
          "Transaction user has different phone. requested={}, transaction={}",
          phoneNumber,
          transactionUser.phoneNumber());
    }

    if (!allowRegistration) {
      log.warn(
          "User not found and registration disabled. phoneNumber={}, allowRegistration=false",
          phoneNumber);
      return User.notFound();
    }

    // 4. Create new user
    log.debug("Creating new user. phoneNumber={}, allowRegistration=true", phoneNumber);
    User user = User.initialized();
    user.setPhoneNumber(phoneNumber);
    user.applyIdentityPolicy(tenant.identityPolicyConfig());

    return user;
  }
}
