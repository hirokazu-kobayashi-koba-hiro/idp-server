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
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementRequest;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
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
  private final ApiPermissionVerifier apiPermissionVerifier;
  private final AuthenticationTransactionManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationTransactionManagementHandler.class);

  public AuthenticationTransactionManagementHandler(
      Map<String, AuthenticationTransactionManagementService<?>> services,
      AuthenticationTransactionManagementApi api,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  /**
   * Handles an authentication transaction management request.
   *
   * @param method the operation method (get, findList)
   * @param authenticationContext the admin authentication context
   * @param tenantIdentifier the tenant identifier
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to perform a dry run (preview only)
   * @return the operation result
   */
  public AuthenticationTransactionManagementResult handle(
      String method,
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // 1. Service selection
    AuthenticationTransactionManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation (before Tenant retrieval - enables audit logging on errors)
    AuthenticationTransactionManagementContextBuilder contextBuilder =
        new AuthenticationTransactionManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request);

    try {
      // 3. Tenant retrieval
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Permission verification
      AdminPermissions requiredPermissions = api.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // 5. Delegate to service
      AuthenticationTransactionManagementResponse response =
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
      return AuthenticationTransactionManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());

      ResourceNotFoundException resourceNotFoundException =
          new ResourceNotFoundException(e.getMessage());

      AuditableContext partialContext = contextBuilder.buildPartial(resourceNotFoundException);
      return AuthenticationTransactionManagementResult.error(
          partialContext, resourceNotFoundException);

    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext partialContext = contextBuilder.buildPartial(e);
      return AuthenticationTransactionManagementResult.error(partialContext, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> AuthenticationTransactionManagementResponse executeService(
      AuthenticationTransactionManagementService<?> service,
      AuthenticationTransactionManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthenticationTransactionManagementService<T> typedService =
        (AuthenticationTransactionManagementService<T>) service;

    return typedService.execute(
        contextBuilder, tenant, operator, oAuthToken, request, requestAttributes, dryRun);
  }
}
