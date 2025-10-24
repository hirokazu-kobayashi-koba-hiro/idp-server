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
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigurationManagementApi;
import org.idp.server.control_plane.management.authentication.policy.handler.*;
import org.idp.server.control_plane.management.authentication.policy.handler.AuthenticationPolicyConfigUpdateRequest;
import org.idp.server.control_plane.management.authentication.policy.io.*;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuthenticationPolicyConfigurationManagementEntryService
    implements AuthenticationPolicyConfigurationManagementApi {

  AuditLogPublisher auditLogPublisher;
  TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationPolicyConfigurationManagementEntryService.class);

  // Handler/Service pattern
  private AuthenticationPolicyConfigManagementHandler handler;

  public AuthenticationPolicyConfigurationManagementEntryService(
      AuthenticationPolicyConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository,
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
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

  private AuthenticationPolicyConfigManagementHandler createHandler(
      AuthenticationPolicyConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository,
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository) {

    Map<String, AuthenticationPolicyConfigManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new AuthenticationPolicyConfigCreationService(
            authenticationPolicyConfigurationCommandRepository));
    services.put(
        "findList",
        new AuthenticationPolicyConfigFindListService(
            authenticationPolicyConfigurationQueryRepository));
    services.put(
        "get",
        new AuthenticationPolicyConfigFindService(
            authenticationPolicyConfigurationQueryRepository));
    services.put(
        "update",
        new AuthenticationPolicyConfigUpdateService(
            authenticationPolicyConfigurationQueryRepository,
            authenticationPolicyConfigurationCommandRepository));
    services.put(
        "delete",
        new AuthenticationPolicyConfigDeletionService(
            authenticationPolicyConfigurationQueryRepository,
            authenticationPolicyConfigurationCommandRepository));

    return new AuthenticationPolicyConfigManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationPolicyConfigManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationPolicyConfigManagementRequest request =
        new AuthenticationPolicyConfigFindListRequest(limit, offset);
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "findList", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationPolicyConfigManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationPolicyConfigFindRequest request =
        new AuthenticationPolicyConfigFindRequest(identifier);
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigurationIdentifier identifier,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    AuthenticationPolicyConfigManagementRequest updateRequest =
        new AuthenticationPolicyConfigUpdateRequest(identifier, request);
    AuthenticationPolicyConfigManagementResult result =
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
  public AuthenticationPolicyConfigManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    AuthenticationPolicyConfigDeleteRequest request =
        new AuthenticationPolicyConfigDeleteRequest(identifier);
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "delete", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    // Record audit log
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
