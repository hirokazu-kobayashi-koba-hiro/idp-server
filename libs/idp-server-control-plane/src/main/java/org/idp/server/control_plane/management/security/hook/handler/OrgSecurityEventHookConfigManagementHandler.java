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

package org.idp.server.control_plane.management.security.hook.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.hook.OrgSecurityEventHookConfigManagementApi;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigManagementContextBuilder;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level security event hook configuration management handler.
 *
 * <p>Orchestrates organization-level security event hook configuration management operations with
 * 4-step access control.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Organization retrieval and validation
 *   <li>Organization membership verification
 *   <li>Tenant retrieval
 *   <li>Organization-tenant relationship verification
 *   <li>Service selection and execution
 *   <li>Exception catching and Result wrapping
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
public class OrgSecurityEventHookConfigManagementHandler {

  private final Map<String, SecurityEventHookConfigManagementService<?>> services;
  private final OrgSecurityEventHookConfigManagementApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgSecurityEventHookConfigManagementHandler.class);

  public OrgSecurityEventHookConfigManagementHandler(
      Map<String, SecurityEventHookConfigManagementService<?>> services,
      OrgSecurityEventHookConfigManagementApi managementApi,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.managementApi = managementApi;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  /**
   * Handles organization-level security event hook configuration management operation.
   *
   * <p>Performs 4-step organization access control before delegating to Service.
   *
   * @param method the operation method (e.g., "create", "findList", "get", "update", "delete")
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes for audit logging
   * @param dryRun whether to simulate the operation without persisting changes
   * @return SecurityEventHookConfigManagementResult containing operation outcome or exception
   */
  public SecurityEventHookConfigManagementResult handle(
      String method,
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookConfigManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();
    Organization organization = authenticationContext.organization();

    // 1. Service selection
    SecurityEventHookConfigManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation (before any retrieval - enables audit logging on errors)
    SecurityEventHookConfigManagementContextBuilder contextBuilder =
        new SecurityEventHookConfigManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request, dryRun);

    try {
      // 3. Organization access verification (4-step verification)
      organizationAccessVerifier.verify(
          organization, tenantIdentifier, operator, managementApi.getRequiredPermissions(method));

      // 4. Tenant retrieval (the actual target tenant)
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 5. Delegate to service
      SecurityEventHookConfigManagementResponse response =
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
      return SecurityEventHookConfigManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(notFound);
      return SecurityEventHookConfigManagementResult.error(context, notFound);
    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext errorContext = contextBuilder.buildPartial(e);
      return SecurityEventHookConfigManagementResult.error(errorContext, e);
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
   * <p>Following the UserManagement pattern:
   *
   * <ol>
   *   <li>Service returns Response directly (and populates builder with withAfter/withBefore)
   *   <li>Handler calls builder.build() to create Context
   *   <li>Handler wraps Response in Result.success(context, response)
   * </ol>
   *
   * @param service the service to execute
   * @param builder context builder for incremental context construction
   * @param tenant the tenant (for context and audit logging)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to simulate the operation
   * @return SecurityEventHookConfigManagementResult containing operation outcome
   */
  @SuppressWarnings("unchecked")
  private <T> SecurityEventHookConfigManagementResponse executeService(
      SecurityEventHookConfigManagementService<T> service,
      SecurityEventHookConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    T typedRequest = (T) request;
    return service.execute(
        builder, tenant, operator, oAuthToken, typedRequest, requestAttributes, dryRun);
  }
}
