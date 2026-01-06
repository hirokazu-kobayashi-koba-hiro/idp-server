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

package org.idp.server.authentication.interactors.fidouaf;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
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
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class FidoUafAuthenticationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationExecutors authenticationExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionCommandRepository authenticationInteractionCommandRepository;
  FidoUafAdditionalRequestResolvers additionalRequestResolvers;
  LoggerWrapper log = LoggerWrapper.getLogger(FidoUafAuthenticationChallengeInteractor.class);

  public FidoUafAuthenticationChallengeInteractor(
      AuthenticationExecutors authenticationExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionCommandRepository authenticationInteractionCommandRepository,
      FidoUafAdditionalRequestResolvers additionalRequestResolvers) {
    this.authenticationExecutors = authenticationExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
    this.authenticationInteractionCommandRepository = authenticationInteractionCommandRepository;
    this.additionalRequestResolvers = additionalRequestResolvers;
  }

  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.FIDO_UAF_AUTHENTICATION_CHALLENGE.toType();
  }

  @Override
  public boolean isBrowserBased() {
    return false;
  }

  @Override
  public OperationType operationType() {
    return OperationType.CHALLENGE;
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.FIDO_UAF.type();
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
      log.debug("FidoUafAuthenticationChallengeInteractor called");

      AuthenticationConfiguration configuration =
          configurationQueryRepository.get(tenant, "fido-uaf");
      AuthenticationInteractionConfig authenticationInteractionConfig =
          configuration.getAuthenticationConfig("fido-uaf-authentication-challenge");
      AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();
      AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

      String deviceId = extractDeviceId(transaction, request, requestAttributes);

      // request
      if (deviceId == null || deviceId.isEmpty()) {

        log.warn("FIDO-UAF authentication challenge failed. device_id is required.");

        Map<String, Object> contents = new HashMap<>();
        contents.put("error", "invalid_request");
        contents.put("error_description", "device_id is required.");

        return AuthenticationInteractionRequestResult.clientError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.fido_uaf_authentication_challenge_failure);
      }

      FidoUafInteraction fidoUafInteraction = new FidoUafInteraction(deviceId);
      authenticationInteractionCommandRepository.register(
          tenant,
          transaction.identifier(),
          "fido-uaf-authentication-challenge",
          fidoUafInteraction);

      // TODO pre_hook
      Map<String, Object> executionRequest = new HashMap<>();
      executionRequest.put("device_id", deviceId);

      Map<String, Object> additionalRequests =
          additionalRequestResolvers.resolveAll(tenant, type, request, transaction);
      executionRequest.putAll(additionalRequests);

      AuthenticationExecutionRequest authenticationExecutionRequest =
          new AuthenticationExecutionRequest(executionRequest);

      // execution
      AuthenticationExecutionResult executionResult =
          executor.execute(
              tenant,
              transaction.identifier(),
              authenticationExecutionRequest,
              requestAttributes,
              execution);

      AuthenticationResponseConfig responseConfig = authenticationInteractionConfig.response();
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(executionResult.contents());
      JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
      Map<String, Object> contents =
          MappingRuleObjectMapper.execute(responseConfig.bodyMappingRules(), jsonPathWrapper);

      if (executionResult.isClientError()) {

        log.warn(
            "FIDO-UAF authentication challenge failed. Client error: {}",
            executionResult.contents());

        return AuthenticationInteractionRequestResult.clientError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.fido_uaf_authentication_challenge_failure);
      }

      if (executionResult.isServerError()) {

        log.warn(
            "FIDO-UAF authentication challenge failed. Server error: {}",
            executionResult.contents());

        return AuthenticationInteractionRequestResult.serverError(
            contents,
            type,
            operationType(),
            method(),
            DefaultSecurityEventType.fido_uaf_authentication_challenge_failure);
      }

      log.debug("FIDO-UAF authentication challenge succeeded for device: {}", deviceId);

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          operationType(),
          method(),
          transaction.user(),
          contents,
          DefaultSecurityEventType.fido_uaf_authentication_challenge_success);
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
          DefaultSecurityEventType.fido_uaf_authentication_challenge_failure);
    }
  }

  private String extractDeviceId(
      AuthenticationTransaction transaction,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    if (request.containsKey("device_id")) {
      String deviceId = request.getValueAsString("device_id");
      log.debug("Device ID extracted from request: {}", deviceId);
      return deviceId;
    }

    if (requestAttributes.containsKey("x-device-id")) {
      String deviceId = requestAttributes.getValueOrEmptyAsString("x-device-id");
      log.debug("Device ID extracted from request attributes (x-device-id): {}", deviceId);
      return deviceId;
    }

    if (transaction.hasAuthenticationDevice()) {
      AuthenticationDevice authenticationDevice = transaction.authenticationDevice();
      String deviceId = authenticationDevice.id();
      log.debug("Device ID extracted from transaction authentication device: {}", deviceId);
      return deviceId;
    }

    log.debug("Device ID not found in any source (request, attributes, transaction)");
    return "";
  }
}
