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

package org.idp.server.control_plane.management.authentication.interaction.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.interaction.AuthenticationInteractionManagementApi;
import org.idp.server.control_plane.management.authentication.interaction.AuthenticationInteractionManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementRequest;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
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
 * Handler for system-level authentication interaction management operations.
 *
 * <p>Orchestrates authentication interaction management requests following the Handler/Service
 * pattern.
 *
 * @see AuthenticationInteractionManagementService
 * @see AuthenticationInteractionManagementResult
 */
public class AuthenticationInteractionManagementHandler {

  private final Map<String, AuthenticationInteractionManagementService<?>> services;
  private final ApiPermissionVerifier apiPermissionVerifier;
  private final AuthenticationInteractionManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationInteractionManagementHandler.class);

  public AuthenticationInteractionManagementHandler(
      Map<String, AuthenticationInteractionManagementService<?>> services,
      AuthenticationInteractionManagementApi api,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  /**
   * Handles an authentication interaction management request.
   *
   * @param method the operation method (get, findList)
   * @param authenticationContext the admin authentication context
   * @param tenantIdentifier the tenant identifier
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @return the operation result
   */
  public AuthenticationInteractionManagementResult handle(
      String method,
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationInteractionManagementRequest request,
      RequestAttributes requestAttributes) {

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // 1. Service selection
    AuthenticationInteractionManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation (before Tenant retrieval - enables audit logging on errors)
    AuthenticationInteractionManagementContextBuilder contextBuilder =
        new AuthenticationInteractionManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request);

    try {
      // 3. Tenant retrieval
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Permission verification
      AdminPermissions requiredPermissions = api.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // 5. Delegate to service
      AuthenticationInteractionManagementResponse response =
          executeService(
              service, contextBuilder, tenant, operator, oAuthToken, request, requestAttributes);

      AuditableContext context = contextBuilder.build();
      return AuthenticationInteractionManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());

      ResourceNotFoundException resourceNotFoundException =
          new ResourceNotFoundException(e.getMessage());

      AuditableContext partialContext = contextBuilder.buildPartial(resourceNotFoundException);
      return AuthenticationInteractionManagementResult.error(
          partialContext, resourceNotFoundException);

    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext partialContext = contextBuilder.buildPartial(e);
      return AuthenticationInteractionManagementResult.error(partialContext, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> AuthenticationInteractionManagementResponse executeService(
      AuthenticationInteractionManagementService<?> service,
      AuthenticationInteractionManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes) {

    AuthenticationInteractionManagementService<T> typedService =
        (AuthenticationInteractionManagementService<T>) service;

    return typedService.execute(
        contextBuilder, tenant, operator, oAuthToken, request, requestAttributes);
  }
}
