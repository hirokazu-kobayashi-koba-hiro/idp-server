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
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.identity.user.*;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.handler.UserCreationService;
import org.idp.server.control_plane.management.identity.user.handler.UserDeletionContext;
import org.idp.server.control_plane.management.identity.user.handler.UserDeletionService;
import org.idp.server.control_plane.management.identity.user.handler.UserFindListService;
import org.idp.server.control_plane.management.identity.user.handler.UserFindService;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementHandler;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementResult;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementService;
import org.idp.server.control_plane.management.identity.user.handler.UserPasswordUpdateService;
import org.idp.server.control_plane.management.identity.user.handler.UserPatchService;
import org.idp.server.control_plane.management.identity.user.handler.UserUpdateRequest;
import org.idp.server.control_plane.management.identity.user.handler.UserUpdateService;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationRelatedDataVerifier;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerifier;
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
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEventPublisher;
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

  // PoC: Handler/Service pattern components
  UserManagementHandler pocHandler;

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

    // PoC: Initialize Handler/Service pattern components
    initializePocComponents();
  }

  private void initializePocComponents() {
    // Create all services
    UserCreationService userCreationService =
        new UserCreationService(
            userCommandRepository, passwordEncodeDelegation, verifier, managementEventPublisher);

    UserUpdateService userUpdateService =
        new UserUpdateService(userQueryRepository, userCommandRepository, managementEventPublisher);

    UserPatchService userPatchService =
        new UserPatchService(userQueryRepository, userCommandRepository, managementEventPublisher);

    UserPasswordUpdateService userPasswordUpdateService =
        new UserPasswordUpdateService(
            userQueryRepository,
            userCommandRepository,
            passwordEncodeDelegation,
            managementEventPublisher);

    UserDeletionService userDeletionService =
        new UserDeletionService(
            userQueryRepository, userCommandRepository, userLifecycleEventPublisher);

    UserFindListService userFindListService = new UserFindListService(userQueryRepository);

    UserFindService userFindService = new UserFindService(userQueryRepository);

    // Create service map with all operations
    Map<String, UserManagementService<?>> services = new HashMap<>();
    services.put("create", userCreationService);
    services.put("update", userUpdateService);
    services.put("patch", userPatchService);
    services.put("updatePassword", userPasswordUpdateService);
    services.put("delete", userDeletionService);
    services.put("findList", userFindListService);
    services.put("get", userFindService);

    // Create Handler
    this.pocHandler = new UserManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  public UserManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // PoC: Delegate to new Handler/Service pattern with exception-based error handling
    log.info("PoC: Using Handler/Service pattern for user creation");
    UserManagementResult result =
        pocHandler.handle(
            "create", tenantIdentifier, operator, oAuthToken, request, requestAttributes, dryRun);

    // Record audit log (separate transaction via @Async) - always record, success or failure
    if (result.hasException()) {
      // Failure case - tenant is already set in result by Handler
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.create",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

    // Success case - record with context
    AuditLog auditLog =
        AuditLogCreator.create(
            "UserManagementApi.create",
            result.tenant(),
            operator,
            oAuthToken,
            (UserRegistrationContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserQueries queries,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        pocHandler.handle(
            "findList", tenantIdentifier, operator, oAuthToken, queries, requestAttributes, false);

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "UserManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (result.hasException()) {
      throw result.getException();
    }

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        pocHandler.handle(
            "get",
            tenantIdentifier,
            operator,
            oAuthToken,
            userIdentifier,
            requestAttributes,
            false);

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "UserManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (result.hasException()) {
      throw result.getException();
    }

    return result.toResponse(false);
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

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        pocHandler.handle(
            "update",
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.update",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "UserManagementApi.update",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
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

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        pocHandler.handle(
            "patch",
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.patch",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "UserManagementApi.patch",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
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

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        pocHandler.handle(
            "updatePassword",
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.updatePassword",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "UserManagementApi.updatePassword",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        pocHandler.handle(
            "delete",
            tenantIdentifier,
            operator,
            oAuthToken,
            userIdentifier,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.delete",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

    // Success case - record deletion audit log
    UserDeletionContext context = (UserDeletionContext) result.context();
    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "UserManagementApi.delete",
            "delete",
            result.tenant(),
            operator,
            oAuthToken,
            context.beforePayload(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
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
