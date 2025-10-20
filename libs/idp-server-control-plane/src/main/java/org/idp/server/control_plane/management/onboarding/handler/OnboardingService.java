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

package org.idp.server.control_plane.management.onboarding.handler;

import org.idp.server.control_plane.management.onboarding.OnboardingContext;
import org.idp.server.control_plane.management.onboarding.OnboardingContextCreator;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.management.onboarding.validator.OnboardingRequestValidator;
import org.idp.server.control_plane.management.onboarding.verifier.OnboardingVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for onboarding operations.
 *
 * <p>Handles tenant onboarding logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation via OnboardingRequestValidator (throws InvalidRequestException)
 *   <li>Context creation via OnboardingContextCreator
 *   <li>Business rule verification via OnboardingVerifier (throws InvalidRequestException)
 *   <li>Tenant/Organization/User/Client registration (or dry-run simulation)
 * </ul>
 */
public class OnboardingService {

  private final TenantCommandRepository tenantCommandRepository;
  private final OrganizationRepository organizationRepository;
  private final PermissionCommandRepository permissionCommandRepository;
  private final RoleCommandRepository roleCommandRepository;
  private final UserRegistrator userRegistrator;
  private final AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  private final ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  private final OnboardingVerifier onboardingVerifier;
  private final PasswordEncodeDelegation passwordEncodeDelegation;

  public OnboardingService(
      TenantCommandRepository tenantCommandRepository,
      OrganizationRepository organizationRepository,
      PermissionCommandRepository permissionCommandRepository,
      RoleCommandRepository roleCommandRepository,
      UserRegistrator userRegistrator,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      OnboardingVerifier onboardingVerifier,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.organizationRepository = organizationRepository;
    this.permissionCommandRepository = permissionCommandRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.userRegistrator = userRegistrator;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.onboardingVerifier = onboardingVerifier;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  public OnboardingManagementResult execute(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      OnboardingRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    new OnboardingRequestValidator(request, dryRun).validate();

    OnboardingContextCreator contextCreator =
        new OnboardingContextCreator(request, passwordEncodeDelegation, dryRun);
    OnboardingContext context = contextCreator.create();

    onboardingVerifier.verify(context);

    if (dryRun) {
      return OnboardingManagementResult.success(
          adminTenantIdentifier, context.toResponse(), context);
    }

    Tenant tenant = context.tenant();
    tenantCommandRepository.register(tenant);
    authorizationServerConfigurationCommandRepository.register(
        tenant, context.authorizationServerConfiguration());
    organizationRepository.register(context.organization());
    permissionCommandRepository.bulkRegister(tenant, context.permissions());
    roleCommandRepository.bulkRegister(tenant, context.roles());
    userRegistrator.registerOrUpdate(tenant, context.user());
    clientConfigurationCommandRepository.register(tenant, context.clientConfiguration());

    return OnboardingManagementResult.success(adminTenantIdentifier, context.toResponse(), context);
  }
}
