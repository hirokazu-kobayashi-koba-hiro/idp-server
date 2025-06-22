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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserStatus;
import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class FidoUafRegistrationInteractor implements AuthenticationInteractor {

  FidoUafExecutors fidoUafExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  FidoUafAdditionalRequestResolvers additionalRequestResolvers;

  public FidoUafRegistrationInteractor(
      FidoUafExecutors fidoUafExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      FidoUafAdditionalRequestResolvers additionalRequestResolvers) {
    this.fidoUafExecutors = fidoUafExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
    this.additionalRequestResolvers = additionalRequestResolvers;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      UserQueryRepository userQueryRepository) {

    FidoUafConfiguration fidoUafConfiguration =
        configurationQueryRepository.get(tenant, "fido-uaf", FidoUafConfiguration.class);
    FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

    Map<String, Object> executionRequest = new HashMap<>(request.toMap());
    Map<String, Object> additionalRequests =
        additionalRequestResolvers.resolveAll(tenant, type, request, transaction);
    executionRequest.putAll(additionalRequests);

    FidoUafExecutionRequest fidoUafExecutionRequest = new FidoUafExecutionRequest(executionRequest);
    FidoUafExecutionResult executionResult =
        fidoUafExecutor.verifyRegistration(
            tenant, transaction.identifier(), fidoUafExecutionRequest, fidoUafConfiguration);

    if (executionResult.isClientError()) {
      return AuthenticationInteractionRequestResult.clientError(
          executionResult.contents(), type, DefaultSecurityEventType.fido_uaf_registration_failure);
    }

    if (executionResult.isServerError()) {
      return AuthenticationInteractionRequestResult.serverError(
          executionResult.contents(), type, DefaultSecurityEventType.fido_uaf_registration_failure);
    }

    String deviceId =
        executionResult.getValueAsStringFromContents(fidoUafConfiguration.deviceIdParam());
    User user = transaction.user();
    AuthenticationDevice authenticationDevice =
        createAuthenticationDevice(deviceId, transaction.attributes());

    HashMap<String, Object> mfa = new HashMap<>();
    mfa.put("fido-uaf", true);
    User addedDeviceUser =
        user.addAuthenticationDevice(authenticationDevice).addMultiFactorAuthentication(mfa);

    AuthenticationPolicy authenticationPolicy = transaction.authenticationPolicy();
    if (authenticationPolicy.authenticationDeviceRule().requiredIdentityVerification()) {
      addedDeviceUser.setStatus(UserStatus.IDENTITY_VERIFICATION_REQUIRED);
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("hwk")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        addedDeviceUser,
        authentication,
        executionResult.contents(),
        DefaultSecurityEventType.fido_uaf_registration_success);
  }

  private AuthenticationDevice createAuthenticationDevice(
      String deviceId, AuthenticationTransactionAttributes attributes) {

    String platform = attributes.getValueOrEmpty("platform");
    String os = attributes.getValueOrEmpty("os");
    String model = attributes.getValueOrEmpty("model");
    String notificationChannel = attributes.getValueOrEmpty("notification_channel");
    String notificationToken = attributes.getValueOrEmpty("notification_token");
    boolean preferredForNotification = attributes.getValueAsBoolean("preferred_for_notification");

    return new AuthenticationDevice(
        deviceId,
        platform,
        os,
        model,
        notificationChannel,
        notificationToken,
        preferredForNotification);
  }
}
