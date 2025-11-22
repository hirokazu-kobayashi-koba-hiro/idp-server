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

package org.idp.server.control_plane.management.organization.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.organization.OrganizationManagementApi;
import org.idp.server.control_plane.management.organization.OrganizationManagementContextBuilder;
import org.idp.server.control_plane.management.organization.io.OrganizationManagementRequest;
import org.idp.server.control_plane.management.organization.io.OrganizationManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * System-level organization management handler.
 *
 * <p>Orchestrates organization management operations by delegating to appropriate Service
 * implementations via strategy pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Admin tenant retrieval (cross-cutting concern)
 *   <li>Permission verification (cross-cutting concern)
 *   <li>Service selection based on operation method
 *   <li>Orchestration of business logic execution
 *   <li>Exception handling and Result wrapping
 * </ul>
 *
 * <h2>NOT Responsibilities (delegated to Services)</h2>
 *
 * <ul>
 *   <li>Request validation
 *   <li>Business rule verification
 *   <li>Repository operations
 *   <li>Event publishing
 * </ul>
 */
public class OrganizationManagementHandler {

  private final Map<String, OrganizationManagementService<?>> services;
  private final ApiPermissionVerifier apiPermissionVerifier;
  private final OrganizationManagementApi managementApi;
  LoggerWrapper log = LoggerWrapper.getLogger(OrganizationManagementHandler.class);

  public OrganizationManagementHandler(
      Map<String, OrganizationManagementService<?>> services,
      OrganizationManagementApi managementApi) {
    this.services = services;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
    this.managementApi = managementApi;
  }

  public OrganizationManagementResult handle(
      String method,
      AdminAuthenticationContext authenticationContext,
      OrganizationManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Service selection
    OrganizationManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    Tenant adminTenant = authenticationContext.adminTenant();
    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();
    OrganizationManagementContextBuilder contextBuilder =
        new OrganizationManagementContextBuilder(
            operator, oAuthToken, requestAttributes, request, dryRun);
    if (request.hasOrganizationIdentifier()) {
      contextBuilder.withTargetOrganizationIdentifier(request.organizationIdentifier());
    }

    try {

      // 2. Permission verification (throws PermissionDeniedException if denied)
      AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // 3. Delegate to service (pass adminTenant to avoid duplicate retrieval)
      OrganizationManagementResponse organizationManagementResponse =
          executeService(
              service,
              contextBuilder,
              adminTenant,
              operator,
              oAuthToken,
              request,
              requestAttributes,
              dryRun);

      AuditableContext context = contextBuilder.build();

      return OrganizationManagementResult.success(context, organizationManagementResponse);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(notFound);
      return OrganizationManagementResult.error(context, notFound);

    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(e);
      return OrganizationManagementResult.error(context, e);
    }
  }

  /**
   * Executes the service with type-safe request handling.
   *
   * <p>This helper method uses generics to ensure type safety when calling service.execute().
   * The @SuppressWarnings("unchecked") is safe because:
   *
   * <ul>
   *   <li>Each service is registered with its expected request type
   *   <li>EntryService methods pass the correct request type for each operation
   * </ul>
   *
   * @param service the service to execute
   * @param adminTenant the admin tenant (passed from Handler)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun if true, validate but don't persist changes
   * @return OrganizationManagementResult containing operation outcome
   */
  private <T> OrganizationManagementResponse executeService(
      OrganizationManagementService<T> service,
      OrganizationManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      OrganizationManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    @SuppressWarnings("unchecked")
    T typedRequest = (T) request;
    return service.execute(
        builder, adminTenant, operator, oAuthToken, typedRequest, requestAttributes, dryRun);
  }
}
