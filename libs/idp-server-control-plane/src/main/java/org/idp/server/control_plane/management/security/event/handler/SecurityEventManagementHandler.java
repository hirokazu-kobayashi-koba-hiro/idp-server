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

package org.idp.server.control_plane.management.security.event.handler;

import java.util.Map;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.security.event.SecurityEventManagementApi;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * System-level security event management handler.
 *
 * <p>Orchestrates security event management operations by delegating to appropriate Service
 * implementations via strategy pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Tenant retrieval and validation
 *   <li>Permission verification
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
public class SecurityEventManagementHandler {

  private final Map<String, SecurityEventManagementService<?>> services;
  private final SecurityEventManagementApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;
  private final ApiPermissionVerifier apiPermissionVerifier;

  public SecurityEventManagementHandler(
      Map<String, SecurityEventManagementService<?>> services,
      SecurityEventManagementApi managementApi,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.managementApi = managementApi;
    this.tenantQueryRepository = tenantQueryRepository;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  /**
   * Handles security event management operation.
   *
   * <p>Catches ManagementApiException and wraps them in Result. EntryService will check {@code
   * result.hasException()} and return appropriate HTTP status.
   *
   * @param method the operation method (e.g., "findList", "get")
   * @param tenantIdentifier the tenant identifier
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @return SecurityEventManagementResult containing operation outcome or exception
   */
  public SecurityEventManagementResult handle(
      String method,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes) {

    Tenant tenant = null;
    try {
      // 1. Tenant retrieval
      tenant = tenantQueryRepository.get(tenantIdentifier);

      // 2. Permission verification
      AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // 3. Service selection
      SecurityEventManagementService<?> service = services.get(method);
      if (service == null) {
        throw new UnSupportedException("Unsupported operation method: " + method);
      }

      // 4. Delegate to service
      return executeService(service, tenant, operator, oAuthToken, request, requestAttributes);

    } catch (org.idp.server.control_plane.management.exception.ManagementApiException e) {
      // Wrap exception in Result with tenant for audit logging
      return SecurityEventManagementResult.error(tenant, e);
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
   * @return SecurityEventManagementResult containing operation outcome
   */
  private <T> SecurityEventManagementResult executeService(
      SecurityEventManagementService<T> service,
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
