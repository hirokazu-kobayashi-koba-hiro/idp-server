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
import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.identity.user.*;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.validator.*;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationRelatedDataVerifier;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerificationResult;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerifier;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
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
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level user management entry service.
 *
 * <p>This service implements organization-scoped user management operations that allow organization
 * administrators to manage users within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       DefaultAdminPermission
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes and comprehensive
 * audit logging for organization-level user operations.
 *
 * @see OrgUserManagementApi
 * @see OrganizationAccessVerifier
 * @see org.idp.server.usecases.control_plane.system_manager.UserManagementEntryService
 */
@Transaction
public class OrgUserManagementEntryService implements OrgUserManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  RoleQueryRepository roleQueryRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  UserRegistrationVerifier verifier;
  UserRegistrationRelatedDataVerifier updateVerifier;
  UserLifecycleEventPublisher userLifecycleEventPublisher;
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;
  ManagementEventPublisher managementEventPublisher;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgUserManagementEntryService.class);

  /**
   * Creates a new organization user management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param userQueryRepository the user query repository
   * @param userCommandRepository the user command repository
   * @param roleQueryRepository the role query repository
   * @param passwordEncodeDelegation the password encode delegation
   * @param userLifecycleEventPublisher the user lifecycle event publisher
   * @param auditLogPublisher the audit log publisher
   */
  public OrgUserManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      RoleQueryRepository roleQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      AuditLogPublisher auditLogPublisher,
      SecurityEventPublisher securityEventPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.roleQueryRepository = roleQueryRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    UserRegistrationRelatedDataVerifier userRegistrationRelatedDataVerifier =
        new UserRegistrationRelatedDataVerifier(
            roleQueryRepository, tenantQueryRepository, organizationRepository);
    UserVerifier userVerifier = new UserVerifier(userQueryRepository);
    this.verifier = new UserRegistrationVerifier(userVerifier, userRegistrationRelatedDataVerifier);
    this.updateVerifier = userRegistrationRelatedDataVerifier;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
    this.managementEventPublisher = new ManagementEventPublisher(securityEventPublisher);
  }

  @Override
  public UserManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    UserRegistrationRequestValidator validator =
        new UserRegistrationRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    UserRegistrationContextCreator contextCreator =
        new UserRegistrationContextCreator(targetTenant, request, dryRun, passwordEncodeDelegation);
    UserRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgUserManagementApi.create",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    UserRegistrationVerificationResult verify = verifier.verify(context);

    if (!verify.isValid()) {
      return verify.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.register(targetTenant, context.user());

    //    UserLifecycleEvent event =
    //        new UserLifecycleEvent(
    //            UserLifecycleType.USER_CREATED,
    //            targetTenant,
    //            context.user().userIdentifier(),
    //            context.user());
    //    userLifecycleEventPublisher.publish(event);

    // Publish SecurityEvent for user creation
    managementEventPublisher.publish(
        targetTenant,
        operator,
        context.user(),
        oAuthToken,
        DefaultSecurityEventType.user_create.toEventType(),
        requestAttributes);

    return context.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserQueries queries,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgUserManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    long totalCount = userQueryRepository.findTotalCount(targetTenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", totalCount);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new UserManagementResponse(UserManagementStatus.OK, response);
    }

    List<User> users = userQueryRepository.findList(targetTenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", users.stream().map(User::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new UserManagementResponse(UserManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    User user = userQueryRepository.get(targetTenant, userIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgUserManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!user.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    return new UserManagementResponse(UserManagementStatus.OK, user.toMap());
  }

  @Override
  public UserManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    User before = userQueryRepository.get(targetTenant, userIdentifier);

    UserUpdateRequestValidator validator = new UserUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    UserUpdateContextCreator contextCreator =
        new UserUpdateContextCreator(targetTenant, before, request, dryRun);
    UserUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.update",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.update(targetTenant, context.after());

    //    UserLifecycleEvent event =
    //        new UserLifecycleEvent(
    //            UserLifecycleType.USER_UPDATED,
    //            targetTenant,
    //            context.after().userIdentifier(),
    //            context.after());
    //    userLifecycleEventPublisher.publish(event);

    // Publish SecurityEvent for user update
    managementEventPublisher.publish(
        targetTenant,
        operator,
        context.after(),
        oAuthToken,
        DefaultSecurityEventType.user_edit.toEventType(),
        requestAttributes);

    return context.toResponse();
  }

  @Override
  public UserManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    User user = userQueryRepository.get(targetTenant, userIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgUserManagementApi.delete",
            "delete",
            targetTenant,
            operator,
            oAuthToken,
            user.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!user.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "User deletion simulated successfully");
      response.put("sub", user.sub());
      response.put("dry_run", true);
      return new UserManagementResponse(UserManagementStatus.OK, response);
    }

    userCommandRepository.delete(targetTenant, user.userIdentifier());

    //    UserLifecycleEvent event =
    //        new UserLifecycleEvent(
    //            UserLifecycleType.USER_DELETED,
    //            targetTenant,
    //            user.userIdentifier(),
    //            user);
    //    userLifecycleEventPublisher.publish(event);

    // Publish SecurityEvent for user deletion
    managementEventPublisher.publish(
        targetTenant,
        operator,
        user,
        oAuthToken,
        DefaultSecurityEventType.user_delete.toEventType(),
        requestAttributes);

    return new UserManagementResponse(UserManagementStatus.NO_CONTENT, Map.of());
  }

  @Override
  public UserManagementResponse patch(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("patch");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    User before = userQueryRepository.get(targetTenant, userIdentifier);

    UserUpdateRequestValidator validator = new UserUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    UserPatchContextCreator contextCreator =
        new UserPatchContextCreator(targetTenant, before, request, dryRun);
    UserUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.patch",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.update(targetTenant, context.after());

    // Publish SecurityEvent for user patch
    managementEventPublisher.publish(
        targetTenant,
        operator,
        context.after(),
        oAuthToken,
        DefaultSecurityEventType.user_edit.toEventType(),
        requestAttributes);

    return context.toResponse();
  }

  @Override
  public UserManagementResponse updatePassword(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("updatePassword");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    User before = userQueryRepository.get(targetTenant, userIdentifier);

    UserPasswordUpdateRequestValidator validator =
        new UserPasswordUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    UserPasswordUpdateContextCreator contextCreator =
        new UserPasswordUpdateContextCreator(
            targetTenant, before, request, dryRun, passwordEncodeDelegation);
    UserUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.updatePassword",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.updatePassword(targetTenant, context.after());

    // Publish SecurityEvent for password update
    managementEventPublisher.publish(
        targetTenant,
        operator,
        context.after(),
        oAuthToken,
        DefaultSecurityEventType.password_change.toEventType(),
        requestAttributes);

    return context.toResponse();
  }

  @Override
  public UserManagementResponse updateRoles(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("updateRoles");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    User before = userQueryRepository.get(targetTenant, userIdentifier);

    UserRolesUpdateRequestValidator validator =
        new UserRolesUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    UserRolesUpdateContextCreator contextCreator =
        new UserRolesUpdateContextCreator(targetTenant, before, request, dryRun);
    UserUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.updateRoles",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    // Validate roles
    VerificationResult roleValidation = updateVerifier.verifyRoles(targetTenant, request);

    if (!roleValidation.isValid()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "roles verification is failed");
      response.put("error_messages", roleValidation.errors());
      return new UserManagementResponse(UserManagementStatus.INVALID_REQUEST, response);
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.update(targetTenant, context.after());
    return context.toResponse();
  }

  @Override
  public UserManagementResponse updateTenantAssignments(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("updateTenantAssignments");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    UserTenantAssignmentsUpdateRequestValidator validator =
        new UserTenantAssignmentsUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    User before = userQueryRepository.get(targetTenant, userIdentifier);

    UserTenantAssignmentsUpdateContextCreator contextCreator =
        new UserTenantAssignmentsUpdateContextCreator(targetTenant, before, request, dryRun);
    UserUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.updateTenantAssignments",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.update(targetTenant, context.after());
    return context.toResponse();
  }

  @Override
  public UserManagementResponse updateOrganizationAssignments(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("updateOrganizationAssignments");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    UserOrganizationAssignmentsUpdateRequestValidator validator =
        new UserOrganizationAssignmentsUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    User before = userQueryRepository.get(targetTenant, userIdentifier);

    UserOrganizationAssignmentsUpdateContextCreator contextCreator =
        new UserOrganizationAssignmentsUpdateContextCreator(targetTenant, before, request, dryRun);
    UserUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.updateOrganizationAssignments",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.update(targetTenant, context.after());
    return context.toResponse();
  }
}
