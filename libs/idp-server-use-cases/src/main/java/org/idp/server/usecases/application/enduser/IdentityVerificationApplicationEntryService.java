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
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.*;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationHandler;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplyingResult;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationQueries;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolver;
import org.idp.server.core.extension.identity.verification.application.validation.IdentityVerificationApplicationRequestValidator;
import org.idp.server.core.extension.identity.verification.application.validation.IdentityVerificationApplicationValidationResult;
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
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.UserEventPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class IdentityVerificationApplicationEntryService
    implements IdentityVerificationApplicationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationCommandRepository applicationCommandRepository;
  IdentityVerificationApplicationQueryRepository applicationQueryRepository;
  IdentityVerificationResultCommandRepository resultCommandRepository;
  IdentityVerificationApplicationHandler identityVerificationApplicationHandler;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  UserEventPublisher eventPublisher;

  public IdentityVerificationApplicationEntryService(
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
  public IdentityVerificationApplicationResponse apply(
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
    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationApplicationRequestValidator applicationValidator =
        new IdentityVerificationApplicationRequestValidator(
            processConfig, request, requestAttributes);
    IdentityVerificationApplicationValidationResult requestValidationResult =
        applicationValidator.validate();

    if (requestValidationResult.isError()) {
      return requestValidationResult.errorResponse();
    }

    IdentityVerificationApplications applications =
        applicationQueryRepository.findHistory(tenant, user, processConfig.historyPlan(type));

    IdentityVerificationApplyingResult applyingResult =
        identityVerificationApplicationHandler.executeRequest(
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
      Map<String, Object> executionResult = applyingResult.errorResponse().response();
      eventPublisher.publish(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_failure,
          executionResult,
          requestAttributes);
      SecurityEventType typeSpecificFailureEvent =
          new SecurityEventType(type.name() + "_application_failure");
      eventPublisher.publish(
          tenant, oAuthToken, typeSpecificFailureEvent, executionResult, requestAttributes);
      return applyingResult.errorResponse();
    }

    IdentityVerificationApplication application =
        IdentityVerificationApplication.create(
            tenant,
            oAuthToken.requestedClientId(),
            user,
            type,
            applyingResult.applicationContext(),
            process,
            verificationConfiguration);
    applicationCommandRepository.register(tenant, application);
    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_apply,
        requestAttributes);
    SecurityEventType typeSpecificApplyEvent =
        new SecurityEventType(type.name() + "_application_success");
    eventPublisher.publish(tenant, oAuthToken, typeSpecificApplyEvent, requestAttributes);

    handleTerminalTransition(
        tenant,
        user,
        oAuthToken,
        type,
        application,
        applyingResult,
        verificationConfiguration,
        requestAttributes);

    IdentityVerificationContext updatedContext =
        new IdentityVerificationContextBuilder()
            .previousContext(applyingResult.applicationContext())
            .application(application)
            .user(user)
            .build();
    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            updatedContext, processConfig.response());
    response.put("id", application.identifier().value());

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

    long totalCount = applicationQueryRepository.findTotalCount(tenant, user, queries);
    if (totalCount == 0) {

      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return IdentityVerificationApplicationResponse.OK(response);
    }

    IdentityVerificationApplications applications =
        applicationQueryRepository.findList(tenant, user, queries);

    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_findList,
        requestAttributes);

    Map<String, Object> response = new HashMap<>();
    response.put("list", applications.toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
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
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationApplication application =
        applicationQueryRepository.get(tenant, user, identifier);
    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);
    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(process);

    IdentityVerificationApplicationRequestValidator applicationValidator =
        new IdentityVerificationApplicationRequestValidator(
            processConfig, request, requestAttributes);
    IdentityVerificationApplicationValidationResult requestValidationResult =
        applicationValidator.validate();

    if (requestValidationResult.isError()) {

      IdentityVerificationApplicationResponse errorResponse =
          requestValidationResult.errorResponse();
      SecurityEventType securityEventType =
          new SecurityEventType(type.name() + "_" + process.name() + "_" + "failure");
      eventPublisher.publish(
          tenant, oAuthToken, securityEventType, errorResponse.response(), requestAttributes);
      return errorResponse;
    }

    IdentityVerificationApplications applications =
        applicationQueryRepository.findHistory(tenant, user, processConfig.historyPlan(type));

    IdentityVerificationApplyingResult applyingResult =
        identityVerificationApplicationHandler.executeRequest(
            tenant,
            user,
            application,
            applications,
            type,
            process,
            request,
            requestAttributes,
            verificationConfiguration);
    // Record the process attempt for both success and failure. updateProcessWith advances
    // success_count or failure_count from applyingResult, so failure_count-based conditions
    // (lock_conditions / retry limits) work for failed attempts too (#1608). An unsuccessful
    // attempt
    // (verification / pre-hook / execution error) holds the status in place; only successful
    // attempts
    // re-evaluate it, and the result is reconciled to forbid backward / terminal-overwriting
    // transitions. (#1617)
    IdentityVerificationApplication updated =
        application.updateProcessWith(process, applyingResult, verificationConfiguration);
    applicationCommandRepository.update(tenant, updated);

    if (applyingResult.isError()) {
      SecurityEventType securityEventType =
          new SecurityEventType(type.name() + "_" + process.name() + "_" + "failure");
      eventPublisher.publish(
          tenant,
          oAuthToken,
          securityEventType,
          applyingResult.errorResponse().response(),
          requestAttributes);

      return applyingResult.errorResponse();
    }

    SecurityEventType securityEventType =
        new SecurityEventType(type.name() + "_" + process.name() + "_" + "success");
    eventPublisher.publish(tenant, oAuthToken, securityEventType, requestAttributes);

    handleTerminalTransition(
        tenant,
        user,
        oAuthToken,
        type,
        updated,
        applyingResult,
        verificationConfiguration,
        requestAttributes);

    IdentityVerificationContext updatedContext =
        new IdentityVerificationContextBuilder()
            .previousContext(applyingResult.applicationContext())
            .application(application)
            .user(user)
            .build();

    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            updatedContext, processConfig.response());

    return IdentityVerificationApplicationResponse.OK(response);
  }

  /**
   * Applies the terminal transition (approved / rejected / cancelled) reached by an application.
   * Shared by the single-process immediate-approval path ({@link #apply}) and the multi-step path
   * ({@link #process}) so the on-approval side effects — registering the verification result,
   * patching user attributes via {@link IdentityVerificationUserUpdater}, and publishing lifecycle
   * security events — stay in one place.
   */
  private void handleTerminalTransition(
      Tenant tenant,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationType type,
      IdentityVerificationApplication application,
      IdentityVerificationApplyingResult applyingResult,
      IdentityVerificationConfiguration verificationConfiguration,
      RequestAttributes requestAttributes) {

    if (application.isApproved()) {
      IdentityVerificationResult identityVerificationResult =
          IdentityVerificationResult.create(
              application, applyingResult.applicationContext(), verificationConfiguration);
      resultCommandRepository.register(tenant, identityVerificationResult);

      User verifiedUser =
          IdentityVerificationUserUpdater.update(
              tenant,
              user,
              applyingResult.applicationContext(),
              identityVerificationResult.verifiedClaims().toMap(),
              verificationConfiguration.result());
      userCommandRepository.update(tenant, verifiedUser);
      eventPublisher.publishSync(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_approved,
          requestAttributes);
      eventPublisher.publish(
          tenant, oAuthToken, new SecurityEventType(type.name() + "_approved"), requestAttributes);
    }

    if (application.isRejected()) {
      eventPublisher.publishSync(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_rejected,
          requestAttributes);
      eventPublisher.publish(
          tenant, oAuthToken, new SecurityEventType(type.name() + "_rejected"), requestAttributes);
    }

    if (application.isCancelled()) {
      eventPublisher.publishSync(
          tenant,
          oAuthToken,
          DefaultSecurityEventType.identity_verification_application_cancelled,
          requestAttributes);
      eventPublisher.publish(
          tenant, oAuthToken, new SecurityEventType(type.name() + "_cancelled"), requestAttributes);
    }
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
