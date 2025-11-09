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
import java.util.UUID;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.policy.AuthenticationStepDefinition;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
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
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
    AuthenticationExecutor executor = executors.get(execution.function());

    AuthenticationExecutionRequest executionRequest =
        new AuthenticationExecutionRequest(request.toMap());
    AuthenticationExecutionResult executionResult =
        executor.execute(
            tenant, transaction.identifier(), executionRequest, requestAttributes, execution);

    if (executionResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          executionResult.contents(),
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.sms_verification_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(),
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.sms_verification_failure);
    }

    SmslVerificationChallengeRequest smslVerificationChallengeRequest =
        interactionQueryRepository.get(
            tenant,
            transaction.identifier(),
            "sms-authentication-challenge-request",
            SmslVerificationChallengeRequest.class);

    String providerId = smslVerificationChallengeRequest.providerId();
    String phoneNumber = smslVerificationChallengeRequest.phoneNumber();
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

    Map<String, Object> contents = new HashMap<>();
    contents.put("user", verifiedUser.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        verifiedUser,
        contents,
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
    AuthenticationStepDefinition stepDefinition = getCurrentStepDefinition(transaction, method());
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
    User user = new User();
    String id = UUID.randomUUID().toString();
    user.setSub(id);
    user.setPhoneNumber(phoneNumber);

    return user;
  }
}
