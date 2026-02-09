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
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class SmsAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationExecutors executors;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(SmsAuthenticationInteractor.class);

  public SmsAuthenticationInteractor(
      AuthenticationExecutors executors,
      AuthenticationInteractionQueryRepository interactionQueryRepository,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.executors = executors;
    this.interactionQueryRepository = interactionQueryRepository;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.SMS_AUTHENTICATION.toType();
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

    log.debug("SmsAuthenticationInteractor called");

    AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "sms");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("sms-authentication");

    // Issue #1021: Fetch challenge request early for user resolution on failure
    // Challenge request must exist in authentication phase (created in challenge phase)
    SmslVerificationChallengeRequest challengeRequest =
        interactionQueryRepository.get(
            tenant,
            transaction.identifier(),
            "sms-authentication-challenge-request",
            SmslVerificationChallengeRequest.class);
    String phoneNumber = challengeRequest.phoneNumber();
    String providerId = challengeRequest.providerId();

    // JSON Schema validation (Layer 2) - Issue #1008
    JsonSchemaDefinition schemaDefinition =
        authenticationInteractionConfig.request().requestSchemaAsDefinition();

    if (schemaDefinition.exists()) {
      JsonNodeWrapper requestNode = JsonNodeWrapper.fromMap(request.toMap());
      JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
      JsonSchemaValidationResult validationResult = validator.validate(requestNode);

      if (!validationResult.isValid()) {
        log.warn(
            "SMS authentication request validation failed: error_count={}, errors={}",
            validationResult.errors().size(),
            validationResult.errors());

        // Issue #1034: Check transaction user first (for registration flow),
        // then try database lookup (for login flow)
        User attemptedUser =
            transaction.hasUser()
                ? transaction.user()
                : tryResolveUserForLogging(tenant, phoneNumber, providerId, userQueryRepository);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "invalid_request");
        errorResponse.put(
            "error_description", "The authentication request is invalid. Please check your input.");
        errorResponse.put("error_messages", validationResult.errors());

        return AuthenticationInteractionRequestResult.clientError(
            errorResponse,
            type,
            operationType(),
            method(),
            attemptedUser,
            DefaultSecurityEventType.sms_verification_failure);
      }
    }

    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = executors.get(execution.function());

    AuthenticationExecutionRequest executionRequest =
        new AuthenticationExecutionRequest(request.toMap());
    AuthenticationExecutionResult executionResult =
        executor.execute(
            tenant, transaction.identifier(), executionRequest, requestAttributes, execution);

    AuthenticationResponseConfig responseConfig = authenticationInteractionConfig.response();
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> contents =
        MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

    if (!executionResult.isSuccess()) {
      log.warn(
          "SMS authentication failed. status={}, contents={}",
          executionResult.statusCode(),
          executionResult.contents());
      // Issue #1034: Check transaction user first (for registration flow),
      // then try database lookup (for login flow)
      User attemptedUser =
          transaction.hasUser()
              ? transaction.user()
              : tryResolveUserForLogging(tenant, phoneNumber, providerId, userQueryRepository);
      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
          contents,
          type,
          operationType(),
          method(),
          attemptedUser,
          DefaultSecurityEventType.sms_verification_failure);
    }

    // phoneNumber and providerId already retrieved from challenge request earlier (Issue #1021)
    User verifiedUser =
        resolveUser(tenant, transaction, phoneNumber, providerId, userQueryRepository);

    // Check if user was not found (registration disabled or 2nd factor without authenticated
    // user)
    if (!verifiedUser.exists()) {
      log.warn(
          "User resolution failed. phoneNumber={}, providerId={}, method={}",
          phoneNumber,
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
          DefaultSecurityEventType.sms_verification_challenge_failure);
    }

    verifiedUser.setPhoneNumberVerified(true);

    log.debug("SMS authentication succeeded for user: {}", verifiedUser.sub());

    Map<String, Object> responseContents = new HashMap<>(contents);
    responseContents.put("user", verifiedUser.toMinimalizedMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        verifiedUser,
        responseContents,
        DefaultSecurityEventType.sms_verification_success);
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

  /**
   * Attempts to resolve user from phone number for security event logging.
   *
   * <p><b>Issue #1021:</b> This method is used to attach user information to authentication failure
   * security events. It returns null if the user cannot be resolved (e.g., user doesn't exist),
   * which is acceptable for logging purposes.
   *
   * @param tenant the tenant
   * @param phoneNumber the phone number from the challenge request
   * @param providerId the provider ID
   * @param userQueryRepository the user query repository
   * @return the resolved user, or null if not found
   */
  private User tryResolveUserForLogging(
      Tenant tenant,
      String phoneNumber,
      String providerId,
      UserQueryRepository userQueryRepository) {
    if (phoneNumber == null || phoneNumber.isEmpty()) {
      return null;
    }

    try {
      User user = userQueryRepository.findByPhone(tenant, phoneNumber, providerId);
      if (user.exists()) {
        log.debug(
            "User resolved for security event logging. phoneNumber={}, sub={}",
            phoneNumber,
            user.sub());
        return user;
      }
    } catch (Exception e) {
      log.debug("Failed to resolve user for security event logging. phoneNumber={}", phoneNumber);
    }

    return null;
  }
}
