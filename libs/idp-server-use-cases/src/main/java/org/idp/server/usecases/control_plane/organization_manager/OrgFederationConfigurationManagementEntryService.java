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
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.management.federation.OrgFederationConfigManagementApi;
import org.idp.server.control_plane.management.federation.handler.*;
import org.idp.server.control_plane.management.federation.io.*;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.FederationQueries;
import org.idp.server.core.openid.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level federation configuration management entry service.
 *
 * <p>This service implements organization-scoped federation configuration management operations
 * that allow organization administrators to manage federation configurations within their
 * organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       FEDERATION_CONFIG_* permissions
 * </ol>
 *
 * <p>This service provides federation configuration CRUD operations with comprehensive audit
 * logging for organization-level configuration management.
 *
 * @see OrgFederationConfigManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.FederationConfigurationManagementEntryService
 */
@Transaction
public class OrgFederationConfigurationManagementEntryService
    implements OrgFederationConfigManagementApi {

  private final OrgFederationConfigManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new organization federation configuration management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param federationConfigurationQueryRepository the federation configuration query repository
   * @param federationConfigurationCommandRepository the federation configuration command repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgFederationConfigurationManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      FederationConfigurationQueryRepository federationConfigurationQueryRepository,
      FederationConfigurationCommandRepository federationConfigurationCommandRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, FederationConfigManagementService<?>> services = new HashMap<>();
    services.put(
        "create", new FederationConfigCreationService(federationConfigurationCommandRepository));
    services.put(
        "findList", new FederationConfigFindListService(federationConfigurationQueryRepository));
    services.put("get", new FederationConfigFindService(federationConfigurationQueryRepository));
    services.put(
        "update",
        new FederationConfigUpdateService(
            federationConfigurationQueryRepository, federationConfigurationCommandRepository));
    services.put(
        "delete",
        new FederationConfigDeletionService(
            federationConfigurationQueryRepository, federationConfigurationCommandRepository));

    this.handler =
        new OrgFederationConfigManagementHandler(
            services,
            this,
            tenantQueryRepository,
            organizationRepository,
            new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public FederationConfigManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    FederationConfigManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public FederationConfigManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      FederationQueries queries,
      RequestAttributes requestAttributes) {

    FederationConfigFindListRequest request = new FederationConfigFindListRequest(queries);
    FederationConfigManagementResult result =
        handler.handle(
            "findList", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public FederationConfigManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    FederationConfigFindRequest request = new FederationConfigFindRequest(identifier);
    FederationConfigManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public FederationConfigManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      FederationConfigurationIdentifier identifier,
      FederationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    FederationConfigManagementResult result =
        handler.handle(
            "update", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public FederationConfigManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    FederationConfigDeleteRequest deleteRequest = new FederationConfigDeleteRequest(identifier);
    FederationConfigManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            tenantIdentifier,
            deleteRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
