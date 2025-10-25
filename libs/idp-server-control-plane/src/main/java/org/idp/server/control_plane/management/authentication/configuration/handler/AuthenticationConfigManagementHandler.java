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
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigurationManagementApi;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementRequest;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for authentication policy configuration management operations.
 *
 * <p>Orchestrates authentication policy configuration management operations following the
 * Handler/Service pattern. Delegates business logic to specific
 * AuthenticationConfigManagementService implementations based on the operation method.
 *
 * <h2>Architecture Pattern</h2>
 *
 * <pre>{@code
 * AuthenticationConfigurationManagementEntryService (exception handling, audit logging)
 *   ↓
 * AuthenticationConfigManagementHandler (orchestration, permission checking)
 *   ↓
 * AuthenticationConfigManagementService (business logic, throws exceptions)
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
 * @see AuthenticationConfigManagementService
 * @see AuthenticationConfigManagementResult
 */
public class AuthenticationConfigManagementHandler {

  private final Map<String, AuthenticationConfigManagementService<?>> services;
  private final ApiPermissionVerifier apiPermissionVerifier;
  private final AuthenticationConfigurationManagementApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationConfigManagementHandler.class);

  public AuthenticationConfigManagementHandler(
      Map<String, AuthenticationConfigManagementService<?>> services,
      AuthenticationConfigurationManagementApi managementApi,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
    this.managementApi = managementApi;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  /**
   * Handles authentication policy configuration management operations.
   *
   * @param method the operation method (create, findList, get, update, delete)
   * @param authenticationContext the admin authentication context
   * @param tenantIdentifier the tenant identifier
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to perform a dry run (preview only)
   * @return the operation result
   */
  public AuthenticationConfigManagementResult handle(
      String method,
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // 1. Service selection
    AuthenticationConfigManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation (before Tenant retrieval - enables audit logging on errors)
    AuthenticationConfigManagementContextBuilder contextBuilder =
        new AuthenticationConfigManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request, dryRun);

    try {
      // 3. Tenant retrieval
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Permission verification
      AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // 5. Delegate to service
      AuthenticationConfigManagementResponse response =
          executeService(
              service,
              contextBuilder,
              tenant,
              operator,
              oAuthToken,
              request,
              requestAttributes,
              dryRun);

      AuditableContext context = contextBuilder.build();
      return AuthenticationConfigManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());

      ResourceNotFoundException resourceNotFoundException =
          new ResourceNotFoundException(e.getMessage());

      AuditableContext partialContext = contextBuilder.buildPartial(resourceNotFoundException);
      return AuthenticationConfigManagementResult.error(partialContext, resourceNotFoundException);

    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext partialContext = contextBuilder.buildPartial(e);
      return AuthenticationConfigManagementResult.error(partialContext, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> AuthenticationConfigManagementResponse executeService(
      AuthenticationConfigManagementService<?> service,
      AuthenticationConfigManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthenticationConfigManagementService<T> typedService =
        (AuthenticationConfigManagementService<T>) service;

    return typedService.execute(
        contextBuilder, tenant, operator, oAuthToken, request, requestAttributes, dryRun);
  }
}
