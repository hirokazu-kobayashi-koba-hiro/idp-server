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

import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.security.event.OrgSecurityEventManagementApi;
import org.idp.server.control_plane.management.security.event.handler.OrgSecurityEventManagementHandler;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventFindListService;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventFindService;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventManagementResult;
import org.idp.server.control_plane.management.security.event.handler.SecurityEventManagementService;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEventQueries;
import org.idp.server.platform.security.event.SecurityEventIdentifier;
import org.idp.server.platform.security.repository.SecurityEventQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level security event management entry service.
 *
 * <p>This service implements organization-scoped security event management operations that allow
 * organization administrators to view security events within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       SECURITY_EVENT_READ permission
 * </ol>
 *
 * <p>All operations provide comprehensive audit logging for organization-level security event
 * access.
 *
 * @see OrgSecurityEventManagementApi
 * @see OrganizationAccessVerifier
 * @see org.idp.server.usecases.control_plane.system_manager.SecurityEventManagementEntryService
 */
@Transaction
public class OrgSecurityEventManagementEntryService implements OrgSecurityEventManagementApi {

  private final OrgSecurityEventManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new organization security event management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param securityEventQueryRepository the security event query repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgSecurityEventManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      SecurityEventQueryRepository securityEventQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    Map<String, SecurityEventManagementService<?>> services =
        Map.of(
            "findList", new SecurityEventFindListService(securityEventQueryRepository),
            "get", new SecurityEventFindService(securityEventQueryRepository));

    this.handler =
        new OrgSecurityEventManagementHandler(
            services,
            this,
            tenantQueryRepository,
            organizationRepository,
            new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventQueries queries,
      RequestAttributes requestAttributes) {

    SecurityEventManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            queries,
            requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgSecurityEventManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgSecurityEventManagementApi.findList",
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
  public SecurityEventManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventManagementResult result =
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
              "OrgSecurityEventManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgSecurityEventManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}
