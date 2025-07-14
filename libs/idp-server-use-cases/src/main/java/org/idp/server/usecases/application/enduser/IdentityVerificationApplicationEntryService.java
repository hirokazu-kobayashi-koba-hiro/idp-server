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
import org.idp.server.core.extension.identity.verification.*;
import org.idp.server.core.extension.identity.verification.application.*;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationApplyingResult;
import org.idp.server.core.extension.identity.verification.handler.IdentityVerificationHandler;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationApplicationResponse;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationDynamicResponseMapper;
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
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.TokenEventPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventType;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class IdentityVerificationApplicationEntryService
    implements IdentityVerificationApplicationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationCommandRepository applicationCommandRepository;
  IdentityVerificationApplicationQueryRepository applicationQueryRepository;
  IdentityVerificationResultCommandRepository resultCommandRepository;
  IdentityVerificationHandler identityVerificationHandler;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  TokenEventPublisher eventPublisher;

  public IdentityVerificationApplicationEntryService(
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
  public IdentityVerificationApplicationResponse apply(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationApplications applications =
        applicationQueryRepository.findAll(tenant, user);

    ExternalIdentityVerificationApplyingResult applyingResult =
        identityVerificationHandler.handleRequest(
            tenant,
            user,
            new IdentityVerificationApplication(),
            applications,
            type,
            process,
            request,
            requestAttributes,
            verificationConfiguration);
    if (applyingResult.isError()) {

      eventPublisher.publish(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_failure,
          requestAttributes);
      return applyingResult.errorResponse();
    }

    IdentityVerificationApplication application =
        IdentityVerificationApplication.create(
            tenant,
            oAuthToken.requestedClientId(),
            user,
            type,
            request,
            verificationConfiguration.externalIdentityVerificationService(),
            applyingResult,
            process,
            verificationConfiguration);
    applicationCommandRepository.register(tenant, application);
    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_apply,
        requestAttributes);

    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            application,
            applyingResult.externalWorkflowResponse(),
            process,
            verificationConfiguration);

    return IdentityVerificationApplicationResponse.OK(response);
  }

  @Override
  public IdentityVerificationApplicationResponse findApplications(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationQueries queries,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationApplications applications =
        applicationQueryRepository.findList(tenant, user, queries);

    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_findList,
        requestAttributes);

    Map<String, Object> response = new HashMap<>();
    response.put("list", applications.toList());
    return IdentityVerificationApplicationResponse.OK(response);
  }

  @Override
  public IdentityVerificationApplicationResponse process(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, user, identifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationApplications applications =
        applicationQueryRepository.findAll(tenant, user);

    ExternalIdentityVerificationApplyingResult applyingResult =
        identityVerificationHandler.handleRequest(
            tenant,
            user,
            application,
            applications,
            type,
            process,
            request,
            requestAttributes,
            verificationConfiguration);
    if (applyingResult.isError()) {

      eventPublisher.publish(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_failure,
          requestAttributes);

      return applyingResult.errorResponse();
    }

    IdentityVerificationApplication updated =
        application.updateProcess(process, request, applyingResult, verificationConfiguration);
    applicationCommandRepository.update(tenant, updated);
    SecurityEventType securityEventType =
        new SecurityEventType(type.name() + "_" + process.name() + "_" + "success");

    eventPublisher.publish(tenant, oAuthToken, securityEventType, requestAttributes);

    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            application,
            applyingResult.externalWorkflowResponse(),
            process,
            verificationConfiguration);
    return IdentityVerificationApplicationResponse.OK(response);
  }

  @Override
  public IdentityVerificationApplicationResponse evaluateResult(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType type,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationProcess process =
        ReservedIdentityVerificationProcess.EVALUATE_RESULT.toProcess();
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationApplicationRequestValidator applicationValidator =
        new IdentityVerificationApplicationRequestValidator(processConfiguration, request);
    IdentityVerificationApplicationValidationResult validationResult =
        applicationValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, user, identifier);

    IdentityVerificationApplication updatedExamination =
        application.completeExamination(process, request, verificationConfiguration);
    applicationCommandRepository.update(tenant, updatedExamination);

    if (updatedExamination.isApproved()) {
      IdentityVerificationResult identityVerificationResult =
          IdentityVerificationResult.create(updatedExamination, request, verificationConfiguration);
      resultCommandRepository.register(tenant, identityVerificationResult);

      // TODO dynamic lifecycle management
      User verifiedUser =
          user.transitStatus(UserStatus.IDENTITY_VERIFIED)
              .setVerifiedClaims(identityVerificationResult.verifiedClaims().toMap());

      userCommandRepository.update(tenant, verifiedUser);
    }

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationApplicationResponse.OK(response);
  }

  @Override
  public IdentityVerificationApplicationResponse delete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType type,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    applicationQueryRepository.get(tenant, user, identifier);
    applicationCommandRepository.delete(tenant, user, identifier);

    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_delete,
        requestAttributes);

    return IdentityVerificationApplicationResponse.OK(Map.of());
  }
}
