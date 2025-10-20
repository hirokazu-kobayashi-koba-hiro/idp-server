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
import org.idp.server.control_plane.base.PermissionVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for authentication transaction management operations.
 *
 * <p>Orchestrates authentication transaction management operations following the Handler/Service
 * pattern. Delegates business logic to specific AuthenticationTransactionManagementService
 * implementations based on the operation method.
 *
 * <h2>Architecture Pattern</h2>
 *
 * <pre>{@code
 * AuthenticationTransactionManagementEntryService (exception handling, audit logging)
 *   ↓
 * AuthenticationTransactionManagementHandler (orchestration, permission checking)
 *   ↓
 * AuthenticationTransactionManagementService (business logic, throws exceptions)
 * }</pre>
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Permission verification (cross-cutting concern)
 *   <li>Service selection based on operation method
 *   <li>Orchestration of business logic execution
 * </ul>
 *
 * <h2>NOT Responsibilities (delegated to Services)</h2>
 *
 * <ul>
 *   <li>Query execution
 *   <li>Data retrieval
 *   <li>Response formatting
 * </ul>
 *
 * @see AuthenticationTransactionManagementService
 * @see AuthenticationTransactionManagementResult
 */
public class AuthenticationTransactionManagementHandler {

  private final Map<String, AuthenticationTransactionManagementService<?>> services;
  private final PermissionVerifier permissionVerifier;
  private final AuthenticationTransactionManagementApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;

  public AuthenticationTransactionManagementHandler(
      Map<String, AuthenticationTransactionManagementService<?>> services,
      AuthenticationTransactionManagementApi managementApi,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.permissionVerifier = new PermissionVerifier();
    this.managementApi = managementApi;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  /**
   * Handles authentication transaction management operations.
   *
   * <p>Catches ManagementApiException and wraps them in Result. EntryService will check {@code
   * result.hasException()} and re-throw for transaction rollback.
   *
   * @param method the operation method (e.g., "findList", "get")
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
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    try {
      // 0. Get tenant first (needed for audit logging even if operation fails)
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 1. Permission verification (throws PermissionDeniedException if denied)
      AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method);
      permissionVerifier.verify(operator, requiredPermissions);

      // 2. Service selection
      AuthenticationTransactionManagementService<?> service = services.get(method);
      if (service == null) {
        throw new IllegalArgumentException("Unsupported operation method: " + method);
      }

      // 3. Delegate to service (pass tenant to avoid duplicate retrieval)
      return executeService(
          service, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      // Wrap exception in Result with tenant identifier for audit logging
      return AuthenticationTransactionManagementResult.error(tenantIdentifier, e);
    }
  }

  /**
   * Executes the service with type-safe request handling.
   *
   * <p>This helper method uses generics to ensure type safety when calling service.execute().
   * The @SuppressWarnings("unchecked") is safe because:
   *
   * <ul>
   *   <li>Each service implementation defines its own REQUEST type parameter
   *   <li>The service map is built at initialization time with correct type mappings
   *   <li>The Handler doesn't know the concrete request type at compile time
   * </ul>
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
