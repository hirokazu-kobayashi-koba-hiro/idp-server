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
 * operation} field determines which interaction configuration to use. Each operation maps to a
 * separate key in the {@code interactions} map of the authentication configuration.
 *
 * <p>The idp-server endpoint type is fixed ({@code external-api-authentication}), but each
 * operation can call a different external API endpoint with its own request schema, execution
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
 * <p><b>Request routing:</b> The {@code operation} field in the request body selects the
 * interaction config key. For example, {@code {"operation": "password_verify", "username": "...",
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

    String operation = request.optValueAsString("operation", "");
    if (operation.isEmpty()) {
      log.warn("External API authentication request missing 'operation' field");
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("operation", "");
      errorResponse.put("error", "invalid_request");
      errorResponse.put(
          "error_description", "The 'operation' field is required in the request body.");
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
        configuration.getAuthenticationConfig(operation);
    if (interactionConfig == null) {
      log.warn(
          "External API authentication operation not found. operation={}, tenant={}",
          operation,
          tenant.identifierValue());
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("operation", operation);
      errorResponse.put("error", "invalid_request");
      errorResponse.put(
          "error_description", String.format("The operation '%s' is not configured.", operation));
      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.external_api_authentication_failure);
    }

    SecurityEventType successEvent = toEventType(operation, "success");
    SecurityEventType failureEvent = toEventType(operation, "failure");

    // JSON Schema validation (Layer 2)
    JsonSchemaDefinition schemaDefinition = interactionConfig.request().requestSchemaAsDefinition();
    if (schemaDefinition.exists()) {
      JsonNodeWrapper requestNode = JsonNodeWrapper.fromMap(request.toMap());
      JsonSchemaValidator validator = new JsonSchemaValidator(schemaDefinition);
      JsonSchemaValidationResult validationResult = validator.validate(requestNode);

      if (!validationResult.isValid()) {
        log.warn(
            "External API authentication request validation failed. operation={}, error_count={}, errors={}",
            operation,
            validationResult.errors().size(),
            validationResult.errors());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("operation", operation);
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
          "External API authentication failed. operation={}, status={}, contents={}",
          operation,
          executionResult.statusCode(),
          executionResult.contents());
      contents.put("operation", operation);
      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.fromStatusCode(executionResult.statusCode()),
          type,
          operationType(),
          method(),
          null,
          contents,
          failureEvent);
    }

    // User resolution (only when user_resolve is configured for this operation)
    List<MappingRule> userMappingRules = interactionConfig.userResolve().userMappingRules();
    if (!userMappingRules.isEmpty()) {
      User user =
          resolveUser(tenant, request, executionResult, userMappingRules, userQueryRepository);

      user.applyIdentityPolicy(tenant.identityPolicyConfig());

      Map<String, Object> responseContents = new HashMap<>(contents);
      responseContents.put("operation", operation);
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
    contents.put("operation", operation);
    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        null,
        contents,
        successEvent);
  }

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
   * Creates a dynamic SecurityEventType with the operation name embedded.
   *
   * <p>Format: {@code external_api_{operation}_{result}} (e.g., {@code
   * external_api_password_verify_success})
   */
  private SecurityEventType toEventType(String operation, String result) {
    return new SecurityEventType("external_api_" + operation + "_" + result);
  }

  private User toUser(List<MappingRule> mappingRules, Map<String, Object> results) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(results);
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> executed = MappingRuleObjectMapper.execute(mappingRules, jsonPath);
    return jsonConverter.read(executed, User.class);
  }
}
