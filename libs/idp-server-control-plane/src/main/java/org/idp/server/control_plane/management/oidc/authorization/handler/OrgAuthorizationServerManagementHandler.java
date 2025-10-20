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
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.oidc.authorization.OrgAuthorizationServerManagementApi;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for organization-level authorization server management operations.
 *
 * <p>Orchestrates authorization server management operations with organization-level access
 * control. Delegates to shared service implementations after verifying organization access. Part of
 * Handler/Service pattern.
 *
 * @see AuthorizationServerManagementService
 * @see AuthorizationServerManagementResult
 * @see OrganizationAccessVerifier
 */
public class OrgAuthorizationServerManagementHandler {

  private final Map<String, AuthorizationServerManagementService<?>> services;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  private final OrgAuthorizationServerManagementApi api;

  public OrgAuthorizationServerManagementHandler(
      Map<String, AuthorizationServerManagementService<?>> services,
      OrganizationRepository organizationRepository,
      OrgAuthorizationServerManagementApi api) {
    this.services = services;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
    this.api = api;
  }

  /**
   * Handles organization-level authorization server management operations.
   *
   * @param method the method name (get, update)
   * @param organizationIdentifier the organization identifier
   * @param tenant the tenant
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param request the request object (type varies by method, may be null for get)
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run
   * @return the result of the operation
   */
  public <T> AuthorizationServerManagementResult handle(
      String method,
      OrganizationIdentifier organizationIdentifier,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    try {
      AdminPermissions requiredPermissions = api.getRequiredPermissions(method);

      Organization organization = organizationRepository.get(organizationIdentifier);

      OrganizationAccessControlResult accessResult =
          organizationAccessVerifier.verifyAccess(
              organization, tenant.identifier(), operator, requiredPermissions);

      if (!accessResult.isSuccess()) {
        throw new PermissionDeniedException(requiredPermissions, Set.of());
      }

      return executeService(
          method, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      return AuthorizationServerManagementResult.error(tenant.identifier(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> AuthorizationServerManagementResult executeService(
      String method,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthorizationServerManagementService<T> service =
        (AuthorizationServerManagementService<T>) services.get(method);

    if (service == null) {
      throw new IllegalArgumentException("Unsupported method: " + method);
    }

    return service.execute(tenant, operator, oAuthToken, request, requestAttributes, dryRun);
  }
}
