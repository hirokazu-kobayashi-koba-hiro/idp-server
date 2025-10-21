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
import org.idp.server.control_plane.management.audit.OrgAuditLogManagementApi;
import org.idp.server.control_plane.management.audit.handler.*;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level audit log management entry service.
 *
 * <p>This service implements organization-scoped audit log management operations that allow
 * organization administrators to view audit logs within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary AUDIT_LOG_READ
 *       permission
 * </ol>
 *
 * <p>All operations provide comprehensive audit logging for organization-level audit log access.
 *
 * @see OrgAuditLogManagementApi
 * @see OrganizationAccessVerifier
 * @see org.idp.server.usecases.control_plane.system_manager.AuditLogManagementEntryService
 */
@Transaction
public class OrgAuditLogManagementEntryService implements OrgAuditLogManagementApi {

  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgAuditLogManagementEntryService.class);

  // Handler/Service pattern (organization-level)
  private OrgAuditLogManagementHandler handler;

  /**
   * Creates a new organization audit log management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param auditLogQueryRepository the audit log query repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgAuditLogManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuditLogQueryRepository auditLogQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;

    this.handler =
        createHandler(auditLogQueryRepository, tenantQueryRepository, organizationRepository);
  }

  private OrgAuditLogManagementHandler createHandler(
      AuditLogQueryRepository auditLogQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {

    Map<String, AuditLogManagementService<?>> services = new HashMap<>();
    services.put("findList", new AuditLogFindListService(auditLogQueryRepository));
    services.put("get", new AuditLogFindService(auditLogQueryRepository));

    return new OrgAuditLogManagementHandler(
        services, this, tenantQueryRepository, organizationRepository);
  }

  @Transaction(readOnly = true)
  public AuditLogManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuditLogQueries queries,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuditLogFindListRequest request = new AuditLogFindListRequest(queries);
    AuditLogManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            request,
            requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuditLogManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuditLogManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public AuditLogManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuditLogIdentifier identifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuditLogManagementResult result =
        handler.handle(
            "get",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuditLogManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuditLogManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
