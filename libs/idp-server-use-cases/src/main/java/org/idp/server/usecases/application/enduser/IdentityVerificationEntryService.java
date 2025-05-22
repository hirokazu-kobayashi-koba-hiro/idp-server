/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.usecases.application.enduser;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.*;
import org.idp.server.core.extension.identity.verification.application.*;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.delegation.ExternalWorkflowApplyingResult;
import org.idp.server.core.extension.identity.verification.handler.IdentityVerificationHandler;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationDynamicResponseMapper;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultCommandRepository;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationRequestValidator;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationValidationResult;
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
public class IdentityVerificationEntryService implements IdentityVerificationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationCommandRepository applicationCommandRepository;
  IdentityVerificationApplicationQueryRepository applicationQueryRepository;
  IdentityVerificationResultCommandRepository resultCommandRepository;
  IdentityVerificationHandler identityVerificationHandler;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  TokenEventPublisher eventPublisher;

  public IdentityVerificationEntryService(
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
  public IdentityVerificationResponse apply(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationApplications applications =
        applicationQueryRepository.findAll(tenant, user);

    ExternalWorkflowApplyingResult applyingResult =
        identityVerificationHandler.handleRequest(
            tenant, user, applications, type, process, request, verificationConfiguration);
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
            verificationConfiguration.externalWorkflowDelegation(),
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

    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse findApplications(
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
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse process(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, user, identifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationApplications applications =
        applicationQueryRepository.findAll(tenant, user);

    ExternalWorkflowApplyingResult applyingResult =
        identityVerificationHandler.handleRequest(
            tenant, user, applications, type, process, request, verificationConfiguration);
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
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse callbackExaminationForStaticPath(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);

    IdentityVerificationProcess process =
        ReservedIdentityVerificationProcess.CALLBACK_EXAMINATION.toProcess();
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationRequestValidator applicationValidator =
        new IdentityVerificationRequestValidator(processConfiguration, request);
    IdentityVerificationValidationResult validationResult = applicationValidator.validate();

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
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse callbackResultForStaticPath(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationProcess process =
        ReservedIdentityVerificationProcess.CALLBACK_RESULT.toProcess();
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationRequestValidator applicationValidator =
        new IdentityVerificationRequestValidator(processConfiguration, request);
    IdentityVerificationValidationResult validationResult = applicationValidator.validate();

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
    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse delete(
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

    return IdentityVerificationResponse.OK(Map.of());
  }
}
