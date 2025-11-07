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

package org.idp.server.authentication.interactors.fido2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationInteractionConfig;
import org.idp.server.core.openid.authentication.config.AuthenticationResponseConfig;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionRequest;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutionResult;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutor;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
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

public class Fido2RegistrationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  AuthenticationExecutors authenticationExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(Fido2RegistrationInteractor.class);

  public Fido2RegistrationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository,
      AuthenticationExecutors authenticationExecutors) {
    this.configurationRepository = configurationRepository;
    this.authenticationExecutors = authenticationExecutors;
  }

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.FIDO2_REGISTRATION.toType();
  }

  @Override
  public String method() {
    return StandardAuthenticationMethod.FIDO2.type();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository) {

    log.debug("WebAuthnRegistrationInteractor called");

    AuthenticationConfiguration configuration = configurationRepository.get(tenant, "fido2");
    AuthenticationInteractionConfig authenticationInteractionConfig =
        configuration.getAuthenticationConfig("fido2-registration");
    AuthenticationExecutionConfig execution = authenticationInteractionConfig.execution();

    AuthenticationExecutor executor = authenticationExecutors.get(execution.function());

    AuthenticationExecutionRequest authenticationExecutionRequest =
        new AuthenticationExecutionRequest(request.toMap());
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

      log.warn("Fido2 registration is failed. Client error: {}", executionResult.contents());

      return AuthenticationInteractionRequestResult.clientError(
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido2_registration_failure);
    }

    if (executionResult.isServerError()) {

      log.warn("Fido2 registration is failed. Server error: {}", executionResult.contents());

      return AuthenticationInteractionRequestResult.serverError(
          contents,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido2_registration_failure);
    }

    User baseUser = transaction.user();
    // Handle reset action: remove existing FIDO-UAF devices before adding new one
    if (isRestAction(transaction)) {
      baseUser = baseUser.removeAllAuthenticationDevicesOfType("fido2");
    }

    DefaultSecurityEventType eventType =
        isRestAction(transaction)
            ? DefaultSecurityEventType.fido2_reset_success
            : DefaultSecurityEventType.fido2_registration_success;

    String deviceId = UUID.randomUUID().toString();
    String userId = resolveUserId(contents, configuration);
    log.info("fido2 registration success deviceId: {}, userId: {}", deviceId, userId);

    User addedDeviceUser =
        addAuthenticationDevice(baseUser, deviceId, userId, transaction.attributes());

    AuthenticationPolicy authenticationPolicy = transaction.authenticationPolicy();
    if (authenticationPolicy.authenticationDeviceRule().requiredIdentityVerification()) {
      addedDeviceUser.setStatus(UserStatus.IDENTITY_VERIFICATION_REQUIRED);
    }

    Map<String, Object> response = new HashMap<>(contents);
    response.put("device_id", deviceId);

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        transaction.user(),
        response,
        eventType);
  }

  private String resolveUserId(
      Map<String, Object> contents, AuthenticationConfiguration configuration) {

    Map<String, Object> metadata = configuration.metadata();
    Fido2MetadataConfig fido2MetadataConfig = new Fido2MetadataConfig(metadata);

    String userIdParam = fido2MetadataConfig.userIdParam();
    if (contents.containsKey(userIdParam)) {
      return contents.get(userIdParam).toString();
    }

    return "";
  }

  private boolean isRestAction(AuthenticationTransaction transaction) {
    return "reset".equals(transaction.attributes().getValueOrEmpty("action"));
  }

  private User addAuthenticationDevice(
      User user, String deviceId, String userId, AuthenticationTransactionAttributes attributes) {

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
            userId,
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
