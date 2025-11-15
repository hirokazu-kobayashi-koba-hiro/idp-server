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

package org.idp.server.usecases.application.enduser;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.mfa.*;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.openid.identity.*;
import org.idp.server.core.openid.identity.authentication.PasswordChangeRequest;
import org.idp.server.core.openid.identity.authentication.PasswordChangeResponse;
import org.idp.server.core.openid.identity.authentication.PasswordChangeService;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.AuthenticationDevicePatchValidator;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.core.openid.identity.io.AuthenticationDevicePatchRequest;
import org.idp.server.core.openid.identity.io.MfaRegistrationRequest;
import org.idp.server.core.openid.identity.io.UserOperationResponse;
import org.idp.server.core.openid.identity.io.UserOperationStatus;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.UserEventPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class UserOperationEntryService implements UserOperationApi {

  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;
  AuthenticationPolicyConfigurationQueryRepository authenticationPolicyConfigurationQueryRepository;
  MfaRegistrationVerifiers mfaRegistrationVerifiers;
  AuthenticationInteractors authenticationInteractors;
  UserEventPublisher eventPublisher;
  UserOperationEventPublisher userOperationEventPublisher;
  UserLifecycleEventPublisher userLifecycleEventPublisher;
  PasswordVerificationDelegation passwordVerificationDelegation;
  PasswordEncodeDelegation passwordEncodeDelegation;

  public UserOperationEntryService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
      AuthenticationInteractors authenticationInteractors,
      UserEventPublisher eventPublisher,
      UserOperationEventPublisher userOperationEventPublisher,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      PasswordVerificationDelegation passwordVerificationDelegation,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.authenticationTransactionCommandRepository = authenticationTransactionCommandRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
    this.authenticationPolicyConfigurationQueryRepository =
        authenticationPolicyConfigurationQueryRepository;
    this.mfaRegistrationVerifiers = new MfaRegistrationVerifiers();
    this.authenticationInteractors = authenticationInteractors;
    this.eventPublisher = eventPublisher;
    this.userOperationEventPublisher = userOperationEventPublisher;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
    this.passwordVerificationDelegation = passwordVerificationDelegation;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  @Override
  public UserOperationResponse requestMfaOperation(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken token,
      AuthFlow authFlow,
      MfaRegistrationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationPolicyConfiguration authenticationPolicyConfiguration =
        authenticationPolicyConfigurationQueryRepository.get(tenant, authFlow);

    // TODO to be more correct getting client attributes
    AuthenticationTransaction authenticationTransaction =
        MfaRegistrationTransactionCreator.create(
            tenant, user, token, authFlow, request, authenticationPolicyConfiguration);

    MfaRequestVerifier mfaRequestVerifier = mfaRegistrationVerifiers.get(authFlow);
    MfaVerificationResult verificationResult =
        mfaRequestVerifier.verify(user, request, authenticationTransaction.authenticationPolicy());

    if (verificationResult.isFailure()) {
      return UserOperationResponse.failure(verificationResult.errorContents());
    }

    authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);

    Map<String, Object> contents = new HashMap<>();
    contents.put("id", authenticationTransaction.identifier().value());

    return UserOperationResponse.success(contents);
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(tenant, authenticationTransactionIdentifier);
    AuthenticationInteractionRequestResult result =
        authenticationInteractor.interact(
            tenant,
            authenticationTransaction,
            type,
            request,
            requestAttributes,
            userQueryRepository);

    AuthenticationTransaction updatedTransaction = authenticationTransaction.updateWith(result);

    userOperationEventPublisher.publish(
        tenant, authenticationTransaction, result.eventType(), requestAttributes);

    if (updatedTransaction.isSuccess()) {
      // TODO to be more correctly. no verification update is danger.
      userCommandRepository.update(tenant, authenticationTransaction.user());
    }

    if (updatedTransaction.isLocked()) {
      UserLifecycleEvent userLifecycleEvent =
          new UserLifecycleEvent(tenant, result.user(), UserLifecycleType.LOCK);
      userLifecycleEventPublisher.publish(userLifecycleEvent);
    }

    if (updatedTransaction.isComplete()) {
      authenticationTransactionCommandRepository.delete(
          tenant, authenticationTransactionIdentifier);
    } else {
      authenticationTransactionCommandRepository.update(tenant, updatedTransaction);
    }

    return result;
  }

  @Override
  public UserOperationResponse patchAuthenticationDevice(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      AuthenticationDevicePatchRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Scope validation - RFC 6750 Section 3.1
    if (!oAuthToken.scopes().contains("claims:authentication_devices")) {
      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "insufficient_scope");
      contents.put(
          "error_description", "The request requires 'claims:authentication_devices' scope");
      contents.put("scope", "claims:authentication_devices");
      return UserOperationResponse.insufficientScope(contents);
    }

    AuthenticationDevicePatchValidator validator = new AuthenticationDevicePatchValidator(request);
    JsonSchemaValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "invalid_request");
      contents.put("error_description", "authentication device patch is failed");
      contents.put("error_messages", validate.errors());
      return UserOperationResponse.failure(contents);
    }

    if (!user.hasAuthenticationDevice(authenticationDeviceIdentifier)) {

      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "invalid_request");
      contents.put(
          "error_description",
          String.format(
              "User does not have authentication device (%s).",
              authenticationDeviceIdentifier.value()));
      return UserOperationResponse.failure(contents);
    }

    AuthenticationDevice newAuthenticationDevice =
        request.toAuthenticationDevice(authenticationDeviceIdentifier);
    User patched = user.patchWithAuthenticationDevice(newAuthenticationDevice);
    userCommandRepository.update(tenant, patched);

    return UserOperationResponse.success(null);
  }

  @Override
  public UserOperationResponse delete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Scope validation - RFC 6750 Section 3.1
    if (!oAuthToken.scopes().contains("openid")) {
      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "insufficient_scope");
      contents.put("error_description", "The request requires 'openid' scope");
      contents.put("scope", "openid");
      return UserOperationResponse.insufficientScope(contents);
    }

    userCommandRepository.delete(tenant, user.userIdentifier());

    UserLifecycleEvent userLifecycleEvent =
        new UserLifecycleEvent(tenant, user, UserLifecycleType.DELETE);
    userLifecycleEventPublisher.publish(userLifecycleEvent);
    eventPublisher.publish(
        tenant, oAuthToken, DefaultSecurityEventType.user_delete, requestAttributes);

    return new UserOperationResponse(UserOperationStatus.NO_CONTENT, null);
  }

  @Override
  public PasswordChangeResponse changePassword(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      PasswordChangeRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Scope validation - RFC 6750 Section 3.1
    if (!oAuthToken.scopes().contains("openid")) {
      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "insufficient_scope");
      contents.put("error_description", "The request requires 'openid' scope");
      contents.put("scope", "openid");
      return PasswordChangeResponse.insufficientScope(contents);
    }

    PasswordChangeService passwordChangeService =
        new PasswordChangeService(
            passwordVerificationDelegation, passwordEncodeDelegation, userCommandRepository);

    PasswordChangeResponse response = passwordChangeService.changePassword(tenant, user, request);

    if (response.isSuccess()) {
      eventPublisher.publish(
          tenant, oAuthToken, DefaultSecurityEventType.password_change_success, requestAttributes);
    } else {
      eventPublisher.publish(
          tenant, oAuthToken, DefaultSecurityEventType.password_change_failure, requestAttributes);
    }

    return response;
  }
}
