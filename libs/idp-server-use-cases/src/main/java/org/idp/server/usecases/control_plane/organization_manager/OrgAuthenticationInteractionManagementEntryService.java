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
import org.idp.server.control_plane.management.authentication.interaction.OrgAuthenticationInteractionManagementApi;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementStatus;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
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
 * Organization-level authentication interaction management entry service.
 *
 * <p>This service implements organization-scoped authentication interaction management operations
 * that allow organization administrators to monitor authentication interactions within their
 * organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_INTERACTION_READ permissions
 * </ol>
 *
 * <p>This service provides read-only access to authentication interaction data and comprehensive
 * audit logging for organization-level authentication interaction monitoring operations.
 *
 * @see OrgAuthenticationInteractionManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthenticationInteractionManagementEntryService
 */
@Transaction
public class OrgAuthenticationInteractionManagementEntryService
    implements OrgAuthenticationInteractionManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository;
  AuditLogWriters auditLogWriters;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log =
      LoggerWrapper.getLogger(OrgAuthenticationInteractionManagementEntryService.class);

  /**
   * Creates a new organization authentication interaction management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param authenticationInteractionQueryRepository the authentication interaction query repository
   * @param auditLogWriters the audit log writers
   */
  public OrgAuthenticationInteractionManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.authenticationInteractionQueryRepository = authenticationInteractionQueryRepository;
    this.auditLogWriters = auditLogWriters;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  public AuthenticationInteractionManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationInteractionQueries queries,
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
      return new AuthenticationInteractionManagementResponse(
          AuthenticationInteractionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    List<AuthenticationInteraction> interactions =
        authenticationInteractionQueryRepository.findList(targetTenant, queries);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationInteractionManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    Map<String, Object> response = new HashMap<>();
    response.put("results", interactions.stream().map(AuthenticationInteraction::toMap).toList());
    return new AuthenticationInteractionManagementResponse(
        AuthenticationInteractionManagementStatus.OK, response);
  }

  @Override
  public AuthenticationInteractionManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionIdentifier identifier,
      String type,
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
      return new AuthenticationInteractionManagementResponse(
          AuthenticationInteractionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationInteraction interaction =
        authenticationInteractionQueryRepository.find(targetTenant, identifier, type);

    if (!interaction.exists()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "not_found");
      errorResponse.put("error_description", "Authentication interaction not found");
      return new AuthenticationInteractionManagementResponse(
          AuthenticationInteractionManagementStatus.NOT_FOUND, errorResponse);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationInteractionManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    Map<String, Object> response = new HashMap<>();
    response.put("result", interaction.toMap());
    return new AuthenticationInteractionManagementResponse(
        AuthenticationInteractionManagementStatus.OK, response);
  }
}
