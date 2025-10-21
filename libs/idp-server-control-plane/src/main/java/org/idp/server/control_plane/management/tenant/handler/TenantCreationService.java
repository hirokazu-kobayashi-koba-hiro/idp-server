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

package org.idp.server.control_plane.management.tenant.handler;

import org.idp.server.control_plane.management.tenant.TenantManagementRegistrationContext;
import org.idp.server.control_plane.management.tenant.TenantManagementRegistrationContextCreator;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.control_plane.management.tenant.validator.TenantRequestValidator;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating new tenants.
 *
 * <p>Handles tenant creation logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation
 *   <li>Context creation
 *   <li>Business rule verification
 *   <li>Tenant registration in repository
 *   <li>Organization update
 *   <li>Authorization server configuration registration
 *   <li>User update (operator's assigned tenants)
 * </ul>
 */
public class TenantCreationService implements TenantManagementService<TenantRequest> {

  private final TenantCommandRepository tenantCommandRepository;
  private final OrganizationRepository organizationRepository;
  private final AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  private final UserCommandRepository userCommandRepository;
  private final TenantManagementVerifier tenantManagementVerifier;

  public TenantCreationService(
      TenantCommandRepository tenantCommandRepository,
      OrganizationRepository organizationRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      UserCommandRepository userCommandRepository,
      TenantManagementVerifier tenantManagementVerifier) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.organizationRepository = organizationRepository;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.userCommandRepository = userCommandRepository;
    this.tenantManagementVerifier = tenantManagementVerifier;
  }

  @Override
  public TenantManagementResult execute(
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Request validation
    new TenantRequestValidator(request, dryRun).validate();

    // 2. Get organization
    OrganizationIdentifier organizationIdentifier = operator.currentOrganizationIdentifier();
    Organization organization = organizationRepository.get(organizationIdentifier);

    // 3. Context creation
    TenantManagementRegistrationContextCreator contextCreator =
        new TenantManagementRegistrationContextCreator(
            adminTenant, request, organization, operator, dryRun);
    TenantManagementRegistrationContext context = contextCreator.create();

    // 4. Business rule verification
    tenantManagementVerifier.verify(context);

    // 5. Dry-run check
    if (dryRun) {
      return TenantManagementResult.success(adminTenant, context, context.toResponse());
    }

    // 6. Repository operations
    tenantCommandRepository.register(context.newTenant());
    organizationRepository.update(context.organization());
    authorizationServerConfigurationCommandRepository.register(
        context.newTenant(), context.authorizationServerConfiguration());
    userCommandRepository.update(adminTenant, context.user());

    return TenantManagementResult.success(adminTenant, context, context.toResponse());
  }
}
