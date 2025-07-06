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
import org.idp.server.core.extension.identity.verification.claims.VerifiedClaims;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationRegistrationConfiguration;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationResponse;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultCommandRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationRequestValidator;
import org.idp.server.core.extension.identity.verification.validation.IdentityVerificationValidationResult;
import org.idp.server.core.extension.identity.verification.verifier.result.IdentityVerificationRequestVerifiedResult;
import org.idp.server.core.extension.identity.verification.verifier.result.IdentityVerificationRequestVerifiers;
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
public class IdentityVerificationEntryService implements IdentityVerificationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationResultCommandRepository resultCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  IdentityVerificationRequestVerifiers verifiers;
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
    this.tenantQueryRepository = tenantQueryRepository;
    this.resultCommandRepository = resultCommandRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.verifiers = new IdentityVerificationRequestVerifiers();
    this.eventPublisher = eventPublisher;
  }

  @Override
  public IdentityVerificationResponse register(
      TenantIdentifier tenantIdentifier,
      BasicAuth basicAuth,
      IdentityVerificationType type,
      IdentityVerificationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, type);

    IdentityVerificationRegistrationConfiguration registrationConfiguration =
        verificationConfiguration.registrationConfiguration();

    IdentityVerificationRequestValidator verificationRequestValidator =
        new IdentityVerificationRequestValidator(registrationConfiguration, request);
    IdentityVerificationValidationResult validationResult = verificationRequestValidator.validate();

    if (validationResult.isError()) {

      return validationResult.errorResponse();
    }

    User user = userQueryRepository.get(tenant, request.userIdentifier());

    IdentityVerificationRequestVerifiedResult verifiedResult =
        verifiers.verify(
            tenant, user, basicAuth, type, request, requestAttributes, verificationConfiguration);

    if (verifiedResult.isError()) {
      return verifiedResult.errorResponse();
    }

    IdentityVerificationResult identityVerificationResult =
        IdentityVerificationResult.createOnDirect(
            tenantIdentifier, type, request, verificationConfiguration);
    VerifiedClaims verifiedClaims =
        VerifiedClaims.create(request, verificationConfiguration.verifiedClaimsConfiguration());

    resultCommandRepository.register(tenant, identityVerificationResult);

    // TODO dynamic lifecycle management
    User verifiedUser =
        user.transitStatus(UserStatus.IDENTITY_VERIFIED).setVerifiedClaims(verifiedClaims.toMap());

    userCommandRepository.update(tenant, verifiedUser);

    Map<String, Object> response = new HashMap<>();
    return IdentityVerificationResponse.OK(response);
  }
}
