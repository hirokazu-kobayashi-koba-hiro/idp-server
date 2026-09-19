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

package org.idp.server.core.openid.identity.contact;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.interaction.execution.ExternalRequestUserContextCreator;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.contact.execution.ContactExecutionRequest;
import org.idp.server.core.openid.identity.contact.execution.ContactExecutionResult;
import org.idp.server.core.openid.identity.contact.execution.ContactVerificationExecutor;
import org.idp.server.core.openid.identity.contact.execution.ContactVerificationExecutors;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Drives one contact code exchange through the tenant's existing authentication configuration
 * (Issue #1416).
 *
 * <p>The same pipeline {@code ExternalApiAuthenticationInteractor} runs: look up {@code
 * interactions.{key}}, pick the executor named by {@code execution.function}, hand it the request
 * material, and map the answer with {@code response.body_mapping_rules}. Nothing here asks whether
 * the tenant "delegates" — that question was the bug. {@code email_authentication_challenge} names
 * a locally generated code and {@code http_request} names an external exchange, and a function with
 * no executor is reported as the configuration error it is instead of reaching a null sender.
 *
 * <p>Timing ({@code expire_seconds}, {@code retry_count_limitation}, {@code
 * resend_cooldown_seconds}) is read straight off {@code execution.details}. Both channels spell
 * those keys identically, so there is nothing channel-specific to resolve, and a configuration that
 * omits them — every delegated one does — gets the stated default rather than zero.
 *
 * <p>Response mapping is applied to failures only. On the login path the mapped body <em>is</em>
 * the endpoint's response; here the endpoint has a fixed contract ({@code {id}}, then the updated
 * user), so letting tenant rules reshape a success would make a documented API tenant-dependent.
 * What a failure needs is the opposite — the external service's own reason for refusing — so a
 * mapped {@code error_description} is surfaced.
 */
public class ContactVerificationExchange {

  static final int DEFAULT_EXPIRE_SECONDS = 300;
  static final int DEFAULT_RETRY_COUNT_LIMITATION = 5;
  static final int DEFAULT_RESEND_COOLDOWN_SECONDS = 60;

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  ContactVerificationExecutors executors;
  LoggerWrapper log = LoggerWrapper.getLogger(ContactVerificationExchange.class);

