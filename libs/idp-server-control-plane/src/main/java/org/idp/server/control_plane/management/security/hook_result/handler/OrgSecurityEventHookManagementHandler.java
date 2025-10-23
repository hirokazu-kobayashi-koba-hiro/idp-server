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

package org.idp.server.control_plane.management.security.hook_result.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.hook_result.OrgSecurityEventHookManagementApi;
import org.idp.server.control_plane.management.security.hook_result.SecurityEventHookManagementContextBuilder;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementRequest;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
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
 * Organization-level security event hook management handler.
 *
 * <p>Orchestrates organization-level security event hook management operations with 4-step access
 * control.
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
public class OrgSecurityEventHookManagementHandler {

  private final Map<String, SecurityEventHookManagementService<?>> services;
  private final OrgSecurityEventHookManagementApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgSecurityEventHookManagementHandler.class);

  public OrgSecurityEventHookManagementHandler(
      Map<String, SecurityEventHookManagementService<?>> services,
      OrgSecurityEventHookManagementApi managementApi,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.managementApi = managementApi;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  public SecurityEventHookManagementResult handle(
      String method,
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookManagementRequest request,
      RequestAttributes requestAttributes) {

    // 1. Service selection
    SecurityEventHookManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();
    SecurityEventHookManagementContextBuilder builder =
        new SecurityEventHookManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request);
    try {
      // 2. Organization retrieval
      Organization organization = authenticationContext.organization();

      // 3. Tenant retrieval (the actual target tenant)
      Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Organization access verification (4-step verification)
      organizationAccessVerifier.verify(
          organization, tenantIdentifier, operator, managementApi.getRequiredPermissions(method));

      // 5. Delegate to service
      SecurityEventHookManagementResponse response =
          executeService(
              service, builder, targetTenant, operator, oAuthToken, request, requestAttributes);
      AuditableContext context = builder.build();

      return SecurityEventHookManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = builder.buildPartial(notFound);
      return SecurityEventHookManagementResult.error(context, notFound);
    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext context = builder.buildPartial(e);
      return SecurityEventHookManagementResult.error(context, e);
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
   * @param tenant the tenant (for context and audit logging)
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @return SecurityEventHookManagementResult containing operation outcome
   */
  private <T> SecurityEventHookManagementResponse executeService(
      SecurityEventHookManagementService<T> service,
      SecurityEventHookManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes) {
    @SuppressWarnings("unchecked")
    T typedRequest = (T) request;
    return service.execute(builder, tenant, operator, oAuthToken, typedRequest, requestAttributes);
  }
}
