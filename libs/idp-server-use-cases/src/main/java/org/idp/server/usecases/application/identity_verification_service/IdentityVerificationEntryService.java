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
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApi;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContextBuilder;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.pre_hook.basic_auth.IdentityVerificationRequestVerifiedResult;
import org.idp.server.core.extension.identity.verification.application.pre_hook.basic_auth.IdentityVerificationRequestVerifiers;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.registration.IdentityVerificationRegistrationConfig;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationDynamicResponseMapper;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.extension.identity.verification.registration.IdentityVerificationRequestValidator;
import org.idp.server.core.extension.identity.verification.registration.IdentityVerificationValidationResult;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultQueryRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultQueries;
import org.idp.server.core.extension.identity.verified.VerifiedClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.UserEventPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class IdentityVerificationEntryService implements IdentityVerificationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationResultCommandRepository resultCommandRepository;
  IdentityVerificationResultQueryRepository resultQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  IdentityVerificationRequestVerifiers verifiers;
  UserEventPublisher eventPublisher;

  public IdentityVerificationEntryService(
      IdentityVerificationConfigurationQueryRepository configurationQueryRepository,
      IdentityVerificationResultCommandRepository resultCommandRepository,
      IdentityVerificationResultQueryRepository resultQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      UserEventPublisher eventPublisher) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.resultCommandRepository = resultCommandRepository;
    this.resultQueryRepository = resultQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.verifiers = new IdentityVerificationRequestVerifiers();
    this.eventPublisher = eventPublisher;
  }

  @Override
  public IdentityVerificationResponse register(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);

    IdentityVerificationRegistrationConfig registrationConfiguration =
        verificationConfiguration.registration();

    IdentityVerificationRequestValidator verificationRequestValidator =
        new IdentityVerificationRequestValidator(registrationConfiguration, request);
    IdentityVerificationValidationResult validationResult = verificationRequestValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    // TODO improve type-safe request parameter access (#1268)
    UserIdentifier userIdentifier = new UserIdentifier(request.getValueAsString("user_id"));
    User user = userQueryRepository.get(tenant, userIdentifier);

    IdentityVerificationRequestVerifiedResult verifiedResult =
        verifiers.verify(tenant, user, type, request, requestAttributes, verificationConfiguration);

    if (verifiedResult.isError()) {
      return verifiedResult.errorResponse();
    }

    IdentityVerificationContext context =
        new IdentityVerificationContextBuilder()
            .request(request)
            .requestAttributes(requestAttributes)
            .build();

    IdentityVerificationResult identityVerificationResult =
        IdentityVerificationResult.createOnDirect(
            tenantIdentifier, user, type, context, verificationConfiguration);
    VerifiedClaims verifiedClaims = identityVerificationResult.verifiedClaims();

    resultCommandRepository.register(tenant, identityVerificationResult);

    // TODO dynamic lifecycle management (#1268)
    User verifiedUser =
        user.transitStatus(UserStatus.IDENTITY_VERIFIED).setVerifiedClaims(verifiedClaims.toMap());

    userCommandRepository.update(tenant, verifiedUser);

    Map<String, Object> response =
        IdentityVerificationDynamicResponseMapper.buildDynamicResponse(
            context, registrationConfiguration.response());

    return IdentityVerificationResponse.OK(response);
  }

  @Override
  public IdentityVerificationResponse findList(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      IdentityVerificationResultQueries queries,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    long totalCount = resultQueryRepository.findTotalCount(tenant, user, queries);

    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return IdentityVerificationResponse.OK(response);
    }

    List<IdentityVerificationResult> resultList =
        resultQueryRepository.findList(tenant, user, queries);

    eventPublisher.publish(
        tenant,
        oAuthToken,
        DefaultSecurityEventType.identity_verification_application_findList,
        requestAttributes);

    Map<String, Object> response = new HashMap<>();
    response.put("list", resultList.stream().map(IdentityVerificationResult::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
    return IdentityVerificationResponse.OK(response);
  }
}
