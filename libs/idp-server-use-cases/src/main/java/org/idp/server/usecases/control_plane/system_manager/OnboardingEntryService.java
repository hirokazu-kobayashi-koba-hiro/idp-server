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

package org.idp.server.usecases.control_plane.system_manager;

import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.management.onboarding.OnboardingApi;
import org.idp.server.control_plane.management.onboarding.handler.OnboardingManagementHandler;
import org.idp.server.control_plane.management.onboarding.handler.OnboardingManagementResult;
import org.idp.server.control_plane.management.onboarding.handler.OnboardingService;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.management.onboarding.verifier.OnboardingVerifier;
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OnboardingEntryService implements OnboardingApi {

  private final OnboardingManagementHandler handler;
  private final TenantQueryRepository tenantQueryRepository;
  private final AuditLogPublisher auditLogPublisher;

  public OnboardingEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      PermissionCommandRepository permissionCommandRepository,
      RoleCommandRepository roleCommandRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      AuditLogPublisher auditLogPublisher) {

    UserRegistrator userRegistrator =
        new UserRegistrator(userQueryRepository, userCommandRepository);
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    OnboardingVerifier onboardingVerifier = new OnboardingVerifier(tenantVerifier);

    OnboardingService service =
        new OnboardingService(
            tenantCommandRepository,
            organizationRepository,
            permissionCommandRepository,
            roleCommandRepository,
            userRegistrator,
            authorizationServerConfigurationCommandRepository,
            clientConfigurationCommandRepository,
            onboardingVerifier,
            passwordEncodeDelegation);

    this.handler = new OnboardingManagementHandler(service, this);
    this.tenantQueryRepository = tenantQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  public OnboardingResponse onboard(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier adminTenantIdentifier,
      OnboardingRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    OnboardingManagementResult result =
        handler.handle(
            authenticationContext, adminTenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
