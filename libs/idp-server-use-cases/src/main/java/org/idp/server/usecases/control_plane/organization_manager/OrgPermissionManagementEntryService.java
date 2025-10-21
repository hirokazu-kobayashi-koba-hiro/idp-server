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
import org.idp.server.control_plane.management.permission.*;
import org.idp.server.control_plane.management.permission.handler.*;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.permission.PermissionIdentifier;
import org.idp.server.core.openid.identity.permission.PermissionQueries;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
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
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionManagementResult result =
        handler.handle(
            "create",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            request,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgPermissionManagementApi.create",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgPermissionManagementApi.create",
            result.tenant(),
            operator,
            oAuthToken,
            (PermissionRegistrationContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public PermissionManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionQueries queries,
      RequestAttributes requestAttributes) {

    PermissionManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            queries,
            requestAttributes,
            false);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgPermissionManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(false);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgPermissionManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public PermissionManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionIdentifier identifier,
      RequestAttributes requestAttributes) {

    PermissionManagementResult result =
        handler.handle(
            "get",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes,
            false);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgPermissionManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(false);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgPermissionManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public PermissionManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionIdentifier identifier,
      PermissionRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionUpdateRequest updateRequest = new PermissionUpdateRequest(identifier, request);
    PermissionManagementResult result =
        handler.handle(
            "update",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgPermissionManagementApi.update",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgPermissionManagementApi.update",
            result.tenant(),
            operator,
            oAuthToken,
            (PermissionUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public PermissionManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    PermissionManagementResult result =
        handler.handle(
            "delete",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgPermissionManagementApi.delete",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgPermissionManagementApi.delete",
            "delete",
            result.tenant(),
            operator,
            oAuthToken,
            (Map<String, Object>) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
