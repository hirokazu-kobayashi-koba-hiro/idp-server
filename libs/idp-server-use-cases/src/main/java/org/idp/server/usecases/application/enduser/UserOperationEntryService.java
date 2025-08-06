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
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.mfa.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.identity.*;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.oidc.identity.device.AuthenticationDevicePatchValidator;
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.oidc.identity.event.UserLifecycleType;
import org.idp.server.core.oidc.identity.io.AuthenticationDevicePatchRequest;
import org.idp.server.core.oidc.identity.io.MfaRegistrationRequest;
import org.idp.server.core.oidc.identity.io.UserOperationResponse;
import org.idp.server.core.oidc.identity.repository.UserCommandRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.TokenEventPublisher;
import org.idp.server.core.oidc.type.AuthFlow;
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
  MfaPolicies mfaPolicies;
  MfaRegistrationVerifiers mfaRegistrationVerifiers;
  AuthenticationInteractors authenticationInteractors;
  TokenEventPublisher eventPublisher;
  UserOperationEventPublisher userOperationEventPublisher;
  UserLifecycleEventPublisher userLifecycleEventPublisher;

  public UserOperationEntryService(
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      AuthenticationInteractors authenticationInteractors,
      TokenEventPublisher eventPublisher,
      UserOperationEventPublisher userOperationEventPublisher,
      UserLifecycleEventPublisher userLifecycleEventPublisher) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.authenticationTransactionCommandRepository = authenticationTransactionCommandRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
    this.mfaPolicies = new MfaPolicies();
    this.mfaRegistrationVerifiers = new MfaRegistrationVerifiers();
    this.authenticationInteractors = authenticationInteractors;
    this.eventPublisher = eventPublisher;
    this.userOperationEventPublisher = userOperationEventPublisher;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
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

    // TODO to be more correct getting mfaPolicies
    AuthenticationPolicy authenticationPolicy = mfaPolicies.get(authFlow);
    MfaRequestVerifier mfaRequestVerifier = mfaRegistrationVerifiers.get(authFlow);
    MfaVerificationResult verificationResult =
        mfaRequestVerifier.verify(user, request, authenticationPolicy);

    if (verificationResult.isFailure()) {
      return UserOperationResponse.failure(verificationResult.errorContents());
    }

    // TODO to be more correct getting client attributes
    AuthenticationTransaction authenticationTransaction =
        MfaRegistrationTransactionCreator.create(
            tenant, user, token, authFlow, request, authenticationPolicy);
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

    if (updatedTransaction.isSuccess()) {
      // TODO to be more correctly
      userOperationEventPublisher.publish(
          tenant, authenticationTransaction, type, true, requestAttributes);
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

    AuthenticationDevicePatchValidator validator = new AuthenticationDevicePatchValidator(request);
    JsonSchemaValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      Map<String, Object> contents = new HashMap<>();
      contents.put("error", "invalid_request");
      contents.put("error_description", "authentication device patch is failed");
      Map<String, Object> details = new HashMap<>();
      details.put("messages", validate.errors());
      contents.put("error_details", details);
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
  public void delete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    userCommandRepository.delete(tenant, user.userIdentifier());

    UserLifecycleEvent userLifecycleEvent =
        new UserLifecycleEvent(tenant, user, UserLifecycleType.DELETE);
    userLifecycleEventPublisher.publish(userLifecycleEvent);
    eventPublisher.publish(
        tenant, oAuthToken, DefaultSecurityEventType.user_delete, requestAttributes);
  }
}
