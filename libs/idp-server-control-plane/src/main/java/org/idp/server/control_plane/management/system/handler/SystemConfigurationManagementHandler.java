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

package org.idp.server.control_plane.management.system.handler;

import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.system.SystemConfigurationManagementApi;
import org.idp.server.control_plane.management.system.SystemConfigurationManagementContextBuilder;
import org.idp.server.control_plane.management.system.io.SystemConfigurationFindRequest;
import org.idp.server.control_plane.management.system.io.SystemConfigurationManagementResponse;
import org.idp.server.control_plane.management.system.io.SystemConfigurationUpdateRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for system configuration management operations.
 *
 * <p>Orchestrates system configuration management by delegating to Service implementations.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Permission verification (cross-cutting concern)
 *   <li>Service selection and delegation
 *   <li>Exception handling
 * </ul>
 *
 * <h2>NOT Responsibilities (delegated to Services)</h2>
 *
 * <ul>
 *   <li>Request validation
 *   <li>Repository operations
 *   <li>Cache management
 * </ul>
 */
public class SystemConfigurationManagementHandler {

  private final Map<String, SystemConfigurationManagementService<?>> services;
  private final SystemConfigurationManagementApi managementApi;
  private final ApiPermissionVerifier apiPermissionVerifier;
  private final LoggerWrapper log =
      LoggerWrapper.getLogger(SystemConfigurationManagementHandler.class);

  public SystemConfigurationManagementHandler(
      Map<String, SystemConfigurationManagementService<?>> services,
      SystemConfigurationManagementApi managementApi) {
    this.services = services;
    this.managementApi = managementApi;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  public SystemConfigurationManagementResult handle(
      String method,
      AdminAuthenticationContext authenticationContext,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Service selection
    SystemConfigurationManagementService<?> service = services.get(method);
    if (service == null) {
      throw new UnSupportedException("Unsupported operation method: " + method);
    }

    User operator = authenticationContext.operator();
    OAuthToken oAuthToken = authenticationContext.oAuthToken();

    // Create context builder for audit logging
    SystemConfigurationManagementContextBuilder contextBuilder =
        new SystemConfigurationManagementContextBuilder(
            operator,
            oAuthToken,
            requestAttributes,
            request instanceof SystemConfigurationUpdateRequest
                ? (SystemConfigurationUpdateRequest) request
                : null,
            dryRun);

    try {
      // 2. Permission verification
      AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method);
      apiPermissionVerifier.verify(operator, requiredPermissions);

      // 3. Delegate to service
      SystemConfigurationManagementResponse response =
          executeService(
              service, contextBuilder, operator, oAuthToken, request, requestAttributes, dryRun);

      AuditableContext context = contextBuilder.build();
      return SystemConfigurationManagementResult.success(context, response);

    } catch (ManagementApiException e) {
      log.warn(e.getMessage());
      AuditableContext context = contextBuilder.buildPartial(e);
      return SystemConfigurationManagementResult.error(context, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> SystemConfigurationManagementResponse executeService(
      SystemConfigurationManagementService<T> service,
      SystemConfigurationManagementContextBuilder contextBuilder,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    T typedRequest;
    if (request == null) {
      typedRequest = (T) new SystemConfigurationFindRequest();
    } else if (request instanceof SystemConfigurationUpdateRequest) {
      typedRequest = (T) request;
    } else {
      typedRequest = (T) request;
    }
    return service.execute(
        contextBuilder, operator, oAuthToken, typedRequest, requestAttributes, dryRun);
  }
}
