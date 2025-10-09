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
import org.idp.server.control_plane.management.role.*;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.control_plane.management.role.validator.RoleRequestValidationResult;
import org.idp.server.control_plane.management.role.validator.RoleRequestValidator;
import org.idp.server.control_plane.management.role.verifier.RoleRegistrationVerificationResult;
import org.idp.server.control_plane.management.role.verifier.RoleRegistrationVerifier;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.*;
import org.idp.server.core.openid.identity.role.*;
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
 * Organization-level role management entry service.
 *
 * <p>This service implements organization-scoped role management operations that allow organization
 * administrators to manage roles within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary ROLE_*
 *       permissions
 * </ol>
 *
 * <p>This service provides role CRUD operations with comprehensive audit logging for
 * organization-level role management.
 *
 * @see OrgRoleManagementApi
 * @see OrganizationAccessVerifier
 * @see org.idp.server.usecases.control_plane.system_manager.RoleManagementEntryService
 */
@Transaction
public class OrgRoleManagementEntryService implements OrgRoleManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  RoleQueryRepository roleQueryRepository;
  RoleCommandRepository roleCommandRepository;
  PermissionQueryRepository permissionQueryRepository;
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgRoleManagementEntryService.class);

  /**
   * Creates a new organization role management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param roleQueryRepository the role query repository
   * @param roleCommandRepository the role command repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgRoleManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      RoleQueryRepository roleQueryRepository,
      RoleCommandRepository roleCommandRepository,
      PermissionQueryRepository permissionQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.roleQueryRepository = roleQueryRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.permissionQueryRepository = permissionQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  public RoleManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleRequest request,
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
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    RoleRequestValidator validator = new RoleRequestValidator(request, dryRun);
    RoleRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    Roles roles = roleQueryRepository.findAll(targetTenant);
    Permissions permissionList = permissionQueryRepository.findAll(targetTenant);
    RoleRegistrationContextCreator contextCreator =
        new RoleRegistrationContextCreator(targetTenant, request, roles, permissionList, dryRun);
    RoleRegistrationContext context = contextCreator.create();

    RoleRegistrationVerifier verifier = new RoleRegistrationVerifier();
    RoleRegistrationVerificationResult verificationResult = verifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgRoleManagementApi.create",
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

    roleCommandRepository.register(targetTenant, context.role());

    return context.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public RoleManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleQueries queries,
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
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    long totalCount = roleQueryRepository.findTotalCount(targetTenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());

      AuditLog auditLog =
          AuditLogCreator.createOnRead(
              "OrgRoleManagementApi.findList",
              "findList",
              targetTenant,
              operator,
              oAuthToken,
              requestAttributes);
      auditLogPublisher.publish(auditLog);

      return new RoleManagementResponse(RoleManagementStatus.OK, response);
    }

    List<Role> roleList = roleQueryRepository.findList(targetTenant, queries);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgRoleManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    Map<String, Object> response = new HashMap<>();
    response.put("list", roleList.stream().map(Role::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
    return new RoleManagementResponse(RoleManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public RoleManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier identifier,
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
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    Role role = roleQueryRepository.find(targetTenant, identifier);

    if (!role.exists()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "not_found");
      errorResponse.put("error_description", "Role not found");
      return new RoleManagementResponse(RoleManagementStatus.NOT_FOUND, errorResponse);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgRoleManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return new RoleManagementResponse(RoleManagementStatus.OK, role.toMap());
  }

  @Override
  public RoleManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier identifier,
      RoleRequest request,
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
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    Role before = roleQueryRepository.find(targetTenant, identifier);

    if (!before.exists()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "not_found");
      errorResponse.put("error_description", "Role not found");
      return new RoleManagementResponse(RoleManagementStatus.NOT_FOUND, errorResponse);
    }

    RoleRequestValidator validator = new RoleRequestValidator(request, dryRun);
    RoleRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    Roles roles = roleQueryRepository.findAll(targetTenant);
    Permissions permissionList = permissionQueryRepository.findAll(targetTenant);
    RoleUpdateContextCreator contextCreator =
        new RoleUpdateContextCreator(targetTenant, before, request, roles, permissionList, dryRun);
    RoleUpdateContext context = contextCreator.create();

    RoleRegistrationVerifier verifier = new RoleRegistrationVerifier();
    RoleRegistrationVerificationResult verificationResult = verifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgRoleManagementApi.update",
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

    roleCommandRepository.update(targetTenant, context.after());

    return context.toResponse();
  }

  @Override
  public RoleManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier identifier,
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
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    Role role = roleQueryRepository.find(targetTenant, identifier);

    if (!role.exists()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "not_found");
      errorResponse.put("error_description", "Role not found");
      return new RoleManagementResponse(RoleManagementStatus.NOT_FOUND, errorResponse);
    }

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgRoleManagementApi.delete",
            "delete",
            targetTenant,
            operator,
            oAuthToken,
            role.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", role.id());
      response.put("dry_run", true);
      return new RoleManagementResponse(RoleManagementStatus.OK, response);
    }

    roleCommandRepository.delete(targetTenant, role);

    return new RoleManagementResponse(RoleManagementStatus.NO_CONTENT, Map.of());
  }
}
