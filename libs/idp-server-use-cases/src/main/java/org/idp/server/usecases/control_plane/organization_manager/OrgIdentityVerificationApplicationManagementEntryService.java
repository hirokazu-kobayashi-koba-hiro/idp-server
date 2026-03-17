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
import org.idp.server.control_plane.management.identity.verification.application.OrgIdentityVerificationApplicationManagementApi;
import org.idp.server.control_plane.management.identity.verification.application.handler.*;
import org.idp.server.control_plane.management.identity.verification.application.io.*;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationQueries;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgIdentityVerificationApplicationManagementEntryService
    implements OrgIdentityVerificationApplicationManagementApi {

  private final OrgIdentityVerificationApplicationManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public OrgIdentityVerificationApplicationManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      IdentityVerificationApplicationQueryRepository identityVerificationApplicationQueryRepository,
      IdentityVerificationApplicationCommandRepository
          identityVerificationApplicationCommandRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, IdentityVerificationApplicationManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new IdentityVerificationApplicationFindListService(
            identityVerificationApplicationQueryRepository));
    services.put(
        "get",
        new IdentityVerificationApplicationFindService(
            identityVerificationApplicationQueryRepository));
    services.put(
        "delete",
        new IdentityVerificationApplicationDeletionService(
            identityVerificationApplicationQueryRepository,
            identityVerificationApplicationCommandRepository));

    this.handler =
        new OrgIdentityVerificationApplicationManagementHandler(
            services,
            this,
            tenantQueryRepository,
            organizationRepository,
            new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public IdentityVerificationApplicationManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationQueries queries,
      RequestAttributes requestAttributes) {

    IdentityVerificationApplicationFindListRequest findListRequest =
        new IdentityVerificationApplicationFindListRequest(queries);
    IdentityVerificationApplicationManagementResult result =
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
  public IdentityVerificationApplicationManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      RequestAttributes requestAttributes) {

    IdentityVerificationApplicationFindRequest findRequest =
        new IdentityVerificationApplicationFindRequest(identifier);
    IdentityVerificationApplicationManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public IdentityVerificationApplicationManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationApplicationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationApplicationDeleteRequest deleteRequest =
        new IdentityVerificationApplicationDeleteRequest(identifier);
    IdentityVerificationApplicationManagementResult result =
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
