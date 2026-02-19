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
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.device.DeviceSecretIssuer;
import org.idp.server.core.openid.identity.device.DeviceSecretIssuer.DeviceSecretIssuanceResult;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.AuthenticationDeviceRule;
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
  public boolean isBrowserBased() {
    return false;
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

    // Verify user is authenticated
    if (!transaction.hasUser()) {
      log.warn(
          "FIDO-UAF registration rejected: no authenticated user in transaction, tenant={}",
          tenant.identifier().value());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "unauthorized");
      errorResponse.put(
          "error_description", "User must be authenticated before registering a FIDO-UAF device.");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          DefaultSecurityEventType.fido_uaf_registration_failure);
    }

    // Verify device registration ACR/MFA requirements
    if (!isDeviceRegistrationPolicyMet(tenant, transaction)) {
      log.warn(
          "FIDO-UAF device registration policy check failed: user={}, tenant={}",
          transaction.user().sub(),
          tenant.identifier().value());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "forbidden");
      errorResponse.put(
          "error_description",
          "Current authentication level does not meet device registration requirements. "
              + "Please complete required authentication steps (e.g., MFA or existing device authentication).");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          transaction.user(),
          DefaultSecurityEventType.fido_uaf_registration_failure);
    }

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

    if (!executionResult.isSuccess()) {

      log.warn(
          "FIDO-UAF registration failed. status={}, contents={}",
          executionResult.statusCode(),
          executionResult.contents());

      return AuthenticationInteractionRequestResult.error(
          executionResult.statusCode(),
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

    // Verify device count limit (skip for reset action as it replaces devices)
    // IMPORTANT: Fetch latest user state from DB to prevent TOCTOU race condition
    // (transaction.user() may have stale device count if another registration completed)
    if (!isRestAction(transaction)) {
      User latestUser =
          userQueryRepository.findById(
              tenant, new org.idp.server.core.openid.identity.UserIdentifier(baseUser.sub()));
      int authenticationDeviceCount =
          latestUser.exists() ? latestUser.authenticationDeviceCount() : 0;
      int maxDevices = tenant.maxDevicesForAuthentication();

      if (authenticationDeviceCount >= maxDevices) {
        log.warn(
            "FIDO-UAF registration rejected: device limit reached. user={}, current={}, max={}",
            baseUser.sub(),
            authenticationDeviceCount,
            maxDevices);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "invalid_request");
        errorResponse.put(
            "error_description",
            String.format(
                "Maximum number of devices reached %d, user has already %d devices.",
                maxDevices, authenticationDeviceCount));

        return AuthenticationInteractionRequestResult.clientError(
            errorResponse,
            type,
            operationType(),
            method(),
            baseUser,
            DefaultSecurityEventType.fido_uaf_registration_failure);
      }
    }

    DefaultSecurityEventType eventType =
        isRestAction(transaction)
            ? DefaultSecurityEventType.fido_uaf_reset_success
            : DefaultSecurityEventType.fido_uaf_registration_success;

    // Create authentication device
    AuthenticationDevice authenticationDevice =
        createAuthenticationDevice(deviceId, transaction.attributes(), baseUser);

    // Issue device secret if configured in tenant policy
    AuthenticationDeviceRule deviceRule =
        tenant.identityPolicyConfig() != null
            ? tenant.identityPolicyConfig().authenticationDeviceRule()
            : AuthenticationDeviceRule.defaultRule();

    DeviceSecretIssuer deviceSecretIssuer = new DeviceSecretIssuer();
    DeviceSecretIssuanceResult issuanceResult =
        deviceSecretIssuer.issue(authenticationDevice, deviceRule);

    User addedDeviceUser = baseUser.addAuthenticationDevice(issuanceResult.device());

    if (tenant.requiresIdentityVerificationForDeviceRegistration()) {
      addedDeviceUser.setStatus(UserStatus.IDENTITY_VERIFICATION_REQUIRED);
    }

    log.info(
        "FIDO-UAF registration succeeded for user: {}, device: {}, device_secret_issued: {}",
        addedDeviceUser.sub(),
        deviceId,
        issuanceResult.hasDeviceSecret());

    Map<String, Object> responseContents = new HashMap<>(contents);
    responseContents.put("device_id", deviceId);

    // Include device secret in response if issued
    if (issuanceResult.hasDeviceSecret()) {
      responseContents.put("device_secret", issuanceResult.deviceSecret());
      responseContents.put("device_secret_algorithm", issuanceResult.algorithm());
      responseContents.put("device_secret_jwt_issuer", "device:" + deviceId);
    }

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

  private AuthenticationDevice createAuthenticationDevice(
      String deviceId, AuthenticationTransactionAttributes attributes, User user) {

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

    return new AuthenticationDevice(
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
  }

  /**
   * Checks if device registration policy requirements are met.
   *
   * <p>Ensures that FIDO-UAF device registration requires authentication meeting configured policy,
   * typically either:
   *
   * <ul>
   *   <li>Authentication with existing FIDO-UAF device, OR
   *   <li>Multi-factor authentication (password + TOTP)
   * </ul>
   *
   * @param tenant the tenant
   * @param transaction the authentication transaction
   * @return true if policy is met or not configured, false if policy check fails
   */
  private boolean isDeviceRegistrationPolicyMet(
      Tenant tenant, AuthenticationTransaction transaction) {

    AuthenticationPolicy authPolicy = transaction.authenticationPolicy();

    // Only verify if device_registration_conditions is configured
    if (!authPolicy.hasDeviceRegistrationConditions()) {
      log.debug(
          "No device_registration_conditions configured, skipping ACR verification for FIDO-UAF registration");
      return true;
    }

    AuthenticationInteractionResults interactionResults = transaction.interactionResults();
    AuthenticationResultConditionConfig conditions = authPolicy.deviceRegistrationConditions();

    // Evaluate conditions using existing MfaConditionEvaluator
    boolean satisfied =
        org.idp.server.core.openid.authentication.evaluator.MfaConditionEvaluator
            .isSuccessSatisfied(conditions, interactionResults);

    if (satisfied) {
      log.info(
          "FIDO-UAF device registration policy check passed: user={}, tenant={}",
          transaction.user().sub(),
          tenant.identifier().value());
    }

    return satisfied;
  }
}
