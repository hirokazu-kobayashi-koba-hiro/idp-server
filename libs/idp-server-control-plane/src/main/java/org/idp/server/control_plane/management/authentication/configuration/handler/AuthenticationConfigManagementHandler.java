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

package org.idp.server.control_plane.management.authentication.configuration.handler;

import java.util.Map;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigurationManagementApi;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * System-level authentication configuration management handler.
 *
 * <p>Orchestrates system-scoped authentication configuration management operations by delegating to
 * appropriate Service implementations via strategy pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Tenant retrieval (once per request)
 *   <li>Permission verification
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
 * @see AuthenticationConfigManagementService
 * @see AuthenticationConfigManagementResult
 */
public class AuthenticationConfigManagementHandler {

  private final Map<String, AuthenticationConfigManagementService<?>> services;
  private final AuthenticationConfigurationManagementApi entryService;
  private final TenantQueryRepository tenantQueryRepository;
  private final ApiPermissionVerifier apiPermissionVerifier;

  public AuthenticationConfigManagementHandler(
      Map<String, AuthenticationConfigManagementService<?>> services,
      AuthenticationConfigurationManagementApi entryService,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.entryService = entryService;
    this.tenantQueryRepository = tenantQueryRepository;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  /**
   * Handles system-level authentication configuration management operation.
   *
   * @param method the operation method (e.g., "create", "update", "delete")
   * @param tenantIdentifier the tenant context
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the operation
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun if true, validate but don't persist changes
   * @return AuthenticationConfigManagementResult containing operation outcome or exception
   */
  public AuthenticationConfigManagementResult handle(
      String method,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = null;
    try {
      // 1. Tenant retrieval (once per request)
      tenant = tenantQueryRepository.get(tenantIdentifier);

      // 2. Permission verification
      AdminPermissions requiredPermissions = entryService.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // 3. Service selection
      AuthenticationConfigManagementService<?> service = services.get(method);
      if (service == null) {
        throw new UnSupportedException("Unsupported operation method: " + method);
      }

      // 4. Delegate to service
      return executeService(
          service, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      // Wrap exception in Result with tenant for audit logging
      return AuthenticationConfigManagementResult.error(tenant, e);
    }
  }

  /**
   * Executes the service with type-safe request handling.
   *
   * <p>This helper method uses generics to ensure type safety when calling service.execute().
   */
  @SuppressWarnings("unchecked")
  private <REQUEST> AuthenticationConfigManagementResult executeService(
      AuthenticationConfigManagementService<REQUEST> service,
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
