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

package org.idp.server.control_plane.management.contact.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.contact.ContactVerificationChallengeManagementContextBuilder;
import org.idp.server.control_plane.management.contact.OrgContactVerificationChallengeManagementApi;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementRequest;
import org.idp.server.control_plane.management.contact.io.ContactVerificationChallengeManagementResponse;
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
 * Handler for organization-level contact verification challenge management operations.
 *
 * <p>Orchestrates organization-scoped contact verification challenge management requests following
 * the Handler/Service pattern.
 *
 * @see ContactVerificationChallengeManagementService
 * @see ContactVerificationChallengeManagementResult
 * @see OrganizationAccessVerifier
 */
public class OrgContactVerificationChallengeManagementHandler {

  private final Map<String, ContactVerificationChallengeManagementService<?>> services;
  private final OrgContactVerificationChallengeManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  LoggerWrapper log =
      LoggerWrapper.getLogger(OrgContactVerificationChallengeManagementHandler.class);

  public OrgContactVerificationChallengeManagementHandler(
      Map<String, ContactVerificationChallengeManagementService<?>> services,
      OrgContactVerificationChallengeManagementApi api,
      TenantQueryRepository tenantQueryRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  /**
   * Handles an organization-level contact verification challenge management request.
   *
   * @param method the operation method (get, findList)
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant identifier
   * @param request the operation-specific request object
   * @param requestAttributes HTTP request attributes
   * @return the operation result
   */
  public ContactVerificationChallengeManagementResult handle(
      String method,
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ContactVerificationChallengeManagementRequest request,
      RequestAttributes requestAttributes) {

    Organization organization = authenticationContext.organization();
    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // 1. Service selection
    ContactVerificationChallengeManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    // 2. Context Builder creation
    ContactVerificationChallengeManagementContextBuilder contextBuilder =
        new ContactVerificationChallengeManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request);

    try {
      // 3. Tenant retrieval
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      // 4. Organization access verification
      AdminPermissions permissions = api.getRequiredPermissions(method);
      organizationAccessVerifier.verify(organization, tenantIdentifier, operator, permissions);

      // 5. Delegate to service
      ContactVerificationChallengeManagementResponse response =
          executeService(
              service, contextBuilder, tenant, operator, oAuthToken, request, requestAttributes);

      AuditableContext context = contextBuilder.build();
      return ContactVerificationChallengeManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(notFound);
      return ContactVerificationChallengeManagementResult.error(context, notFound);

    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext partialContext = contextBuilder.buildPartial(e);
      return ContactVerificationChallengeManagementResult.error(partialContext, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> ContactVerificationChallengeManagementResponse executeService(
      ContactVerificationChallengeManagementService<?> service,
      ContactVerificationChallengeManagementContextBuilder contextBuilder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes) {

    ContactVerificationChallengeManagementService<T> typedService =
        (ContactVerificationChallengeManagementService<T>) service;

    return typedService.execute(
        contextBuilder, tenant, operator, oAuthToken, request, requestAttributes);
  }
}
