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

package org.idp.server.control_plane.management.authentication.transaction.handler;

import java.util.Map;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.transaction.OrgAuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authentication transaction management handler.
 *
 * <p>Orchestrates organization-scoped authentication transaction management operations by
 * delegating to appropriate Service implementations via strategy pattern.
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
 *   <li>Organization access control verification
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
 *
 * @see AuthenticationTransactionManagementService
 * @see AuthenticationTransactionManagementResult
 * @see OrganizationAccessVerifier
 */
public class OrgAuthenticationTransactionManagementHandler {

  private final Map<String, AuthenticationTransactionManagementService<?>> services;
  private final OrgAuthenticationTransactionManagementApi entryService;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;

  public OrgAuthenticationTransactionManagementHandler(
      Map<String, AuthenticationTransactionManagementService<?>> services,
      OrgAuthenticationTransactionManagementApi entryService,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {
    this.services = services;
    this.entryService = entryService;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  /**
   * Handles organization-level authentication transaction management operation.
   *
   * <p>Organization-level operations include additional access control verification to ensure the
   * operator has access to both the organization and the tenant.
   *
   * @param method the operation method (e.g., "findList", "get")
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant context
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun if true, validate but don't persist changes
   * @return AuthenticationTransactionManagementResult containing operation outcome or exception
   */
  public AuthenticationTransactionManagementResult handle(
      String method,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = null;
    try {
      // 0. Get organization and tenant
      Organization organization = organizationRepository.get(organizationIdentifier);
      tenant = tenantQueryRepository.get(tenantIdentifier);

      // 1. Organization access verification (4-step verification)
      AdminPermissions requiredPermissions = entryService.getRequiredPermissions(method);
      organizationAccessVerifier.verify(
          organization, tenantIdentifier, operator, requiredPermissions);

      // 2. Service selection
      AuthenticationTransactionManagementService<?> service = services.get(method);
      if (service == null) {
        throw new UnSupportedException("Unsupported operation method: " + method);
      }

      // 3. Delegate to service (pass tenant to avoid duplicate retrieval)
      return executeService(
          service, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      // Wrap exception in Result with tenant for audit logging
      return AuthenticationTransactionManagementResult.error(
          tenant != null ? tenant.identifier() : tenantIdentifier, e);
    }
  }

  /**
   * Executes the service with type-safe request handling.
   *
   * <p>This helper method uses generics to ensure type safety when calling service.execute().
   */
  @SuppressWarnings("unchecked")
  private <REQUEST> AuthenticationTransactionManagementResult executeService(
      AuthenticationTransactionManagementService<REQUEST> service,
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
