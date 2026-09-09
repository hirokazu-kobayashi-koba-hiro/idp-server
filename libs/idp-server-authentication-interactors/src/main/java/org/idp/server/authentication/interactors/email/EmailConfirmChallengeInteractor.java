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
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.Pairs;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Challenge step of the self-service email verification / change flows (Issue #1416).
 *
 * <p>One interactor serves both operations because the commit logic is identical; what differs is
 * the <b>target address</b>, and that is decided by the transaction's auth flow, never by the
 * request body:
 *
 * <ul>
 *   <li>{@code email-verify} — the code goes to the address already on the account ({@code
 *       user.email()}). The request body is ignored entirely, so this half cannot be pointed at an
 *       attacker-chosen address at all.
 *   <li>{@code email-change} — the code goes to the request-supplied {@code new_email}, after
 *       validation. This is the only path that accepts a caller-supplied recipient.
 * </ul>
 *
 * <p><b>Why the flow, not an address comparison.</b> The two operations carry different privilege:
 * verification only sets a claim, whereas a change moves {@code preferred_username} — the login
 * identifier — under an EMAIL identity policy. Authorization therefore has to be decided before the
 * code is sent, which is impossible if the intent is inferred from comparing {@code new_email} to
 * the current address. Splitting the flows lets each endpoint carry its own scope requirement.
 *
 * <p><b>Restricted to those two flows.</b> Every interactor is registered globally and is reachable
 * through the unauthenticated {@code POST
 * /{tenant-id}/v1/authorizations|authentications/{id}/{type}} endpoints on <i>any</i> transaction,
 * where {@code hasUser()} only means "identified" — a {@code login_hint} on the authorization
 * endpoint fills it with no client authentication at all. The flow guard below is what keeps a
 * login / CIBA transaction out of this code path, the same concern that makes {@link
 * EmailAuthenticationChallengeInteractor#resolveEmail} ignore request input for an established
 * user.
 *
 * <p>Uniqueness is checked at commit time (verify), not here.
 */
public class EmailConfirmChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationExecutors authenticationExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(EmailConfirmChallengeInteractor.class);

  public EmailConfirmChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.interactionCommandRepository = interactionCommandRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_CONFIRM_CHALLENGE.toType();
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

    log.debug("EmailConfirmChallengeInteractor called");

    EmailConfirmOperation operation = EmailConfirmOperation.of(transaction.flow());
    if (operation == null) {
      log.warn(
          "Email confirm challenge rejected: transaction flow is {}.", transaction.flow().name());
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

    User user = transaction.user();
    Pairs<String, AuthenticationInteractionRequestResult> target =
        resolveTarget(type, operation, user, request);
    if (target.getRight() != null) {
      return target.getRight();
    }
    String targetEmail = target.getLeft();

    AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "email");
    AuthenticationInteractionConfig authenticationConfig =
        configuration.getAuthenticationConfig("email-authentication-challenge");
    AuthenticationExecutionConfig execution = authenticationConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    Map<String, Object> executionRequestValues = new HashMap<>(request.toMap());
    executionRequestValues.put("email", targetEmail);
    // Force the operation-specific template so the code email reads as a change / a verification
    // (not a login OTP), regardless of what the caller passed.
    executionRequestValues.put("template", operation.templateKey());
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

    if (!executionResult.isSuccess()) {
      log.warn(
          "Email confirm challenge execution failed. status={}, contents={}",
          executionResult.statusCode(),
          contents);
      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
          contents,
          type,
          operationType(),
          method(),
          user,
          operation.requestFailureEvent());
    }

    EmailVerificationChallengeRequest challengeRequest =
        new EmailVerificationChallengeRequest(user.providerId(), targetEmail);
    interactionCommandRepository.register(
        tenant, transaction.identifier(), "email-confirm-challenge-request", challengeRequest);

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        contents,
        operation.requestSuccessEvent());
  }

  /**
   * Resolves where the code is sent. Returns the address on the left, or a client error on the
   * right.
   */
  private Pairs<String, AuthenticationInteractionRequestResult> resolveTarget(
      AuthenticationInteractionType type,
      EmailConfirmOperation operation,
      User user,
      AuthenticationInteractionRequest request) {

    if (operation.isVerify()) {
      // Deliberately ignores the request body: the verification half has no caller-supplied
      // recipient, so it cannot be redirected.
      if (!user.hasEmail()) {
        return Pairs.of(
            null, clientError(type, operation, "the account has no email address to verify."));
      }
      return Pairs.of(user.email(), null);
    }

    JsonSchemaValidationResult validationResult =
        new EmailChangeRequestValidator(request).validate();
    if (!validationResult.isValid()) {
      return Pairs.of(
          null,
          clientError(
              type, operation, "new_email is unspecified or invalid format.", validationResult));
    }

    String newEmail = request.optValueAsString("new_email", "");
    // Case-sensitive on purpose: email is stored as-is and preferred_username uniqueness is
    // case-sensitive, so a case-only difference is a real change.
    if (newEmail.equals(user.email())) {
      return Pairs.of(
          null,
          clientError(
              type,
              operation,
              "new_email is the current address. use the email verification endpoint instead."));
    }
    return Pairs.of(newEmail, null);
  }

  private AuthenticationInteractionRequestResult clientError(
      AuthenticationInteractionType type, EmailConfirmOperation operation, String description) {
    return clientError(type, operation, description, null);
  }

  private AuthenticationInteractionRequestResult clientError(
      AuthenticationInteractionType type,
      EmailConfirmOperation operation,
      String description,
      JsonSchemaValidationResult validationResult) {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", description);
    if (validationResult != null) {
      response.put("error_messages", validationResult.errors());
    }
    return AuthenticationInteractionRequestResult.clientError(
        response, type, operationType(), method(), operation.requestFailureEvent());
  }
}
