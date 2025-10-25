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
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.transaction.OrgAuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementRequest;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for organization-level authentication transaction management operations.
 *
 * <p>Orchestrates organization-scoped authentication transaction management requests following the
 * Handler/Service pattern. This handler adds organization-level access control on top of the
 * standard authentication transaction management flow.
 *
 * <h2>Organization-Level Access Control</h2>
 *
 * <ol>
 *   <li>Organization membership verification
 *   <li>Tenant access verification within the organization
 *   <li>Organization-tenant relationship verification
 *   <li>Required permissions verification
 * </ol>
 *
 * @see AuthenticationTransactionManagementService
 * @see AuthenticationTransactionManagementResult
 * @see OrganizationAccessVerifier
 */
public class OrgAuthenticationTransactionManagementHandler {

  private final Map<String, AuthenticationTransactionManagementService<?>> services;
  private final OrgAuthenticationTransactionManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgAuthenticationTransactionManagementHandler.class);

  public OrgAuthenticationTransactionManagementHandler(
      Map<String, AuthenticationTransactionManagementService<?>> services,
      OrgAuthenticationTransactionManagementApi api,
      TenantQueryRepository tenantQueryRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  /**
   * Handles an organization-level authentication transaction management request.
   *
   * @param method the operation method (get, findList)
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to perform a dry run (preview only)
   * @return the operation result
   */
  public AuthenticationTransactionManagementResult handle(
      String method,
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Organization organization = authenticationContext.organization();
    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // 1. Service selection
    AuthenticationTransactionManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation
    AuthenticationTransactionManagementContextBuilder contextBuilder =
        new AuthenticationTransactionManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request);

    try {
      // 3. Tenant retrieval
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Organization access verification
      AdminPermissions permissions = api.getRequiredPermissions(method);
      organizationAccessVerifier.verify(organization, tenantIdentifier, operator, permissions);

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
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(notFound);
      return AuthenticationTransactionManagementResult.error(context, notFound);

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
