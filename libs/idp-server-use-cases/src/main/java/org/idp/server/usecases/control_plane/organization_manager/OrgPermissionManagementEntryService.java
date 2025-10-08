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
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.permission.*;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionManagementStatus;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.control_plane.management.permission.validator.PermissionRequestValidationResult;
import org.idp.server.control_plane.management.permission.validator.PermissionRequestValidator;
import org.idp.server.control_plane.management.permission.validator.PermissionUpdateRequestValidator;
import org.idp.server.control_plane.management.permission.verifier.PermissionRegistrationVerificationResult;
import org.idp.server.control_plane.management.permission.verifier.PermissionRegistrationVerifier;
import org.idp.server.control_plane.management.permission.verifier.PermissionVerifier;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.*;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
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

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  PermissionQueryRepository permissionQueryRepository;
  PermissionCommandRepository permissionCommandRepository;
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgPermissionManagementEntryService.class);

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
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.permissionQueryRepository = permissionQueryRepository;
    this.permissionCommandRepository = permissionCommandRepository;
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
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
    AdminPermissions permissions = getRequiredPermissions("create");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    PermissionRequestValidator validator = new PermissionRequestValidator(request, dryRun);
    PermissionRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    PermissionRegistrationContextCreator contextCreator =
        new PermissionRegistrationContextCreator(targetTenant, request, dryRun);
    PermissionRegistrationContext context = contextCreator.create();

    PermissionVerifier permissionVerifier = new PermissionVerifier(permissionQueryRepository);
    PermissionRegistrationVerifier verifier =
        new PermissionRegistrationVerifier(permissionVerifier);
    PermissionRegistrationVerificationResult verificationResult = verifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgPermissionManagementApi.create",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    permissionCommandRepository.register(targetTenant, context.permission());

    return context.toResponse();
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
    AdminPermissions permissions = getRequiredPermissions("findList");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    long totalCount = permissionQueryRepository.findTotalCount(targetTenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());

      AuditLog auditLog =
          AuditLogCreator.createOnRead(
              "OrgPermissionManagementApi.findList",
              "findList",
              targetTenant,
              operator,
              oAuthToken,
              requestAttributes);
      auditLogPublisher.publish(auditLog);

      return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
    }

    List<Permission> permissionList = permissionQueryRepository.findList(targetTenant, queries);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgPermissionManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    Map<String, Object> response = new HashMap<>();
    response.put("list", permissionList.stream().map(Permission::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
    return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
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
    AdminPermissions permissions = getRequiredPermissions("get");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    Permission permission = permissionQueryRepository.find(targetTenant, identifier);

    if (!permission.exists()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "not_found");
      errorResponse.put("error_description", "Permission not found");
      return new PermissionManagementResponse(PermissionManagementStatus.NOT_FOUND, errorResponse);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgPermissionManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return new PermissionManagementResponse(PermissionManagementStatus.OK, permission.toMap());
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
    AdminPermissions permissions = getRequiredPermissions("update");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    Permission before = permissionQueryRepository.find(targetTenant, identifier);

    if (!before.exists()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "not_found");
      errorResponse.put("error_description", "Permission not found");
      return new PermissionManagementResponse(PermissionManagementStatus.NOT_FOUND, errorResponse);
    }

    PermissionUpdateRequestValidator validator =
        new PermissionUpdateRequestValidator(request, dryRun);
    PermissionRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    PermissionUpdateContextCreator contextCreator =
        new PermissionUpdateContextCreator(targetTenant, before, request, dryRun);
    PermissionUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgPermissionManagementApi.update",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (dryRun) {
      return context.toResponse();
    }

    permissionCommandRepository.update(targetTenant, context.after());

    return context.toResponse();
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
    AdminPermissions permissions = getRequiredPermissions("delete");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    Permission permission = permissionQueryRepository.find(targetTenant, identifier);

    if (!permission.exists()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "not_found");
      errorResponse.put("error_description", "Permission not found");
      return new PermissionManagementResponse(PermissionManagementStatus.NOT_FOUND, errorResponse);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgPermissionManagementApi.delete",
            "delete",
            targetTenant,
            operator,
            oAuthToken,
            permission.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", permission.id());
      response.put("dry_run", true);
      return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
    }

    permissionCommandRepository.delete(targetTenant, permission);

    return new PermissionManagementResponse(PermissionManagementStatus.NO_CONTENT, Map.of());
  }
}
