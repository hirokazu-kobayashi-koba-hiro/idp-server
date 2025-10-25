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
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementContextBuilder;
import org.idp.server.control_plane.management.oidc.authorization.OrgAuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementRequest;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
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
 * Handler for organization-level authorization server management operations.
 *
 * <p>Orchestrates organization-scoped authorization server management requests following the
 * Handler/Service pattern. This handler adds organization-level access control on top of the
 * standard authorization server management flow.
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
 * <h2>Handler/Service Pattern Flow</h2>
 *
 * <ol>
 *   <li>Handler resolves organization and tenant
 *   <li>Handler verifies organization access (4-step verification)
 *   <li>Handler delegates to Service implementation
 *   <li>Service throws ManagementApiException on validation/verification failures
 *   <li>Handler catches exception and converts to Result
 *   <li>EntryService converts Result to HTTP response
 * </ol>
 *
 * @see AuthorizationServerManagementService
 * @see AuthorizationServerManagementResult
 * @see OrganizationAccessVerifier
 */
public class OrgAuthorizationServerManagementHandler {

  private final Map<String, AuthorizationServerManagementService<?>> services;
  private final OrgAuthorizationServerManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgAuthorizationServerManagementHandler.class);

  /**
   * Creates a new organization authorization server management handler.
   *
   * @param services map of operation method names to Service implementations
   * @param api the organization authorization server management API (for permission definitions)
   * @param tenantQueryRepository the tenant query repository
   * @param organizationAccessVerifier the organization access verifier
   */
  public OrgAuthorizationServerManagementHandler(
      Map<String, AuthorizationServerManagementService<?>> services,
      OrgAuthorizationServerManagementApi api,
      TenantQueryRepository tenantQueryRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  /**
   * Handles an organization-level authorization server management request.
   *
   * @param method the operation method (get, update)
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param request the operation-specific request object (may be null for get)
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to perform a dry run (preview only)
   * @return the operation result
   */
  public AuthorizationServerManagementResult handle(
      String method,
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationServerManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Organization organization = authenticationContext.organization();
    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // 1. Service selection
    AuthorizationServerManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation
    AuthorizationServerManagementContextBuilder contextBuilder =
        new AuthorizationServerManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request, dryRun);

    try {
      // 3. Tenant retrieval
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Organization access verification
      AdminPermissions permissions = api.getRequiredPermissions(method);
      organizationAccessVerifier.verify(organization, tenantIdentifier, operator, permissions);

      // 5. Delegate to service
      AuthorizationServerManagementResponse response =
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
      return AuthorizationServerManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(notFound);
      return AuthorizationServerManagementResult.error(context, notFound);

    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext partialContext = contextBuilder.buildPartial(e);
      return AuthorizationServerManagementResult.error(partialContext, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> AuthorizationServerManagementResponse executeService(
      AuthorizationServerManagementService<?> service,
      AuthorizationServerManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthorizationServerManagementService<T> typedService =
        (AuthorizationServerManagementService<T>) service;

    return typedService.execute(
        contextBuilder, tenant, operator, oAuthToken, request, requestAttributes, dryRun);
  }
}
