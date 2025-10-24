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

package org.idp.server.usecases.control_plane.system_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigurationManagementApi;
import org.idp.server.control_plane.management.authentication.configuration.handler.*;
import org.idp.server.control_plane.management.authentication.configuration.io.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuthenticationConfigurationManagementEntryService
    implements AuthenticationConfigurationManagementApi {

  AuditLogPublisher auditLogPublisher;
  TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationConfigurationManagementEntryService.class);

  // Handler/Service pattern
  private AuthenticationConfigManagementHandler handler;

  public AuthenticationConfigurationManagementEntryService(
      AuthenticationConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository,
      AuthenticationConfigurationQueryRepository authenticationPolicyConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;
    this.tenantQueryRepository = tenantQueryRepository;

    this.handler =
        createHandler(
            authenticationPolicyConfigurationCommandRepository,
            authenticationPolicyConfigurationQueryRepository,
            tenantQueryRepository);
  }

  private AuthenticationConfigManagementHandler createHandler(
      AuthenticationConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository,
      AuthenticationConfigurationQueryRepository authenticationPolicyConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository) {

    Map<String, AuthenticationConfigManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new AuthenticationConfigCreationService(
            authenticationPolicyConfigurationCommandRepository));
    services.put(
        "findList",
        new AuthenticationConfigFindListService(authenticationPolicyConfigurationQueryRepository));
    services.put(
        "get",
        new AuthenticationConfigFindService(authenticationPolicyConfigurationQueryRepository));
    services.put(
        "update",
        new AuthenticationConfigUpdateService(
            authenticationPolicyConfigurationQueryRepository,
            authenticationPolicyConfigurationCommandRepository));
    services.put(
        "delete",
        new AuthenticationConfigDeletionService(
            authenticationPolicyConfigurationQueryRepository,
            authenticationPolicyConfigurationCommandRepository));

    return new AuthenticationConfigManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  public AuthenticationConfigManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    AuthenticationConfigManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationConfigManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationConfigManagementRequest request =
        new AuthenticationConfigFindListRequest(limit, offset);
    AuthenticationConfigManagementResult result =
        handler.handle(
            "findList", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationConfigManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationConfigFindRequest request = new AuthenticationConfigFindRequest(identifier);
    AuthenticationConfigManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public AuthenticationConfigManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    AuthenticationConfigManagementRequest updateRequest =
        new AuthenticationConfigUpdateRequest(identifier, request);
    AuthenticationConfigManagementResult result =
        handler.handle(
            "update",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public AuthenticationConfigManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    AuthenticationConfigDeleteRequest request = new AuthenticationConfigDeleteRequest(identifier);
    AuthenticationConfigManagementResult result =
        handler.handle(
            "delete", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
