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

import org.idp.server.control_plane.admin.starter.IdpServerStarterApi;
import org.idp.server.control_plane.admin.starter.IdpServerStarterContext;
import org.idp.server.control_plane.admin.starter.IdpServerStarterContextCreator;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterResponse;
import org.idp.server.control_plane.admin.starter.validator.IdpServerInitializeRequestValidationResult;
import org.idp.server.control_plane.admin.starter.validator.IdpServerInitializeRequestValidator;
import org.idp.server.control_plane.admin.starter.verifier.IdpServerStarterVerifier;
import org.idp.server.control_plane.admin.starter.verifier.IdpServerVerificationResult;
import org.idp.server.control_plane.admin.starter.verifier.StarterTenantVerifier;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.oidc.identity.permission.PermissionCommandRepository;
import org.idp.server.core.oidc.identity.repository.UserCommandRepository;
import org.idp.server.core.oidc.identity.role.RoleCommandRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class IdpServerStarterEntryService implements IdpServerStarterApi {

  OrganizationRepository organizationRepository;
  TenantCommandRepository tenantCommandRepository;
  UserCommandRepository userCommandRepository;
  PermissionCommandRepository permissionCommandRepository;
  RoleCommandRepository roleCommandRepository;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  IdpServerStarterVerifier starterVerifier;

  public IdpServerStarterEntryService(
      OrganizationRepository organizationRepository,
      TenantQueryRepository tenantQueryRepository,
      TenantCommandRepository tenantCommandRepository,
      UserCommandRepository userCommandRepository,
      PermissionCommandRepository permissionCommandRepository,
      RoleCommandRepository roleCommandRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.organizationRepository = organizationRepository;
    this.tenantCommandRepository = tenantCommandRepository;
    this.userCommandRepository = userCommandRepository;
    this.permissionCommandRepository = permissionCommandRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    StarterTenantVerifier starterTenantVerifier = new StarterTenantVerifier(tenantQueryRepository);
    this.starterVerifier = new IdpServerStarterVerifier(starterTenantVerifier);
  }

  @Override
  public IdpServerStarterResponse initialize(
      TenantIdentifier adminTenantIdentifier,
      IdpServerStarterRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdpServerInitializeRequestValidator requestValidator =
        new IdpServerInitializeRequestValidator(request, dryRun);
    IdpServerInitializeRequestValidationResult validated = requestValidator.validate();
    if (!validated.isValid()) {
      return validated.errorResponse();
    }

    IdpServerStarterContextCreator contextCreator =
        new IdpServerStarterContextCreator(request, dryRun, passwordEncodeDelegation);
    IdpServerStarterContext context = contextCreator.create();

    IdpServerVerificationResult verificationResult = starterVerifier.verify(context);
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
    permissionCommandRepository.bulkRegister(tenant, context.permissions());
    roleCommandRepository.bulkRegister(tenant, context.roles());
    userCommandRepository.register(tenant, context.user());
    clientConfigurationCommandRepository.register(tenant, context.clientConfiguration());

    return context.toResponse();
  }
}
