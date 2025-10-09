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
import org.idp.server.control_plane.management.identity.user.validator.UserPasswordUpdateRequestValidator;
import org.idp.server.control_plane.management.identity.user.validator.UserRegistrationRequestValidator;
import org.idp.server.control_plane.management.identity.user.validator.UserRequestValidationResult;
import org.idp.server.control_plane.management.identity.user.validator.UserUpdateRequestValidator;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationRelatedDataVerifier;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerificationResult;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class UserManagementEntryService implements UserManagementApi {

  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  RoleQueryRepository roleQueryRepository;
  OrganizationRepository organizationRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  UserRegistrationVerifier verifier;
  UserRegistrationRelatedDataVerifier updateVerifier;
  UserLifecycleEventPublisher userLifecycleEventPublisher;
  AuditLogPublisher auditLogPublisher;
  ManagementEventPublisher managementEventPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(UserManagementEntryService.class);

  public UserManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      RoleQueryRepository roleQueryRepository,
      OrganizationRepository organizationRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      AuditLogPublisher auditLogPublisher,
      SecurityEventPublisher securityEventPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.roleQueryRepository = roleQueryRepository;
    this.organizationRepository = organizationRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    UserRegistrationRelatedDataVerifier userRegistrationRelatedDataVerifier =
        new UserRegistrationRelatedDataVerifier(
            roleQueryRepository, tenantQueryRepository, organizationRepository);
    UserVerifier userVerifier = new UserVerifier(userQueryRepository);
    this.verifier = new UserRegistrationVerifier(userVerifier, userRegistrationRelatedDataVerifier);
    this.updateVerifier = userRegistrationRelatedDataVerifier;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
    this.auditLogPublisher = auditLogPublisher;
    this.managementEventPublisher = new ManagementEventPublisher(securityEventPublisher);
  }

  @Override
  public UserManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    UserRegistrationRequestValidator validator =
        new UserRegistrationRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    UserRegistrationContextCreator userRegistrationContextCreator =
        new UserRegistrationContextCreator(tenant, request, dryRun, passwordEncodeDelegation);
    UserRegistrationContext context = userRegistrationContextCreator.create();

    UserRegistrationVerificationResult verificationResult = verifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.create(
            "UserManagementApi.create", tenant, operator, oAuthToken, context, requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.register(tenant, context.user());

    // Publish SecurityEvent for user creation
    managementEventPublisher.publish(
        tenant,
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
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserQueries queries,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "UserManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    long totalCount = userQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new UserManagementResponse(UserManagementStatus.OK, response);
    }

    List<User> users = userQueryRepository.findList(tenant, queries);
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
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User user = userQueryRepository.findById(tenant, userIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "UserManagementApi.get", "get", tenant, operator, oAuthToken, requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!user.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    return new UserManagementResponse(UserManagementStatus.OK, user.toMap());
  }

  @Override
  public UserManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User before = userQueryRepository.findById(tenant, userIdentifier);

    UserUpdateRequestValidator validator = new UserUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    UserUpdateContextCreator userUpdateContextCreator =
        new UserUpdateContextCreator(tenant, before, request, dryRun);
    UserUpdateContext context = userUpdateContextCreator.create();

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }
    userCommandRepository.update(tenant, context.after());

    // Publish SecurityEvent for user update
    managementEventPublisher.publish(
        tenant,
        operator,
        context.after(),
        oAuthToken,
        DefaultSecurityEventType.user_edit.toEventType(),
        requestAttributes);

    return context.toResponse();
  }

  @Override
  public UserManagementResponse patch(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("patch");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User before = userQueryRepository.findById(tenant, userIdentifier);

    UserUpdateRequestValidator validator = new UserUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    UserPatchContextCreator patchContextCreator =
        new UserPatchContextCreator(tenant, before, request, dryRun);
    UserUpdateContext context = patchContextCreator.create();

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }
    userCommandRepository.update(tenant, context.after());

    // Publish SecurityEvent for user patch
    managementEventPublisher.publish(
        tenant,
        operator,
        context.after(),
        oAuthToken,
        DefaultSecurityEventType.user_edit.toEventType(),
        requestAttributes);

    return context.toResponse();
  }

  @Override
  public UserManagementResponse updatePassword(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("updatePassword");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User before = userQueryRepository.findById(tenant, userIdentifier);

    UserPasswordUpdateRequestValidator validator =
        new UserPasswordUpdateRequestValidator(request, dryRun);
    UserRequestValidationResult validate = validator.validate();

    UserPasswordUpdateContextCreator passwordUpdateContextCreator =
        new UserPasswordUpdateContextCreator(
            tenant, before, request, dryRun, passwordEncodeDelegation);
    UserUpdateContext context = passwordUpdateContextCreator.create();

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }
    userCommandRepository.updatePassword(tenant, context.after());

    // Publish SecurityEvent for password update
    managementEventPublisher.publish(
        tenant,
        operator,
        context.after(),
        oAuthToken,
        DefaultSecurityEventType.password_change.toEventType(),
        requestAttributes);

    return context.toResponse();
  }

  @Override
  public UserManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User user = userQueryRepository.get(tenant, userIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "UserManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            user.toMaskedValueMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("sub", user.sub());
      response.put("dry_run", true);
      return new UserManagementResponse(UserManagementStatus.OK, response);
    }

    userCommandRepository.delete(tenant, userIdentifier);

    UserLifecycleEvent userLifecycleEvent =
        new UserLifecycleEvent(tenant, user, UserLifecycleType.DELETE);
    userLifecycleEventPublisher.publish(userLifecycleEvent);

    // Publish SecurityEvent for user deletion
    managementEventPublisher.publish(
        tenant,
        operator,
        user,
        oAuthToken,
        DefaultSecurityEventType.user_delete.toEventType(),
        requestAttributes);

    return new UserManagementResponse(UserManagementStatus.NO_CONTENT, Map.of());
  }

  @Override
  public UserManagementResponse updateRoles(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("updateRoles");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User before = userQueryRepository.findById(tenant, userIdentifier);

    //    AuditLog auditLog =
    //        AuditLogCreator.createOnUpdate(
    //            "UserManagementApi.updateRoles", "updateRoles", tenant, operator, oAuthToken,
    // requestAttributes);
    //    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    // Validate roles
    VerificationResult roleValidation = updateVerifier.verifyRoles(tenant, request);
    if (!roleValidation.isValid()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", String.join(", ", roleValidation.errors()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.INVALID_REQUEST, response);
    }

    User updatedUser = before.setRoles(request.roles()).setPermissions(request.permissions());

    if (dryRun) {
      return new UserManagementResponse(UserManagementStatus.OK, updatedUser.toMap());
    }

    userCommandRepository.update(tenant, updatedUser);

    return new UserManagementResponse(UserManagementStatus.OK, updatedUser.toMap());
  }

  @Override
  public UserManagementResponse updateTenantAssignments(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("updateTenantAssignments");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User before = userQueryRepository.findById(tenant, userIdentifier);

    //    AuditLog auditLog =
    //        AuditLogCreator.createOnUpdate(
    //            "UserManagementApi.updateTenantAssignments", "updateTenantAssignments", tenant,
    // operator, oAuthToken, requestAttributes);
    //    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    // Validate tenant assignments
    VerificationResult tenantValidation = updateVerifier.verifyTenantAssignments(request);
    if (!tenantValidation.isValid()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", String.join(", ", tenantValidation.errors()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.INVALID_REQUEST, response);
    }

    User updatedUser = before;

    if (request.currentTenant() != null) {
      updatedUser =
          updatedUser.setCurrentTenantId(
              new org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier(
                  request.currentTenant()));
    }

    if (!request.assignedTenants().isEmpty()) {
      updatedUser = updatedUser.setAssignedTenants(request.assignedTenants());
    }

    if (dryRun) {
      return new UserManagementResponse(UserManagementStatus.OK, updatedUser.toMap());
    }

    userCommandRepository.update(tenant, updatedUser);

    return new UserManagementResponse(UserManagementStatus.OK, updatedUser.toMap());
  }

  @Override
  public UserManagementResponse updateOrganizationAssignments(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("updateOrganizationAssignments");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User before = userQueryRepository.findById(tenant, userIdentifier);

    //        AuditLog auditLog =
    //            AuditLogCreator.createOnUpdate(
    //                "UserManagementApi.updateOrganizationAssignments", tenant, operator,
    // oAuthToken,
    //     requestAttributes);
    //        auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    // Validate organization assignments
    VerificationResult organizationValidation =
        updateVerifier.verifyOrganizationAssignments(tenant, request);
    if (!organizationValidation.isValid()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", String.join(", ", organizationValidation.errors()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.INVALID_REQUEST, response);
    }

    User updatedUser = before;

    if (request.currentOrganizationId() != null) {
      updatedUser =
          updatedUser.setCurrentOrganizationId(
              new org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier(
                  request.currentOrganizationId()));
    }

    if (!request.assignedOrganizations().isEmpty()) {
      updatedUser = updatedUser.setAssignedOrganizations(request.assignedOrganizations());
    }

    if (dryRun) {
      return new UserManagementResponse(UserManagementStatus.OK, updatedUser.toMap());
    }

    userCommandRepository.update(tenant, updatedUser);

    return new UserManagementResponse(UserManagementStatus.OK, updatedUser.toMap());
  }
}
