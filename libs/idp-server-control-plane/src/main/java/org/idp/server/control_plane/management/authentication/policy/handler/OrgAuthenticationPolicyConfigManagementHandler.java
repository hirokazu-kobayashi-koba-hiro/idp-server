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

package org.idp.server.control_plane.management.authentication.policy.handler;

import java.util.Map;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigManagementContextBuilder;
import org.idp.server.control_plane.management.authentication.policy.OrgAuthenticationPolicyConfigManagementApi;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementRequest;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
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
 * Organization-level authentication policy configuration management handler.
 *
 * <p>Orchestrates organization-scoped authentication policy configuration management operations by
 * delegating to appropriate Service implementations via strategy pattern.
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
 *   <li>Organization access control verification
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
 *
 * @see AuthenticationPolicyConfigManagementService
 * @see AuthenticationPolicyConfigManagementResult
 * @see OrganizationAccessVerifier
 */
public class OrgAuthenticationPolicyConfigManagementHandler {

  private final Map<String, AuthenticationPolicyConfigManagementService<?>> services;
  private final OrgAuthenticationPolicyConfigManagementApi entryService;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  private final ApiPermissionVerifier apiPermissionVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgAuthenticationPolicyConfigManagementHandler.class);

  public OrgAuthenticationPolicyConfigManagementHandler(
      Map<String, AuthenticationPolicyConfigManagementService<?>> services,
      OrgAuthenticationPolicyConfigManagementApi entryService,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {
    this.services = services;
    this.entryService = entryService;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  public AuthenticationPolicyConfigManagementResult handle(
      String method,
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Organization organization = authenticationContext.organization();
    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // 1. Service selection
    AuthenticationPolicyConfigManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation (before Tenant retrieval - enables audit logging on errors)
    AuthenticationPolicyConfigManagementContextBuilder contextBuilder =
        new AuthenticationPolicyConfigManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request, dryRun);

    try {
      // 3. Get organization and tenant
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Organization access verification (4-step verification)
      AdminPermissions requiredPermissions = entryService.getRequiredPermissions(method);
      organizationAccessVerifier.verify(
          organization, tenantIdentifier, operator, requiredPermissions);

      // 5. Delegate to service
      AuthenticationPolicyConfigManagementResponse response =
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
      return AuthenticationPolicyConfigManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(notFound);
      return AuthenticationPolicyConfigManagementResult.error(context, notFound);
    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext partialContext = contextBuilder.buildPartial(e);
      return AuthenticationPolicyConfigManagementResult.error(partialContext, e);
    }
  }

  /**
   * Executes the service with type-safe request handling.
   *
   * <p>This helper method uses generics to ensure type safety when calling service.execute().
   */
  @SuppressWarnings("unchecked")
  private <T> AuthenticationPolicyConfigManagementResponse executeService(
      AuthenticationPolicyConfigManagementService<?> service,
      AuthenticationPolicyConfigManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthenticationPolicyConfigManagementService<T> typedService =
        (AuthenticationPolicyConfigManagementService<T>) service;

    return typedService.execute(
        contextBuilder, tenant, operator, oAuthToken, request, requestAttributes, dryRun);
  }
}
