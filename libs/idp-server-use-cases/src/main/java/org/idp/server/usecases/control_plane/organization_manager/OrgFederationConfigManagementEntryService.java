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
import org.idp.server.control_plane.management.federation.FederationConfigRegistrationContext;
import org.idp.server.control_plane.management.federation.FederationConfigUpdateContext;
import org.idp.server.control_plane.management.federation.OrgFederationConfigManagementApi;
import org.idp.server.control_plane.management.federation.handler.*;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.FederationQueries;
import org.idp.server.core.openid.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
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
 * @see OrgFederationConfigManagementApi
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.FederationConfigurationManagementEntryService
 */
@Transaction
public class OrgFederationConfigManagementEntryService implements OrgFederationConfigManagementApi {

  private final OrgFederationConfigManagementHandler handler;
  private final TenantQueryRepository tenantQueryRepository;
  private final FederationConfigurationQueryRepository federationConfigurationQueryRepository;
  private final AuditLogPublisher auditLogPublisher;

  public OrgFederationConfigManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      FederationConfigurationCommandRepository federationConfigurationCommandRepository,
      FederationConfigurationQueryRepository federationConfigurationQueryRepository,
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

    this.handler = new OrgFederationConfigManagementHandler(services, organizationRepository, this);

    this.tenantQueryRepository = tenantQueryRepository;
    this.federationConfigurationQueryRepository = federationConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public FederationConfigManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfigManagementResult result =
        handler.handle(
            "create",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            request,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgFederationConfigManagementApi.create",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public FederationConfigManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationQueries queries,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfigManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            queries,
            requestAttributes,
            false);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgFederationConfigManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public FederationConfigManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfigManagementResult result =
        handler.handle(
            "get",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            identifier,
            requestAttributes,
            false);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgFederationConfigManagementApi.get",
            "get",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public FederationConfigManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Wrap request for handler
    FederationConfigUpdateRequest updateRequest =
        new FederationConfigUpdateRequest(identifier, request);

    FederationConfigManagementResult result =
        handler.handle(
            "update",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgFederationConfigManagementApi.update",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.create(
            result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public FederationConfigManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Get configuration for audit log before deletion
    FederationConfiguration configuration =
        federationConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    FederationConfigManagementResult result =
        handler.handle(
            "delete",
            organizationIdentifier,
            tenant,
            operator,
            oAuthToken,
            identifier,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgFederationConfigManagementApi.delete",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgFederationConfigManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            configuration.payload(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
