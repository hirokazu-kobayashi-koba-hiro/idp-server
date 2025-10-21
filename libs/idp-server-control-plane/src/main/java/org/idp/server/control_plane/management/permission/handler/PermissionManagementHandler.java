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
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.permission.PermissionManagementApi;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for system-level permission management operations.
 *
 * <p>Orchestrates permission management requests following the Handler/Service pattern.
 * Responsibilities include:
 *
 * <ul>
 *   <li>Tenant resolution from identifier
 *   <li>Permission verification
 *   <li>Delegating to appropriate Service implementation based on operation method
 *   <li>Exception handling and conversion to Result objects
 * </ul>
 *
 * <h2>Handler/Service Pattern Flow</h2>
 *
 * <ol>
 *   <li>Handler resolves tenant from identifier
 *   <li>Handler verifies operator permissions
 *   <li>Handler delegates to Service implementation (create, findList, get, update, delete)
 *   <li>Service throws ManagementApiException on validation/verification failures
 *   <li>Handler catches exception and converts to Result
 *   <li>EntryService converts Result to HTTP response
 * </ol>
 *
 * @see PermissionManagementService
 * @see PermissionManagementResult
 */
public class PermissionManagementHandler {

  private final Map<String, PermissionManagementService<?>> services;
  private final PermissionManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  private final ApiPermissionVerifier apiPermissionVerifier;

  /**
   * Creates a new permission management handler.
   *
   * @param services map of operation method names to Service implementations
   * @param api the permission management API (for permission definitions)
   * @param tenantQueryRepository the tenant query repository
   */
  public PermissionManagementHandler(
      Map<String, PermissionManagementService<?>> services,
      PermissionManagementApi api,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  /**
   * Handles a permission management request.
   *
   * @param method the operation method (create, findList, get, update, delete)
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
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      REQUEST request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = null;
    try {
      tenant = tenantQueryRepository.get(tenantIdentifier);

      AdminPermissions requiredPermissions = api.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      PermissionManagementService<REQUEST> service =
          (PermissionManagementService<REQUEST>) services.get(method);
      if (service == null) {
        throw new UnSupportedException("Unsupported operation method: " + method);
      }

      return service.execute(tenant, operator, oAuthToken, request, requestAttributes, dryRun);
    } catch (ManagementApiException e) {
      return PermissionManagementResult.error(tenant, e);
    }
  }
}
