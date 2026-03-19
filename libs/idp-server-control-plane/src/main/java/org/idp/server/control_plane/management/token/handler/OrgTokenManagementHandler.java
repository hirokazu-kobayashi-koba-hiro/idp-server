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

package org.idp.server.control_plane.management.token.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.token.OrgTokenManagementApi;
import org.idp.server.control_plane.management.token.TokenManagementContextBuilder;
import org.idp.server.control_plane.management.token.io.TokenManagementRequest;
import org.idp.server.control_plane.management.token.io.TokenManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

public class OrgTokenManagementHandler {

  private final Map<String, TokenManagementService<?>> services;
  private final OrgTokenManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(OrgTokenManagementHandler.class);

  public OrgTokenManagementHandler(
      Map<String, TokenManagementService<?>> services,
      OrgTokenManagementApi api,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.services = services;
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  public TokenManagementResult handle(
      String method,
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      TokenManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Organization organization = authenticationContext.organization();
    User operator = authenticationContext.operator();

    TokenManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    TokenManagementContextBuilder contextBuilder =
        new TokenManagementContextBuilder(
            tenantIdentifier, operator, requestAttributes, request, dryRun);

    try {
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      AdminPermissions permissions = api.getRequiredPermissions(method);
      organizationAccessVerifier.verify(organization, tenantIdentifier, operator, permissions);

      TokenManagementResponse response =
          executeService(
              service, contextBuilder, tenant, operator, request, requestAttributes, dryRun);

      AuditableContext context = contextBuilder.build();
      return TokenManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(notFound);
      return TokenManagementResult.error(context, notFound);

    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext errorContext = contextBuilder.buildPartial(e);
      return TokenManagementResult.error(errorContext, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> TokenManagementResponse executeService(
      TokenManagementService<T> service,
      TokenManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    T typedRequest = (T) request;
    return service.execute(builder, tenant, operator, typedRequest, requestAttributes, dryRun);
  }
}
