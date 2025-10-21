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

package org.idp.server.control_plane.management.identity.verification.handler;

import java.util.Map;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementApi;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for identity verification configuration management operations.
 *
 * <p>Orchestrates identity verification configuration management operations by checking permissions
 * and delegating to appropriate service implementations. Part of Handler/Service pattern.
 *
 * @see IdentityVerificationConfigManagementService
 * @see IdentityVerificationConfigManagementResult
 */
public class IdentityVerificationConfigManagementHandler {

  private final Map<String, IdentityVerificationConfigManagementService<?>> services;
  private final IdentityVerificationConfigManagementApi api;
  private final ApiPermissionVerifier apiPermissionVerifier;

  public IdentityVerificationConfigManagementHandler(
      Map<String, IdentityVerificationConfigManagementService<?>> services,
      IdentityVerificationConfigManagementApi api) {
    this.services = services;
    this.api = api;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  /**
   * Handles identity verification configuration management operations.
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
  public <T> IdentityVerificationConfigManagementResult handle(
      String method,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    try {
      AdminPermissions requiredPermissions = api.getRequiredPermissions(method);

      apiPermissionVerifier.verify(operator, requiredPermissions);

      return executeService(
          method, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      return IdentityVerificationConfigManagementResult.error(tenant.identifier(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> IdentityVerificationConfigManagementResult executeService(
      String method,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationConfigManagementService<T> service =
        (IdentityVerificationConfigManagementService<T>) services.get(method);

    if (service == null) {
      throw new IllegalArgumentException("Unsupported method: " + method);
    }

    return service.execute(tenant, operator, oAuthToken, request, requestAttributes, dryRun);
  }
}
