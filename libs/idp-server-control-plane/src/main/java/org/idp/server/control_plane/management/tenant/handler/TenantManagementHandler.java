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
import org.idp.server.control_plane.base.PermissionVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.tenant.TenantManagementApi;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * System-level tenant management handler.
 *
 * <p>Orchestrates tenant management operations by delegating to appropriate Service implementations
 * via strategy pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Admin tenant retrieval (cross-cutting concern)
 *   <li>Permission verification (cross-cutting concern)
 *   <li>Service selection based on operation method
 *   <li>Orchestration of business logic execution
 *   <li>Exception handling and Result wrapping
 * </ul>
 *
 * <h2>NOT Responsibilities (delegated to Services)</h2>
 *
 * <ul>
 *   <li>Request validation
 *   <li>Business rule verification
 *   <li>Repository operations
 *   <li>Event publishing
 * </ul>
 */
public class TenantManagementHandler {

  private final Map<String, TenantManagementService<?>> services;
  private final PermissionVerifier permissionVerifier;
  private final TenantManagementApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;

  public TenantManagementHandler(
      Map<String, TenantManagementService<?>> services,
      TenantManagementApi managementApi,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.permissionVerifier = new PermissionVerifier();
    this.managementApi = managementApi;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  /**
   * Handles tenant management operations.
   *
   * <p>Catches ManagementApiException and wraps them in Result. EntryService will check {@code
   * result.hasException()} and re-throw for transaction rollback.
   *
   * @param method the operation method (e.g., "create", "get", "update", "delete", "findList")
   * @param adminTenantIdentifier the admin tenant identifier
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun if true, validate but don't persist changes
   * @return TenantManagementResult containing operation outcome or exception
   */
  public TenantManagementResult handle(
      String method,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant adminTenant = null;
    try {
      // 0. Get admin tenant first (needed for audit logging even if operation fails)
      adminTenant = tenantQueryRepository.get(adminTenantIdentifier);

      // 1. Permission verification (throws PermissionDeniedException if denied)
      AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method);
      permissionVerifier.verify(operator, requiredPermissions);

      // 2. Service selection
      TenantManagementService<?> service = services.get(method);
      if (service == null) {
        throw new IllegalArgumentException("Unsupported operation method: " + method);
      }

      // 3. Delegate to service (pass adminTenant to avoid duplicate retrieval)
      return executeService(
          service, adminTenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      // Wrap exception in Result with adminTenant for audit logging
      return TenantManagementResult.error(adminTenant, e);
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
   * @param adminTenant the admin tenant (passed from Handler)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun if true, validate but don't persist changes
   * @return TenantManagementResult containing operation outcome
   */
  private <T> TenantManagementResult executeService(
      TenantManagementService<T> service,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    @SuppressWarnings("unchecked")
    T typedRequest = (T) request;
    return service.execute(
        adminTenant, operator, oAuthToken, typedRequest, requestAttributes, dryRun);
  }
}
