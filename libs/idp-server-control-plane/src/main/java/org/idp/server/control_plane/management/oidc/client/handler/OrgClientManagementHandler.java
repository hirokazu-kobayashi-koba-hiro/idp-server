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
import org.idp.server.control_plane.management.oidc.client.OrgClientManagementApi;
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
 * Handler for organization-level client management operations.
 *
 * <p>Orchestrates client management operations with organization-level access control. Delegates to
 * shared service implementations after verifying organization access. Part of Handler/Service
 * pattern.
 *
 * @see ClientManagementService
 * @see ClientManagementResult
 * @see OrganizationAccessVerifier
 */
public class OrgClientManagementHandler {

  private final Map<String, ClientManagementService<?>> services;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  private final OrgClientManagementApi api;

  public OrgClientManagementHandler(
      Map<String, ClientManagementService<?>> services,
      OrganizationRepository organizationRepository,
      OrgClientManagementApi api) {
    this.services = services;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
    this.api = api;
  }

  /**
   * Handles organization-level client management operations.
   *
   * @param method the method name (create, findList, get, update, delete)
   * @param organizationIdentifier the organization identifier
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
