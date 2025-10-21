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

package org.idp.server.control_plane.management.oidc.authorization.handler;

import java.util.Map;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementApi;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for authorization server management operations.
 *
 * <p>Routes requests to appropriate service implementations and handles exception conversion to
 * results. Part of Handler/Service pattern.
 *
 * @see AuthorizationServerManagementService
 * @see AuthorizationServerManagementResult
 */
public class AuthorizationServerManagementHandler {

  private final Map<String, AuthorizationServerManagementService<?>> services;
  private final AuthorizationServerManagementApi entryService;

  public AuthorizationServerManagementHandler(
      Map<String, AuthorizationServerManagementService<?>> services,
      AuthorizationServerManagementApi entryService) {
    this.services = services;
    this.entryService = entryService;
  }

  /**
   * Handles authorization server management operations.
   *
   * @param method the operation method (get, update)
   * @param tenant the tenant
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param request the request object (type varies by operation, may be null for get)
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run
   * @return the operation result
   */
  public AuthorizationServerManagementResult handle(
      String method,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    try {
      return executeService(
          method, tenant, operator, oAuthToken, request, requestAttributes, dryRun);
    } catch (ManagementApiException e) {
      return AuthorizationServerManagementResult.error(tenant.identifier(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private AuthorizationServerManagementResult executeService(
      String method,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthorizationServerManagementService service = services.get(method);
    if (service == null) {
      throw new IllegalArgumentException("Unknown method: " + method);
    }

    return service.execute(tenant, operator, oAuthToken, request, requestAttributes, dryRun);
  }
}
