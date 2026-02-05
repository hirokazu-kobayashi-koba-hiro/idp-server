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
import org.idp.server.core.openid.authentication.policy.AuthenticationResultConditionConfig;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.type.DeviceInfo;
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

    // FIDO2 registration requires authenticated session
    if (!transaction.hasUser()) {
      log.warn("FIDO2 registration: unauthenticated request");

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "unauthorized");
      errorResponse.put(
          "error_description", "FIDO2 device registration requires authenticated session");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          null,
          DefaultSecurityEventType.fido2_registration_failure);
    }

    // Verify device registration ACR/MFA requirements
    if (!isDeviceRegistrationPolicyMet(tenant, transaction)) {
      log.warn(
          "FIDO2 device registration policy check failed: user={}, tenant={}",
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
          DefaultSecurityEventType.fido2_registration_failure);
    }

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

    // Resolve or create User based on registration result
    String userId = resolveUsername(contents, configuration);
    User baseUser = resolveUser(tenant, transaction, userId, userQueryRepository);
    if (baseUser == null) {
      // Username mismatch detected
      log.warn("FIDO2 registration: username mismatch for user={}", transaction.user().sub());

      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "forbidden");
      errorResponse.put("error_description", "Cannot register FIDO2 device for a different user");

      return AuthenticationInteractionRequestResult.clientError(
          errorResponse,
          type,
          operationType(),
          method(),
          transaction.user(),
          DefaultSecurityEventType.fido2_registration_failure);
    }

    // Handle reset action: remove existing FIDO2 devices before adding new one
    if (isRestAction(transaction)) {
      baseUser = baseUser.removeAllAuthenticationDevicesOfType("fido2");
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
            "FIDO2 registration rejected: device limit reached. user={}, current={}, max={}",
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
            DefaultSecurityEventType.fido2_registration_failure);
      }
    }

    DefaultSecurityEventType eventType =
        isRestAction(transaction)
            ? DefaultSecurityEventType.fido2_reset_success
            : DefaultSecurityEventType.fido2_registration_success;

    String deviceId = UUID.randomUUID().toString();
    log.info("fido2 registration success deviceId: {}, userId: {}", deviceId, userId);

    // Get credentialId from request (sent by client from authenticator response)
    String credentialId = request.getValueAsString("id");
    // Get rpId from execution config
    String rpId = (String) execution.details().get("rp_id");

    User addedDeviceUser =
        addAuthenticationDevice(
            baseUser,
            deviceId,
            transaction.attributes(),
            requestAttributes,
            credentialId,
            rpId,
            configuration.id());

    if (tenant.requiresIdentityVerificationForDeviceRegistration()) {
      addedDeviceUser.setStatus(UserStatus.IDENTITY_VERIFICATION_REQUIRED);
    }

    Map<String, Object> response = new HashMap<>(contents);
    response.put("device_id", deviceId);

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        operationType(),
        method(),
        addedDeviceUser,
        response,
        eventType);
  }

  /**
   * Resolves or creates User based on userId (username from FIDO2 credential).
   *
   * <p>Resolution strategy (same as Email/SMS authentication - Issue #800 fix pattern):
   *
   * <ol>
   *   <li>Search by preferredUsername in database (highest priority - Issue #800 fix)
   *   <li>If transaction.hasUser() && same username: reuse existing User
   *   <li>Create new User with generated UUID
   * </ol>
   *
   * @param tenant the tenant
   * @param transaction the authentication transaction
   * @param username the username from FIDO2 credential (userId decoded)
   * @param userQueryRepository the user query repository
   * @return the resolved or created User
   */
  private User resolveUser(
      Tenant tenant,
      AuthenticationTransaction transaction,
      String username,
      UserQueryRepository userQueryRepository) {

    // FIDO2 registration requires authenticated session (already checked in interact method)
    // Strategy 1: Use transaction.user() only (prevent user impersonation)
    User transactionUser = transaction.user();
    String transactionUsername =
        resolveUsernameFromUser(transactionUser, tenant.identityPolicyConfig());

    if (!username.equals(transactionUsername)) {
      // Different username → reject (prevent registering device for different user)
      log.warn(
          "FIDO2 registration: username mismatch detected. transaction={}, credential={}",
          transactionUsername,
          username);
      return null;
    }

    log.debug("FIDO2 registration: using transaction user with verified username: {}", username);
    return transactionUser;
  }

  /**
   * Checks if device registration policy requirements are met.
   *
   * <p>Ensures that FIDO2 device registration requires authentication meeting configured policy,
   * typically either:
   *
   * <ul>
   *   <li>Authentication with existing FIDO2 device, OR
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
      log.debug("No device_registration_conditions configured, skipping ACR verification");
      return true;
    }

    AuthenticationInteractionResults interactionResults = transaction.interactionResults();
    AuthenticationResultConditionConfig conditions = authPolicy.deviceRegistrationConditions();

    // Evaluate using MfaConditionEvaluator
    boolean satisfied =
        org.idp.server.core.openid.authentication.evaluator.MfaConditionEvaluator
            .isSuccessSatisfied(conditions, interactionResults);

    if (satisfied) {
      log.info(
          "FIDO2 device registration policy check passed: user={}, tenant={}",
          transaction.user().sub(),
          tenant.identifier().value());
    }

    return satisfied;
  }

  private String resolveUsername(
      Map<String, Object> contents, AuthenticationConfiguration configuration) {

    Map<String, Object> metadata = configuration.metadata();
    Fido2MetadataConfig fido2MetadataConfig = new Fido2MetadataConfig(metadata);

    String usernameParam = fido2MetadataConfig.usernameParam();
    if (contents.containsKey(usernameParam)) {
      return contents.get(usernameParam).toString();
    }

    return "";
  }

  private boolean isRestAction(AuthenticationTransaction transaction) {
    return "reset".equals(transaction.attributes().getValueOrEmpty("action"));
  }

  private User addAuthenticationDevice(
      User user,
      String deviceId,
      AuthenticationTransactionAttributes attributes,
      RequestAttributes requestAttributes,
      String credentialId,
      String rpId,
      String fidoServerId) {

    // Extract device info from User-Agent if not provided in attributes
    DeviceInfo deviceInfo = extractDeviceInfo(attributes, requestAttributes);

    String appName = getOrDefault(attributes, "app_name", deviceInfo.toLabel());
    String platform = getOrDefault(attributes, "platform", deviceInfo.platform());
    String os = getOrDefault(attributes, "os", deviceInfo.os());
    String model = getOrDefault(attributes, "model", deviceInfo.model());
    String locale = attributes.getValueOrEmpty("locale");
    String notificationChannel = attributes.getValueOrEmpty("notification_channel");
    String notificationToken = attributes.getValueOrEmpty("notification_token");
    List<String> availableAuthenticationMethods = List.of(method());
    int priority =
        attributes.containsKey("priority")
            ? attributes.getValueAsInteger("priority")
            : user.authenticationDeviceNextCount();

    log.debug(
        "Creating authentication device: deviceId={}, appName={}, platform={}, os={}, model={}",
        deviceId,
        appName,
        platform,
        os,
        model);

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

    // Set FIDO2 credential fields directly on authentication device
    if (credentialId != null && !credentialId.isEmpty()) {
      // credentialPayload: empty for now (will be populated by FIDO server on authentication)
      Map<String, Object> credentialPayload = new HashMap<>();

      // credentialMetadata: FIDO-specific metadata
      Map<String, Object> credentialMetadata = new HashMap<>();
      credentialMetadata.put("rp_id", rpId);
      credentialMetadata.put("fido_server_id", fidoServerId);
      credentialMetadata.put("created_at", SystemDateTime.now().toString());

      authenticationDevice =
          authenticationDevice.withCredential(
              "fido2", credentialId, credentialPayload, credentialMetadata);

      log.debug(
          "Set FIDO2 credential on device: deviceId={}, credentialId={}, rpId={}",
          deviceId,
          credentialId,
          rpId);
    }

    return user.addAuthenticationDevice(authenticationDevice);
  }

  private DeviceInfo extractDeviceInfo(
      AuthenticationTransactionAttributes attributes, RequestAttributes requestAttributes) {

    // If attributes already have device info, skip parsing
    if (hasDeviceAttributes(attributes)) {
      return DeviceInfo.unknown();
    }

    // Parse User-Agent from request attributes
    if (requestAttributes.hasUserAgent()) {
      DeviceInfo deviceInfo = requestAttributes.getUserAgent().toDeviceInfo();
      log.debug("Extracted device info from User-Agent: {}", deviceInfo);
      return deviceInfo;
    }

    return DeviceInfo.unknown();
  }

  private boolean hasDeviceAttributes(AuthenticationTransactionAttributes attributes) {
    return attributes.containsKey("platform")
        || attributes.containsKey("os")
        || attributes.containsKey("model");
  }

  private String getOrDefault(
      AuthenticationTransactionAttributes attributes, String key, String defaultValue) {
    String value = attributes.getValueOrEmpty(key);
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    return value;
  }

  /**
   * Resolves username from User based on Tenant Identity Policy.
   *
   * @param user the user
   * @param identityPolicy the tenant identity policy
   * @return username, or empty string if not resolvable
   */
  private String resolveUsernameFromUser(User user, TenantIdentityPolicy identityPolicy) {
    switch (identityPolicy.uniqueKeyType()) {
      case USERNAME:
      case USERNAME_OR_EXTERNAL_USER_ID:
        return user.preferredUsername();

      case EMAIL:
      case EMAIL_OR_EXTERNAL_USER_ID:
        return user.email();

      case PHONE:
      case PHONE_OR_EXTERNAL_USER_ID:
        return user.phoneNumber();

      case EXTERNAL_USER_ID:
        return user.externalUserId();

      default:
        // Fallback to preferredUsername
        return user.preferredUsername();
    }
  }
}