  public ContactVerificationExchange(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      ContactVerificationExecutors executors) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.executors = executors;
  }

  /** Asks the configured executor to issue and deliver a code to {@code targetValue}. */
  public ContactExecutionResult challenge(
      Tenant tenant,
      User user,
      ContactVerificationOperation operation,
      String targetValue,
      RequestAttributes requestAttributes) {

    Map<String, Object> body = new HashMap<>();
    body.put(operation.channel().targetFieldName(), targetValue);
    body.put("template", operation.templateKey());
    body.put("operation", operation.value());

    String interactionKey = operation.channel().challengeInteractionKey();
    AuthenticationInteractionConfig interactionConfig =
        interactionConfig(tenant, operation, interactionKey);

    // Not optional, unlike the verify half: without it there is no sender, so nothing can be
    // delivered and there is no safe default to fall back to.
    if (interactionConfig == null) {
      return configurationError(
          String.format(
              "authentication configuration (%s) has no interaction (%s).",
              operation.channel().authenticationConfigType(), interactionKey));
    }

    return run(
        tenant,
        user,
        interactionKey,
        interactionConfig,
        ContactVerificationChallenge.notFound(),
        body,
        requestAttributes);
  }

  /** Asks the configured executor whether the submitted code is the right one. */
  public ContactExecutionResult verify(
      Tenant tenant,
      User user,
      ContactVerificationOperation operation,
      ContactVerificationChallenge challenge,
      String submittedCode,
      RequestAttributes requestAttributes) {

    Map<String, Object> body = new HashMap<>();
    body.put("verification_code", submittedCode);
    body.put("operation", operation.value());

    String interactionKey = operation.channel().verifyInteractionKey();
    AuthenticationInteractionConfig interactionConfig =
        interactionConfig(tenant, operation, interactionKey);

    // The verify interaction is optional. A locally generated code is decided against the challenge
    // row and needs nothing configured, so requiring the interaction would reject configurations
    // that describe only how a code is sent — which used to work. A delegated challenge that lands
    // here has no code on the row, so the comparison fails closed.
    if (interactionConfig == null) {
      ContactExecutionRequest request = executionRequest(user, body);
      return executors
          .get(operation.channel().localVerifyFunction())
          .execute(
              tenant, challenge, request, requestAttributes, new AuthenticationExecutionConfig());
    }

    return run(tenant, user, interactionKey, interactionConfig, challenge, body, requestAttributes);
  }

  public int expireSeconds(Tenant tenant, ContactVerificationOperation operation) {
    return optInt(challengeExecution(tenant, operation), "expire_seconds", DEFAULT_EXPIRE_SECONDS);
  }

  public int retryCountLimitation(Tenant tenant, ContactVerificationOperation operation) {
    return optInt(
        challengeExecution(tenant, operation),
        "retry_count_limitation",
        DEFAULT_RETRY_COUNT_LIMITATION);
  }

  public int resendCooldownSeconds(Tenant tenant, ContactVerificationOperation operation) {
    return optInt(
        challengeExecution(tenant, operation),
        "resend_cooldown_seconds",
        DEFAULT_RESEND_COOLDOWN_SECONDS);
  }

  private ContactExecutionResult run(
      Tenant tenant,
      User user,
      String interactionKey,
      AuthenticationInteractionConfig interactionConfig,
      ContactVerificationChallenge challenge,
      Map<String, Object> body,
      RequestAttributes requestAttributes) {

    AuthenticationExecutionConfig execution = interactionConfig.execution();
    if (!executors.contains(execution.function())) {
      return configurationError(
          String.format(
              "interaction (%s) declares execution function (%s), which cannot drive a contact verification.",
              interactionKey, execution.function()));
    }

    ContactExecutionRequest request = executionRequest(user, body);

    ContactVerificationExecutor executor = executors.get(execution.function());
    ContactExecutionResult result =
        executor.execute(tenant, challenge, request, requestAttributes, execution);

    if (result.isSuccess()) {
      return result;
    }

    return withMappedFailure(interactionConfig.response(), result, interactionKey);
  }

  /**
   * Replaces the failure contents with what the tenant's {@code response.body_mapping_rules}
   * produced, so a refusal reports the external service's own reason rather than a transport code.
   */
  private ContactExecutionResult withMappedFailure(
      AuthenticationResponseConfig responseConfig,
      ContactExecutionResult result,
      String interactionKey) {

    if (responseConfig == null || responseConfig.bodyMappingRules().isEmpty()) {
      log.warn(
          "Contact verification execution failed. interaction={}, status={}",
          interactionKey,
          result.statusCode());
      return result;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(result.contents());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> mapped =
        MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

    log.warn(
        "Contact verification execution failed. interaction={}, status={}, mapped={}",
        interactionKey,
        result.statusCode(),
        mapped);

    return ContactExecutionResult.error(result.statusCode(), mapped);
  }

  private ContactExecutionRequest executionRequest(User user, Map<String, Object> body) {
    ContactExecutionRequest request = new ContactExecutionRequest(body);
    request.setUser(ExternalRequestUserContextCreator.create(user));
    return request;
  }

  private ContactExecutionResult configurationError(String description) {
    log.error("Contact verification configuration error: {}", description);
    Map<String, Object> contents = new HashMap<>();
    contents.put("error", "server_error");
    contents.put("error_description", description);
    return ContactExecutionResult.error(500, contents);
  }

  private AuthenticationExecutionConfig challengeExecution(
      Tenant tenant, ContactVerificationOperation operation) {

    AuthenticationInteractionConfig interactionConfig =
        interactionConfig(tenant, operation, operation.channel().challengeInteractionKey());
    return interactionConfig == null ? null : interactionConfig.execution();
  }

  private AuthenticationInteractionConfig interactionConfig(
      Tenant tenant, ContactVerificationOperation operation, String key) {

    AuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, operation.channel().authenticationConfigType());
    return configuration.getAuthenticationConfig(key);
  }

  private int optInt(AuthenticationExecutionConfig execution, String key, int defaultValue) {
    if (execution == null) {
      return defaultValue;
    }
    Object value = execution.details().get(key);
    if (value instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    return defaultValue;
  }
}
