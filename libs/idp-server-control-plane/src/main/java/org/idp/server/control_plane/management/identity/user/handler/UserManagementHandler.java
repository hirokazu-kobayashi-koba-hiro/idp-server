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
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.identity.user.UserManagementApi;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for user management operations.
 *
 * <p>Orchestrates user management operations following the Handler/Service pattern. Delegates
 * business logic to specific UserManagementService implementations based on the operation method.
 *
 * <h2>Architecture Pattern</h2>
 *
 * <pre>{@code
 * DefaultUserManagementProtocol (exception handling)
 *   ↓
 * UserManagementHandler (orchestration, permission checking)
 *   ↓
 * UserManagementService (business logic, throws exceptions)
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
 *   <li>Request validation
 *   <li>Business rule verification
 *   <li>Repository operations
 *   <li>Event publishing
 * </ul>
 *
 * @see UserManagementService
 * @see UserManagementResult
 */
public class UserManagementHandler {

  private final Map<String, UserManagementService<?>> services;
  private final UserManagementApi managementApi;
  private final ApiPermissionVerifier apiPermissionVerifier;
  private final TenantQueryRepository tenantQueryRepository;

  public UserManagementHandler(
      Map<String, UserManagementService<?>> services,
      UserManagementApi managementApi,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.managementApi = managementApi;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
    this.tenantQueryRepository = tenantQueryRepository;
  }

  /**
   * Handles user management operations.
   *
   * <p>Catches ManagementApiException and wraps them in Result. EntryService will check {@code
   * result.hasException()} and re-throw for transaction rollback.
   *
   * @param method the operation method (e.g., "create", "update", "delete")
   * @param tenantIdentifier the tenant context
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun if true, validate but don't persist changes
   * @return UserManagementResult containing operation outcome or exception
   */
  public UserManagementResult handle(
      String method,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Service selection
    UserManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation (before Tenant retrieval - enables audit logging on errors)
    UserManagementContextBuilder contextBuilder =
        createContextBuilder(
            service, tenantIdentifier, null, operator, oAuthToken, request, requestAttributes, dryRun);

    try {
      // 3. Get tenant
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
      contextBuilder.withTenant(tenant);

      // 4. Permission verification with tenant type check (throws PermissionDeniedException if
      // denied)
      AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method, tenant);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // 5. Delegate to service (pass tenant to avoid duplicate retrieval)
      UserManagementResponse response = executeService(
              service, contextBuilder, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

      AuditableContext context = contextBuilder.build();
      return UserManagementResult.success(tenant, context, response);
    } catch (ManagementApiException e) {
      // Partial Context creation for audit logging (may not have Tenant if retrieval failed)
      AuditableContext errorContext = contextBuilder.buildPartial(e);
      return UserManagementResult.error(errorContext, e);
    }
  }

  /**
   * Creates context builder with type-safe request handling.
   *
   * <p>The @SuppressWarnings("unchecked") is safe because:
   *
   * <ul>
   *   <li>Each service implementation defines its own REQUEST type parameter
   *   <li>The service map is built at initialization time with correct type mappings
   *   <li>The Handler doesn't know the concrete request type at compile time
   * </ul>
   */
  @SuppressWarnings("unchecked")
  private <REQUEST> UserManagementContextBuilder createContextBuilder(
      UserManagementService<REQUEST> service,
      TenantIdentifier tenantIdentifier,
      OrganizationIdentifier organizationIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    return service.createContextBuilder(
        tenantIdentifier,
        organizationIdentifier,
        operator,
        oAuthToken,
        requestAttributes,
        (REQUEST) request,
        dryRun);
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
  private <REQUEST> UserManagementResponse executeService(
      UserManagementService<REQUEST> service,
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    return service.execute(
        builder, tenant, operator, oAuthToken, (REQUEST) request, requestAttributes, dryRun);
  }
}
