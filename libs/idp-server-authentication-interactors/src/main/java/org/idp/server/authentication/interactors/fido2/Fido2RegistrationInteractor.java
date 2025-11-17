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
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
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

    // Resolve or create User based on registration result
    String userId = resolveUsername(contents, configuration);
    User baseUser = resolveUser(tenant, transaction, userId, userQueryRepository);
    // Handle reset action: remove existing FIDO-UAF devices before adding new one
    if (isRestAction(transaction)) {
      baseUser = baseUser.removeAllAuthenticationDevicesOfType("fido2");
    }

    DefaultSecurityEventType eventType =
        isRestAction(transaction)
            ? DefaultSecurityEventType.fido2_reset_success
            : DefaultSecurityEventType.fido2_registration_success;

    String deviceId = UUID.randomUUID().toString();
    log.info("fido2 registration success deviceId: {}, userId: {}", deviceId, userId);

    User addedDeviceUser = addAuthenticationDevice(baseUser, deviceId, transaction.attributes());

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

    // Strategy 1: Reuse transaction.user() if same username
    if (transaction.hasUser()) {
      User transactionUser = transaction.user();
      String transactionUsername =
          resolveUsernameFromUser(transactionUser, tenant.identityPolicyConfig());

      if (username.equals(transactionUsername)) {
        log.debug("FIDO2 registration: reusing transaction user with same username: {}", username);
        return transactionUser;
      }
      // Different username → discard transaction.user(), create new User
    }

    // Strategy 2: Database search (Issue #800 fix)
    User existingUser = userQueryRepository.findByPreferredUsernameNoProvider(tenant, username);
    if (existingUser.exists()) {
      log.debug("FIDO2 registration: found existing user by preferredUsername: {}", username);
      return existingUser;
    }

    // Strategy 3: Create new User
    User user = User.initialized();
    String id = UUID.randomUUID().toString();
    user.setSub(id);
    user.setName(username);
    user.setPreferredUsername(username);

    log.debug(
        "FIDO2 registration: created new user with sub: {}, preferredUsername: {}", id, username);
    return user;
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
