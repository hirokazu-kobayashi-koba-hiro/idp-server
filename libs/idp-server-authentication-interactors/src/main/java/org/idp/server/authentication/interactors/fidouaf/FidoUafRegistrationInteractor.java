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
import java.util.List;
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
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

public class FidoUafRegistrationInteractor implements AuthenticationInteractor {

  AuthenticationExecutors authenticationExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository;
  FidoUafAdditionalRequestResolvers additionalRequestResolvers;
  LoggerWrapper log = LoggerWrapper.getLogger(FidoUafRegistrationInteractor.class);

  public FidoUafRegistrationInteractor(
      AuthenticationExecutors authenticationExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository,
      FidoUafAdditionalRequestResolvers additionalRequestResolvers) {
    this.authenticationExecutors = authenticationExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
    this.authenticationInteractionQueryRepository = authenticationInteractionQueryRepository;
    this.additionalRequestResolvers = additionalRequestResolvers;
  }

  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.FIDO_UAF_REGISTRATION.toType();
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

    log.debug("FidoUafRegistrationInteractor called");

    AuthenticationConfiguration configuration =
        configurationQueryRepository.get(tenant, "fido-uaf");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("fido-uaf-registration");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();

    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    Map<String, Object> executionRequest = new HashMap<>(request.toMap());
    Map<String, Object> additionalRequests =
        additionalRequestResolvers.resolveAll(tenant, type, request, transaction);
    executionRequest.putAll(additionalRequests);

    AuthenticationExecutionRequest authenticationExecutionRequest =
        new AuthenticationExecutionRequest(executionRequest);
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

      log.warn("FIDO-UAF registration failed. Client error: {}", executionResult.contents());

      return AuthenticationInteractionRequestResult.clientError(
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_registration_failure);
    }

    if (executionResult.isServerError()) {

      log.warn("FIDO-UAF registration failed. Server error: {}", executionResult.contents());

      return AuthenticationInteractionRequestResult.serverError(
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_registration_failure);
    }

    FidoUafInteraction interaction =
        authenticationInteractionQueryRepository.get(
            tenant,
            transaction.identifier(),
            "fido-uaf-registration-challenge",
            FidoUafInteraction.class);
    String deviceId = interaction.deviceId();

    User baseUser = transaction.user();
    // Handle reset action: remove existing FIDO-UAF devices before adding new one
    if (isRestAction(transaction)) {
      baseUser = baseUser.removeAllAuthenticationDevicesOfType("fido-uaf");
    }

    DefaultSecurityEventType eventType =
        isRestAction(transaction)
            ? DefaultSecurityEventType.fido_uaf_reset_success
            : DefaultSecurityEventType.fido_uaf_registration_success;

    User addedDeviceUser = addAuthenticationDevice(baseUser, deviceId, transaction.attributes());

    if (tenant.requiresIdentityVerificationForDeviceRegistration()) {
      addedDeviceUser.setStatus(UserStatus.IDENTITY_VERIFICATION_REQUIRED);
    }

    log.debug(
        "FIDO-UAF registration succeeded for user: {}, device: {}",
        addedDeviceUser.sub(),
        deviceId);

    Map<String, Object> responseContents = new HashMap<>(contents);
    responseContents.put("device_id", deviceId);

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        addedDeviceUser,
        responseContents,
        eventType);
  }

  private boolean isRestAction(AuthenticationTransaction transaction) {
    return "reset".equals(transaction.attributes().getValueOrEmpty("action"));
  }

  private User addAuthenticationDevice(
      User user, String deviceId, AuthenticationTransactionAttributes attributes) {

    String appName = attributes.getValueOrEmpty("app_name");
    String platform = attributes.getValueOrEmpty("platform");
    String os = attributes.getValueOrEmpty("os");
    String model = attributes.getValueOrEmpty("model");
    String locale = attributes.getValueOrEmpty("locale");
    String notificationChannel = attributes.getValueOrEmpty("notification_channel");
    String notificationToken = attributes.getValueOrEmpty("notification_token");
    List<String> availableAuthenticationMethods = List.of(method());
    int priority =
        attributes.containsKey("priority")
            ? attributes.getValueAsInteger("priority")
            : user.authenticationDeviceNextCount();

    AuthenticationDevice authenticationDevice =
        new AuthenticationDevice(
            deviceId,
            appName,
            platform,
            os,
            model,
            locale,
            notificationChannel,
            notificationToken,
            availableAuthenticationMethods,
            priority);

    return user.addAuthenticationDevice(authenticationDevice);
  }
}
