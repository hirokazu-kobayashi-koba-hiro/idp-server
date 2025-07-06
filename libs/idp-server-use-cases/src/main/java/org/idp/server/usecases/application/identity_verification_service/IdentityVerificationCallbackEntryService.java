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
import org.idp.server.core.extension.identity.verification.*;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.handler.IdentityVerificationHandler;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationResponse;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultCommandRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationApplicationRequestValidator;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationApplicationValidationResult;
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
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class IdentityVerificationCallbackEntryService implements IdentityVerificationCallbackApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationCommandRepository applicationCommandRepository;
  IdentityVerificationApplicationQueryRepository applicationQueryRepository;
  IdentityVerificationResultCommandRepository resultCommandRepository;
  IdentityVerificationHandler identityVerificationHandler;
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
    this.identityVerificationHandler = new IdentityVerificationHandler();
    this.eventPublisher = eventPublisher;
  }

  @Override
  public IdentityVerificationApplicationResponse callbackExamination(
      TenantIdentifier tenantIdentifier,
      BasicAuth basicAuth,
      IdentityVerificationType type,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);

    IdentityVerificationProcess process =
        ReservedIdentityVerificationProcess.CALLBACK_EXAMINATION.toProcess();
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationApplicationRequestValidator applicationValidator =
        new IdentityVerificationApplicationRequestValidator(processConfiguration, request);
    IdentityVerificationApplicationValidationResult validationResult =
        applicationValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    ExternalWorkflowApplicationIdentifier externalWorkflowApplicationIdentifier =
        new ExternalWorkflowApplicationIdentifier(
            request.getValueAsString(
                verificationConfiguration.externalWorkflowApplicationIdParam().value()));
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, externalWorkflowApplicationIdentifier);

    IdentityVerificationApplication updatedExamination =
        application.updateExamination(process, request, verificationConfiguration);
    applicationCommandRepository.update(tenant, updatedExamination);

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationApplicationResponse.OK(response);
  }

  @Override
  public IdentityVerificationApplicationResponse callbackResult(
      TenantIdentifier tenantIdentifier,
      BasicAuth basicAuth,
      IdentityVerificationType type,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationProcess process =
        ReservedIdentityVerificationProcess.CALLBACK_RESULT.toProcess();
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationApplicationRequestValidator applicationValidator =
        new IdentityVerificationApplicationRequestValidator(processConfiguration, request);
    IdentityVerificationApplicationValidationResult validationResult =
        applicationValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    ExternalWorkflowApplicationIdentifier externalWorkflowApplicationIdentifier =
        new ExternalWorkflowApplicationIdentifier(
            request.getValueAsString(
                verificationConfiguration.externalWorkflowApplicationIdParam().value()));
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, externalWorkflowApplicationIdentifier);

    IdentityVerificationApplication updatedExamination =
        application.completeExamination(process, request, verificationConfiguration);
    applicationCommandRepository.update(tenant, updatedExamination);

    IdentityVerificationResult identityVerificationResult =
        IdentityVerificationResult.create(updatedExamination, request, verificationConfiguration);
    resultCommandRepository.register(tenant, identityVerificationResult);

    // TODO dynamic lifecycle management
    User user = userQueryRepository.get(tenant, application.userIdentifier());
    User verifiedUser =
        user.transitStatus(UserStatus.IDENTITY_VERIFIED)
            .setVerifiedClaims(identityVerificationResult.verifiedClaims().toMap());

    userCommandRepository.update(tenant, verifiedUser);

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationApplicationResponse.OK(response);
  }
}
