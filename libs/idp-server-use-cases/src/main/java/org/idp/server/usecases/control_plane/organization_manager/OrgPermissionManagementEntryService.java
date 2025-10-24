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
import org.idp.server.control_plane.management.permission.*;
import org.idp.server.control_plane.management.permission.handler.*;
import org.idp.server.control_plane.management.permission.io.*;
import org.idp.server.core.openid.identity.permission.*;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level permission management entry service.
 *
 * <p>This service implements organization-scoped permission management operations that allow
 * organization administrators to manage permissions within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary PERMISSION_*
 *       permissions
 * </ol>
 *
 * <p>This service provides permission CRUD operations with comprehensive audit logging for
 * organization-level permission management.
 *
 * @see OrgPermissionManagementApi
 * @see OrganizationAccessVerifier
 * @see org.idp.server.usecases.control_plane.system_manager.PermissionManagementEntryService
 */
@Transaction
public class OrgPermissionManagementEntryService implements OrgPermissionManagementApi {

  private final OrgPermissionManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new organization permission management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param permissionQueryRepository the permission query repository
   * @param permissionCommandRepository the permission command repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgPermissionManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      PermissionQueryRepository permissionQueryRepository,
      PermissionCommandRepository permissionCommandRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, PermissionManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new PermissionCreateService(permissionQueryRepository, permissionCommandRepository));
    services.put("findList", new PermissionFindListService(permissionQueryRepository));
    services.put("get", new PermissionFindService(permissionQueryRepository));
    services.put(
        "update",
        new PermissionUpdateService(permissionQueryRepository, permissionCommandRepository));
    services.put(
        "delete",
        new PermissionDeleteService(permissionQueryRepository, permissionCommandRepository));

    this.handler =
        new OrgPermissionManagementHandler(
            services,
            this,
            tenantQueryRepository,
            organizationRepository,
            new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public PermissionManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      PermissionRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public PermissionManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      PermissionQueries queries,
      RequestAttributes requestAttributes) {

    PermissionFindListRequest request = new PermissionFindListRequest(queries);
    PermissionManagementResult result =
        handler.handle(
            "findList", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public PermissionManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      PermissionIdentifier identifier,
      RequestAttributes requestAttributes) {

    PermissionFindRequest request = new PermissionFindRequest(identifier);
    PermissionManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, request, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public PermissionManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      PermissionIdentifier identifier,
      PermissionRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionUpdateRequest updateRequest = new PermissionUpdateRequest(identifier, request);
    PermissionManagementResult result =
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
  public PermissionManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      PermissionIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionDeleteRequest deleteRequest = new PermissionDeleteRequest(identifier);
    PermissionManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            tenantIdentifier,
            deleteRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
