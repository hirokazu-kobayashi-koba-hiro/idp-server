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

package org.idp.server.control_plane.management.permission.handler;

import java.util.Map;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.permission.OrgPermissionManagementApi;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for organization-level permission management operations.
 *
 * <p>Orchestrates organization-scoped permission management requests following the Handler/Service
 * pattern. This handler adds organization-level access control on top of the standard permission
 * management flow.
 *
 * <h2>Organization-Level Access Control</h2>
 *
 * <ol>
 *   <li>Organization membership verification
 *   <li>Tenant access verification within the organization
 *   <li>Organization-tenant relationship verification
 *   <li>Required permissions verification
 * </ol>
 *
 * <h2>Handler/Service Pattern Flow</h2>
 *
 * <ol>
 *   <li>Handler resolves organization and tenant
 *   <li>Handler verifies organization access (4-step verification)
 *   <li>Handler delegates to Service implementation
 *   <li>Service throws ManagementApiException on validation/verification failures
 *   <li>Handler catches exception and converts to Result
 *   <li>EntryService converts Result to HTTP response
 * </ol>
 *
 * @see PermissionManagementService
 * @see PermissionManagementResult
 * @see OrganizationAccessVerifier
 */
public class OrgPermissionManagementHandler {

  private final Map<String, PermissionManagementService<?>> services;
  private final OrgPermissionManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;

  /**
   * Creates a new organization permission management handler.
   *
   * @param services map of operation method names to Service implementations
   * @param api the organization permission management API (for permission definitions)
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param organizationAccessVerifier the organization access verifier
   */
  public OrgPermissionManagementHandler(
      Map<String, PermissionManagementService<?>> services,
      OrgPermissionManagementApi api,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  /**
   * Handles an organization-level permission management request.
   *
   * @param method the operation method (create, findList, get, update, delete)
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to perform a dry run (preview only)
   * @return the operation result
   */
  @SuppressWarnings("unchecked")
  public <REQUEST> PermissionManagementResult handle(
      String method,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      REQUEST request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = null;
    try {
      Organization organization = organizationRepository.get(organizationIdentifier);
      tenant = tenantQueryRepository.get(tenantIdentifier);
      AdminPermissions permissions = api.getRequiredPermissions(method);

      OrganizationAccessControlResult accessResult =
          organizationAccessVerifier.verifyAccess(
              organization, tenantIdentifier, operator, permissions);

      if (!accessResult.isSuccess()) {
        throw new PermissionDeniedException(permissions, operator.permissionsAsSet());
      }

      PermissionManagementService<REQUEST> service =
          (PermissionManagementService<REQUEST>) services.get(method);
      if (service == null) {
        throw new IllegalArgumentException("Unsupported operation method: " + method);
      }

      return service.execute(tenant, operator, oAuthToken, request, requestAttributes, dryRun);
    } catch (ManagementApiException e) {
      return PermissionManagementResult.error(tenant, e);
    }
  }
}
