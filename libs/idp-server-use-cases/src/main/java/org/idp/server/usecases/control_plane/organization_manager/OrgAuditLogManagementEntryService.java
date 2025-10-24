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
import org.idp.server.control_plane.management.audit.OrgAuditLogManagementApi;
import org.idp.server.control_plane.management.audit.handler.*;
import org.idp.server.control_plane.management.audit.io.AuditLogFindListRequest;
import org.idp.server.control_plane.management.audit.io.AuditLogFindRequest;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogIdentifier;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.audit.AuditLogQueries;
import org.idp.server.platform.audit.AuditLogQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level audit log management entry service.
 *
 * <p>This service implements organization-scoped audit log management operations that allow
 * organization administrators to monitor audit logs within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary AUDIT_LOG_READ
 *       permissions
 * </ol>
 *
 * <p>This service provides read-only access to audit log data and comprehensive audit logging for
 * organization-level audit log monitoring operations.
 *
 * @see OrgAuditLogManagementApi
 * @see OrganizationAccessVerifier
 * @see org.idp.server.usecases.control_plane.system_manager.AuditLogManagementEntryService
 */
@Transaction
public class OrgAuditLogManagementEntryService implements OrgAuditLogManagementApi {

  private final OrgAuditLogManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new organization audit log management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param auditLogQueryRepository the audit log query repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgAuditLogManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuditLogQueryRepository auditLogQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, AuditLogManagementService<?>> services = new HashMap<>();
    services.put("findList", new AuditLogFindListService(auditLogQueryRepository));
    services.put("get", new AuditLogFindService(auditLogQueryRepository));

    this.handler =
        new OrgAuditLogManagementHandler(
            services, this, tenantQueryRepository, new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public AuditLogManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuditLogQueries queries,
      RequestAttributes requestAttributes) {

    AuditLogFindListRequest findListRequest = new AuditLogFindListRequest(queries);
    AuditLogManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            findListRequest,
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public AuditLogManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuditLogIdentifier identifier,
      RequestAttributes requestAttributes) {

    AuditLogFindRequest findRequest = new AuditLogFindRequest(identifier);
    AuditLogManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
