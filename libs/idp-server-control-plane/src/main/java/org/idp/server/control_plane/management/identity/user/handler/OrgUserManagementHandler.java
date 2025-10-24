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

package org.idp.server.control_plane.management.identity.user.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.user.OrgUserManagementApi;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level user management handler.
 *
 * <p>Orchestrates organization-scoped user management operations by delegating to appropriate
 * Service implementations via strategy pattern.
 *
 * <h2>Organization-Level Access Control</h2>
 *
 * <p>Unlike system-level operations, organization-level operations require:
 *
 * <ol>
 *   <li>Organization access verification (via OrganizationAccessVerifier)
 *   <li>Permission verification (same as system-level)
 *   <li>Tenant retrieval within organization context
 * </ol>
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization retrieval and validation
 *   <li>Organization access control verification
 *   <li>Tenant retrieval within organization context
 *   <li>Service selection and execution
 *   <li>Result/Exception wrapping
 * </ul>
 *
 * <h2>NOT Responsibilities</h2>
 *
 * <ul>
 *   <li>Business logic (delegated to Service)
 *   <li>Audit logging (handled by EntryService)
 *   <li>Transaction management (handled by EntryService)
 * </ul>
 */
public class OrgUserManagementHandler {

  private final Map<String, UserManagementService<?>> services;
  private final OrgUserManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgUserManagementHandler.class);

  public OrgUserManagementHandler(
      Map<String, UserManagementService<?>> services,
      OrgUserManagementApi api,
      TenantQueryRepository tenantQueryRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  /**
   * Handles organization-level user management operation.
   *
   * @param operation the operation name (e.g., "create", "update", "delete")
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param request the request object (type varies by operation)
   * @param requestAttributes the request attributes
   * @param dryRun whether this is a dry-run operation
   * @return the user management result
   */
  public UserManagementResult handle(
      String operation,
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Organization organization = authenticationContext.organization();
    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // 1. Service selection
    UserManagementService<?> service = services.get(operation);
    if (service == null) {
      throw new UnsupportedOperationException("Unknown operation: " + operation);
    }

    // 2. Context Builder creation (before Organization/Tenant retrieval)
    UserManagementContextBuilder contextBuilder =
        new UserManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request, dryRun);

    try {
      // 3. Organization and Tenant retrieval
      // Organization already retrieved from authenticationContext
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Get required permissions based on tenant type
      AdminPermissions permissions = api.getRequiredPermissions(operation, tenant);

      // 5. Organization-level access control (includes permission verification)
      organizationAccessVerifier.verify(organization, tenantIdentifier, operator, permissions);

      // 6. Execute service
      UserManagementResponse response =
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
      return UserManagementResult.success(context, response);

    } catch (NotFoundException notFoundException) {
      ResourceNotFoundException resourceNotFoundException =
          new ResourceNotFoundException(notFoundException.getMessage());
      AuditableContext errorContext = contextBuilder.buildPartial(resourceNotFoundException);
      return UserManagementResult.error(errorContext, resourceNotFoundException);
    } catch (ManagementApiException e) {
      log.warn(e.getMessage());
      // Partial Context creation for audit logging (may not have Tenant if retrieval failed)
      AuditableContext errorContext = contextBuilder.buildPartial(e);
      return UserManagementResult.error(errorContext, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <REQUEST> UserManagementResponse executeService(
      UserManagementService<REQUEST> service,
      UserManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      UserManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    return service.execute(
        builder, tenant, operator, oAuthToken, (REQUEST) request, requestAttributes, dryRun);
  }
}
