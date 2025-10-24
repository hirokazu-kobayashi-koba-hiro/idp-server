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
import org.idp.server.control_plane.management.identity.verification.OrgIdentityVerificationConfigManagementApi;
import org.idp.server.control_plane.management.identity.verification.handler.*;
import org.idp.server.control_plane.management.identity.verification.io.*;
import org.idp.server.core.extension.identity.verification.configuration.*;
import org.idp.server.core.extension.identity.verification.repository.*;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgIdentityVerificationConfigManagementEntryService
    implements OrgIdentityVerificationConfigManagementApi {

  private final OrgIdentityVerificationConfigManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public OrgIdentityVerificationConfigManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      IdentityVerificationConfigurationCommandRepository
          identityVerificationConfigurationCommandRepository,
      IdentityVerificationConfigurationQueryRepository
          identityVerificationConfigurationQueryRepository,
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

    this.handler =
        new OrgIdentityVerificationConfigManagementHandler(
            services,
            this,
            tenantQueryRepository,
            organizationRepository,
            new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public IdentityVerificationConfigManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationConfigManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public IdentityVerificationConfigManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationQueries queries,
      RequestAttributes requestAttributes) {

    IdentityVerificationConfigFindListRequest findListRequest =
        new IdentityVerificationConfigFindListRequest(queries);
    IdentityVerificationConfigManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            findListRequest,
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public IdentityVerificationConfigManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    IdentityVerificationConfigFindRequest findRequest =
        new IdentityVerificationConfigFindRequest(identifier);
    IdentityVerificationConfigManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public IdentityVerificationConfigManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigurationIdentifier identifier,
      IdentityVerificationConfigUpdateRequest updateRequest,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationConfigUpdateRequest request =
        new IdentityVerificationConfigUpdateRequest(
            identifier, new IdentityVerificationConfigRegistrationRequest(updateRequest.toMap()));
    IdentityVerificationConfigManagementResult result =
        handler.handle(
            "update", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public IdentityVerificationConfigManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationConfigDeleteRequest deleteRequest =
        new IdentityVerificationConfigDeleteRequest(identifier);
    IdentityVerificationConfigManagementResult result =
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
