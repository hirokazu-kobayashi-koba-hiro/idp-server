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
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserVerifier;
import org.idp.server.core.openid.identity.exception.UserDuplicateException;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Self-service email-change verify (Issue #1416).
 *
 * <p>Reads the target address stored by {@link EmailConfirmChallengeInteractor}, verifies the
 * one-time code through the same executor as login, and on success commits {@code email} + {@code
 * email_verified=true} to the authenticated user. The mutated {@link User} is persisted by the
 * caller's success branch (same as MFA registration).
 *
 * <p>Serves both the {@code email-verify} and {@code email-change} flows: the commit is identical,
 * and for a verification the stored address simply equals the one already on the account, so only
 * {@code email_verified} actually changes. Which operation it is comes from the transaction's flow
 * — see {@link EmailConfirmOperation} for why that is not inferred from comparing addresses.
 *
 * <p>Uniqueness is enforced here at commit time: if the target address is already used by a
 * different user in the tenant/provider, the commit is rejected.
 *
 * <p><b>Restricted to those two flows</b>, for the reason spelled out on {@link
 * EmailConfirmChallengeInteractor}: this interactor commits an identity attribute, and the generic
 * {@code POST /{tenant}/v1/authorizations|authentications/{id}/{interaction-type}} endpoints are
 * unauthenticated.
 */
public class EmailConfirmInteractor implements AuthenticationInteractor {

  AuthenticationExecutors authenticationExecutors;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(EmailConfirmInteractor.class);

  public EmailConfirmInteractor(
      AuthenticationExecutors authenticationExecutors,
      AuthenticationInteractionQueryRepository interactionQueryRepository,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.authenticationExecutors = authenticationExecutors;
    this.interactionQueryRepository = interactionQueryRepository;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_CONFIRM.toType();
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

    log.debug("EmailConfirmInteractor called");

    EmailConfirmOperation operation = EmailConfirmOperation.of(transaction.flow());
    if (operation == null) {
      log.warn("Email confirm verify rejected: transaction flow is {}.", transaction.flow().name());
      return clientError(
          type,
          EmailConfirmOperation.CHANGE,
          "email confirmation is not allowed for this transaction.");
    }

    if (!transaction.hasUser()) {
      return clientError(
          type,
          operation,
          "email confirm requires an authenticated user, but none is established.");
    }

    EmailVerificationChallengeRequest challengeRequest =
        interactionQueryRepository.get(
            tenant,
            transaction.identifier(),
            "email-confirm-challenge-request",
            EmailVerificationChallengeRequest.class);
    String targetEmail = challengeRequest.email();
    String providerId = challengeRequest.providerId();

    AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "email");
    AuthenticationInteractionConfig authenticationConfig =
        configuration.getAuthenticationConfig("email-authentication");
    AuthenticationExecutionConfig execution = authenticationConfig.execution();
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

    if (!executionResult.isSuccess()) {
      log.warn("Email confirm verification failed. status={}", executionResult.statusCode());
      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
          contents,
          type,
          operationType(),
          method(),
          transaction.user(),
          operation.failureEvent());
    }

    User user = transaction.user();
    user.setEmail(targetEmail);
    user.setEmailVerified(true);
    // Recompute preferred_username: when the tenant identity policy keys on email, the login
    // identifier must track the new email (same as UserRegistrator does on update).
    user.applyIdentityPolicy(tenant.identityPolicyConfig());

    // Friendly uniqueness pre-check on preferred_username (per tenant/provider, excluding self).
    // The DB constraint uk_preferred_username(tenant_id, provider_id, preferred_username) is the
    // authoritative race guard — matching the registration path's guarantees.
    try {
      new UserVerifier(userQueryRepository).verify(tenant, user);
    } catch (UserDuplicateException e) {
      log.warn(
          "Email confirm rejected: preferred_username already in use. providerId={}", providerId);
      return clientError(type, operation, "new_email is already in use.");
    }

    log.debug("Email confirm succeeded for user: {}", user.sub());

    Map<String, Object> responseContents = new HashMap<>(contents);
    responseContents.put("user", user.toMinimalizedMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        responseContents,
        operation.successEvent());
  }

  private AuthenticationInteractionRequestResult clientError(
      AuthenticationInteractionType type, EmailConfirmOperation operation, String description) {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", description);
    return AuthenticationInteractionRequestResult.clientError(
        response, type, operationType(), method(), operation.failureEvent());
  }
}
