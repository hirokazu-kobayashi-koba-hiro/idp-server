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

package org.idp.server.control_plane.management.identity.user.handler;

import java.util.Map;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.identity.user.OrgUserManagementApi;
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
 * Organization-level user management handler.
 *
 * <p>Orchestrates organization-scoped user management operations by delegating to appropriate
 * Service implementations via strategy pattern.
 *
 * <h2>Organization-Level Access Control</h2>
 *
 * <p>Unlike system-level operations, organization-level operations require:
 *
 * <ol>
 *   <li>Organization access verification (via OrganizationAccessVerifier)
 *   <li>Permission verification (same as system-level)
 *   <li>Tenant retrieval within organization context
 * </ol>
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization retrieval and validation
 *   <li>Organization access control verification
 *   <li>Tenant retrieval within organization context
 *   <li>Service selection and execution
 *   <li>Result/Exception wrapping
 * </ul>
 *
 * <h2>NOT Responsibilities</h2>
 *
 * <ul>
 *   <li>Business logic (delegated to Service)
 *   <li>Audit logging (handled by EntryService)
 *   <li>Transaction management (handled by EntryService)
 * </ul>
 */
public class OrgUserManagementHandler {

  private final Map<String, UserManagementService<?>> services;
  private final OrgUserManagementApi entryService;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;

  public OrgUserManagementHandler(
      Map<String, UserManagementService<?>> services,
      OrgUserManagementApi entryService,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {
    this.services = services;
    this.entryService = entryService;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  /**
   * Handles organization-level user management operation.
   *
   * @param operation the operation name (e.g., "create", "update", "delete")
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator performing the action
   * @param oAuthToken the OAuth token
   * @param request the request object (type varies by operation)
   * @param requestAttributes the request attributes
   * @param dryRun whether this is a dry-run operation
   * @return the user management result
   */
  public UserManagementResult handle(
      String operation,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = null;
    try {
      // 1. Get required permissions
      AdminPermissions permissions = entryService.getRequiredPermissions(operation);

      // 2. Organization and Tenant retrieval
      Organization organization = organizationRepository.get(organizationIdentifier);
      tenant = tenantQueryRepository.get(tenantIdentifier);

      // 3. Organization-level access control
      OrganizationAccessControlResult accessResult =
          organizationAccessVerifier.verifyAccess(
              organization, tenantIdentifier, operator, permissions);

      if (!accessResult.isSuccess()) {
        throw new org.idp.server.control_plane.management.exception.PermissionDeniedException(
            permissions, java.util.Set.of());
      }

      // 4. Service selection
      UserManagementService<?> service = services.get(operation);
      if (service == null) {
        throw new IllegalArgumentException("Unknown operation: " + operation);
      }

      // 5. Execute service
      return executeService(
          service, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (org.idp.server.control_plane.management.exception.ManagementApiException e) {
      // Wrap exception in Result with tenant for audit logging
      return UserManagementResult.error(tenant, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <REQUEST> UserManagementResult executeService(
      UserManagementService<REQUEST> service,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    return service.execute(
        tenant, operator, oAuthToken, (REQUEST) request, requestAttributes, dryRun);
  }
}
