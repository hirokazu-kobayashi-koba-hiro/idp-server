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
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.TokenEventPublisher;
import org.idp.server.platform.datasource.Transaction;
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
    this.identityVerificationApplicationHandler = new IdentityVerificationApplicationHandler();
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
        applicationQueryRepository.findAll(tenant, user);

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
            applyingResult.applicationContext(),
            process,
            verificationConfiguration);
    applicationCommandRepository.register(tenant, application);
    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_apply,
        requestAttributes);

    IdentityVerificationContext updatedContext =
        new IdentityVerificationContextBuilder()
            .previousContext(applyingResult.applicationContext())
            .application(application)
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
      return requestValidationResult.errorResponse();
    }

    IdentityVerificationApplications applications =
        applicationQueryRepository.findAll(tenant, user);

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
    if (applyingResult.isError()) {

      SecurityEventType securityEventType =
          new SecurityEventType(type.name() + "_" + process.name() + "_" + "failure");
      eventPublisher.publish(tenant, oAuthToken, securityEventType, requestAttributes);

      return applyingResult.errorResponse();
    }

    IdentityVerificationApplication updated =
        application.updateProcessWith(process, applyingResult, verificationConfiguration);
    applicationCommandRepository.update(tenant, updated);
    SecurityEventType securityEventType =
        new SecurityEventType(type.name() + "_" + process.name() + "_" + "success");

    eventPublisher.publish(tenant, oAuthToken, securityEventType, requestAttributes);

    if (updated.isApproved()) {
      IdentityVerificationResult identityVerificationResult =
          IdentityVerificationResult.create(
              updated, applyingResult.applicationContext(), verificationConfiguration);
      resultCommandRepository.register(tenant, identityVerificationResult);

      // TODO dynamic lifecycle management
      User verifiedUser =
          user.transitStatus(UserStatus.IDENTITY_VERIFIED)
              .setVerifiedClaims(identityVerificationResult.verifiedClaims().toMap());

      userCommandRepository.update(tenant, verifiedUser);
    }

    IdentityVerificationContext updatedContext =
        new IdentityVerificationContextBuilder()
            .previousContext(applyingResult.applicationContext())
            .application(application)
            .build();

    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            updatedContext, processConfig.response());

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
