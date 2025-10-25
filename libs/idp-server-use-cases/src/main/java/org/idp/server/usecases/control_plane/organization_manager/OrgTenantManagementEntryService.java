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

package org.idp.server.usecases.control_plane.organization_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.management.tenant.OrgTenantManagementApi;
import org.idp.server.control_plane.management.tenant.handler.*;
import org.idp.server.control_plane.management.tenant.io.*;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerifier;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgTenantManagementEntryService implements OrgTenantManagementApi {

  AuditLogPublisher auditLogPublisher;
  private OrgTenantManagementHandler handler;

  public OrgTenantManagementEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      UserCommandRepository userCommandRepository,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;

    // Create verifiers
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    TenantManagementVerifier tenantManagementVerifier =
        new TenantManagementVerifier(tenantVerifier);

    // Create Handler
    this.handler =
        createHandler(
            tenantCommandRepository,
            tenantQueryRepository,
            organizationRepository,
            authorizationServerConfigurationCommandRepository,
            userCommandRepository,
            tenantManagementVerifier);
  }

  private OrgTenantManagementHandler createHandler(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      UserCommandRepository userCommandRepository,
      TenantManagementVerifier tenantManagementVerifier) {

    Map<String, TenantManagementService<?>> services = new HashMap<>();

    services.put(
        "create",
        new OrgTenantCreationService(
            tenantCommandRepository,
            organizationRepository,
            authorizationServerConfigurationCommandRepository,
            userCommandRepository,
            tenantManagementVerifier));

    services.put("findList", new TenantFindListService(tenantQueryRepository));
    services.put("get", new TenantFindService(tenantQueryRepository));
    services.put("update", new TenantUpdateService(tenantQueryRepository, tenantCommandRepository));
    services.put(
        "delete", new TenantDeletionService(tenantQueryRepository, tenantCommandRepository));

    return new OrgTenantManagementHandler(
        services, this, tenantQueryRepository, organizationRepository);
  }

  @Override
  public TenantManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    TenantManagementResult result =
        handler.handle(
            "create",
            authenticationContext,
            new OrgTenantCreationRequest(authenticationContext.organization(), request),
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public TenantManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    TenantManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            new TenantFindListRequest(
                authenticationContext
                    .organization()
                    .assignedTenants()
                    .tenantIdentifiers()), // No request object for findList (handler will pass
            // Organization)
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public TenantManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes) {

    TenantManagementResult result =
        handler.handle(
            "get",
            authenticationContext,
            new TenantFindRequest(tenantIdentifier),
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public TenantManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    TenantUpdateRequest updateRequest = new TenantUpdateRequest(tenantIdentifier, request);
    TenantManagementResult result =
        handler.handle("update", authenticationContext, updateRequest, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public TenantManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    TenantManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            new TenantDeleteRequest(tenantIdentifier),
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
