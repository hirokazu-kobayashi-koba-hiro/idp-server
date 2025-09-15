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
import org.idp.server.control_plane.management.authentication.transaction.OrgAuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementStatus;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogWriters;
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
 * Organization-level authentication transaction management entry service.
 *
 * <p>This service implements organization-scoped authentication transaction management operations
 * that allow organization administrators to monitor authentication transactions within their
 * organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_TRANSACTION_READ permissions
 * </ol>
 *
 * <p>This service provides read-only access to authentication transaction data and comprehensive
 * audit logging for organization-level authentication transaction monitoring operations.
 *
 * @see OrgAuthenticationTransactionManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthenticationTransactionManagementEntryService
 */
@Transaction
public class OrgAuthenticationTransactionManagementEntryService
    implements OrgAuthenticationTransactionManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;
  AuditLogWriters auditLogWriters;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log =
      LoggerWrapper.getLogger(OrgAuthenticationTransactionManagementEntryService.class);

  /**
   * Creates a new organization authentication transaction management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param authenticationTransactionQueryRepository the authentication transaction query repository
   * @param auditLogWriters the audit log writers
   */
  public OrgAuthenticationTransactionManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
    this.auditLogWriters = auditLogWriters;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  public AuthenticationTransactionManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionQueries queries,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new AuthenticationTransactionManagementResponse(
          AuthenticationTransactionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    long totalCount =
        authenticationTransactionQueryRepository.findTotalCount(targetTenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());

      AuditLog auditLog =
          AuditLogCreator.createOnRead(
              "OrgAuthenticationTransactionManagementApi.findList",
              "findList",
              targetTenant,
              operator,
              oAuthToken,
              requestAttributes);
      auditLogWriters.write(targetTenant, auditLog);

      return new AuthenticationTransactionManagementResponse(
          AuthenticationTransactionManagementStatus.OK, response);
    }

    List<AuthenticationTransaction> transactions =
        authenticationTransactionQueryRepository.findList(targetTenant, queries);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationTransactionManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", transactions.stream().map(AuthenticationTransaction::toRequestMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
    return new AuthenticationTransactionManagementResponse(
        AuthenticationTransactionManagementStatus.OK, response);
  }

  @Override
  public AuthenticationTransactionManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionIdentifier identifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new AuthenticationTransactionManagementResponse(
          AuthenticationTransactionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationTransaction transaction =
        authenticationTransactionQueryRepository.find(targetTenant, identifier);

    if (!transaction.exists()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "not_found");
      errorResponse.put("error_description", "Authentication transaction not found");
      return new AuthenticationTransactionManagementResponse(
          AuthenticationTransactionManagementStatus.NOT_FOUND, errorResponse);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationTransactionManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    return new AuthenticationTransactionManagementResponse(
        AuthenticationTransactionManagementStatus.OK, transaction.toRequestMap());
  }
}
