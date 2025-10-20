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

package org.idp.server.control_plane.management.oidc.client.handler;

import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.oidc.client.ClientManagementApi;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for client management operations.
 *
 * <p>Orchestrates client management operations by checking permissions and delegating to
 * appropriate service implementations. Part of Handler/Service pattern.
 *
 * @see ClientManagementService
 * @see ClientManagementResult
 */
public class ClientManagementHandler {

  private final Map<String, ClientManagementService<?>> services;
  private final ClientManagementApi api;

  public ClientManagementHandler(
      Map<String, ClientManagementService<?>> services, ClientManagementApi api) {
    this.services = services;
    this.api = api;
  }

  /**
   * Handles client management operations.
   *
   * @param method the method name (create, findList, get, update, delete)
   * @param tenant the tenant
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param request the request object (type varies by method)
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run
   * @return the result of the operation
   */
  public <T> ClientManagementResult handle(
      String method,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    try {
      AdminPermissions requiredPermissions = api.getRequiredPermissions(method);

      if (!requiredPermissions.includesAll(operator.permissionsAsSet())) {
        throw new PermissionDeniedException(requiredPermissions, Set.of());
      }

      return executeService(
          method, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      return ClientManagementResult.error(tenant.identifier(), e);
    } catch (IllegalArgumentException e) {
      InvalidRequestException invalidRequestException =
          new InvalidRequestException("Invalid request parameters: " + e.getMessage());
      return ClientManagementResult.error(tenant.identifier(), invalidRequestException);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> ClientManagementResult executeService(
      String method,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    ClientManagementService<T> service = (ClientManagementService<T>) services.get(method);

    if (service == null) {
      throw new IllegalArgumentException("Unsupported method: " + method);
    }

    return service.execute(tenant, operator, oAuthToken, request, requestAttributes, dryRun);
  }
}
