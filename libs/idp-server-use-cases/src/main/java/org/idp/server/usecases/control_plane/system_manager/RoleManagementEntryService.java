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
import org.idp.server.control_plane.management.role.*;
import org.idp.server.control_plane.management.role.handler.*;
import org.idp.server.control_plane.management.role.io.*;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.identity.role.RoleIdentifier;
import org.idp.server.core.openid.identity.role.RoleQueries;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * System-level role management entry service.
 *
 * <p>This service implements role management operations using the Handler/Service pattern.
 * Responsibilities include:
 *
 * <ul>
 *   <li>Orchestrating Handler/Service components
 *   <li>Audit log publication
 *   <li>HTTP response generation from Result objects
 * </ul>
 *
 * @see RoleManagementApi
 * @see RoleManagementHandler
 */
@Transaction
public class RoleManagementEntryService implements RoleManagementApi {

  private final RoleManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  /**
   * Creates a new role management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param roleQueryRepository the role query repository
   * @param roleCommandRepository the role command repository
   * @param permissionQueryRepository the permission query repository
   * @param auditLogPublisher the audit log publisher
   */
  public RoleManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      RoleQueryRepository roleQueryRepository,
      RoleCommandRepository roleCommandRepository,
      PermissionQueryRepository permissionQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, RoleManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new RoleCreateService(
            roleQueryRepository, roleCommandRepository, permissionQueryRepository));
    services.put("findList", new RoleFindListService(roleQueryRepository));
    services.put("get", new RoleFindService(roleQueryRepository));
    services.put(
        "update",
        new RoleUpdateService(
            roleQueryRepository, roleCommandRepository, permissionQueryRepository));
    services.put(
        "removePermissions",
        new RoleRemovePermissionsService(
            roleQueryRepository, roleCommandRepository, permissionQueryRepository));
    services.put("delete", new RoleDeleteService(roleQueryRepository, roleCommandRepository));

    this.handler = new RoleManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public RoleManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    RoleManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public RoleManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleQueries queries,
      RequestAttributes requestAttributes) {

    RoleManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            new RoleFindListRequest(queries),
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public RoleManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleIdentifier identifier,
      RequestAttributes requestAttributes) {

    RoleManagementResult result =
        handler.handle(
            "get",
            authenticationContext,
            tenantIdentifier,
            new RoleFindRequest(identifier),
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public RoleManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleIdentifier identifier,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    RoleUpdateRequest updateRequest = new RoleUpdateRequest(identifier, request);
    RoleManagementResult result =
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
  public RoleManagementResponse removePermissions(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleIdentifier identifier,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    RoleRemovePermissionsRequest removeRequest =
        new RoleRemovePermissionsRequest(identifier, request);
    RoleManagementResult result =
        handler.handle(
            "removePermissions",
            authenticationContext,
            tenantIdentifier,
            removeRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public RoleManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    RoleManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            tenantIdentifier,
            new RoleDeleteRequest(identifier),
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
