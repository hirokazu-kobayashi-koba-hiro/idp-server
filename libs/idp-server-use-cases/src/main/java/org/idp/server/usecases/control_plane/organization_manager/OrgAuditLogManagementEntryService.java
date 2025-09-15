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
import org.idp.server.control_plane.management.audit.OrgAuditLogManagementApi;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementStatus;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
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

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuditLogQueryRepository auditLogQueryRepository;
  AuditLogWriters auditLogWriters;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgAuditLogManagementEntryService.class);

  /**
   * Creates a new organization audit log management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param auditLogQueryRepository the audit log query repository
   * @param auditLogWriters the audit log writers
   */
  public OrgAuditLogManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuditLogQueryRepository auditLogQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.auditLogQueryRepository = auditLogQueryRepository;
    this.auditLogWriters = auditLogWriters;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  public AuditLogManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier adminTenant,
      User operator,
      OAuthToken oAuthToken,
      AuditLogQueries queries,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(adminTenant);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(organization, adminTenant, operator, permissions);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuditLogManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuditLogManagementResponse(AuditLogManagementStatus.FORBIDDEN, response);
    }

    long totalCount = auditLogQueryRepository.findTotalCount(targetTenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", totalCount);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new AuditLogManagementResponse(AuditLogManagementStatus.OK, response);
    }

    List<AuditLog> logs = auditLogQueryRepository.findList(targetTenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", logs.stream().map(AuditLog::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new AuditLogManagementResponse(AuditLogManagementStatus.OK, response);
  }

  @Override
  public AuditLogManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier adminTenant,
      User operator,
      OAuthToken oAuthToken,
      AuditLogIdentifier identifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(adminTenant);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(organization, adminTenant, operator, permissions);

    AuditLog log = auditLogQueryRepository.find(targetTenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuditLogManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuditLogManagementResponse(AuditLogManagementStatus.FORBIDDEN, response);
    }

    if (!log.exists()) {
      return new AuditLogManagementResponse(AuditLogManagementStatus.NOT_FOUND, Map.of());
    }

    return new AuditLogManagementResponse(AuditLogManagementStatus.OK, log.toMap());
  }
}
