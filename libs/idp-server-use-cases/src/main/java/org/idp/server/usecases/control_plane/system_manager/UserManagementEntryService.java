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
import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.management.identity.user.*;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.handler.*;
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
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class UserManagementEntryService implements UserManagementApi {

  AuditLogPublisher auditLogPublisher;

  LoggerWrapper log = LoggerWrapper.getLogger(UserManagementEntryService.class);

  // Handler/Service pattern
  private UserManagementHandler handler;

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
    this.auditLogPublisher = auditLogPublisher;

    UserRegistrationRelatedDataVerifier relatedDataVerifier =
        new UserRegistrationRelatedDataVerifier(
            roleQueryRepository, tenantQueryRepository, organizationRepository);
    UserVerifier userVerifier = new UserVerifier(userQueryRepository);
    UserRegistrationVerifier verifier =
        new UserRegistrationVerifier(userVerifier, relatedDataVerifier);

    ManagementEventPublisher managementEventPublisher =
        new ManagementEventPublisher(securityEventPublisher);

    this.handler =
        createHandler(
            tenantQueryRepository,
            userQueryRepository,
            userCommandRepository,
            roleQueryRepository,
            organizationRepository,
            passwordEncodeDelegation,
            verifier,
            userLifecycleEventPublisher,
            managementEventPublisher);
  }

  private UserManagementHandler createHandler(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      RoleQueryRepository roleQueryRepository,
      OrganizationRepository organizationRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserRegistrationVerifier verifier,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      ManagementEventPublisher managementEventPublisher) {

    UserRegistrationRelatedDataVerifier updateVerifier =
        new UserRegistrationRelatedDataVerifier(
            roleQueryRepository, tenantQueryRepository, organizationRepository);

    Map<String, UserManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new UserCreationService(
            userCommandRepository, passwordEncodeDelegation, verifier, managementEventPublisher));
    services.put(
        "update",
        new UserUpdateService(
            userQueryRepository, userCommandRepository, managementEventPublisher));
    services.put(
        "patch",
        new UserPatchService(userQueryRepository, userCommandRepository, managementEventPublisher));
    services.put(
        "updatePassword",
        new UserPasswordUpdateService(
            userQueryRepository,
            userCommandRepository,
            passwordEncodeDelegation,
            managementEventPublisher));
    services.put(
        "delete",
        new UserDeletionService(
            userQueryRepository,
            userCommandRepository,
            userLifecycleEventPublisher,
            managementEventPublisher));
    services.put("findList", new UserFindListService(userQueryRepository));
    services.put("get", new UserFindService(userQueryRepository));
    services.put(
        "updateRoles",
        new UserRolesUpdateService(userQueryRepository, userCommandRepository, updateVerifier));
    services.put(
        "updateTenantAssignments",
        new UserTenantAssignmentsUpdateService(userQueryRepository, userCommandRepository));
    services.put(
        "updateOrganizationAssignments",
        new UserOrganizationAssignmentsUpdateService(userQueryRepository, userCommandRepository));

    return new UserManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  public UserManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    UserManagementResult result =
        handler.handle(
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
        handler.handle(
            "findList", tenantIdentifier, operator, oAuthToken, queries, requestAttributes, false);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

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
        handler.handle(
            "get",
            tenantIdentifier,
            operator,
            oAuthToken,
            userIdentifier,
            requestAttributes,
            false);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

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
        handler.handle(
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
        handler.handle(
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
        handler.handle(
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
        handler.handle(
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

    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateRoles",
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.updateRoles",
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
            "UserManagementApi.updateRoles",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
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

    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateTenantAssignments",
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.updateTenantAssignments",
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
            "UserManagementApi.updateTenantAssignments",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
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

    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateOrganizationAssignments",
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "UserManagementApi.updateOrganizationAssignments",
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
            "UserManagementApi.updateOrganizationAssignments",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
