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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.federation.FederationConfigurationManagementApi;
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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class FederationConfigurationManagementEntryService
    implements FederationConfigurationManagementApi {

  private final FederationConfigManagementHandler handler;
  private final TenantQueryRepository tenantQueryRepository;
  private final FederationConfigurationQueryRepository federationConfigurationQueryRepository;
  private final AuditLogPublisher auditLogPublisher;

  public FederationConfigurationManagementEntryService(
      FederationConfigurationQueryRepository federationConfigurationQueryRepository,
      FederationConfigurationCommandRepository federationConfigurationCommandRepository,
      TenantQueryRepository tenantQueryRepository,
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

    this.handler = new FederationConfigManagementHandler(services, this);

    this.tenantQueryRepository = tenantQueryRepository;
    this.federationConfigurationQueryRepository = federationConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public FederationConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfigManagementResult result =
        handler.handle("create", tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "FederationConfigurationManagementApi.create",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public FederationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationQueries queries,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfigManagementResult result =
        handler.handle("findList", tenant, operator, oAuthToken, queries, requestAttributes, false);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "FederationConfigurationManagementApi.findList",
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
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfigManagementResult result =
        handler.handle("get", tenant, operator, oAuthToken, identifier, requestAttributes, false);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "FederationConfigurationManagementApi.get",
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
            "update", tenant, operator, oAuthToken, updateRequest, requestAttributes, dryRun);

    if (result.hasException()) {
      // Use before config for audit log if available
      FederationConfiguration before =
          federationConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "FederationConfigurationManagementApi.update",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public FederationConfigManagementResponse delete(
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
            "delete", tenant, operator, oAuthToken, identifier, requestAttributes, dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "FederationConfigurationManagementApi.delete",
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
            "FederationConfigurationManagementApi.delete",
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
