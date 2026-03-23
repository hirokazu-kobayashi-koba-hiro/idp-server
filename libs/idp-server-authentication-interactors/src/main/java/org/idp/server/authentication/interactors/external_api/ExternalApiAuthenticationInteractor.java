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

package org.idp.server.authentication.interactors.external_api;

import java.util.*;
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
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.json.schema.JsonSchemaValidator;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * External API authentication interactor.
 *
 * <p>Provides a generic external API authentication mechanism where the request body's {@code
 * interaction} field determines which interaction configuration to use. Each interaction maps to a
 * separate key in the {@code interactions} map of the authentication configuration.
 *
 * <p>The idp-server endpoint type is fixed ({@code external-api-authentication}), but each
 * interaction can call a different external API endpoint with its own request schema, execution
 * config, user resolution rules, and response mapping.
 *
 * <p><b>Configuration structure:</b>
 *
 * <pre>{@code
 * {
 *   "type": "external-api-authentication",
 *   "interactions": {
 *     "password_verify": {
 *       "request": { "schema": { ... } },
 *       "execution": { "function": "http_request", "http_request": { ... } },
 *       "user_resolve": { "user_mapping_rules": [ ... ] },
 *       "response": { "body_mapping_rules": [ ... ] }
 *     },
 *     "risk_assessment": {
 *       "request": { "schema": { ... } },
 *       "execution": { "function": "http_request", "http_request": { ... } },
 *       "response": { "body_mapping_rules": [ ... ] }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p><b>Request routing:</b> The {@code interaction} field in the request body selects the
 * interaction config key. For example, {@code {"interaction": "password_verify", "username": "...",
 * "password": "..."}} routes to the {@code password_verify} interaction config.
 */
public class ExternalApiAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  AuthenticationExecutors authenticationExecutors;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(ExternalApiAuthenticationInteractor.class);

  public ExternalApiAuthenticationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationRepository = configurationRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return new AuthenticationInteractionType("external-api-authentication");
  }

  @Override
  public String method() {
    return "external-api";
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("ExternalApiAuthenticationInteractor called");

    String interaction = request.optValueAsString("interaction", "");
    if (interaction.isEmpty()) {
      log.warn("External API authentication request missing 'interaction' field");
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("interaction", "");
      errorResponse.put("error", "invalid_request");
      errorResponse.put(
          "error_description", "The 'interaction' field is required in the request body.");
      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.external_api_authentication_failure);
    }

    AuthenticationConfiguration configuration =
        configurationRepository.get(tenant, "external-api-authentication");

    AuthenticationInteractionConfig interactionConfig =
        configuration.getAuthenticationConfig(interaction);
    if (interactionConfig == null) {
      log.warn(
          "External API authentication interaction not found. interaction={}, tenant={}",
          interaction,
          tenant.identifierValue());
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("interaction", interaction);
      errorResponse.put("error", "invalid_request");
      errorResponse.put(
          "error_description",
          String.format("The interaction '%s' is not configured.", interaction));
      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.external_api_authentication_failure);
    }

    SecurityEventType successEvent = toEventType(interaction, "success");
    SecurityEventType failureEvent = toEventType(interaction, "failure");

    // JSON Schema validation (Layer 2)
    JsonSchemaDefinition schemaDefinition = interactionConfig.request().requestSchemaAsDefinition();
    if (schemaDefinition.exists()) {
      JsonNodeWrapper requestNode = JsonNodeWrapper.fromMap(request.toMap());
      JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
      JsonSchemaValidationResult validationResult = validator.validate(requestNode);

      if (!validationResult.isValid()) {
        log.warn(
            "External API authentication request validation failed. interaction={}, error_count={}, errors={}",
            interaction,
            validationResult.errors().size(),
            validationResult.errors());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("interaction", interaction);
        errorResponse.put("error", "invalid_request");
        errorResponse.put(
            "error_description", "The authentication request is invalid. Please check your input.");
        errorResponse.put("error_messages", validationResult.errors());

        return new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.CLIENT_ERROR,
            type,
            operationType(),
            method(),
            null,
            errorResponse,
            failureEvent);
      }
    }

    // Execute external API call
    AuthenticationExecutionConfig executionConfig = interactionConfig.execution();
    AuthenticationExecutor executor = authenticationExecutors.get(executionConfig.function());
    AuthenticationExecutionRequest executionRequest =
        new AuthenticationExecutionRequest(request.toMap());
    AuthenticationExecutionResult executionResult =
        executor.execute(
            tenant, transaction.identifier(), executionRequest, requestAttributes, executionConfig);

    // Response mapping
    AuthenticationResponseConfig responseConfig = interactionConfig.response();
    Map<String, Object> contents;
    if (responseConfig.bodyMappingRules().isEmpty()) {
      contents = executionResult.contents();
    } else {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
      JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
      contents =
          MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);
    }

    if (!executionResult.isSuccess()) {
      log.warn(
          "External API authentication failed. interaction={}, status={}, contents={}",
          interaction,
          executionResult.statusCode(),
          executionResult.contents());
      contents.put("interaction", interaction);
      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.fromStatusCode(executionResult.statusCode()),
          type,
          operationType(),
          method(),
          null,
          contents,
          failureEvent);
    }

    // User resolution (only when user_resolve is configured for this interaction)
    List<MappingRule> userMappingRules = interactionConfig.userResolve().userMappingRules();
    if (!userMappingRules.isEmpty()) {

      // 2nd factor: verify external API user matches authenticated user
      AuthenticationStepDefinition stepDefinition = transaction.getCurrentStepDefinition(method());
      if (stepDefinition != null && stepDefinition.requiresUser()) {
        return handleSecondFactor(
            tenant,
            transaction,
            request,
            executionResult,
            userMappingRules,
            interaction,
            type,
            contents,
            successEvent,
            failureEvent);
      }

      // 1st factor: resolve user from external API response
      User user =
          resolveUser(tenant, request, executionResult, userMappingRules, userQueryRepository);

      if (!user.exists()) {
        log.warn("User resolution failed. interaction={}, method={}", interaction, method());
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("interaction", interaction);
        errorResponse.put("error", "user_not_found");
        errorResponse.put("error_description", "User not found.");
        return new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.CLIENT_ERROR,
            type,
            operationType(),
            method(),
            null,
            errorResponse,
            failureEvent);
      }

      user.applyIdentityPolicy(tenant.identityPolicyConfig());

      Map<String, Object> responseContents = new HashMap<>(contents);
      responseContents.put("interaction", interaction);
      responseContents.put("user", user.toMinimalizedMap());

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          operationType(),
          method(),
          user,
          responseContents,
          successEvent);
    }

    // No user resolution - return execution result as-is
    contents.put("interaction", interaction);
    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        null,
        contents,
        successEvent);
  }

  /**
   * Handles 2nd factor authentication with user identity verification.
   *
   * <p>SECURITY: Verifies that the external API response user matches the authenticated transaction
   * user. Returns distinct error codes for each failure case:
   *
   * <ul>
   *   <li>{@code user_not_found}: No authenticated user in transaction (1st factor skipped)
   *   <li>{@code user_identity_mismatch}: External API returned a different user
   * </ul>
   */
  private AuthenticationInteractionRequestResult handleSecondFactor(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionRequest request,
      AuthenticationExecutionResult executionResult,
      List<MappingRule> userMappingRules,
      String interaction,
      AuthenticationInteractionType type,
      Map<String, Object> contents,
      SecurityEventType successEvent,
      SecurityEventType failureEvent) {

    if (!transaction.hasUser()) {
      log.warn(
          "2nd factor requires authenticated user but transaction has no user. method={}",
          method());
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("interaction", interaction);
      errorResponse.put("error", "user_not_found");
      errorResponse.put(
          "error_description", "2nd factor requires an authenticated user from the previous step.");
      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          operationType(),
          method(),
          null,
          errorResponse,
          failureEvent);
    }

    User transactionUser = transaction.user();

    // Verify external API response matches the authenticated user
    Map<String, Object> mappingSource = new HashMap<>();
    mappingSource.put("request_body", request.toMap());
    mappingSource.putAll(executionResult.contents());
    User externalUser = toUser(userMappingRules, mappingSource);

    if (!matchesTransactionUser(transactionUser, externalUser)) {
      log.warn(
          "2nd factor user identity mismatch. transaction_sub={}, external_email={}, external_user_id={}",
          transactionUser.sub(),
          externalUser.email(),
          externalUser.externalUserId());
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("interaction", interaction);
      errorResponse.put("error", "user_identity_mismatch");
      errorResponse.put(
          "error_description",
          "The external API returned a different user than the authenticated user.");
      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          operationType(),
          method(),
          transactionUser,
          errorResponse,
          failureEvent);
    }

    log.debug(
        "2nd factor: user identity verified. method={}, sub={}", method(), transactionUser.sub());

    transactionUser.applyIdentityPolicy(tenant.identityPolicyConfig());

    Map<String, Object> responseContents = new HashMap<>(contents);
    responseContents.put("interaction", interaction);
    responseContents.put("user", transactionUser.toMinimalizedMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        transactionUser,
        responseContents,
        successEvent);
  }

  /** Resolves user from external API response (1st factor only). */
  private User resolveUser(
      Tenant tenant,
      AuthenticationInteractionRequest request,
      AuthenticationExecutionResult executionResult,
      List<MappingRule> userMappingRules,
      UserQueryRepository userQueryRepository) {

    Map<String, Object> mappingSource = new HashMap<>();
    mappingSource.put("request_body", request.toMap());
    mappingSource.putAll(executionResult.contents());

    User user = toUser(userMappingRules, mappingSource);

    User existingUser =
        userQueryRepository.findByProvider(tenant, user.providerId(), user.externalUserId());

    if (existingUser.exists()) {
      log.debug(
          "Existing user found. providerId={}, externalUserId={}, sub={}",
          user.providerId(),
          user.externalUserId(),
          existingUser.sub());
      user.setSub(existingUser.sub());
      user.setStatus(existingUser.status());
    } else {
      log.debug(
          "New user from external API. providerId={}, externalUserId={}",
          user.providerId(),
          user.externalUserId());
      user.setSub(UUID.randomUUID().toString());
      if (!user.hasStatus()) {
        user.setStatus(UserStatus.INITIALIZED);
      }
    }

    return user;
  }

  /**
   * Creates a dynamic SecurityEventType with the interaction name embedded.
   *
   * <p>Format: {@code external_api_{interaction}_{result}} (e.g., {@code
   * external_api_password_verify_success})
   */
  private SecurityEventType toEventType(String interaction, String result) {
    return new SecurityEventType("external_api_" + interaction + "_" + result);
  }

  /**
   * Verifies that the external API response user matches the authenticated transaction user.
   *
   * <p>Checks email or externalUserId match to prevent 2nd factor identity swap attacks. At least
   * one non-empty field must match.
   */
  private boolean matchesTransactionUser(User transactionUser, User externalUser) {
    String txEmail = transactionUser.email();
    String extEmail = externalUser.email();
    if (txEmail != null && !txEmail.isEmpty() && extEmail != null && !extEmail.isEmpty()) {
      return txEmail.equals(extEmail);
    }

    String txExtId = transactionUser.externalUserId();
    String extExtId = externalUser.externalUserId();
    if (txExtId != null && !txExtId.isEmpty() && extExtId != null && !extExtId.isEmpty()) {
      return txExtId.equals(extExtId);
    }

    // No comparable fields → cannot verify identity
    log.warn(
        "Cannot verify 2nd factor user identity: no comparable fields. transaction_email={}, external_email={}, transaction_ext_id={}, external_ext_id={}",
        txEmail,
        extEmail,
        txExtId,
        extExtId);
    return false;
  }

  private User toUser(List<MappingRule> mappingRules, Map<String, Object> results) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(results);
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> executed = MappingRuleObjectMapper.execute(mappingRules, jsonPath);
    return jsonConverter.read(executed, User.class);
  }
}
