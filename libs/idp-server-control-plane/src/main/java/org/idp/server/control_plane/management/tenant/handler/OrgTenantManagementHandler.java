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

package org.idp.server.control_plane.management.tenant.handler;

import java.util.Map;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.tenant.OrgTenantManagementApi;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
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
 * Organization-level tenant management handler.
 *
 * <p>Orchestrates organization-scoped tenant management operations by delegating to appropriate
 * Service implementations via strategy pattern.
 *
 * <h2>Organization-Level Access Control</h2>
 *
 * <p>Unlike system-level operations, organization-level operations require:
 *
 * <ol>
 *   <li>Organization retrieval and validation
 *   <li>Organization access control verification (via OrganizationAccessVerifier)
 *   <li>Organization tenant (orgTenant) retrieval for audit logging
 *   <li>Permission verification within organization context
 * </ol>
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization retrieval and validation
 *   <li>Organization access control verification
 *   <li>Organization tenant (orgTenant) retrieval for audit logging
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
public class OrgTenantManagementHandler {

  private final Map<String, TenantManagementService<?>> services;
  private final OrgTenantManagementApi entryService;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;

  public OrgTenantManagementHandler(
      Map<String, TenantManagementService<?>> services,
      OrgTenantManagementApi entryService,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {
    this.services = services;
    this.entryService = entryService;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  /**
   * Handles organization-level tenant management operation.
   *
   * <p>Catches ManagementApiException and wraps them in Result. EntryService will check {@code
   * result.hasException()} and re-throw for transaction rollback.
   *
   * @param method the operation method (e.g., "create", "findList", "get", "update", "delete")
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier (can be empty for create/findList operations)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun if true, validate but don't persist changes
   * @return TenantManagementResult containing operation outcome or exception
   */
  public TenantManagementResult handle(
      String method,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant orgTenant = null;
    try {
      // 0. Get required permissions
      AdminPermissions permissions = entryService.getRequiredPermissions(method);

      // 1. Organization retrieval
      Organization organization = organizationRepository.get(organizationIdentifier);

      // 2. Get organization tenant for audit logging
      orgTenant = tenantQueryRepository.get(organization.findOrgTenant().tenantIdentifier());

      // 3. Organization-level access control
      organizationAccessVerifier.verify(organization, tenantIdentifier, operator, permissions);

      // 4. Service selection
      TenantManagementService<?> service = services.get(method);
      if (service == null) {
        throw new UnSupportedException("Unsupported operation method: " + method);
      }

      // 5. Delegate to service (pass orgTenant for context)
      // Special cases:
      // - findList operation needs Organization object
      // - create operation needs OrgTenantCreationRequest wrapper
      Object serviceRequest = request;
      if ("findList".equals(method)) {
        serviceRequest = organization;
      } else if ("create".equals(method)) {
        serviceRequest = new OrgTenantCreationRequest(organization, (TenantRequest) request);
      }
      return executeService(
          service, orgTenant, operator, oAuthToken, serviceRequest, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      // Wrap exception in Result with orgTenant for audit logging
      return TenantManagementResult.error(orgTenant, e);
    }
  }

  /**
   * Executes the service with type-safe request handling.
   *
   * <p>This helper method uses generics to ensure type safety when calling service.execute().
   * The @SuppressWarnings("unchecked") is safe because:
   *
   * <ul>
   *   <li>Each service is registered with its expected request type
   *   <li>EntryService methods pass the correct request type for each operation
   * </ul>
   *
   * @param service the service to execute
   * @param orgTenant the organization tenant (for context and audit logging)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun if true, validate but don't persist changes
   * @return TenantManagementResult containing operation outcome
   */
  private <T> TenantManagementResult executeService(
      TenantManagementService<T> service,
      Tenant orgTenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    @SuppressWarnings("unchecked")
    T typedRequest = (T) request;
    return service.execute(
        orgTenant, operator, oAuthToken, typedRequest, requestAttributes, dryRun);
  }
}
