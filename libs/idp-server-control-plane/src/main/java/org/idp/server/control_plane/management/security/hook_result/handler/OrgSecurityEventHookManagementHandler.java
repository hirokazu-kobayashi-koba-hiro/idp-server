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

package org.idp.server.control_plane.management.security.hook_result.handler;

import java.util.Map;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.security.hook_result.OrgSecurityEventHookManagementApi;
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
 * Organization-level security event hook management handler.
 *
 * <p>Orchestrates organization-level security event hook management operations with 4-step access
 * control.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization retrieval and validation
 *   <li>Organization membership verification
 *   <li>Tenant retrieval
 *   <li>Organization-tenant relationship verification
 *   <li>Service selection and execution
 *   <li>Exception catching and Result wrapping
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
public class OrgSecurityEventHookManagementHandler {

  private final Map<String, SecurityEventHookManagementService<?>> services;
  private final OrgSecurityEventHookManagementApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;

  public OrgSecurityEventHookManagementHandler(
      Map<String, SecurityEventHookManagementService<?>> services,
      OrgSecurityEventHookManagementApi managementApi,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.managementApi = managementApi;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  /**
   * Handles organization-level security event hook management operation.
   *
   * <p>Performs 4-step organization access control before delegating to Service.
   *
   * @param method the operation method (e.g., "findList", "get", "retry")
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @return SecurityEventHookManagementResult containing operation outcome or exception
   */
  public SecurityEventHookManagementResult handle(
      String method,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes) {

    Tenant orgTenant = null;
    try {
      // 1. Organization retrieval
      Organization organization = organizationRepository.get(organizationIdentifier);

      // 2. Org tenant retrieval for audit logging
      orgTenant = tenantQueryRepository.get(organization.findOrgTenant().tenantIdentifier());

      // 3. Organization access verification (4-step verification)
      organizationAccessVerifier.verify(
          organization, tenantIdentifier, operator, managementApi.getRequiredPermissions(method));

      // 4. Service selection
      SecurityEventHookManagementService<?> service = services.get(method);
      if (service == null) {
        throw new UnSupportedException("Unsupported operation method: " + method);
      }

      // 5. Tenant retrieval (the actual target tenant)
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 6. Delegate to service
      return executeService(service, tenant, operator, oAuthToken, request, requestAttributes);

    } catch (ManagementApiException e) {
      // Wrap exception in Result with org tenant for audit logging
      return SecurityEventHookManagementResult.error(orgTenant, e);
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
   * @param tenant the tenant (for context and audit logging)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @return SecurityEventHookManagementResult containing operation outcome
   */
  private <T> SecurityEventHookManagementResult executeService(
      SecurityEventHookManagementService<T> service,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes) {
    @SuppressWarnings("unchecked")
    T typedRequest = (T) request;
    return service.execute(tenant, operator, oAuthToken, typedRequest, requestAttributes);
  }
}
