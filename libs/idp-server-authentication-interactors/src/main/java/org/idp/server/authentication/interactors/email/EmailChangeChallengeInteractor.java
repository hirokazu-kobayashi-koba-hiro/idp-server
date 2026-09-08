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
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Self-service email-change challenge (Issue #1416).
 *
 * <p>Unlike {@link EmailAuthenticationChallengeInteractor}, whose {@code resolveEmail} deliberately
 * ignores request input for an established user (identifier-switching hardening), this interactor
 * always sends the verification code to the <b>request-supplied candidate address</b> — that is the
 * point of a change flow. It stores the candidate under a change-specific key so the {@link
 * EmailChangeInteractor} verify step can commit it.
 *
 * <p><b>Restricted to the {@code email-change} auth flow.</b> Dropping the identifier-switching
 * hardening is only safe on a transaction minted by the token-authenticated {@code
 * /v1/me/email/confirm} entry. Every interactor is registered globally and is therefore reachable
 * through the unauthenticated {@code POST /{tenant}/v1/authentications/{id}/{interaction-type}}
 * endpoint on <i>any</i> transaction, where {@code hasUser()} only means "identified" — a {@code
 * login_hint} on the authorization endpoint fills it with no client authentication at all. The flow
 * guard below is what keeps a login / CIBA transaction out of this code path.
 *
 * <p>The authoritative uniqueness check happens at commit time (verify), not here; the challenge
 * only rejects a malformed or empty candidate. A candidate equal to the current address is a
 * supported case (re-verification), not an error.
 */
public class EmailChangeChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionCommandRepository interactionCommandRepository;
  AuthenticationExecutors authenticationExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(EmailChangeChallengeInteractor.class);

  public EmailChangeChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.interactionCommandRepository = interactionCommandRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_CHANGE_CHALLENGE.toType();
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

    log.debug("EmailChangeChallengeInteractor called");

    // Only a transaction created by the token-authenticated /v1/me/email/confirm entry may drive an
    // identifier change. Without this the unauthenticated /v1/authentications/{id}/{type} endpoint
    // would reach this interactor on a login / CIBA transaction. See the class javadoc.
    if (!StandardAuthFlow.EMAIL_CHANGE.toAuthFlow().equals(transaction.flow())) {
      log.warn(
          "Email change challenge rejected: transaction flow is {}, expected {}.",
          transaction.flow().name(),
          StandardAuthFlow.EMAIL_CHANGE.value());
      return clientError(type, "email change is not allowed for this transaction.");
    }

    if (!transaction.hasUser()) {
      return clientError(
          type, "email change requires an authenticated user, but none is established.");
    }

    // Every client error below is raised before the change / verify intent is known (a malformed
    // candidate can never be a re-verification of the current address), so they all report as
    // email_change_request_failure. The split kicks in from the executor result onwards.
    JsonSchemaValidationResult validationResult =
        new EmailChangeRequestValidator(request).validate();
    if (!validationResult.isValid()) {
      return clientError(type, "new_email is unspecified or invalid format.", validationResult);
    }

    String newEmail = request.optValueAsString("new_email", "");

    User user = transaction.user();
    // Same address = re-verify the current email; different = change. The commit logic is
    // identical;
    // only the template and audit event differ (see isChange). Case-sensitive on purpose: email is
    // stored as-is and preferred_username uniqueness is case-sensitive, so a case change is a
    // change.
    boolean isChange = !newEmail.equals(user.email());
    String providerId = user.providerId();

    AuthenticationConfiguration configuration = configurationQueryRepository.get(tenant, "email");
    AuthenticationInteractionConfig authenticationConfig =
        configuration.getAuthenticationConfig("email-authentication-challenge");
    AuthenticationExecutionConfig execution = authenticationConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    Map<String, Object> executionRequestValues = new HashMap<>(request.toMap());
    executionRequestValues.put("email", newEmail);
    // Force the operation-specific template so the code email reads as an email change /
    // verification
    // (not a login OTP), regardless of what the caller passed.
    executionRequestValues.put("template", isChange ? "email_change" : "email_verify");
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
          "Email change challenge execution failed. status={}, contents={}",
          executionResult.statusCode(),
          contents);
      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
          contents,
          type,
          operationType(),
          method(),
          user,
          isChange
              ? DefaultSecurityEventType.email_change_request_failure
              : DefaultSecurityEventType.email_verify_request_failure);
    }

    EmailVerificationChallengeRequest challengeRequest =
        new EmailVerificationChallengeRequest(providerId, newEmail);
    interactionCommandRepository.register(
        tenant, transaction.identifier(), "email-change-challenge-request", challengeRequest);

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        user,
        contents,
        isChange
            ? DefaultSecurityEventType.email_change_request_success
            : DefaultSecurityEventType.email_verify_request_success);
  }

  private AuthenticationInteractionRequestResult clientError(
      AuthenticationInteractionType type, String description) {
    return clientError(type, description, null);
  }

  private AuthenticationInteractionRequestResult clientError(
      AuthenticationInteractionType type,
      String description,
      JsonSchemaValidationResult validationResult) {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", description);
    if (validationResult != null) {
      response.put("error_messages", validationResult.errors());
    }
    return AuthenticationInteractionRequestResult.clientError(
        response,
        type,
        operationType(),
        method(),
        DefaultSecurityEventType.email_change_request_failure);
  }
}
