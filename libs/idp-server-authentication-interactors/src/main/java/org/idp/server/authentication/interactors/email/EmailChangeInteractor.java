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
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Self-service email-change verify (Issue #1416).
 *
 * <p>Reads the candidate address stored by {@link EmailChangeChallengeInteractor}, verifies the
 * one-time code through the same executor as login, and on success commits {@code email} + {@code
 * email_verified=true} to the authenticated user. The mutated {@link User} is persisted by the
 * caller's success branch (same as MFA registration).
 *
 * <p>Uniqueness is enforced here at commit time: if the candidate address is already used by a
 * different user in the tenant/provider, the change is rejected.
 */
public class EmailChangeInteractor implements AuthenticationInteractor {

  AuthenticationExecutors authenticationExecutors;
  AuthenticationInteractionQueryRepository interactionQueryRepository;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(EmailChangeInteractor.class);

  public EmailChangeInteractor(
      AuthenticationExecutors authenticationExecutors,
      AuthenticationInteractionQueryRepository interactionQueryRepository,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.authenticationExecutors = authenticationExecutors;
    this.interactionQueryRepository = interactionQueryRepository;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_CHANGE.toType();
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

    log.debug("EmailChangeInteractor called");

    if (!transaction.hasUser()) {
      return clientError(
          type, "email change requires an authenticated user, but none is established.");
    }

    EmailVerificationChallengeRequest challengeRequest =
        interactionQueryRepository.get(
            tenant,
            transaction.identifier(),
            "email-change-challenge-request",
            EmailVerificationChallengeRequest.class);
    String newEmail = challengeRequest.email();
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
      log.warn("Email change verification failed. status={}", executionResult.statusCode());
      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
          contents,
          type,
          operationType(),
          method(),
          transaction.user(),
          DefaultSecurityEventType.email_change_failure);
    }

    User user = transaction.user();
    user.setEmail(newEmail);
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
          "Email change rejected: preferred_username already in use. providerId={}", providerId);
      return clientError(type, "new_email is already in use.");
    }

    log.debug("Email change succeeded for user: {}", user.sub());

    Map<String, Object> responseContents = new HashMap<>(contents);
    responseContents.put("user", user.toMinimalizedMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        responseContents,
        DefaultSecurityEventType.email_change_success);
  }

  private AuthenticationInteractionRequestResult clientError(
      AuthenticationInteractionType type, String description) {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", description);
    return AuthenticationInteractionRequestResult.clientError(
        response, type, operationType(), method(), DefaultSecurityEventType.email_change_failure);
  }
}
