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

package org.idp.server.usecases.control_plane.organization_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.management.authentication.configuration.OrgAuthenticationConfigManagementApi;
import org.idp.server.control_plane.management.authentication.configuration.handler.*;
import org.idp.server.control_plane.management.authentication.configuration.io.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authentication policy configuration management entry service.
 *
 * <p>This service implements organization-scoped authentication policy configuration management
 * operations that allow organization administrators to manage authentication policy configurations
 * within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_POLICY_CONFIG_* permissions (handled by Handler)
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes and comprehensive
 * audit logging for organization-level authentication policy configuration operations.
 *
 * @see OrgAuthenticationConfigManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthenticationConfigurationManagementEntryService
 */
@Transaction
public class OrgAuthenticationConfigManagementEntryService
    implements OrgAuthenticationConfigManagementApi {

  private final OrgAuthenticationConfigManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public OrgAuthenticationConfigManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository,
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, AuthenticationConfigManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new AuthenticationConfigCreationService(authenticationConfigurationCommandRepository));
    services.put(
        "findList",
        new AuthenticationConfigFindListService(authenticationConfigurationQueryRepository));
    services.put(
        "get", new AuthenticationConfigFindService(authenticationConfigurationQueryRepository));
    services.put(
        "update",
        new AuthenticationConfigUpdateService(
            authenticationConfigurationQueryRepository,
            authenticationConfigurationCommandRepository));
    services.put(
        "delete",
        new AuthenticationConfigDeletionService(
            authenticationConfigurationQueryRepository,
            authenticationConfigurationCommandRepository));

    this.handler =
        new OrgAuthenticationConfigManagementHandler(
            services, this, tenantQueryRepository, new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public AuthenticationConfigManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationConfigManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationConfigManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationConfigFindListRequest request =
        new AuthenticationConfigFindListRequest(limit, offset);
    AuthenticationConfigManagementResult result =
        handler.handle(
            "findList", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationConfigManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationConfigManagementResult result =
        handler.handle(
            "get",
            authenticationContext,
            tenantIdentifier,
            new AuthenticationConfigFindRequest(identifier),
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public AuthenticationConfigManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationConfigUpdateRequest updateRequest =
        new AuthenticationConfigUpdateRequest(identifier, request);
    AuthenticationConfigManagementResult result =
        handler.handle(
            "update",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public AuthenticationConfigManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationConfigManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            tenantIdentifier,
            new AuthenticationConfigDeleteRequest(identifier),
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
