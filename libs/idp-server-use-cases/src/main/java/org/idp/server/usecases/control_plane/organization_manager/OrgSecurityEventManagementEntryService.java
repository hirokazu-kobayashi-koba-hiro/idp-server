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
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.security.event.OrgSecurityEventManagementApi;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementStatus;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEvent;
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

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  SecurityEventQueryRepository securityEventQueryRepository;
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgSecurityEventManagementEntryService.class);

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
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.securityEventQueryRepository = securityEventQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
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

    AdminPermissions permissions = getRequiredPermissions("findList");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgSecurityEventManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new SecurityEventManagementResponse(SecurityEventManagementStatus.FORBIDDEN, response);
    }

    long totalCount = securityEventQueryRepository.findTotalCount(targetTenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", totalCount);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, response);
    }

    List<SecurityEvent> events = securityEventQueryRepository.findList(targetTenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", events.stream().map(SecurityEvent::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, response);
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

    AdminPermissions permissions = getRequiredPermissions("get");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    SecurityEvent event = securityEventQueryRepository.find(targetTenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgSecurityEventManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new SecurityEventManagementResponse(SecurityEventManagementStatus.FORBIDDEN, response);
    }

    if (!event.exists()) {
      return new SecurityEventManagementResponse(SecurityEventManagementStatus.NOT_FOUND, Map.of());
    }

    return new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, event.toMap());
  }
}
