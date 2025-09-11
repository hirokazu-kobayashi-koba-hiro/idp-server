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

package org.idp.server.usecases.control_plane.system_administrator;

import org.idp.server.control_plane.admin.organization.OrganizationInitializationApi;
import org.idp.server.control_plane.admin.organization.OrganizationInitializationContext;
import org.idp.server.control_plane.admin.organization.OrganizationInitializationContextCreator;
import org.idp.server.control_plane.admin.organization.io.OrganizationInitializationRequest;
import org.idp.server.control_plane.admin.organization.io.OrganizationInitializationResponse;
import org.idp.server.control_plane.admin.organization.validator.OrganizationInitializeRequestValidationResult;
import org.idp.server.control_plane.admin.organization.validator.OrganizationInitializeRequestValidator;
import org.idp.server.control_plane.admin.organization.verifier.OrganizationInitializationVerificationResult;
import org.idp.server.control_plane.admin.organization.verifier.OrganizationInitializationVerifier;
import org.idp.server.control_plane.base.verifier.ClientVerifier;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrganizationInitializationEntryService implements OrganizationInitializationApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  UserRegistrator userRegistrator;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  OrganizationInitializationVerifier organizationInitializationVerifier;
  PasswordEncodeDelegation passwordEncodeDelegation;

  public OrganizationInitializationEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository, userCommandRepository);
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    ClientVerifier clientVerifier = new ClientVerifier(clientConfigurationQueryRepository);
    this.organizationInitializationVerifier =
        new OrganizationInitializationVerifier(tenantVerifier, clientVerifier);
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  @Override
  public OrganizationInitializationResponse initialize(
      TenantIdentifier adminTenantIdentifier,
      OrganizationInitializationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    OrganizationInitializeRequestValidator validator =
        new OrganizationInitializeRequestValidator(request, dryRun);
    OrganizationInitializeRequestValidationResult validationResult = validator.validate();
    if (!validationResult.isValid()) {
      return validationResult.errorResponse();
    }

    OrganizationInitializationContextCreator contextCreator =
        new OrganizationInitializationContextCreator(request, dryRun, passwordEncodeDelegation);
    OrganizationInitializationContext context = contextCreator.create();

    OrganizationInitializationVerificationResult verificationResult =
        organizationInitializationVerifier.verify(context);

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
    organizationRepository.register(context.organization());
    Tenant admin = tenantQueryRepository.getAdmin();
    userRegistrator.registerOrUpdate(admin, context.user());
    clientConfigurationCommandRepository.register(tenant, context.clientConfiguration());

    return context.toResponse();
  }
}
