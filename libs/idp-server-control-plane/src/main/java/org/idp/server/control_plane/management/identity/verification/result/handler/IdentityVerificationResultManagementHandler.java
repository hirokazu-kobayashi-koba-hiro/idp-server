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

package org.idp.server.control_plane.management.identity.verification.result.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.verification.result.IdentityVerificationResultManagementApi;
import org.idp.server.control_plane.management.identity.verification.result.IdentityVerificationResultManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultManagementRequest;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultManagementResponse;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationResultManagementHandler {

  private final Map<String, IdentityVerificationResultManagementService<?>> services;
  private final IdentityVerificationResultManagementApi api;
  private final TenantQueryRepository tenantQueryRepository;
  private final ApiPermissionVerifier apiPermissionVerifier;
  LoggerWrapper log = LoggerWrapper.getLogger(IdentityVerificationResultManagementHandler.class);

  public IdentityVerificationResultManagementHandler(
      Map<String, IdentityVerificationResultManagementService<?>> services,
      IdentityVerificationResultManagementApi api,
      TenantQueryRepository tenantQueryRepository) {
    this.services = services;
    this.api = api;
    this.tenantQueryRepository = tenantQueryRepository;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  public IdentityVerificationResultManagementResult handle(
      String method,
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      IdentityVerificationResultManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    IdentityVerificationResultManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    IdentityVerificationResultManagementContextBuilder contextBuilder =
        new IdentityVerificationResultManagementContextBuilder(
            tenantIdentifier, operator, oAuthToken, requestAttributes, request, dryRun);

    try {
      Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

      AdminPermissions requiredPermissions = api.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      IdentityVerificationResultManagementResponse response =
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
      return IdentityVerificationResultManagementResult.success(context, response);
    } catch (NotFoundException e) {

      log.warn(e.getMessage());
      ResourceNotFoundException notFound = new ResourceNotFoundException(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(notFound);
      return IdentityVerificationResultManagementResult.error(context, notFound);

    } catch (ManagementApiException e) {

      log.warn(e.getMessage());
      AuditableContext errorContext = contextBuilder.buildPartial(e);
      return IdentityVerificationResultManagementResult.error(errorContext, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> IdentityVerificationResultManagementResponse executeService(
      IdentityVerificationResultManagementService<T> service,
      IdentityVerificationResultManagementContextBuilder builder,
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
