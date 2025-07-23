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

package org.idp.server.usecases.application.identity_verification_service;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationCallbackApi;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationHandler;
import org.idp.server.core.extension.identity.verification.application.execution.IdentityVerificationApplicationContext;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.callback.validation.IdentityVerificationCallbackRequestValidator;
import org.idp.server.core.extension.identity.verification.callback.validation.IdentityVerificationCallbackValidationResult;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.process.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.io.*;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultCommandRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserStatus;
import org.idp.server.core.oidc.identity.repository.UserCommandRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.token.TokenEventPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class IdentityVerificationCallbackEntryService implements IdentityVerificationCallbackApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationCommandRepository applicationCommandRepository;
  IdentityVerificationApplicationQueryRepository applicationQueryRepository;
  IdentityVerificationResultCommandRepository resultCommandRepository;
  IdentityVerificationApplicationHandler identityVerificationApplicationHandler;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  TokenEventPublisher eventPublisher;

  public IdentityVerificationCallbackEntryService(
      IdentityVerificationConfigurationQueryRepository configurationQueryRepository,
      IdentityVerificationApplicationCommandRepository applicationCommandRepository,
      IdentityVerificationApplicationQueryRepository applicationQueryRepository,
      IdentityVerificationResultCommandRepository resultCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      TokenEventPublisher eventPublisher) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.applicationCommandRepository = applicationCommandRepository;
    this.applicationQueryRepository = applicationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.resultCommandRepository = resultCommandRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.identityVerificationApplicationHandler = new IdentityVerificationApplicationHandler();
    this.eventPublisher = eventPublisher;
  }

  @Override
  public IdentityVerificationCallbackResponse callback(
      TenantIdentifier tenantIdentifier,
      BasicAuth basicAuth,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationCallbackRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);

    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationCallbackRequestValidator validator =
        new IdentityVerificationCallbackRequestValidator(
            processConfiguration, request, requestAttributes);

    IdentityVerificationCallbackValidationResult validationResult = validator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    String applicationIdParams = verificationConfiguration.getCallbackApplicationId(process);
    String applicationId = request.getValueAsString(applicationIdParams);
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, applicationIdParams, applicationId);

    IdentityVerificationApplication updatedApplication =
        application.updateCallbackWith(process, request, verificationConfiguration);
    applicationCommandRepository.update(tenant, updatedApplication);

    // TODO to be more correct
    IdentityVerificationApplicationContext context =
        new IdentityVerificationApplicationContext(
            Map.of(
                "request_body",
                request.toMap(),
                "request_attributes",
                requestAttributes.toMap(),
                "application",
                application.toMap()),
            Map.of());
    if (updatedApplication.isApproved()) {
      IdentityVerificationResult identityVerificationResult =
          IdentityVerificationResult.createOnCallback(
              updatedApplication, context, verificationConfiguration);
      resultCommandRepository.register(tenant, identityVerificationResult);

      // TODO dynamic lifecycle management
      User user = userQueryRepository.get(tenant, application.userIdentifier());
      User verifiedUser =
          user.transitStatus(UserStatus.IDENTITY_VERIFIED)
              .setVerifiedClaims(identityVerificationResult.verifiedClaims().toMap());

      userCommandRepository.update(tenant, verifiedUser);
    }

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationCallbackResponse.OK(response);
  }
}
