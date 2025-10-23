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
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementApi;
import org.idp.server.control_plane.management.identity.verification.handler.IdentityVerificationConfigCreationService;
import org.idp.server.control_plane.management.identity.verification.handler.IdentityVerificationConfigDeletionService;
import org.idp.server.control_plane.management.identity.verification.handler.IdentityVerificationConfigFindListService;
import org.idp.server.control_plane.management.identity.verification.handler.IdentityVerificationConfigFindService;
import org.idp.server.control_plane.management.identity.verification.handler.IdentityVerificationConfigManagementHandler;
import org.idp.server.control_plane.management.identity.verification.handler.IdentityVerificationConfigManagementResult;
import org.idp.server.control_plane.management.identity.verification.handler.IdentityVerificationConfigManagementService;
import org.idp.server.control_plane.management.identity.verification.handler.IdentityVerificationConfigUpdateService;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationQueries;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
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
public class IdentityVerificationConfigManagementEntryService
    implements IdentityVerificationConfigManagementApi {

  private final IdentityVerificationConfigManagementHandler handler;
  private final TenantQueryRepository tenantQueryRepository;
  private final IdentityVerificationConfigurationQueryRepository
      identityVerificationConfigurationQueryRepository;
  private final AuditLogPublisher auditLogPublisher;

  public IdentityVerificationConfigManagementEntryService(
      IdentityVerificationConfigurationCommandRepository
          identityVerificationConfigurationCommandRepository,
      IdentityVerificationConfigurationQueryRepository
          identityVerificationConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, IdentityVerificationConfigManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new IdentityVerificationConfigCreationService(
            identityVerificationConfigurationCommandRepository));
    services.put(
        "findList",
        new IdentityVerificationConfigFindListService(
            identityVerificationConfigurationQueryRepository));
    services.put(
        "get",
        new IdentityVerificationConfigFindService(
            identityVerificationConfigurationQueryRepository));
    services.put(
        "update",
        new IdentityVerificationConfigUpdateService(
            identityVerificationConfigurationQueryRepository,
            identityVerificationConfigurationCommandRepository));
    services.put(
        "delete",
        new IdentityVerificationConfigDeletionService(
            identityVerificationConfigurationQueryRepository,
            identityVerificationConfigurationCommandRepository));

    this.handler = new IdentityVerificationConfigManagementHandler(services, this);

    this.tenantQueryRepository = tenantQueryRepository;
    this.identityVerificationConfigurationQueryRepository =
        identityVerificationConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public IdentityVerificationConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfigManagementResult result =
        handler.handle("create", tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "IdentityVerificationConfigManagementApi.create",
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
  public IdentityVerificationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationQueries queries,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfigManagementResult result =
        handler.handle("findList", tenant, operator, oAuthToken, queries, requestAttributes, false);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "IdentityVerificationConfigManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public IdentityVerificationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfigManagementResult result =
        handler.handle("get", tenant, operator, oAuthToken, identifier, requestAttributes, false);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "IdentityVerificationConfigManagementApi.get",
            "get",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  public IdentityVerificationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      IdentityVerificationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Inject identifier into request
    IdentityVerificationConfigUpdateRequest requestWithId =
        new IdentityVerificationConfigUpdateRequest(identifier.value(), request.toMap());

    IdentityVerificationConfigManagementResult result =
        handler.handle(
            "update", tenant, operator, oAuthToken, requestWithId, requestAttributes, dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "IdentityVerificationConfigManagementApi.update",
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
  public IdentityVerificationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.find(tenant, identifier);

    IdentityVerificationConfigManagementResult result =
        handler.handle(
            "delete", tenant, operator, oAuthToken, identifier, requestAttributes, dryRun);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "IdentityVerificationConfigManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            configuration.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
