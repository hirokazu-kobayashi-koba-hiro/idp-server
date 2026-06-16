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

import java.util.Map;
import org.idp.server.core.extension.identity.verification.*;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationHandler;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplyingResult;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolver;
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
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationUserUpdater;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.UserEventPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventType;
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
  UserEventPublisher eventPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(IdentityVerificationCallbackEntryService.class);

  public IdentityVerificationCallbackEntryService(
      IdentityVerificationConfigurationQueryRepository configurationQueryRepository,
      IdentityVerificationApplicationCommandRepository applicationCommandRepository,
      IdentityVerificationApplicationQueryRepository applicationQueryRepository,
      IdentityVerificationResultCommandRepository resultCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      UserEventPublisher eventPublisher,
      Map<String, AdditionalRequestParameterResolver> additional,
      HttpRequestExecutor httpRequestExecutor) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.applicationCommandRepository = applicationCommandRepository;
    this.applicationQueryRepository = applicationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.resultCommandRepository = resultCommandRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.identityVerificationApplicationHandler =
        new IdentityVerificationApplicationHandler(additional, httpRequestExecutor);
    this.eventPublisher = eventPublisher;
  }

  @Override
  public IdentityVerificationCallbackResponse callback(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
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
    log.info("IdentityVerification callback received {}: {}", applicationIdParams, applicationId);
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, applicationIdParams, applicationId);

    return updateApplicationAndCreateResponse(
        type,
        process,
        request,
        requestAttributes,
        application,
        verificationConfiguration,
        tenant,
        processConfiguration);
  }

  @Override
  public IdentityVerificationCallbackResponse callback(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
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

    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, identifier);

    return updateApplicationAndCreateResponse(
        type,
        process,
        request,
        requestAttributes,
        application,
        verificationConfiguration,
        tenant,
        processConfiguration);
  }

  private IdentityVerificationCallbackResponse updateApplicationAndCreateResponse(
      IdentityVerificationType type,
      IdentityVerificationProcess process,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes,
      IdentityVerificationApplication application,
      IdentityVerificationConfiguration verificationConfiguration,
      Tenant tenant,
      IdentityVerificationProcessConfiguration processConfiguration) {
    User user = userQueryRepository.get(tenant, application.userIdentifier());

    // Run the same pipeline as the application path: pre_hook (verifications +
    // additional_parameters) → execute. For callback processes (no execution block) execute is
    // no_action, so the inbound result carried in the request is incorporated by
    // updateCallbackWith.
    // This makes callback processes honor the same config as the application path. (#1522)
    IdentityVerificationApplications previousApplications =
        applicationQueryRepository.findHistory(
            tenant, user, processConfiguration.historyPlan(type));
    IdentityVerificationApplyingResult applyingResult =
        identityVerificationApplicationHandler.executeRequest(
            tenant,
            user,
            application,
            previousApplications,
            type,
            process,
            request,
            requestAttributes,
            verificationConfiguration);
    if (applyingResult.isError()) {
      return IdentityVerificationCallbackResponse.CLIENT_ERROR(
          applyingResult.errorResponse().response());
    }

    IdentityVerificationContext context = applyingResult.applicationContext();

    // Merge uses updateCallbackWith (not updateProcessWith): the two are identical except the
    // fallback status when no transition condition matches — callback → EXAMINATION_PROCESSING
    // (awaiting async review), process → APPLYING. That difference is intentional, so the callback
    // keeps its own merge. The shared part (pre_hook + execute) is unified above. (#1522)
    IdentityVerificationApplication updatedApplication =
        application.updateCallbackWith(process, context, verificationConfiguration);
    applicationCommandRepository.update(tenant, updatedApplication);

    if (updatedApplication.isApproved()) {
      IdentityVerificationResult identityVerificationResult =
          IdentityVerificationResult.createOnCallback(
              updatedApplication, context, verificationConfiguration);
      resultCommandRepository.register(tenant, identityVerificationResult);

      User verifiedUser =
          IdentityVerificationUserUpdater.update(
              tenant,
              user,
              context,
              identityVerificationResult.verifiedClaims().toMap(),
              verificationConfiguration.result());

      userCommandRepository.update(tenant, verifiedUser);

      eventPublisher.publishSync(
          tenant,
          application.requestedClientId(),
          user,
          DefaultSecurityEventType.identity_verification_application_approved.toEventType(),
          requestAttributes);
      SecurityEventType typeSpecificApprovedEvent =
          new SecurityEventType(type.name() + "_approved");
      eventPublisher.publish(
          tenant,
          application.requestedClientId(),
          user,
          typeSpecificApprovedEvent,
          requestAttributes);
    }

    if (updatedApplication.isRejected()) {
      eventPublisher.publishSync(
          tenant,
          application.requestedClientId(),
          user,
          DefaultSecurityEventType.identity_verification_application_rejected.toEventType(),
          requestAttributes);
      SecurityEventType typeSpecificRejectedEvent =
          new SecurityEventType(type.name() + "_rejected");
      eventPublisher.publish(
          tenant,
          application.requestedClientId(),
          user,
          typeSpecificRejectedEvent,
          requestAttributes);
    }

    if (updatedApplication.isCancelled()) {
      eventPublisher.publishSync(
          tenant,
          application.requestedClientId(),
          user,
          DefaultSecurityEventType.identity_verification_application_cancelled.toEventType(),
          requestAttributes);
      SecurityEventType typeSpecificCancelledEvent =
          new SecurityEventType(type.name() + "_cancelled");
      eventPublisher.publish(
          tenant,
          application.requestedClientId(),
          user,
          typeSpecificCancelledEvent,
          requestAttributes);
    }

    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            context, processConfiguration.response());
    return IdentityVerificationCallbackResponse.OK(response);
  }
}
