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
import org.idp.server.control_plane.management.security.hook.*;
import org.idp.server.control_plane.management.security.hook.handler.*;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigDeleteRequest;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigFindRequest;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level security event hook configuration management entry service.
 *
 * <p>This service implements organization-scoped security event hook configuration management
 * operations that allow organization administrators to manage security event hook configurations
 * within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       SECURITY_EVENT_HOOK_CONFIG_* permissions
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes and comprehensive
 * audit logging for organization-level security event hook configuration operations.
 *
 * @see OrgSecurityEventHookConfigManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.SecurityEventHookConfigurationManagementEntryService
 */
@Transaction
public class OrgSecurityEventHookConfigManagementEntryService
    implements OrgSecurityEventHookConfigManagementApi {

  private final OrgSecurityEventHookConfigManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new organization security event hook configuration management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param securityEventHookConfigurationCommandRepository the security event hook configuration
   *     command repository
   * @param securityEventHookConfigurationQueryRepository the security event hook configuration
   *     query repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgSecurityEventHookConfigManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      SecurityEventHookConfigurationCommandRepository
          securityEventHookConfigurationCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, SecurityEventHookConfigManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new SecurityEventHookConfigCreateService(securityEventHookConfigurationCommandRepository));
    services.put(
        "findList",
        new SecurityEventHookConfigFindListService(securityEventHookConfigurationQueryRepository));
    services.put(
        "get",
        new SecurityEventHookConfigFindService(securityEventHookConfigurationQueryRepository));
    services.put(
        "update",
        new SecurityEventHookConfigUpdateService(
            securityEventHookConfigurationQueryRepository,
            securityEventHookConfigurationCommandRepository));
    services.put(
        "delete",
        new SecurityEventHookConfigDeleteService(
            securityEventHookConfigurationQueryRepository,
            securityEventHookConfigurationCommandRepository));

    this.handler =
        new OrgSecurityEventHookConfigManagementHandler(
            services,
            this,
            tenantQueryRepository,
            organizationRepository,
            new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public SecurityEventHookConfigManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookConfigManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    SecurityEventHookConfigFindListRequest request =
        new SecurityEventHookConfigFindListRequest(limit, offset);
    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "findList", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookConfigManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventHookConfigFindRequest request = new SecurityEventHookConfigFindRequest(identifier);
    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public SecurityEventHookConfigManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookConfigurationIdentifier identifier,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfigUpdateRequest updateRequest =
        new SecurityEventHookConfigUpdateRequest(identifier, request);
    SecurityEventHookConfigManagementResult result =
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
  public SecurityEventHookConfigManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SecurityEventHookConfigDeleteRequest request =
        new SecurityEventHookConfigDeleteRequest(identifier);
    SecurityEventHookConfigManagementResult result =
        handler.handle(
            "delete", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
