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

package org.idp.server.usecases.control_plane.tenant_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.management.onboarding.OnboardingApi;
import org.idp.server.control_plane.management.onboarding.OnboardingContext;
import org.idp.server.control_plane.management.onboarding.OnboardingContextCreator;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.management.onboarding.io.OnboardingStatus;
import org.idp.server.control_plane.management.onboarding.validator.OnboardingRequestValidationResult;
import org.idp.server.control_plane.management.onboarding.validator.OnboardingRequestValidator;
import org.idp.server.control_plane.management.onboarding.verifier.OnboardingVerificationResult;
import org.idp.server.control_plane.management.onboarding.verifier.OnboardingVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.*;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OnboardingEntryService implements OnboardingApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  UserRegistrator userRegistrator;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  OnboardingVerifier onboardingVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OnboardingEntryService.class);

  public OnboardingEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository, userCommandRepository);
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    this.onboardingVerifier = new OnboardingVerifier(tenantVerifier);
  }

  public OnboardingResponse onboard(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OnboardingRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("onboard");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new OnboardingResponse(OnboardingStatus.FORBIDDEN, response);
    }

    OnboardingRequestValidator validator = new OnboardingRequestValidator(request, dryRun);
    OnboardingRequestValidationResult validationResult = validator.validate();
    if (!validationResult.isValid()) {
      return validationResult.errorResponse();
    }

    OnboardingContextCreator contextCreator =
        new OnboardingContextCreator(request, operator, dryRun);
    OnboardingContext context = contextCreator.create();

    OnboardingVerificationResult verificationResult = onboardingVerifier.verify(context);
    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    Tenant tenant = context.tenant();
    tenantCommandRepository.register(tenant);
    authorizationServerConfigurationCommandRepository.register(
        tenant, context.authorizationServerConfiguration());
    organizationRepository.register(tenant, context.organization());
    Tenant admin = tenantQueryRepository.getAdmin();
    userRegistrator.registerOrUpdate(admin, context.user());
    clientConfigurationCommandRepository.register(tenant, context.clientConfiguration());

    return context.toResponse();
  }
}
